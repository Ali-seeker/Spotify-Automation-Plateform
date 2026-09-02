import api from '../api';

/**
 * Authenticate user against POST /login
 */
export async function loginApi(username, password) {
  const response = await api.post('/login', { username, password });
  return response.data; // { access_token, token_type }
}

/**
 * Fetch current authenticated user info
 */
export async function getCurrentUserApi() {
  const response = await api.get('/me');
  return response.data;
}

/**
 * Fetch all registered devices from database
 */
export async function getDevicesApi() {
  const response = await api.get('/devices');
  return response.data;
}

/**
 * Fetch all configured tasks from database
 */
export async function getTasksApi() {
  const response = await api.get('/tasks');
  return response.data;
}

/**
 * Create a new task definition via POST /tasks
 */
export async function createTaskApi(taskData) {
  const response = await api.post('/tasks', taskData);
  return response.data;
}

/**
 * Trigger task execution on target device via POST /send_command
 */
export async function sendCommandApi(taskId, deviceId) {
  const response = await api.post('/send_command', {
    task_id: String(taskId),
    device_id: String(deviceId)
  });
  return response.data; // { command_id, task_id, device_id, run_id, status }
}

/**
 * Fetch execution run logs from database
 */
export async function getRunsApi() {
  const response = await api.get('/runs');
  return response.data;
}
