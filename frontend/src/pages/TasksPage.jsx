import React, { useState, useEffect } from 'react';
import { ListTodo, Play, Plus, RefreshCw, AlertCircle, Clock, Smartphone, CheckCircle2, XCircle, Loader2 } from 'lucide-react';
import { getTasksApi, getDevicesApi, sendCommandApi } from '../services/apiService';
import { useFrontendWebSocket } from '../hooks/useFrontendWebSocket';

export default function TasksPage({ onNavigateToBuilder }) {
  const [tasks, setTasks] = useState([]);
  const [devices, setDevices] = useState([]);
  const [selectedDeviceMap, setSelectedDeviceMap] = useState({}); // taskId -> deviceId
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState('');

  // Per-task execution tracking state
  // taskId -> { runId, status: 'QUEUED'|'RUNNING'|'SUCCESS'|'FAILED', steps: [], error: string }
  const [taskExecutions, setTaskExecutions] = useState({});

  const { isConnected, deviceUpdates, lastEvent } = useFrontendWebSocket();

  useEffect(() => {
    fetchInitialData();
  }, []);

  // Listen for real-time WebSocket events and correlate them with active task executions
  useEffect(() => {
    if (!lastEvent || lastEvent.type !== 'DEVICE_EVENT') return;

    const { run_id, event_type, payload } = lastEvent;
    if (!run_id) return;

    setTaskExecutions((prevExecs) => {
      // Find matching task execution by run_id
      const targetTaskId = Object.keys(prevExecs).find(
        (tid) => prevExecs[tid]?.runId === run_id
      );

      if (!targetTaskId) return prevExecs;

      const currentExec = prevExecs[targetTaskId];
      const newSteps = [...(currentExec.steps || [])];

      if (event_type === 'STEP_STARTED') {
        newSteps.push({
          step_index: payload?.step_index || newSteps.length + 1,
          event_type: 'STEP_STARTED',
          action: payload?.action || payload?.step_name || 'Executing step...',
          timestamp: new Date().toLocaleTimeString()
        });
      } else if (event_type === 'STEP_OK') {
        newSteps.push({
          step_index: payload?.step_index || newSteps.length + 1,
          event_type: 'STEP_OK',
          action: payload?.action || payload?.step_name || 'Step completed',
          timestamp: new Date().toLocaleTimeString()
        });
      } else if (event_type === 'STEP_FAILED') {
        newSteps.push({
          step_index: payload?.step_index || newSteps.length + 1,
          event_type: 'STEP_FAILED',
          action: payload?.action || payload?.step_name || 'Step failed',
          reason_code: payload?.reason_code || payload?.error || 'UNEXPECTED_STATE',
          timestamp: new Date().toLocaleTimeString()
        });
      }

      let updatedStatus = currentExec.status;
      if (event_type === 'COMMAND_DONE') {
        const finalStatus = lastEvent.status || payload?.status || (payload?.result !== false ? 'SUCCESS' : 'FAILED');
        updatedStatus = finalStatus === 'SUCCESS' ? 'SUCCESS' : 'FAILED';
      } else if (event_type === 'STEP_FAILED') {
        updatedStatus = 'FAILED';
      }

      return {
        ...prevExecs,
        [targetTaskId]: {
          ...currentExec,
          status: updatedStatus,
          steps: newSteps
        }
      };
    });
  }, [lastEvent]);

  const fetchInitialData = async () => {
    setLoading(true);
    setErrorMsg('');
    try {
      const [tasksData, devicesData] = await Promise.all([
        getTasksApi().catch(() => []),
        getDevicesApi().catch(() => [])
      ]);

      setTasks(tasksData);
      setDevices(devicesData);

      // Default select the first online/available device for each task
      const defaultDev = devicesData.find((d) => d.status === 'ONLINE' || d.status === 'IDLE')?.device_id || devicesData[0]?.device_id || '';
      const initialMap = {};
      tasksData.forEach((t) => {
        initialMap[t.id] = defaultDev;
      });
      setSelectedDeviceMap(initialMap);
    } catch (err) {
      setErrorMsg('Failed to load tasks or devices from backend.');
    } finally {
      setLoading(false);
    }
  };

  const formatDate = (dateStr) => {
    if (!dateStr) return 'N/A';
    try {
      const d = new Date(dateStr);
      return d.toLocaleDateString() + ' ' + d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    } catch (e) {
      return dateStr;
    }
  };

  // Merge WebSocket device status updates
  const mergedDevices = devices.map((d) => {
    const update = deviceUpdates[d.device_id];
    return update ? { ...d, status: update.status } : d;
  });

  const onlineDevices = mergedDevices.filter((d) => d.status === 'ONLINE' || d.status === 'IDLE' || d.status === 'BUSY');

  const handleDeviceChange = (taskId, deviceId) => {
    setSelectedDeviceMap((prev) => ({ ...prev, [taskId]: deviceId }));
  };

  const handleRunNow = async (task) => {
    const taskId = task.id;
    const selectedDeviceId = selectedDeviceMap[taskId];

    if (!selectedDeviceId) {
      setTaskExecutions((prev) => ({
        ...prev,
        [taskId]: { status: 'FAILED', error: 'No target device selected.', steps: [] }
      }));
      return;
    }

    // Check if target device is online
    const targetDev = mergedDevices.find((d) => d.device_id === selectedDeviceId);
    if (targetDev && targetDev.status === 'OFFLINE') {
      setTaskExecutions((prev) => ({
        ...prev,
        [taskId]: { status: 'FAILED', error: `Device ${selectedDeviceId} is currently OFFLINE.`, steps: [] }
      }));
      return;
    }

    // Prevent double submission
    if (taskExecutions[taskId]?.status === 'RUNNING' || taskExecutions[taskId]?.status === 'QUEUED') {
      return;
    }

    // Set initial execution state to QUEUED
    setTaskExecutions((prev) => ({
      ...prev,
      [taskId]: {
        runId: null,
        status: 'RUNNING',
        steps: [{ step_index: 0, event_type: 'STEP_STARTED', action: 'Command queued and sent to device...', timestamp: new Date().toLocaleTimeString() }],
        error: null
      }
    }));

    try {
      const res = await sendCommandApi(taskId, selectedDeviceId);
      setTaskExecutions((prev) => ({
        ...prev,
        [taskId]: {
          ...prev[taskId],
          runId: res.run_id,
          status: 'RUNNING'
        }
      }));
    } catch (err) {
      const errorDetail = err.response?.data?.detail || 'Failed to dispatch command to device.';
      setTaskExecutions((prev) => ({
        ...prev,
        [taskId]: {
          runId: null,
          status: 'FAILED',
          error: typeof errorDetail === 'string' ? errorDetail : 'Device offline or command execution failed.',
          steps: [{ step_index: 1, event_type: 'STEP_FAILED', reason_code: typeof errorDetail === 'string' ? errorDetail : 'DEVICE_OFFLINE', timestamp: new Date().toLocaleTimeString() }]
        }
      }));
    }
  };

  return (
    <div>
      {/* Header Banner */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.5rem' }}>
        <div>
          <h3 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#fff' }}>Automation Task Library</h3>
          <p style={{ fontSize: '0.85rem', color: '#9ca3af', marginTop: '0.2rem' }}>
            Trigger pipeline execution on connected Android devices and monitor step progress in real time
          </p>
        </div>

        <div style={{ display: 'flex', gap: '0.75rem' }}>
          <button onClick={fetchInitialData} className="btn-secondary">
            <RefreshCw size={16} />
            <span>RELOAD TASKS</span>
          </button>
          <button onClick={onNavigateToBuilder} className="btn-spotify">
            <Plus size={18} fill="#000" />
            <span>CREATE NEW TASK</span>
          </button>
        </div>
      </div>

      {/* Error Alert */}
      {errorMsg && (
        <div style={{
          backgroundColor: 'rgba(239, 68, 68, 0.12)',
          border: '1px solid rgba(239, 68, 68, 0.3)',
          borderRadius: '8px',
          padding: '0.75rem 1rem',
          color: '#ef4444',
          fontSize: '0.82rem',
          marginBottom: '1.25rem',
          display: 'flex',
          alignItems: 'center',
          gap: '0.5rem'
        }}>
          <AlertCircle size={18} />
          <span>{errorMsg}</span>
        </div>
      )}

      {/* Loading State */}
      {loading ? (
        <div className="glass-panel" style={{ padding: '3rem', textAlign: 'center', color: '#9ca3af' }}>
          <RefreshCw size={28} className="spin" style={{ marginBottom: '0.75rem' }} />
          <div>Loading task definitions & device registry from backend...</div>
        </div>
      ) : tasks.length === 0 ? (
        /* Empty State */
        <div className="glass-panel" style={{ padding: '4rem 2rem', textAlign: 'center' }}>
          <ListTodo size={48} color="#6b7280" style={{ marginBottom: '1rem' }} />
          <h4 style={{ color: '#fff', fontSize: '1.1rem', fontWeight: 700 }}>No Automation Tasks Configured</h4>
          <p style={{ color: '#9ca3af', fontSize: '0.85rem', marginTop: '0.3rem', maxWidth: '480px', margin: '0.3rem auto 1.5rem auto' }}>
            There are no task pipelines configured in the database yet. Click below to build your first automation task.
          </p>
          <button onClick={onNavigateToBuilder} className="btn-spotify" style={{ fontSize: '0.8rem' }}>
            <Plus size={16} fill="#000" />
            <span>Create First Automation Task</span>
          </button>
        </div>
      ) : (
        /* Task Cards / Table View */
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
          {tasks.map((task) => {
            const taskId = task.id;
            const execState = taskExecutions[taskId];
            const isRunning = execState?.status === 'RUNNING' || execState?.status === 'QUEUED';
            const selectedDev = selectedDeviceMap[taskId] || '';
            const devObj = mergedDevices.find((d) => d.device_id === selectedDev);
            const isDevOffline = !devObj || devObj.status === 'OFFLINE';

            return (
              <div key={taskId} className="glass-panel" style={{ padding: '1.25rem' }}>
                {/* Main Task Row Header */}
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '1rem' }}>
                  <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.625rem' }}>
                      <h4 style={{ fontSize: '1.05rem', fontWeight: 700, color: '#fff' }}>{task.task_name}</h4>
                      <span className="badge badge-running" style={{ fontSize: '0.68rem' }}>
                        {task.action_type}
                      </span>
                      <span style={{ fontSize: '0.75rem', color: '#6b7280', fontFamily: 'monospace' }}>
                        ID: #{task.id}
                      </span>
                    </div>

                    <div style={{ fontSize: '0.78rem', color: '#9ca3af', marginTop: '0.3rem', display: 'flex', alignItems: 'center', gap: '1rem' }}>
                      {task.search_query && (
                        <span>Target Query: <strong style={{ color: '#d1d5db' }}>{task.search_query}</strong></span>
                      )}
                      <span style={{ display: 'flex', alignItems: 'center', gap: '0.3rem' }}>
                        <Clock size={12} color="#6b7280" />
                        {formatDate(task.created_at)}
                      </span>
                    </div>
                  </div>

                  {/* Actions & Device Picker Controls */}
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                    {/* Target Device Dropdown */}
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', backgroundColor: 'rgba(0, 0, 0, 0.4)', padding: '0.35rem 0.6rem', borderRadius: '6px', border: '1px solid rgba(255, 255, 255, 0.1)' }}>
                      <Smartphone size={14} color={devObj?.status === 'ONLINE' ? '#10b981' : '#6b7280'} />
                      <select
                        disabled={isRunning}
                        value={selectedDev}
                        onChange={(e) => handleDeviceChange(taskId, e.target.value)}
                        style={{
                          backgroundColor: 'transparent',
                          border: 'none',
                          color: devObj?.status === 'ONLINE' ? '#fff' : '#9ca3af',
                          fontSize: '0.78rem',
                          outline: 'none',
                          cursor: isRunning ? 'not-allowed' : 'pointer'
                        }}
                      >
                        {mergedDevices.length === 0 ? (
                          <option value="">No Devices Registered</option>
                        ) : (
                          mergedDevices.map((d) => (
                            <option key={d.device_id} value={d.device_id} style={{ backgroundColor: '#0b0e17', color: '#fff' }}>
                              {d.device_id} ({d.status})
                            </option>
                          ))
                        )}
                      </select>
                    </div>

                    {/* Run Now Button */}
                    <button
                      onClick={() => handleRunNow(task)}
                      disabled={isRunning || isDevOffline || mergedDevices.length === 0}
                      className="btn-spotify"
                      style={{
                        padding: '0.45rem 1rem',
                        fontSize: '0.8rem',
                        opacity: isRunning || isDevOffline ? 0.6 : 1,
                        cursor: isRunning || isDevOffline ? 'not-allowed' : 'pointer'
                      }}
                    >
                      {isRunning ? (
                        <>
                          <Loader2 size={14} className="spin" />
                          <span>RUNNING...</span>
                        </>
                      ) : (
                        <>
                          <Play size={14} fill="#000" />
                          <span>RUN NOW</span>
                        </>
                      )}
                    </button>
                  </div>
                </div>

                {/* Inline Error Banner if offline or dispatch error */}
                {execState?.error && (
                  <div style={{
                    marginTop: '0.875rem',
                    backgroundColor: 'rgba(239, 68, 68, 0.1)',
                    border: '1px solid rgba(239, 68, 68, 0.3)',
                    borderRadius: '6px',
                    padding: '0.5rem 0.75rem',
                    color: '#ef4444',
                    fontSize: '0.78rem',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '0.4rem'
                  }}>
                    <AlertCircle size={14} />
                    <span>{execState.error}</span>
                  </div>
                )}

                {/* Real-Time Step Progress Panel */}
                {execState && (execState.steps.length > 0 || execState.status) && (
                  <div style={{
                    marginTop: '1rem',
                    paddingTop: '0.875rem',
                    borderTop: '1px solid rgba(255, 255, 255, 0.06)'
                  }}>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.625rem' }}>
                      <span style={{ fontSize: '0.75rem', fontWeight: 600, color: '#9ca3af', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                        Execution Telemetry Stream
                      </span>

                      {/* Status Badges */}
                      {execState.status === 'SUCCESS' && (
                        <span className="badge badge-online" style={{ fontSize: '0.7rem', display: 'flex', alignItems: 'center', gap: '0.3rem' }}>
                          <CheckCircle2 size={12} /> SUCCESS
                        </span>
                      )}
                      {execState.status === 'FAILED' && (
                        <span className="badge badge-error" style={{ fontSize: '0.7rem', display: 'flex', alignItems: 'center', gap: '0.3rem' }}>
                          <XCircle size={12} /> FAILED
                        </span>
                      )}
                      {isRunning && (
                        <span className="badge badge-running" style={{ fontSize: '0.7rem', display: 'flex', alignItems: 'center', gap: '0.3rem' }}>
                          <Loader2 size={12} className="spin" /> RUNNING STEP PROGRESS
                        </span>
                      )}
                    </div>

                    {/* Step History Terminal Feed */}
                    <div style={{
                      backgroundColor: '#07090e',
                      border: '1px solid rgba(255, 255, 255, 0.06)',
                      borderRadius: '6px',
                      padding: '0.75rem 1rem',
                      fontFamily: 'monospace',
                      fontSize: '0.78rem',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: '0.4rem'
                    }}>
                      {execState.steps.map((st, idx) => (
                        <div key={idx} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                            {st.event_type === 'STEP_STARTED' && <Loader2 size={12} color="#3b82f6" className="spin" />}
                            {st.event_type === 'STEP_OK' && <CheckCircle2 size={12} color="#10b981" />}
                            {st.event_type === 'STEP_FAILED' && <XCircle size={12} color="#ef4444" />}
                            <span style={{
                              color: st.event_type === 'STEP_FAILED' ? '#ef4444' : st.event_type === 'STEP_OK' ? '#10b981' : '#3b82f6',
                              fontWeight: 600
                            }}>
                              Step {st.step_index || idx + 1}: {st.event_type}
                            </span>
                            <span style={{ color: '#d1d5db' }}>{st.action}</span>
                            {st.reason_code && (
                              <span style={{
                                backgroundColor: 'rgba(239, 68, 68, 0.2)',
                                color: '#ef4444',
                                padding: '0.1rem 0.4rem',
                                borderRadius: '4px',
                                fontSize: '0.72rem',
                                border: '1px solid rgba(239, 68, 68, 0.4)'
                              }}>
                                Reason: {st.reason_code}
                              </span>
                            )}
                          </div>
                          <span style={{ color: '#6b7280', fontSize: '0.7rem' }}>{st.timestamp}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
