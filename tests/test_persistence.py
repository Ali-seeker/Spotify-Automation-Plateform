import os
import sys
import pytest

# Add workspace root to sys.path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from fastapi.testclient import TestClient
from main import app

client = TestClient(app)


@pytest.fixture
def auth_headers():
    """Helper fixture to obtain JWT Bearer header for protected endpoints."""
    login_res = client.post("/login", json={"username": "admin", "password": "admin123"})
    assert login_res.status_code == 200
    token = login_res.json()["access_token"]
    return {"Authorization": f"Bearer {token}"}


def test_device_crud(auth_headers):
    """Verify Device CRUD: Create, Fetch All, Fetch One, and Update."""
    device_data = {
        "device_id": "test_infinix_x6817",
        "status": "IDLE",
        "capabilities": {"os": "Android 12", "accessibility": True}
    }
    
    # 1. Create Device
    res_create = client.post("/devices", json=device_data, headers=auth_headers)
    assert res_create.status_code == 201
    created_device = res_create.json()
    assert created_device["device_id"] == "test_infinix_x6817"
    assert created_device["capabilities"] == {"os": "Android 12", "accessibility": True}
    device_int_id = created_device["id"]

    # 2. Fetch All Devices
    res_all = client.get("/devices")
    assert res_all.status_code == 200
    devices = res_all.json()
    assert any(d["device_id"] == "test_infinix_x6817" for d in devices)

    # 3. Fetch Specific Device by ID
    res_one = client.get(f"/devices/test_infinix_x6817")
    assert res_one.status_code == 200
    assert res_one.json()["id"] == device_int_id

    # 4. Update Device Status
    res_update = client.put(
        f"/devices/test_infinix_x6817",
        json={"status": "BUSY", "capabilities": {"os": "Android 12", "accessibility": True, "battery": 95}},
        headers=auth_headers
    )
    assert res_update.status_code == 200
    assert res_update.json()["status"] == "BUSY"
    assert res_update.json()["capabilities"]["battery"] == 95


def test_task_crud(auth_headers):
    """Verify Task CRUD: Create, Fetch All, Fetch One, and Update."""
    task_data = {
        "task_name": "Play Spotify Track",
        "action_type": "SEARCH_AND_PLAY",
        "search_query": "Atif Aslam",
        "action_params": {"timeout_sec": 10, "auto_play": True}
    }

    # 1. Create Task
    res_create = client.post("/tasks", json=task_data, headers=auth_headers)
    assert res_create.status_code == 201
    created_task = res_create.json()
    assert created_task["task_name"] == "Play Spotify Track"
    assert created_task["search_query"] == "Atif Aslam"
    task_id = created_task["id"]

    # 2. Fetch All Tasks
    res_all = client.get("/tasks")
    assert res_all.status_code == 200
    tasks = res_all.json()
    assert any(t["id"] == task_id for t in tasks)

    # 3. Fetch Specific Task
    res_one = client.get(f"/tasks/{task_id}")
    assert res_one.status_code == 200
    assert res_one.json()["action_type"] == "SEARCH_AND_PLAY"

    # 4. Update Task
    res_update = client.put(
        f"/tasks/{task_id}",
        json={"search_query": "Arijit Singh", "action_params": {"timeout_sec": 15}},
        headers=auth_headers
    )
    assert res_update.status_code == 200
    assert res_update.json()["search_query"] == "Arijit Singh"
    assert res_update.json()["action_params"]["timeout_sec"] == 15


def test_run_and_event_persistence(auth_headers):
    """Verify Run execution creation, status lifecycle, and full payload Run Event persistence."""
    # Create Task and Device first
    task_res = client.post("/tasks", json={"task_name": "Run Automation Test", "action_type": "CLICK"}, headers=auth_headers)
    task_id = task_res.json()["id"]

    device_res = client.post("/devices", json={"device_id": "run_test_device"}, headers=auth_headers)
    device_id = device_res.json()["id"]

    # 1. Create Run Execution
    run_res = client.post("/runs", json={"task_id": task_id, "device_id": device_id, "status": "RUNNING"})
    assert run_res.status_code == 201
    run_data = run_res.json()
    run_id = run_data["run_id"]
    assert run_data["status"] == "RUNNING"

    # 2. Record STEP_STARTED Event with full payload
    payload_started = {"step_index": 1, "action": "LAUNCH_APP", "package": "com.spotify.music"}
    res_ev1 = client.post(f"/runs/{run_id}/events", json={"run_id": run_id, "event_type": "STEP_STARTED", "payload": payload_started})
    assert res_ev1.status_code == 201
    assert res_ev1.json()["payload"] == payload_started

    # 3. Record STEP_OK Event with full payload
    payload_ok = {"step_index": 2, "action": "CLICK_SEARCH", "result": True, "details": {"target_id": "search_tab"}}
    res_ev2 = client.post(f"/runs/{run_id}/events", json={"run_id": run_id, "event_type": "STEP_OK", "payload": payload_ok})
    assert res_ev2.status_code == 201
    assert res_ev2.json()["payload"] == payload_ok

    # 4. Record STEP_FAILED Event with full payload
    payload_failed = {"step_index": 3, "action": "PLAY_TRACK", "result": False, "reason_code": "UNEXPECTED_STATE"}
    res_ev3 = client.post(f"/runs/{run_id}/events", json={"run_id": run_id, "event_type": "STEP_FAILED", "payload": payload_failed})
    assert res_ev3.status_code == 201
    assert res_ev3.json()["payload"] == payload_failed

    # 5. Update Run status to FAILED
    res_run_upd = client.put(f"/runs/{run_id}", json={"status": "FAILED"})
    assert res_run_upd.status_code == 200
    assert res_run_upd.json()["status"] == "FAILED"
    assert res_run_upd.json()["end_time"] is not None

    # 6. Fetch Run Events and confirm full payload preservation
    events_res = client.get(f"/runs/{run_id}/events")
    assert events_res.status_code == 200
    events = events_res.json()
    assert len(events) == 3
    assert events[0]["event_type"] == "STEP_STARTED"
    assert events[0]["payload"]["package"] == "com.spotify.music"
    assert events[2]["event_type"] == "STEP_FAILED"
    assert events[2]["payload"]["reason_code"] == "UNEXPECTED_STATE"
