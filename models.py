from datetime import datetime, timezone
import json
from sqlalchemy import Column, Integer, String, Boolean, DateTime, ForeignKey, Text
from sqlalchemy.orm import relationship
from database import Base

class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, index=True)
    username = Column(String, unique=True, index=True, nullable=False)
    hashed_password = Column(String, nullable=False)
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))


class Device(Base):
    __tablename__ = "devices"

    id = Column(Integer, primary_key=True, index=True)
    device_id = Column(String, unique=True, index=True, nullable=False)
    status = Column(String, default="IDLE", nullable=False)  # IDLE, BUSY, OFFLINE
    capabilities_json = Column(Text, nullable=True)  # Stored as JSON string
    last_seen = Column(DateTime, default=lambda: datetime.now(timezone.utc))

    runs = relationship("Run", back_populates="device", cascade="all, delete-orphan")

    @property
    def capabilities(self):
        if self.capabilities_json:
            try:
                return json.loads(self.capabilities_json)
            except Exception:
                return {}
        return {}

    @capabilities.setter
    def capabilities(self, value):
        if value is not None:
            self.capabilities_json = json.dumps(value)
        else:
            self.capabilities_json = None


class Task(Base):
    __tablename__ = "tasks"

    id = Column(Integer, primary_key=True, index=True)
    task_name = Column(String, nullable=False)
    action_type = Column(String, nullable=False)  # CLICK, SEARCH, PLAY, PAUSE
    search_query = Column(String, nullable=True)
    action_params_json = Column(Text, nullable=True)  # Stored as JSON string
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))

    runs = relationship("Run", back_populates="task", cascade="all, delete-orphan")

    @property
    def action_params(self):
        if self.action_params_json:
            try:
                return json.loads(self.action_params_json)
            except Exception:
                return {}
        return {}

    @action_params.setter
    def action_params(self, value):
        if value is not None:
            self.action_params_json = json.dumps(value)
        else:
            self.action_params_json = None


class Run(Base):
    __tablename__ = "runs"

    id = Column(Integer, primary_key=True, index=True)
    run_id = Column(String, unique=True, index=True, nullable=False)  # UUID or custom String ID
    task_id = Column(Integer, ForeignKey("tasks.id"), nullable=False)
    device_id = Column(Integer, ForeignKey("devices.id"), nullable=False)
    status = Column(String, default="RUNNING", nullable=False)  # PENDING, RUNNING, SUCCESS, FAILED
    start_time = Column(DateTime, default=lambda: datetime.now(timezone.utc))
    end_time = Column(DateTime, nullable=True)

    task = relationship("Task", back_populates="runs")
    device = relationship("Device", back_populates="runs")
    events = relationship("RunEvent", back_populates="run", cascade="all, delete-orphan")


class RunEvent(Base):
    __tablename__ = "run_events"

    id = Column(Integer, primary_key=True, index=True)
    run_id = Column(String, ForeignKey("runs.run_id"), nullable=False, index=True)
    event_type = Column(String, nullable=False)  # STEP_STARTED, STEP_OK, STEP_FAILED
    payload_json = Column(Text, nullable=False)  # Full JSON payload preserved
    timestamp = Column(DateTime, default=lambda: datetime.now(timezone.utc))

    run = relationship("Run", back_populates="events")

    @property
    def payload(self):
        if self.payload_json:
            try:
                return json.loads(self.payload_json)
            except Exception:
                return {}
        return {}

    @payload.setter
    def payload(self, value):
        if value is not None:
            self.payload_json = json.dumps(value)
        else:
            self.payload_json = "{}"
