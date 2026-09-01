from datetime import datetime, timezone
from typing import List
import uuid
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from database import get_db
from models import Device, Run, RunEvent, Task, User
from schemas import (
    RunCreate,
    RunEventCreate,
    RunEventResponse,
    RunResponse,
    RunUpdate,
)
from security import get_current_user

router = APIRouter(prefix="/runs", tags=["Run & Event History"])


@router.post("", response_model=RunResponse, status_code=status.HTTP_201_CREATED)
def create_run(
    run_in: RunCreate,
    db: Session = Depends(get_db)
):
    """
    Creates and persists a new task execution run record.
    Associates valid task_id and device_id.
    """
    task = db.query(Task).filter(Task.id == run_in.task_id).first()
    if not task:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Task ID {run_in.task_id} not found"
        )

    device = db.query(Device).filter(Device.id == run_in.device_id).first()
    if not device:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Device ID {run_in.device_id} not found"
        )

    generated_run_id = f"run_{uuid.uuid4().hex[:12]}"
    run = Run(
        run_id=generated_run_id,
        task_id=run_in.task_id,
        device_id=run_in.device_id,
        status=run_in.status or "RUNNING",
        start_time=datetime.now(timezone.utc)
    )
    db.add(run)
    db.commit()
    db.refresh(run)
    return run


@router.get("", response_model=List[RunResponse])
def fetch_runs(db: Session = Depends(get_db)):
    """
    Fetches all execution runs.
    """
    return db.query(Run).all()


@router.get("/{identifier}", response_model=RunResponse)
def fetch_run(identifier: str, db: Session = Depends(get_db)):
    """
    Fetches a specific run by run_id (UUID) or integer DB id.
    """
    run = None
    if identifier.isdigit():
        run = db.query(Run).filter(Run.id == int(identifier)).first()
    if not run:
        run = db.query(Run).filter(Run.run_id == identifier).first()

    if not run:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Run '{identifier}' not found"
        )
    return run


@router.put("/{identifier}", response_model=RunResponse)
def update_run(
    identifier: str,
    run_in: RunUpdate,
    db: Session = Depends(get_db)
):
    """
    Updates status (RUNNING, SUCCESS, FAILED) and end_time for an execution run.
    """
    run = None
    if identifier.isdigit():
        run = db.query(Run).filter(Run.id == int(identifier)).first()
    if not run:
        run = db.query(Run).filter(Run.run_id == identifier).first()

    if not run:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Run '{identifier}' not found"
        )

    if run_in.status is not None:
        run.status = run_in.status
        if run_in.status in ["SUCCESS", "FAILED"] and not run_in.end_time:
            run.end_time = datetime.now(timezone.utc)

    if run_in.end_time is not None:
        run.end_time = run_in.end_time

    db.commit()
    db.refresh(run)
    return run


@router.post("/{identifier}/events", response_model=RunEventResponse, status_code=status.HTTP_201_CREATED)
def record_run_event(
    identifier: str,
    event_in: RunEventCreate,
    db: Session = Depends(get_db)
):
    """
    Stores a step-level execution event (STEP_STARTED, STEP_OK, STEP_FAILED) with full JSON payload.
    """
    run = None
    if identifier.isdigit():
        run = db.query(Run).filter(Run.id == int(identifier)).first()
    if not run:
        run = db.query(Run).filter(Run.run_id == identifier).first()

    if not run:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Run '{identifier}' not found"
        )

    event = RunEvent(
        run_id=run.run_id,
        event_type=event_in.event_type,
        timestamp=datetime.now(timezone.utc)
    )
    event.payload = event_in.payload
    db.add(event)
    db.commit()
    db.refresh(event)
    return event


@router.get("/{identifier}/events", response_model=List[RunEventResponse])
def fetch_run_events(identifier: str, db: Session = Depends(get_db)):
    """
    Fetches all execution events associated with a specific run.
    """
    run = None
    if identifier.isdigit():
        run = db.query(Run).filter(Run.id == int(identifier)).first()
    if not run:
        run = db.query(Run).filter(Run.run_id == identifier).first()

    if not run:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Run '{identifier}' not found"
        )

    return db.query(RunEvent).filter(RunEvent.run_id == run.run_id).all()
