import React, { useState, useEffect } from 'react';
import { 
  Activity, 
  AlertCircle, 
  CheckCircle2, 
  Clock, 
  Radio, 
  Server, 
  Smartphone, 
  Zap,
  RefreshCw
} from 'lucide-react';
import { getDevicesApi, getTasksApi, getRunsApi } from '../services/apiService';
import { useFrontendWebSocket } from '../hooks/useFrontendWebSocket';

export default function DashboardOverview({ onNavigate }) {
  const [devices, setDevices] = useState([]);
  const [tasks, setTasks] = useState([]);
  const [runs, setRuns] = useState([]);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState('');

  const { isConnected, deviceUpdates, recentEvents } = useFrontendWebSocket();

  useEffect(() => {
    loadDashboardData();
  }, []);

  const loadDashboardData = async () => {
    setLoading(true);
    setErrorMsg('');
    try {
      const [devsRes, tasksRes, runsRes] = await Promise.all([
        getDevicesApi().catch(() => []),
        getTasksApi().catch(() => []),
        getRunsApi().catch(() => [])
      ]);
      setDevices(devsRes);
      setTasks(tasksRes);
      setRuns(runsRes);
    } catch (err) {
      setErrorMsg('Failed to load some dashboard data from backend server.');
    } finally {
      setLoading(false);
    }
  };

  // Merge dynamic WebSocket status updates into devices array
  const mergedDevices = devices.map((d) => {
    const update = deviceUpdates[d.device_id];
    return update ? { ...d, status: update.status, last_seen: update.last_seen } : d;
  });

  const onlineDevicesCount = mergedDevices.filter(d => d.status === 'ONLINE' || d.status === 'IDLE' || d.status === 'BUSY').length;
  const runningTasksCount = runs.filter(r => r.status === 'RUNNING').length;
  const completedRunsCount = runs.filter(r => r.status === 'SUCCESS').length;
  const failedRunsCount = runs.filter(r => r.status === 'FAILED').length;

  const metrics = [
    { title: 'Connected Devices', value: mergedDevices.length, sub: `${onlineDevicesCount} ONLINE • ${mergedDevices.length - onlineDevicesCount} OFFLINE`, icon: Smartphone, color: '#10b981' },
    { title: 'Running Tasks', value: runningTasksCount, sub: 'Active execution sessions', icon: Activity, color: '#3b82f6' },
    { title: 'Completed Runs', value: completedRunsCount, sub: 'Historical successful runs', icon: CheckCircle2, color: '#1db954' },
    { title: 'Failed Execution', value: failedRunsCount, sub: 'Diagnostic review required', icon: AlertCircle, color: '#ef4444' },
  ];

  return (
    <div>
      {/* 4 Stat Cards */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
        gap: '1.25rem',
        marginBottom: '2rem'
      }}>
        {metrics.map((m, idx) => {
          const Icon = m.icon;
          return (
            <div key={idx} className="glass-panel" style={{ padding: '1.25rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.75rem' }}>
                <span style={{ fontSize: '0.8rem', fontWeight: 600, color: '#9ca3af', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                  {m.title}
                </span>
                <div style={{
                  width: '36px',
                  height: '36px',
                  borderRadius: '8px',
                  backgroundColor: `${m.color}15`,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: m.color
                }}>
                  <Icon size={20} />
                </div>
              </div>
              <div style={{ fontSize: '2rem', fontWeight: 800, color: '#fff', marginBottom: '0.25rem' }}>
                {loading ? '...' : m.value}
              </div>
              <div style={{ fontSize: '0.75rem', color: '#6b7280', fontFamily: 'monospace' }}>
                {m.sub}
              </div>
            </div>
          );
        })}
      </div>

      {/* Main Grid: Active Cluster Sessions & Live Feed */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: '2fr 1fr',
        gap: '1.5rem'
      }}>
        {/* Left: Active Cluster Sessions & Device Summary */}
        <div className="glass-panel" style={{ padding: '1.5rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.25rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.625rem' }}>
              <Server size={20} color="#1db954" />
              <h3 style={{ fontSize: '1.1rem', fontWeight: 700, color: '#fff' }}>Cluster Device Overview</h3>
            </div>
            <button onClick={() => onNavigate('devices')} className="btn-secondary" style={{ padding: '0.375rem 0.75rem', fontSize: '0.78rem' }}>
              Manage Devices ({mergedDevices.length})
            </button>
          </div>

          {loading ? (
            <div style={{ padding: '2rem', textAlign: 'center', color: '#9ca3af' }}>
              <RefreshCw size={24} className="spin" style={{ marginBottom: '0.5rem' }} />
              <div>Fetching cluster devices from backend database...</div>
            </div>
          ) : mergedDevices.length === 0 ? (
            <div style={{
              padding: '2.5rem 1.5rem',
              textAlign: 'center',
              backgroundColor: 'rgba(0, 0, 0, 0.25)',
              borderRadius: '10px',
              border: '1px border-dashed rgba(255, 255, 255, 0.1)'
            }}>
              <Smartphone size={32} color="#6b7280" style={{ marginBottom: '0.75rem' }} />
              <h4 style={{ color: '#fff', fontSize: '1rem', fontWeight: 600 }}>No Devices Registered</h4>
              <p style={{ color: '#9ca3af', fontSize: '0.82rem', marginTop: '0.25rem', marginBottom: '1rem' }}>
                No Android devices are currently registered in the database.
              </p>
              <button onClick={() => onNavigate('devices')} className="btn-spotify" style={{ fontSize: '0.8rem' }}>
                Go to Devices & Pair Node
              </button>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              {mergedDevices.slice(0, 4).map((dev) => (
                <div key={dev.id || dev.device_id} style={{
                  padding: '1.25rem',
                  borderRadius: '10px',
                  backgroundColor: 'rgba(0, 0, 0, 0.3)',
                  border: '1px solid rgba(255, 255, 255, 0.06)'
                }}>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
                    <div>
                      <span className="font-mono" style={{ fontSize: '0.85rem', color: '#1db954', fontWeight: 700, marginRight: '0.5rem' }}>
                        {dev.device_id}
                      </span>
                    </div>
                    <span className={`badge ${dev.status === 'ONLINE' || dev.status === 'IDLE' ? 'badge-online' : dev.status === 'BUSY' ? 'badge-running' : 'badge-offline'}`}>
                      <span className="pulse-dot pulse-dot-green" style={{ width: '6px', height: '6px' }}></span> {dev.status}
                    </span>
                  </div>

                  <div style={{ fontSize: '0.78rem', color: '#9ca3af', display: 'flex', justifyContent: 'space-between' }}>
                    <span>Last Seen: <strong style={{ color: '#d1d5db' }}>{dev.last_seen || 'N/A'}</strong></span>
                    <span onClick={() => onNavigate('devices')} style={{ color: '#3b82f6', cursor: 'pointer', fontWeight: 600 }}>
                      Inspect Device →
                    </span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Right: Real-Time Execution Log Feed */}
        <div className="glass-panel" style={{ padding: '1.5rem', display: 'flex', flexDirection: 'column' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.25rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <Radio size={18} color="#10b981" />
              <h3 style={{ fontSize: '1rem', fontWeight: 700, color: '#fff' }}>Live Event Stream</h3>
            </div>
            <span className={`badge ${isConnected ? 'badge-online' : 'badge-warning'}`} style={{ fontSize: '0.65rem', padding: '0.1rem 0.4rem' }}>
              {isConnected ? '● STREAM ACTIVE' : 'POLLING'}
            </span>
          </div>

          <div style={{
            flex: 1,
            backgroundColor: 'rgba(0, 0, 0, 0.5)',
            border: '1px solid rgba(255, 255, 255, 0.06)',
            borderRadius: '8px',
            padding: '1rem',
            fontFamily: 'monospace',
            fontSize: '0.75rem',
            display: 'flex',
            flexDirection: 'column',
            gap: '0.875rem',
            overflowY: 'auto',
            minHeight: '220px'
          }}>
            {recentEvents.length === 0 ? (
              <div style={{ color: '#6b7280', textAlign: 'center', margin: 'auto' }}>
                Listening for real-time WebSocket events...
              </div>
            ) : (
              recentEvents.map((evt, idx) => (
                <div key={idx} style={{ borderBottom: '1px solid rgba(255, 255, 255, 0.04)', paddingBottom: '0.625rem' }}>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.25rem' }}>
                    <span style={{ color: '#6b7280' }}>Run: {evt.run_id}</span>
                    <span style={{ color: '#10b981', fontWeight: 700 }}>{evt.event_type}</span>
                  </div>
                  <div style={{ color: '#d1d5db', wordBreak: 'break-word' }}>
                    {JSON.stringify(evt.payload || {})}
                  </div>
                </div>
              ))
            )}
          </div>

          <button onClick={() => onNavigate('history')} className="btn-secondary" style={{ width: '100%', justifyContent: 'center', marginTop: '1rem', fontSize: '0.8rem' }}>
            Open Full Log Inspector
          </button>
        </div>
      </div>
    </div>
  );
}
