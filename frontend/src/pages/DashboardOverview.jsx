import React from 'react';
import { 
  Activity, 
  AlertCircle, 
  CheckCircle2, 
  Clock, 
  Play, 
  Radio, 
  Server, 
  Smartphone, 
  Zap 
} from 'lucide-react';

export default function DashboardOverview({ onNavigate }) {
  const metrics = [
    { title: 'Connected Devices', value: '12', sub: '12 ONLINE • 0 OFFLINE', icon: Smartphone, color: '#10b981' },
    { title: 'Running Tasks', value: '6', sub: 'Processing active queues', icon: Activity, color: '#3b82f6' },
    { title: 'Completed Runs', value: '142', sub: '98.6% success rate', icon: CheckCircle2, color: '#1db954' },
    { title: 'Failed Execution', value: '2', sub: 'Requires diagnostic review', icon: AlertCircle, color: '#ef4444' },
  ];

  const activeSessions = [
    { id: '#SESS-4902', device: 'Infinix X6817 (dev_infinix_01)', task: 'Play Playlist: Chill Hits', progress: 57, status: 'RUNNING', step: 'CLICK_SEARCH_TAB' },
    { id: '#SESS-4903', device: 'Galaxy S21 (dev_samsung_02)', task: 'Like Track & Save', progress: 85, status: 'RUNNING', step: 'LIKE_ACTION_OK' },
    { id: '#SESS-4904', device: 'Pixel 6 (dev_pixel_03)', task: 'Follow Artist: Atif Aslam', progress: 30, status: 'RUNNING', step: 'SEARCH_QUERY_ENTERED' },
  ];

  const recentLogs = [
    { id: 1, time: '15:32:10', device: 'Infinix X6817', type: 'STEP_OK', text: 'Accessibility node [search_tab] clicked via ACTION_CLICK', status: 'SUCCESS' },
    { id: 2, time: '15:31:54', device: 'Galaxy S21', type: 'STEP_STARTED', text: 'Spotify launch initiated via Accessibility Service', status: 'RUNNING' },
    { id: 3, time: '15:30:12', device: 'Redmi Note 10', type: 'STEP_FAILED', text: 'Expected UI element [like_button] not found. Retried 2 attempts', status: 'FAILED' },
    { id: 4, time: '15:28:40', device: 'Pixel 6', type: 'STEP_OK', text: 'Command payload cmd_8f9a01 dispatched over WebSocket', status: 'SUCCESS' },
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
                {m.value}
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
        {/* Left: Active Cluster Sessions */}
        <div className="glass-panel" style={{ padding: '1.5rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.25rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.625rem' }}>
              <Server size={20} color="#1db954" />
              <h3 style={{ fontSize: '1.1rem', fontWeight: 700, color: '#fff' }}>Active Cluster Sessions</h3>
            </div>
            <button onClick={() => onNavigate('sessions')} className="btn-secondary" style={{ padding: '0.375rem 0.75rem', fontSize: '0.78rem' }}>
              View All (3)
            </button>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            {activeSessions.map((session) => (
              <div key={session.id} style={{
                padding: '1.25rem',
                borderRadius: '10px',
                backgroundColor: 'rgba(0, 0, 0, 0.3)',
                border: '1px solid rgba(255, 255, 255, 0.06)'
              }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
                  <div>
                    <span className="font-mono" style={{ fontSize: '0.78rem', color: '#1db954', fontWeight: 700, marginRight: '0.5rem' }}>
                      {session.id}
                    </span>
                    <span style={{ fontSize: '0.9rem', fontWeight: 600, color: '#fff' }}>
                      {session.task}
                    </span>
                  </div>
                  <span className="badge badge-running">
                    <span className="pulse-dot pulse-dot-green" style={{ width: '6px', height: '6px' }}></span> {session.status}
                  </span>
                </div>

                <div style={{ fontSize: '0.8rem', color: '#9ca3af', marginBottom: '0.75rem', display: 'flex', justifyContent: 'space-between' }}>
                  <span>Node: <strong style={{ color: '#d1d5db' }}>{session.device}</strong></span>
                  <span className="font-mono" style={{ color: '#3b82f6' }}>Current: {session.step}</span>
                </div>

                {/* Progress bar */}
                <div style={{ width: '100%', height: '6px', backgroundColor: 'rgba(255, 255, 255, 0.08)', borderRadius: '3px', overflow: 'hidden' }}>
                  <div style={{
                    width: `${session.progress}%`,
                    height: '100%',
                    background: 'linear-gradient(90deg, #3b82f6 0%, #1db954 100%)',
                    borderRadius: '3px'
                  }}></div>
                </div>

                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '0.5rem', fontSize: '0.72rem', color: '#6b7280' }}>
                  <span>Progress: {session.progress}%</span>
                  <span onClick={() => onNavigate('telemetry')} style={{ color: '#1db954', cursor: 'pointer', fontWeight: 600 }}>
                    Monitor Telemetry →
                  </span>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Right: Real-Time Execution Log Feed */}
        <div className="glass-panel" style={{ padding: '1.5rem', display: 'flex', flexDirection: 'column' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.25rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <Radio size={18} color="#10b981" />
              <h3 style={{ fontSize: '1rem', fontWeight: 700, color: '#fff' }}>Live Event Feed</h3>
            </div>
            <span className="badge badge-online" style={{ fontSize: '0.65rem', padding: '0.1rem 0.4rem' }}>● LIVE</span>
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
            overflowY: 'auto'
          }}>
            {recentLogs.map((log) => (
              <div key={log.id} style={{ borderBottom: '1px solid rgba(255, 255, 255, 0.04)', paddingBottom: '0.625rem' }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.25rem' }}>
                  <span style={{ color: '#6b7280' }}>[{log.time}]</span>
                  <span style={{
                    color: log.status === 'SUCCESS' ? '#10b981' : log.status === 'FAILED' ? '#ef4444' : '#3b82f6',
                    fontWeight: 700
                  }}>
                    {log.type}
                  </span>
                </div>
                <div style={{ color: '#d1d5db', marginBottom: '0.2rem', wordBreak: 'break-word' }}>
                  {log.text}
                </div>
                <div style={{ color: '#9ca3af', fontSize: '0.7rem' }}>
                  Device: {log.device}
                </div>
              </div>
            ))}
          </div>

          <button onClick={() => onNavigate('history')} className="btn-secondary" style={{ width: '100%', justifyContent: 'center', marginTop: '1rem', fontSize: '0.8rem' }}>
            Open Full Log Inspector
          </button>
        </div>
      </div>
    </div>
  );
}
