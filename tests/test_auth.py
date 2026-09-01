import os
import sys
from datetime import timedelta
import pytest

# Add workspace root to sys.path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from fastapi.testclient import TestClient
from jose import jwt

from main import app
from security import create_access_token, JWT_SECRET, JWT_ALGORITHM

client = TestClient(app)


def test_valid_login():
    """Verify valid username/password returns JWT with access_token, bearer token_type, and expiration."""
    response = client.post("/login", json={"username": "admin", "password": "admin123"})
    assert response.status_code == 200
    data = response.json()
    assert "access_token" in data
    assert data["token_type"] == "bearer"
    
    token = data["access_token"]
    payload = jwt.decode(token, JWT_SECRET, algorithms=[JWT_ALGORITHM])
    assert payload.get("sub") == "admin"
    assert "exp" in payload


def test_invalid_login_wrong_password():
    """Verify invalid password returns HTTP 401 with detail 'INVALID_CREDENTIALS'."""
    response = client.post("/login", json={"username": "admin", "password": "wrongpassword"})
    assert response.status_code == 401
    assert response.json()["detail"] == "INVALID_CREDENTIALS"


def test_invalid_login_nonexistent_user():
    """Verify non-existent username returns HTTP 401 with detail 'INVALID_CREDENTIALS'."""
    response = client.post("/login", json={"username": "nonexistent", "password": "somepassword"})
    assert response.status_code == 401
    assert response.json()["detail"] == "INVALID_CREDENTIALS"


def test_login_missing_fields():
    """Verify request with empty fields fails validation."""
    response = client.post("/login", json={"username": "", "password": ""})
    assert response.status_code == 422


def test_protected_endpoint_without_token():
    """Verify GET /me without token returns HTTP 401."""
    response = client.get("/me")
    assert response.status_code == 401
    assert response.json()["detail"] == "INVALID_CREDENTIALS"


def test_protected_endpoint_with_invalid_token():
    """Verify GET /me with bogus/invalid token returns HTTP 401."""
    headers = {"Authorization": "Bearer invalid_bogus_token_123"}
    response = client.get("/me", headers=headers)
    assert response.status_code == 401
    assert response.json()["detail"] == "INVALID_CREDENTIALS"


def test_protected_endpoint_with_expired_token():
    """Verify GET /me with an expired JWT token returns HTTP 401."""
    expired_token = create_access_token(
        data={"sub": "admin"},
        expires_delta=timedelta(seconds=-10)  # Expired 10 seconds ago
    )
    headers = {"Authorization": f"Bearer {expired_token}"}
    response = client.get("/me", headers=headers)
    assert response.status_code == 401
    assert response.json()["detail"] == "INVALID_CREDENTIALS"


def test_protected_endpoint_with_valid_token():
    """Verify GET /me with a valid JWT token succeeds with HTTP 200 and returns user details."""
    login_res = client.post("/login", json={"username": "admin", "password": "admin123"})
    token = login_res.json()["access_token"]
    
    headers = {"Authorization": f"Bearer {token}"}
    response = client.get("/me", headers=headers)
    assert response.status_code == 200
    data = response.json()
    assert data["username"] == "admin"
    assert data["is_active"] is True
