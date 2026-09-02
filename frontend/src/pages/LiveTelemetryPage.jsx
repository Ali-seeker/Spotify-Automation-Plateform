import React, { useState, useEffect } from 'react';
import { 
  Activity, 
  AlertTriangle, 
  CheckCircle2, 
  Cpu, 
  OctagonAlert, 
  Radio, 
  RefreshCw, 
  Smartphone, 
  Square, 
  Terminal, 
  Wifi 
} from 'lucide-react';

export default function LiveTelemetryPage() {
  const [progress, setProgress] = useState(57);
  const [isKilled, setIsKilled] = useState(false);

  const [logs, setLogs] = useState([
    { time: '15:32:00', type: 'STEP_STARTED', text: 'Task #SESS-4902 initiated on Node dev_infinix_x6817' },
    { time: '15:32:01', type: 'STEP_OK', text: 'Accessibility Service validated. Spotify package com.spotify.music brought to foreground' },
    { time: '15:32:03', type: 'STEP_OK', text: 'Root UI Node retrieved via getRootInActiveWindow(). Search tab accessibility node discovered' },
    { time: '15:32:04', type: 'STEP_OK', text: 'ACTION_CLICK dispatched to Search tab node (0 screen coordinates used)' },
    { time: '15:32:05', type: 'STEP_OK', text: 'Fresh UI tree re-fetched. State change verified: Search input box loaded cleanly' },
    { time: '15:32:07', type: 'STEP_STARTED', text: 'Entering search query: "Chill Hits Playlist"' },
  ]);

  const handleKill = () => {
    setIsKilled(true);
    setLogs((prev) => [
      ...prev,
      { time: new Date().toLocaleTimeString(), type: 'STEP_FAILED', text: 'OPERATION KILLED BY USER VIA MASTER DASHBOARD' }
    ]);
  };

  return (
    <div>
      {/* Session Active Banner */}
      <div className="glass-panel" style={{
        padding: '1.5rem',
        marginBottom: '1.5rem',
        borderLeft: isKilled ? '4px solid #ef4444' : '4px solid #1db954',
        background: 'linear-gradient(90deg, rgba(22, 25, 38, 0.95) 0%, rgba(13, 16, 26, 0.9) 100%)'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.625rem' }}>
              <span className="font-mono" style={{ fontSize: '0.9rem', color: '#1db954', fontWeight: 700 }}>
                #SESS-4902
              </span>
              <h3 style={{ fontSize: '1.2rem', fontWeight: 700, color: '#fff' }}>
                Chill Hits Playlist Streamer
              </h3>
              <span className={`badge ${isKilled ? 'badge-offline' : 'badge-online'}`}>
                {isKilled ? 'KILLED' : '● RUNNING'}
              </span>
            </div>
            <p style={{ fontSize: '0.8rem', color: '#9ca3af', marginTop: '0.25rem' }}>
              Node: <strong style={{ color: '#d1d5db' }}>Infinix HOT 12 Pro (dev_infinix_x6817)</strong> • Target: Spotify Search & Play
            </p>
          </div>

          <button
            onClick={handleKill}
            disabled={isKilled}
            className="btn-danger"
            style={{ opacity: isKilled ? 0.5 : 1 }}
          >
            <OctagonAlert size={18} />
            <span>{isKilled ? 'OPERATION TERMINATED' : 'KILL OPERATION'}</span>
          </button>
        </div>

        {/* Progress Bar */}
        <div>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.78rem', marginBottom: '0.4rem' }}>
            <span style={{ color: '#9ca3af' }}>Pipeline Execution Progress</span>
            <span className="font-mono" style={{ color: '#1db954', fontWeight: 700 }}>{isKilled ? 'ABORTED' : `${progress}%`}</span>
          </div>
          <div style={{ width: '100%', height: '8px', backgroundColor: 'rgba(255, 255, 255, 0.08)', borderRadius: '4px', overflow: 'hidden' }}>
            <div style={{
              width: `${isKilled ? 100 : progress}%`,
              height: '100%',
              backgroundColor: isKilled ? '#ef4444' : '#1db954',
              borderRadius: '4px',
              transition: 'width 0.3s'
            }}></div>
          </div>
        </div>
      </div>

      {/* Grid: Live Log Stream & Hardware Telemetry */}
      <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '1.5rem' }}>
        {/* Real-time Log Stream Terminal */}
        <div className="glass-panel" style={{ padding: '1.5rem', display: 'flex', flexDirection: 'column' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <Terminal size={18} color="#10b981" />
              <h4 style={{ fontSize: '1rem', fontWeight: 700, color: '#fff' }}>Real-Time Execution Logs</h4>
            </div>
            <span className="badge badge-online" style={{ fontSize: '0.65rem', padding: '0.1rem 0.4rem' }}>● LIVE STREAM</span>
          </div>

          <div style={{
            flex: 1,
            backgroundColor: '#05070c',
            border: '1px solid rgba(255, 255, 255, 0.08)',
            borderRadius: '8px',
            padding: '1.25rem',
            fontFamily: 'monospace',
            fontSize: '0.8rem',
            display: 'flex',
            flexDirection: 'column',
            gap: '0.75rem',
            overflowY: 'auto',
            maxHeight: '380px'
          }}>
            {logs.map((log, index) => (
              <div key={index} style={{ borderBottom: '1px solid rgba(255, 255, 255, 0.03)', paddingBottom: '0.5rem' }}>
                <span style={{ color: '#6b7280', marginRight: '0.75rem' }}>[{log.time}]</span>
                <span style={{
                  color: log.type === 'STEP_OK' ? '#10b981' : log.type === 'STEP_FAILED' ? '#ef4444' : '#3b82f6',
                  fontWeight: 700,
                  marginRight: '0.75rem'
                }}>
                  {log.type}
                </span>
                <span style={{ color: '#d1d5db' }}>{log.text}</span>
              </div>
            ))}
          </div>
        </div>

        {/* Hardware & Proxy Info */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
          <div className="glass-panel" style={{ padding: '1.25rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
              <Cpu size={18} color="#3b82f6" />
              <h4 style={{ fontSize: '0.95rem', fontWeight: 700, color: '#fff' }}>Device Hardware Telemetry</h4>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.875rem', fontSize: '0.82rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: '#9ca3af' }}>CPU Usage</span>
                <span className="font-mono" style={{ color: '#10b981', fontWeight: 600 }}>34%</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: '#9ca3af' }}>RAM Allocated</span>
                <span className="font-mono" style={{ color: '#3b82f6', fontWeight: 600 }}>1.4 GB / 4 GB</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: '#9ca3af' }}>Battery Status</span>
                <span className="font-mono" style={{ color: '#10b981', fontWeight: 600 }}>88% (Charging)</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: '#9ca3af' }}>Accessibility Service</span>
                <span className="font-mono" style={{ color: '#10b981', fontWeight: 600 }}>ACTIVE</span>
              </div>
            </div>
          </div>

          <div className="glass-panel" style={{ padding: '1.25rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
              <Wifi size={18} color="#10b981" />
              <h4 style={{ fontSize: '0.95rem', fontWeight: 700, color: '#fff' }}>Network & Proxy Details</h4>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.875rem', fontSize: '0.82rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: '#9ca3af' }}>Local Device IP</span>
                <span className="font-mono" style={{ color: '#fff' }}>192.168.1.104</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: '#9ca3af' }}>Residential Proxy IP</span>
                <span className="font-mono" style={{ color: '#10b981' }}>185.220.101.5</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: '#9ca3af' }}>Latency</span>
                <span className="font-mono" style={{ color: '#10b981' }}>42 ms</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
