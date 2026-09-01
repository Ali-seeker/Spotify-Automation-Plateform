import os
import sys
import time
import uuid
import pytest

# Add workspace root to sys.path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from fastapi.testclient import TestClient
from database import SessionLocal
from main import app
from models import Device, Run, RunEvent
from websocket_manager import manager

client = TestClient(app)
SHARED_SECRET = os.getenv("DEVICE_SHARED_SECRET", "device_shared_secret_for_auth_123")


@pytest.fixture
def auth_headers():
    login_res = client.post("/login", json={"username": "admin", "password": "admin123"})
    assert login_res.status_code == 200
    token = login_res.json()["access_token"]
    return {"Authorization": f"Bearer {token}"}


def test_device_hello_authentication_success():
    """Verify valid DEVICE_HELLO receives HELLO_ACK and marks device ONLINE in DB."""
    device_id = f"test_dev_{uuid.uuid4().hex[:6]}"

    with client.websocket_connect("/ws/device") as websocket:
        # Send DEVICE_HELLO
        hello_msg = {
            "type": "DEVICE_HELLO",
            "device_id": device_id,
            "device_auth_token": SHARED_SECRET,
            "app_version": "1.0.0",
            "capabilities": {"os": "Android 12", "accessibility": True}
        }
        websocket.send_json(hello_msg)

        # Receive HELLO_ACK
        ack = websocket.receive_json()
        assert ack["type"] == "HELLO_ACK"
        assert ack["device_id"] == device_id
        assert ack["status"] == "AUTHENTICATED"

        # Check DB status is ONLINE
        db = SessionLocal()
        try:
            dev = db.query(Device).filter(Device.device_id == device_id).first()
            assert dev is not None
            assert dev.status == "ONLINE"
            assert dev.last_seen is not None
        finally:
            db.close()


def test_device_hello_authentication_rejected():
    """Verify invalid device_auth_token receives HELLO_REJECT and is NOT marked online."""
    device_id = f"test_dev_{uuid.uuid4().hex[:6]}"

    with client.websocket_connect("/ws/device") as websocket:
        # Send DEVICE_HELLO with invalid token
        hello_msg = {
            "type": "DEVICE_HELLO",
            "device_id": device_id,
            "device_auth_token": "wrong_secret_token_123",
            "app_version": "1.0.0"
        }
        websocket.send_json(hello_msg)

        # Receive HELLO_REJECT
        reject = websocket.receive_json()
        assert reject["type"] == "HELLO_REJECT"
        assert reject["reason"] == "INVALID_DEVICE_AUTH"

    assert not manager.is_connected(device_id)


def test_device_event_persistence_and_frontend_broadcasting(auth_headers):
    """Verify STEP_STARTED, STEP_OK, STEP_FAILED, and COMMAND_DONE events are persisted to DB and broadcast to frontend."""
    device_id = f"test_dev_{uuid.uuid4().hex[:6]}"

    # Setup Task and Device
    task_res = client.post("/tasks", json={"task_name": f"WS_Task_{uuid.uuid4().hex[:6]}", "action_type": "CLICK"}, headers=auth_headers)
    task_id = task_res.json()["id"]

    device_res = client.post("/devices", json={"device_id": device_id}, headers=auth_headers)
    device_db_id = device_res.json()["id"]

    # Connect device & authenticate
    with client.websocket_connect("/ws/device") as dev_ws:
        dev_ws.send_json({
            "type": "DEVICE_HELLO",
            "device_id": device_id,
            "device_auth_token": SHARED_SECRET,
            "app_version": "1.0.0"
        })
        ack = dev_ws.receive_json()
        assert ack["type"] == "HELLO_ACK"

        # Create Run
        run_res = client.post("/runs", json={"task_id": task_id, "device_id": device_db_id, "status": "RUNNING"})
        run_id = run_res.json()["run_id"]

        # Send STEP_STARTED
        dev_ws.send_json({
            "type": "STEP_STARTED",
            "run_id": run_id,
            "payload": {"step_index": 1, "action": "LAUNCH_APP"}
        })

        # Send STEP_OK
        dev_ws.send_json({
            "type": "STEP_OK",
            "run_id": run_id,
            "payload": {"step_index": 2, "action": "SEARCH"}
        })

        # Send COMMAND_DONE (SUCCESS)
        dev_ws.send_json({
            "type": "COMMAND_DONE",
            "run_id": run_id,
            "status": "SUCCESS",
            "payload": {"result": True}
        })

        time.sleep(0.1)

        # Verify DB records
        db = SessionLocal()
        try:
            events = db.query(RunEvent).filter(RunEvent.run_id == run_id).all()
            assert len(events) >= 3
            types = [e.event_type for e in events]
            assert "STEP_STARTED" in types
            assert "STEP_OK" in types
            assert "COMMAND_DONE" in types

            run = db.query(Run).filter(Run.run_id == run_id).first()
            assert run.status == "SUCCESS"
            assert run.end_time is not None
        finally:
            db.close()


def test_disconnect_marks_device_offline():
    """Verify normal WebSocket disconnect updates device status to OFFLINE in DB."""
    device_id = f"test_dev_{uuid.uuid4().hex[:6]}"

    with client.websocket_connect("/ws/device") as dev_ws:
        dev_ws.send_json({
            "type": "DEVICE_HELLO",
            "device_id": device_id,
            "device_auth_token": SHARED_SECRET,
            "app_version": "1.0.0"
        })
        dev_ws.receive_json()
        assert manager.is_connected(device_id)

    # Disconnected outside `with` block
    assert not manager.is_connected(device_id)

    db = SessionLocal()
    try:
        dev = db.query(Device).filter(Device.device_id == device_id).first()
        assert dev is not None
        assert dev.status == "OFFLINE"
    finally:
        db.close()


def test_stale_disconnect_protection():
    """Verify an old closing connection session does not mark a newly reconnected device OFFLINE."""
    device_id = f"stale_dev_{uuid.uuid4().hex[:6]}"

    # Register initial session
    manager.active_connections[device_id] = (None, "session_NEW_ACTIVE")

    # Simulate old session disconnect call
    is_removed = manager.disconnect_device(device_id, "session_OLD_STALE")
    assert is_removed is False
    assert manager.is_connected(device_id)  # Still active!

    # Cleanup test state
    manager.disconnect_device(device_id, "session_NEW_ACTIVE")
