import logging
from typing import Dict
from fastapi import WebSocket

logger = logging.getLogger("WebSocketManager")

class WebSocketConnectionManager:
    def __init__(self):
        self.active_connections: Dict[str, WebSocket] = {}

    async def connect(self, device_id: str, websocket: WebSocket):
        """Accepts and stores an active WebSocket connection for a given device_id."""
        await websocket.accept()
        self.active_connections[device_id] = websocket
        logger.info(f"WebSocket connected for device: {device_id}")

    def disconnect(self, device_id: str):
        """Removes an active WebSocket connection for a given device_id."""
        if device_id in self.active_connections:
            del self.active_connections[device_id]
            logger.info(f"WebSocket disconnected for device: {device_id}")

    def is_connected(self, device_id: str) -> bool:
        """Returns True if the device has an active WebSocket connection."""
        return device_id in self.active_connections

    async def send_json(self, device_id: str, payload: dict):
        """Sends a JSON payload over the device's WebSocket connection."""
        if not self.is_connected(device_id):
            raise RuntimeError(f"Device '{device_id}' is not connected via WebSocket")
        websocket = self.active_connections[device_id]
        await websocket.send_json(payload)
        logger.info(f"Sent JSON payload to device {device_id}: command_id={payload.get('command_id')}")

# Shared Singleton Instance
manager = WebSocketConnectionManager()
