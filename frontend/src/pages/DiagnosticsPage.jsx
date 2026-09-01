import React from 'react';
import { AlertOctagon, AlertTriangle, Bug, CheckCircle2, RefreshCw, Terminal, Zap } from 'lucide-react';

export default function DiagnosticsPage({ onRetryPipeline }) {
  const diagnosticItems = [
    {
      id: 'ERR-9021',
      run_id: 'run_8f9a01b2',
      task_name: 'Like Track & Save to Library',
      device_id: 'dev_redmi_note10',
      reason_code: 'UI_ELEMENT_NOT_FOUND',
      error_msg: 'Accessibility node [like_button] was not found in active window root after 2 retries (1000ms bounded wait)',
      payload: {
        task_id: "2",
        device_id: "dev_redmi_note10",
        action_type: "LIKE_TRACK",
        failed_node_id: "com.spotify.music:id/heart_button",
        retry_attempts: 2,
        bounded_timeout_ms: 1000
      },
      time: '15:30:12'
    },
    {
      id: 'ERR-9022',
      run_id: 'run_3c4d5e6f',
      task_name: 'Search & Play Track: Arijit Singh',
      device_id: 'dev_pixel_6',
      reason_code: 'UNEXPECTED_STATE',
      error_msg: 'Search results container loaded but target item failed to launch audio player within 10sec timeout',
      payload: {
        task_id: "1",
        device_id: "dev_pixel_6",
        action_type: "SEARCH_AND_PLAY",
        expected_state: "PLAYER_ACTIVE",
        actual_state: "SEARCH_RESULTS_VIEW",
        timeout_sec: 10
      },
      time: '14:45:00'
    }
  ];

  return (
    <div>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.5rem' }}>
        <div>
          <h3 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#fff' }}>Diagnostic Review & Error Analysis</h3>
          <p style={{ fontSize: '0.85rem', color: '#9ca3af', marginTop: '0.2rem' }}>
            Root cause analysis, failed accessibility node traces, and reason code inspection
          </p>
        </div>

        <button onClick={() => onRetryPipeline(diagnosticItems[0])} className="btn-spotify">
          <RefreshCw size={16} />
          <span>RETRY ALL FAILED PIPELINES</span>
        </button>
      </div>

      {/* Summary Cards */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.25rem', marginBottom: '1.75rem' }}>
        <div className="glass-panel" style={{ padding: '1.25rem', borderLeft: '4px solid #10b981' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
            <span style={{ fontSize: '0.8rem', fontWeight: 600, color: '#9ca3af', textTransform: 'uppercase' }}>Healthy Sessions</span>
            <CheckCircle2 size={20} color="#10b981" />
          </div>
          <div style={{ fontSize: '1.75rem', fontWeight: 800, color: '#fff' }}>142 Passed</div>
          <div style={{ fontSize: '0.75rem', color: '#6b7280', marginTop: '0.2rem' }}>0 Reason Code Errors</div>
        </div>

        <div className="glass-panel" style={{ padding: '1.25rem', borderLeft: '4px solid #ef4444' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
            <span style={{ fontSize: '0.8rem', fontWeight: 600, color: '#9ca3af', textTransform: 'uppercase' }}>Failed Exceptions</span>
            <AlertOctagon size={20} color="#ef4444" />
          </div>
          <div style={{ fontSize: '1.75rem', fontWeight: 800, color: '#fff' }}>2 Action Failures</div>
          <div style={{ fontSize: '0.75rem', color: '#ef4444', marginTop: '0.2rem', fontWeight: 600 }}>Action Required: Retry or Adjust Node Selector</div>
        </div>
      </div>

      {/* Diagnostic Error Details List */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
        {diagnosticItems.map((item) => (
          <div key={item.id} className="glass-panel" style={{ padding: '1.5rem', border: '1px solid rgba(239, 68, 68, 0.25)' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem' }}>
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.625rem' }}>
                  <span className="badge badge-offline">{item.reason_code}</span>
                  <h4 style={{ fontSize: '1.05rem', fontWeight: 700, color: '#fff' }}>{item.task_name}</h4>
                </div>
                <div style={{ fontSize: '0.78rem', color: '#9ca3af', marginTop: '0.25rem', fontFamily: 'monospace' }}>
                  Run ID: {item.run_id} • Target Node: {item.device_id} • Time: {item.time}
                </div>
              </div>

              <button onClick={() => onRetryPipeline(item)} className="btn-spotify" style={{ padding: '0.5rem 1rem', fontSize: '0.82rem' }}>
                <RefreshCw size={14} />
                <span>RETRY PIPELINE NOW</span>
              </button>
            </div>

            {/* Error Message Box */}
            <div style={{
              backgroundColor: 'rgba(239, 68, 68, 0.08)',
              border: '1px solid rgba(239, 68, 68, 0.2)',
              borderRadius: '8px',
              padding: '1rem',
              color: '#f87171',
              fontSize: '0.85rem',
              marginBottom: '1rem',
              display: 'flex',
              alignItems: 'flex-start',
              gap: '0.75rem'
            }}>
              <AlertTriangle size={18} color="#ef4444" style={{ flexShrink: 0, marginTop: '2px' }} />
              <div>
                <strong style={{ display: 'block', marginBottom: '0.2rem' }}>Exception Summary:</strong>
                {item.error_msg}
              </div>
            </div>

            {/* Full JSON Payload Box */}
            <div style={{
              backgroundColor: '#07090e',
              border: '1px solid rgba(255, 255, 255, 0.08)',
              borderRadius: '8px',
              padding: '1rem',
              fontFamily: 'monospace',
              fontSize: '0.78rem',
              color: '#10b981'
            }}>
              <div style={{ fontSize: '0.7rem', color: '#6b7280', marginBottom: '0.5rem' }}>// FULL EVENT PAYLOAD TRACE</div>
              <pre style={{ margin: 0, whiteSpace: 'pre-wrap' }}>
                {JSON.stringify(item.payload, null, 2)}
              </pre>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
