import logging
from typing import Dict, List, Tuple
from fastapi import WebSocket

logger = logging.getLogger("WebSocketManager")


class WebSocketConnectionManager:
    def __init__(self):
        # Map device_id -> (WebSocket instance, session_id string)
        self.active_connections: Dict[str, Tuple[WebSocket, str]] = {}
        # Active frontend WebSockets subscribed to live events
        self.active_frontend_connections: List[WebSocket] = []

    async def connect_device(self, device_id: str, websocket: WebSocket, session_id: str):
        """Accepts and stores an active authenticated WebSocket connection for a device with session_id."""
        self.active_connections[device_id] = (websocket, session_id)
        logger.info(f"WebSocket registered for device: '{device_id}' (session: {session_id})")

    def disconnect_device(self, device_id: str, session_id: str) -> bool:
        """
        Removes an active device connection ONLY if the session_id matches the registered session.
        Prevents an old closing connection from overriding a newly reconnected connection.
        Returns True if the connection was removed (device is now OFFLINE), False otherwise.
        """
        if device_id in self.active_connections:
            ws, current_session_id = self.active_connections[device_id]
            if current_session_id == session_id:
                del self.active_connections[device_id]
                logger.info(f"WebSocket disconnected for device: '{device_id}' (session: {session_id})")
                return True
            else:
                logger.info(f"Stale disconnect ignored for device '{device_id}' (closing session: {session_id}, active session: {current_session_id})")
                return False
        return False

    def disconnect(self, device_id: str):
        """Backward-compatible helper to unregister a device connection."""
        if device_id in self.active_connections:
            del self.active_connections[device_id]
            logger.info(f"WebSocket un-registered for device: '{device_id}'")

    def is_connected(self, device_id: str) -> bool:
        """Returns True if the device has an active authenticated WebSocket connection."""
        return device_id in self.active_connections

    async def send_json(self, device_id: str, payload: dict):
        """Sends a JSON payload over the device's WebSocket connection."""
        if not self.is_connected(device_id):
            raise RuntimeError(f"Device '{device_id}' is not connected via WebSocket")
        websocket, _ = self.active_connections[device_id]
        await websocket.send_json(payload)
        logger.info(f"Sent JSON payload to device '{device_id}': command_id={payload.get('command_id')}")

    # --- Frontend Broadcasting Methods ---
    async def connect_frontend(self, websocket: WebSocket):
        """Accepts and registers a frontend client WebSocket."""
        await websocket.accept()
        self.active_frontend_connections.append(websocket)
        logger.info("Frontend WebSocket client connected")

    def disconnect_frontend(self, websocket: WebSocket):
        """Unregisters a frontend client WebSocket."""
        if websocket in self.active_frontend_connections:
            self.active_frontend_connections.remove(websocket)
            logger.info("Frontend WebSocket client disconnected")

    async def broadcast_to_frontend(self, message: dict):
        """Broadcasts a JSON message (device status change or step event) to all connected frontend clients."""
        disconnected = []
        for ws in self.active_frontend_connections:
            try:
                await ws.send_json(message)
            except Exception as e:
                logger.warning(f"Error broadcasting to frontend client: {e}")
                disconnected.append(ws)

        for ws in disconnected:
            self.disconnect_frontend(ws)


# Shared Singleton Instance
manager = WebSocketConnectionManager()
