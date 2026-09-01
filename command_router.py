from datetime import datetime, timezone
import os
import uuid
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from command_queue import queue_manager
from database import get_db
from models import Device, Run, RunEvent, Task, User
from schemas import SendCommandRequest, SendCommandResponse
from security import get_current_user
from websocket_manager import manager

router = APIRouter(tags=["Command Management"])


@router.post("/send_command", response_model=SendCommandResponse, status_code=status.HTTP_200_OK)
async def send_command(
    request: SendCommandRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    """
    Triggers an automation task execution on a target Android device.
    Requires JWT authentication.
    Validates task, device, and active WebSocket connection.
    Generates UUID command_id, UTC timestamp, enqueues command, and creates run history.
    """
    # 1. Validate Task Existence
    task = None
    if request.task_id.isdigit():
        task = db.query(Task).filter(Task.id == int(request.task_id)).first()
    if not task:
        task = db.query(Task).filter(Task.task_name == request.task_id).first()

    if not task:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="TASK_NOT_FOUND"
        )

    # 2. Validate Device Existence
    device = None
    if request.device_id.isdigit():
        device = db.query(Device).filter(Device.id == int(request.device_id)).first()
    if not device:
        device = db.query(Device).filter(Device.device_id == request.device_id).first()

    if not device:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="DEVICE_NOT_FOUND"
        )

    # 3. Check Active WebSocket Connection
    if not manager.is_connected(device.device_id):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="DEVICE_OFFLINE"
        )

    # 4. Generate UUID command_id, UTC issued_at, and read TTL
    command_id = f"cmd_{uuid.uuid4().hex[:12]}"
    issued_at = datetime.now(timezone.utc).isoformat()
    ttl_ms = int(os.getenv("COMMAND_TTL_MS", "180000"))

    # 5. Create Run Record & STEP_STARTED Event in Database
    generated_run_id = f"run_{uuid.uuid4().hex[:12]}"
    run = Run(
        run_id=generated_run_id,
        task_id=task.id,
        device_id=device.id,
        status="RUNNING",
        start_time=datetime.now(timezone.utc)
    )
    db.add(run)
    db.commit()
    db.refresh(run)

    event = RunEvent(
        run_id=run.run_id,
        event_type="STEP_STARTED",
        timestamp=datetime.now(timezone.utc)
    )
    event.payload = {
        "command_id": command_id,
        "task_name": task.task_name,
        "action_type": task.action_type,
        "device_id": device.device_id,
        "issued_at": issued_at,
        "ttl_ms": ttl_ms
    }
    db.add(event)
    db.commit()

    # 6. Construct Command Payload
    command_payload = {
        "command_id": command_id,
        "task_id": str(task.id),
        "task_name": task.task_name,
        "device_id": device.device_id,
        "action_type": task.action_type,
        "search_query": task.search_query,
        "action_params": task.action_params,
        "issued_at": issued_at,
        "ttl_ms": ttl_ms,
        "run_id": run.run_id
    }

    # 7. Enqueue Command into Per-Device Queue Engine
    await queue_manager.enqueue_command(device.device_id, command_payload)

    return SendCommandResponse(
        command_id=command_id,
        task_id=str(task.id),
        device_id=device.device_id,
        run_id=run.run_id,
        status="QUEUED"
    )
