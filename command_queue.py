import asyncio
from datetime import datetime, timezone
import logging
from typing import Dict

from database import SessionLocal
from models import Run, RunEvent
from websocket_manager import manager

logger = logging.getLogger("CommandQueue")


class DeviceCommandQueueManager:
    def __init__(self):
        self.queues: Dict[str, asyncio.Queue] = {}
        self.workers: Dict[str, asyncio.Task] = {}
        self.lock = asyncio.Lock()

    async def enqueue_command(self, device_id: str, command_item: dict):
        """
        Enqueues a command item for a specific device.
        Ensures a single worker task is processing the device's queue sequentially.
        """
        async with self.lock:
            if device_id not in self.queues:
                self.queues[device_id] = asyncio.Queue()

            await self.queues[device_id].put(command_item)
            logger.info(f"Queued command '{command_item.get('command_id')}' for device '{device_id}'. Queue size: {self.queues[device_id].qsize()}")

            if device_id not in self.workers or self.workers[device_id].done():
                self.workers[device_id] = asyncio.create_task(self._process_queue(device_id))
                logger.info(f"Spawned queue worker task for device '{device_id}'")

    async def _process_queue(self, device_id: str):
        """
        Per-device worker processing commands sequentially.
        """
        logger.info(f"Queue worker started for device '{device_id}'")
        queue = self.queues.get(device_id)
        if not queue:
            return

        while not queue.empty():
            command_item = await queue.get()
            command_id = command_item.get("command_id")
            run_id = command_item.get("run_id")
            ttl_ms = command_item.get("ttl_ms", 180000)
            issued_at_str = command_item.get("issued_at")

            logger.info(f"Processing command '{command_id}' for device '{device_id}'")

            # 1. Check TTL Expiration
            try:
                issued_at = datetime.fromisoformat(issued_at_str)
                now = datetime.now(timezone.utc)
                elapsed_ms = (now - issued_at).total_seconds() * 1000
                if elapsed_ms > ttl_ms:
                    logger.warning(f"Command '{command_id}' expired (elapsed: {elapsed_ms}ms > TTL: {ttl_ms}ms)")
                    self._update_run_failure(run_id, "COMMAND_EXPIRED", f"Command expired after {elapsed_ms}ms")
                    queue.task_done()
                    continue
            except Exception as e:
                logger.error(f"Error checking TTL for command '{command_id}': {e}")

            # 2. Check WebSocket connection and send command
            if not manager.is_connected(device_id):
                logger.error(f"WebSocket send failure: Device '{device_id}' disconnected before command dispatch")
                self._update_run_failure(run_id, "DEVICE_OFFLINE", f"Device '{device_id}' is offline")
                queue.task_done()
                continue

            try:
                await manager.send_json(device_id, command_item)
                logger.info(f"Successfully dispatched command '{command_id}' to device '{device_id}' over WebSocket")
                self._record_step_ok(run_id, command_item)
            except Exception as e:
                logger.error(f"WebSocket send failure for command '{command_id}': {e}")
                self._update_run_failure(run_id, "WEBSOCKET_SEND_FAILURE", str(e))

            queue.task_done()

        logger.info(f"Queue worker completed for device '{device_id}'")

    def _update_run_failure(self, run_id: str, reason_code: str, error_msg: str):
        db = SessionLocal()
        try:
            run = db.query(Run).filter(Run.run_id == run_id).first()
            if run:
                run.status = "FAILED"
                run.end_time = datetime.now(timezone.utc)
                event = RunEvent(
                    run_id=run_id,
                    event_type="STEP_FAILED",
                    timestamp=datetime.now(timezone.utc)
                )
                event.payload = {"reason_code": reason_code, "error": error_msg}
                db.add(event)
                db.commit()
        finally:
            db.close()

    def _record_step_ok(self, run_id: str, command_item: dict):
        db = SessionLocal()
        try:
            run = db.query(Run).filter(Run.run_id == run_id).first()
            if run:
                event = RunEvent(
                    run_id=run_id,
                    event_type="STEP_OK",
                    timestamp=datetime.now(timezone.utc)
                )
                event.payload = {"command_id": command_item.get("command_id"), "status": "SENT"}
                db.add(event)
                db.commit()
        finally:
            db.close()


# Shared Queue Manager Singleton Instance
queue_manager = DeviceCommandQueueManager()
