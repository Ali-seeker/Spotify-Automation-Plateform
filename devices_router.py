from datetime import datetime, timezone
from typing import List
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from database import get_db
from models import Device, User
from schemas import DeviceCreate, DeviceResponse, DeviceUpdate
from security import get_current_user

router = APIRouter(prefix="/devices", tags=["Device Management"])


@router.post("", response_model=DeviceResponse, status_code=status.HTTP_201_CREATED)
def create_device(
    device_in: DeviceCreate,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    """
    Creates and persists a new Android device record.
    Requires authentication.
    """
    existing = db.query(Device).filter(Device.device_id == device_in.device_id).first()
    if existing:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Device with ID '{device_in.device_id}' already exists"
        )

    device = Device(
        device_id=device_in.device_id,
        status=device_in.status or "IDLE",
        last_seen=device_in.last_seen or datetime.now(timezone.utc),
    )
    device.capabilities = device_in.capabilities
    db.add(device)
    db.commit()
    db.refresh(device)
    return device


@router.get("", response_model=List[DeviceResponse])
def fetch_devices(db: Session = Depends(get_db)):
    """
    Fetches all registered devices.
    """
    return db.query(Device).all()


@router.get("/{identifier}", response_model=DeviceResponse)
def fetch_device(identifier: str, db: Session = Depends(get_db)):
    """
    Fetches a specific device by string device_id or integer DB id.
    Raises 404 if not found.
    """
    device = None
    if identifier.isdigit():
        device = db.query(Device).filter(Device.id == int(identifier)).first()
    if not device:
        device = db.query(Device).filter(Device.device_id == identifier).first()

    if not device:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Device '{identifier}' not found"
        )
    return device


@router.put("/{identifier}", response_model=DeviceResponse)
def update_device(
    identifier: str,
    device_in: DeviceUpdate,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    """
    Updates an existing device record and status.
    Requires authentication.
    """
    device = None
    if identifier.isdigit():
        device = db.query(Device).filter(Device.id == int(identifier)).first()
    if not device:
        device = db.query(Device).filter(Device.device_id == identifier).first()

    if not device:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Device '{identifier}' not found"
        )

    if device_in.status is not None:
        device.status = device_in.status
    if device_in.capabilities is not None:
        device.capabilities = device_in.capabilities
    if device_in.last_seen is not None:
        device.last_seen = device_in.last_seen
    else:
        device.last_seen = datetime.now(timezone.utc)

    db.commit()
    db.refresh(device)
    return device
