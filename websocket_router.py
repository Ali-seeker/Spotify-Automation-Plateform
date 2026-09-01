from datetime import datetime, timezone
import json
import logging
import os
import uuid
from fastapi import APIRouter, WebSocket, WebSocketDisconnect

from database import SessionLocal
from models import Device, Run, RunEvent
from websocket_manager import manager

logger = logging.getLogger("WebSocketRouter")

router = APIRouter(tags=["WebSocket Connections"])


@router.websocket("/ws/device")
@router.websocket("/ws/device/{path_device_id}")
async def websocket_device_endpoint(websocket: WebSocket, path_device_id: str = None):
    """
    WebSocket connection endpoint for Android Spotify Bot devices.
    Performs DEVICE_HELLO authentication against DEVICE_SHARED_SECRET.
    Sends HELLO_ACK on success, HELLO_REJECT on failure.
    Tracks ONLINE/OFFLINE device status in SQLite DB with stale connection safety.
    Processes STEP_STARTED, STEP_OK, STEP_FAILED, COMMAND_DONE events and broadcasts to frontend clients.
    """
    await websocket.accept()
    logger.info(f"Incoming WebSocket connection from {websocket.client}")

    # 1. Wait for DEVICE_HELLO handshake message
    try:
        raw_hello = await websocket.receive_text()
        hello_data = json.loads(raw_hello)
    except Exception as e:
        logger.error(f"Failed to receive/parse initial DEVICE_HELLO message: {e}")
        await websocket.send_json({"type": "HELLO_REJECT", "reason": "INVALID_MESSAGE_FORMAT"})
        await websocket.close(code=1008)
        return

    if not isinstance(hello_data, dict) or hello_data.get("type") != "DEVICE_HELLO":
        logger.warning(f"Expected type 'DEVICE_HELLO', received: {hello_data}")
        await websocket.send_json({"type": "HELLO_REJECT", "reason": "EXPECTED_DEVICE_HELLO"})
        await websocket.close(code=1008)
        return

    device_id = hello_data.get("device_id") or path_device_id
    device_auth_token = hello_data.get("device_auth_token")
    app_version = hello_data.get("app_version")
    capabilities = hello_data.get("capabilities")

    if not device_id or not device_auth_token:
        logger.warning(f"Missing device_id or device_auth_token in DEVICE_HELLO")
        await websocket.send_json({"type": "HELLO_REJECT", "reason": "MISSING_CREDENTIALS"})
        await websocket.close(code=1008)
        return

    # 2. Validate device_auth_token against DEVICE_SHARED_SECRET
    shared_secret = os.getenv("DEVICE_SHARED_SECRET")
    if device_auth_token != shared_secret:
        logger.warning(f"Authentication failed for device '{device_id}': invalid device_auth_token")
        await websocket.send_json({"type": "HELLO_REJECT", "reason": "INVALID_DEVICE_AUTH"})
        await websocket.close(code=1008)
        return

    # 3. Successful Authentication Handshake
    session_id = f"sess_{uuid.uuid4().hex[:10]}"
    await manager.connect_device(device_id, websocket, session_id)

    # 4. Update Device in SQLite DB to ONLINE
    db = SessionLocal()
    try:
        device = db.query(Device).filter(Device.device_id == device_id).first()
        if not device:
            device = Device(
                device_id=device_id,
                status="ONLINE",
                last_seen=datetime.now(timezone.utc)
            )
            device.capabilities = capabilities
            db.add(device)
        else:
            device.status = "ONLINE"
            if capabilities is not None:
                device.capabilities = capabilities
            device.last_seen = datetime.now(timezone.utc)
        db.commit()
    finally:
        db.close()

    # 5. Send HELLO_ACK & Broadcast status to frontend clients
    await websocket.send_json({
        "type": "HELLO_ACK",
        "device_id": device_id,
        "status": "AUTHENTICATED"
    })

    await manager.broadcast_to_frontend({
        "type": "DEVICE_STATUS",
        "device_id": device_id,
        "status": "ONLINE",
        "last_seen": datetime.now(timezone.utc).isoformat()
    })

    logger.info(f"Device '{device_id}' authenticated successfully (session: {session_id})")

    # 6. Event Processing Loop
    try:
        while True:
            raw_msg = await websocket.receive_text()
            try:
                msg = json.loads(raw_msg)
            except Exception:
                logger.warning(f"Received non-JSON text from device '{device_id}': {raw_msg}")
                continue

            msg_type = msg.get("type")
            run_id = msg.get("run_id")
            payload = msg.get("payload") or msg

            logger.info(f"Device '{device_id}' sent event '{msg_type}' for run '{run_id}'")

            # Update device last_seen in DB
            db_event = SessionLocal()
            try:
                dev = db_event.query(Device).filter(Device.device_id == device_id).first()
                if dev:
                    dev.last_seen = datetime.now(timezone.utc)

                # Persist event to run_events & update Run status
                if run_id and msg_type in ["STEP_STARTED", "STEP_OK", "STEP_FAILED", "COMMAND_DONE"]:
                    event = RunEvent(
                        run_id=run_id,
                        event_type=msg_type,
                        timestamp=datetime.now(timezone.utc)
                    )
                    event.payload = payload
                    db_event.add(event)

                    run_record = db_event.query(Run).filter(Run.run_id == run_id).first()
                    if run_record:
                        if msg_type == "STEP_FAILED":
                            run_record.status = "FAILED"
                            run_record.end_time = datetime.now(timezone.utc)
                        elif msg_type == "COMMAND_DONE":
                            final_status = msg.get("status") or ("SUCCESS" if msg.get("result") is not False else "FAILED")
                            run_record.status = final_status
                            run_record.end_time = datetime.now(timezone.utc)

                db_event.commit()
            finally:
                db_event.close()

            # Broadcast event to frontend clients
            await manager.broadcast_to_frontend({
                "type": "DEVICE_EVENT",
                "device_id": device_id,
                "event_type": msg_type,
                "run_id": run_id,
                "payload": payload,
                "timestamp": datetime.now(timezone.utc).isoformat()
            })

    except WebSocketDisconnect:
        logger.info(f"WebSocket closed for device '{device_id}' (session: {session_id})")
    except Exception as e:
        logger.error(f"WebSocket exception for device '{device_id}': {e}")
    finally:
        # 7. Clean Disconnect Handling with Stale Session Protection
        is_removed = manager.disconnect_device(device_id, session_id)
        if is_removed:
            db_disc = SessionLocal()
            try:
                dev_disc = db_disc.query(Device).filter(Device.device_id == device_id).first()
                if dev_disc:
                    dev_disc.status = "OFFLINE"
                    dev_disc.last_seen = datetime.now(timezone.utc)
                    db_disc.commit()
            finally:
                db_disc.close()

            await manager.broadcast_to_frontend({
                "type": "DEVICE_STATUS",
                "device_id": device_id,
                "status": "OFFLINE",
                "last_seen": datetime.now(timezone.utc).isoformat()
            })
            logger.info(f"Device '{device_id}' marked OFFLINE")


@router.websocket("/ws/frontend")
async def websocket_frontend_endpoint(websocket: WebSocket):
    """
    WebSocket channel for frontend web dashboards to subscribe to live device status and run events.
    """
    await manager.connect_frontend(websocket)
    try:
        while True:
            # Keep frontend connection alive
            await websocket.receive_text()
    except WebSocketDisconnect:
        manager.disconnect_frontend(websocket)
    except Exception as e:
        logger.error(f"Frontend WebSocket exception: {e}")
        manager.disconnect_frontend(websocket)
