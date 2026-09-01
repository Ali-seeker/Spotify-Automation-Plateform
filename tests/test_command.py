import os
import sys
import uuid
import pytest

# Add workspace root to sys.path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from fastapi.testclient import TestClient
from main import app
from websocket_manager import manager

client = TestClient(app)
SHARED_SECRET = os.getenv("DEVICE_SHARED_SECRET", "device_shared_secret_for_auth_123")


@pytest.fixture
def auth_headers():
    """Helper fixture to obtain JWT Bearer header for protected endpoints."""
    login_res = client.post("/login", json={"username": "admin", "password": "admin123"})
    assert login_res.status_code == 200
    token = login_res.json()["access_token"]
    return {"Authorization": f"Bearer {token}"}


@pytest.fixture
def test_task_and_device(auth_headers):
    """Helper fixture to create a valid task and device in the DB."""
    task_res = client.post(
        "/tasks",
        json={"task_name": f"Cmd_Task_{uuid.uuid4().hex[:6]}", "action_type": "CLICK_SEARCH", "search_query": "Spotify Bot"},
        headers=auth_headers
    )
    assert task_res.status_code == 201
    task_id = str(task_res.json()["id"])

    device_id_str = f"ws_dev_{uuid.uuid4().hex[:6]}"
    device_res = client.post(
        "/devices",
        json={"device_id": device_id_str, "status": "IDLE"},
        headers=auth_headers
    )
    assert device_res.status_code in [200, 201]
    device_id = device_res.json()["device_id"]

    return task_id, device_id


def test_send_command_unauthenticated():
    """Verify /send_command rejects unauthenticated requests with HTTP 401."""
    res = client.post("/send_command", json={"task_id": "1", "device_id": "device_1"})
    assert res.status_code == 401
    assert res.json()["detail"] == "INVALID_CREDENTIALS"


def test_send_command_unknown_task(auth_headers, test_task_and_device):
    """Verify /send_command returns 404 TASK_NOT_FOUND when task does not exist."""
    _, device_id = test_task_and_device
    res = client.post(
        "/send_command",
        json={"task_id": "99999", "device_id": device_id},
        headers=auth_headers
    )
    assert res.status_code == 404
    assert res.json()["detail"] == "TASK_NOT_FOUND"


def test_send_command_unknown_device(auth_headers, test_task_and_device):
    """Verify /send_command returns 404 DEVICE_NOT_FOUND when device does not exist."""
    task_id, _ = test_task_and_device
    res = client.post(
        "/send_command",
        json={"task_id": task_id, "device_id": "non_existent_device_999"},
        headers=auth_headers
    )
    assert res.status_code == 404
    assert res.json()["detail"] == "DEVICE_NOT_FOUND"


def test_send_command_offline_device(auth_headers, test_task_and_device):
    """Verify /send_command returns 400 DEVICE_OFFLINE when device has no active WebSocket connection."""
    task_id, device_id = test_task_and_device
    # Ensure device is not in manager active_connections
    manager.disconnect(device_id)

    res = client.post(
        "/send_command",
        json={"task_id": task_id, "device_id": device_id},
        headers=auth_headers
    )
    assert res.status_code == 400
    assert res.json()["detail"] == "DEVICE_OFFLINE"


def test_send_command_success(auth_headers, test_task_and_device):
    """Verify /send_command generates UUID command_id, UTC timestamp, ttl_ms, creates run history, and queues command for connected device."""
    task_id, device_id = test_task_and_device

    # Connect device to WebSocket via TestClient & authenticate
    with client.websocket_connect(f"/ws/device/{device_id}") as websocket:
        websocket.send_json({
            "type": "DEVICE_HELLO",
            "device_id": device_id,
            "device_auth_token": SHARED_SECRET,
            "app_version": "1.0.0"
        })
        ack = websocket.receive_json()
        assert ack["type"] == "HELLO_ACK"
        assert manager.is_connected(device_id)

        # Trigger /send_command
        res = client.post(
            "/send_command",
            json={"task_id": task_id, "device_id": device_id},
            headers=auth_headers
        )
        assert res.status_code == 200
        data = res.json()
        assert "command_id" in data
        assert data["command_id"].startswith("cmd_")
        assert data["task_id"] == task_id
        assert data["device_id"] == device_id
        assert data["status"] == "QUEUED"

        # Verify command payload received over WebSocket
        received_payload = websocket.receive_json()
        assert received_payload["command_id"] == data["command_id"]
        assert received_payload["task_id"] == task_id
        assert received_payload["device_id"] == device_id
        assert received_payload["ttl_ms"] == 180000
        assert "issued_at" in received_payload

        # Verify Run record & STEP_STARTED event created
        run_res = client.get(f"/runs/{data['run_id']}")
        assert run_res.status_code == 200
        assert run_res.json()["status"] == "RUNNING"


def test_per_device_sequential_queuing(auth_headers, test_task_and_device):
    """Verify multiple commands targeting the same device are queued and received sequentially over WebSocket."""
    task_id, device_id = test_task_and_device

    with client.websocket_connect(f"/ws/device/{device_id}") as websocket:
        websocket.send_json({
            "type": "DEVICE_HELLO",
            "device_id": device_id,
            "device_auth_token": SHARED_SECRET,
            "app_version": "1.0.0"
        })
        ack = websocket.receive_json()
        assert ack["type"] == "HELLO_ACK"

        # Trigger Command 1
        res1 = client.post("/send_command", json={"task_id": task_id, "device_id": device_id}, headers=auth_headers)
        # Trigger Command 2
        res2 = client.post("/send_command", json={"task_id": task_id, "device_id": device_id}, headers=auth_headers)

        assert res1.status_code == 200
        assert res2.status_code == 200

        cmd1_id = res1.json()["command_id"]
        cmd2_id = res2.json()["command_id"]

        # Receive Command 1 first
        received1 = websocket.receive_json()
        assert received1["command_id"] == cmd1_id

        # Receive Command 2 second
        received2 = websocket.receive_json()
        assert received2["command_id"] == cmd2_id
