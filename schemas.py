from datetime import datetime
from typing import Any, Dict, List, Optional
from pydantic import BaseModel, ConfigDict, Field, field_validator

# --- Auth Schemas ---
class LoginRequest(BaseModel):
    username: str = Field(..., description="Username for login", min_length=1)
    password: str = Field(..., description="Password for login", min_length=1)

    @field_validator("username", "password")
    @classmethod
    def not_empty(cls, value: str) -> str:
        if not value or not value.strip():
            raise ValueError("Username and password cannot be empty")
        return value

class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"

class UserResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    username: str
    is_active: bool

# --- Device Schemas ---
class DeviceCreate(BaseModel):
    device_id: str = Field(..., description="Unique Android device ID", min_length=1)
    status: Optional[str] = "IDLE"
    capabilities: Optional[Any] = None
    last_seen: Optional[datetime] = None

class DeviceUpdate(BaseModel):
    status: Optional[str] = None
    capabilities: Optional[Any] = None
    last_seen: Optional[datetime] = None

class DeviceResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    device_id: str
    status: str
    capabilities: Optional[Any] = None
    last_seen: Optional[datetime] = None

# --- Task Schemas ---
class TaskCreate(BaseModel):
    task_name: str = Field(..., description="Name of the task", min_length=1)
    action_type: str = Field(..., description="Action type e.g. CLICK, SEARCH, PLAY", min_length=1)
    search_query: Optional[str] = None
    action_params: Optional[Dict[str, Any]] = None

class TaskUpdate(BaseModel):
    task_name: Optional[str] = None
    action_type: Optional[str] = None
    search_query: Optional[str] = None
    action_params: Optional[Dict[str, Any]] = None

class TaskResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    task_name: str
    action_type: str
    search_query: Optional[str] = None
    action_params: Optional[Dict[str, Any]] = None
    created_at: Optional[datetime] = None

# --- Run Event Schemas ---
class RunEventCreate(BaseModel):
    run_id: str = Field(..., description="Associated run UUID", min_length=1)
    event_type: str = Field(..., description="STEP_STARTED, STEP_OK, STEP_FAILED", min_length=1)
    payload: Dict[str, Any] = Field(..., description="Full event payload")

class RunEventResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    run_id: str
    event_type: str
    payload: Dict[str, Any]
    timestamp: Optional[datetime] = None

# --- Run Schemas ---
class RunCreate(BaseModel):
    task_id: int = Field(..., description="ID of the task to execute")
    device_id: int = Field(..., description="ID of the target device")
    status: Optional[str] = "RUNNING"

class RunUpdate(BaseModel):
    status: Optional[str] = None  # RUNNING, SUCCESS, FAILED
    end_time: Optional[datetime] = None

class RunResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    run_id: str
    task_id: int
    device_id: int
    status: str
    start_time: Optional[datetime] = None
    end_time: Optional[datetime] = None
    events: List[RunEventResponse] = []

# --- Command Trigger Schemas ---
class SendCommandRequest(BaseModel):
    task_id: str = Field(..., description="ID or name of the task to execute", min_length=1)
    device_id: str = Field(..., description="ID of the target device", min_length=1)

class SendCommandResponse(BaseModel):
    command_id: str
    task_id: str
    device_id: str
    run_id: str
    status: str = "QUEUED"
