from datetime import datetime, timezone
import logging
from fastapi import APIRouter, WebSocket, WebSocketDisconnect
from database import SessionLocal
from models import Device
from websocket_manager import manager

logger = logging.getLogger("WebSocketRouter")

router = APIRouter(tags=["WebSocket Connections"])


@router.websocket("/ws/device/{device_id}")
async def websocket_endpoint(websocket: WebSocket, device_id: str):
    """
    WebSocket connection endpoint for Android devices.
    Updates device status to IDLE on connect and OFFLINE on disconnect.
    """
    db = SessionLocal()
    try:
        device = db.query(Device).filter(Device.device_id == device_id).first()
        if not device:
            device = Device(device_id=device_id, status="IDLE", last_seen=datetime.now(timezone.utc))
            db.add(device)
        else:
            device.status = "IDLE"
            device.last_seen = datetime.now(timezone.utc)
        db.commit()
    finally:
        db.close()

    await manager.connect(device_id, websocket)

    try:
        while True:
            data = await websocket.receive_text()
            logger.info(f"Received WebSocket message from device '{device_id}': {data}")
    except WebSocketDisconnect:
        manager.disconnect(device_id)
        db_disc = SessionLocal()
        try:
            device_disc = db_disc.query(Device).filter(Device.device_id == device_id).first()
            if device_disc:
                device_disc.status = "OFFLINE"
                device_disc.last_seen = datetime.now(timezone.utc)
                db_disc.commit()
        finally:
            db_disc.close()
        logger.info(f"Device '{device_id}' WebSocket disconnected cleanly")
