import React, { useState } from 'react';
import { Calendar, CheckCircle2, Eye, Filter, History, Search, X, XCircle } from 'lucide-react';

export default function AutomationHistoryPage() {
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [selectedRun, setSelectedRun] = useState(null);

  const runs = [
    { run_id: 'run_90f8a12b', task_name: 'Play Playlist: Chill Hits', device_id: 'dev_infinix_x6817', status: 'SUCCESS', start_time: '2026-09-01 15:32:00', end_time: '2026-09-01 15:32:08', events_count: 4, payload: { task_id: 1, device_id: 'dev_infinix_x6817', action_type: 'SEARCH_AND_PLAY', query: 'Chill Hits', result: true } },
    { run_id: 'run_8f9a01b2', task_name: 'Like Track & Save to Library', device_id: 'dev_redmi_note10', status: 'FAILED', start_time: '2026-09-01 15:30:00', end_time: '2026-09-01 15:30:12', events_count: 3, payload: { task_id: 2, device_id: 'dev_redmi_note10', action_type: 'LIKE_TRACK', reason_code: 'UI_ELEMENT_NOT_FOUND', attempts: 2 } },
    { run_id: 'run_7a6b5c4d', task_name: 'Follow Artist: Atif Aslam', device_id: 'dev_samsung_s21', status: 'SUCCESS', start_time: '2026-09-01 15:15:20', end_time: '2026-09-01 15:15:30', events_count: 4, payload: { task_id: 3, device_id: 'dev_samsung_s21', action_type: 'FOLLOW_ARTIST', artist: 'Atif Aslam', followed: true } },
    { run_id: 'run_6e5d4c3b', task_name: 'Search & Like Track: Atif Aslam', device_id: 'dev_pixel_6', status: 'SUCCESS', start_time: '2026-09-01 15:00:10', end_time: '2026-09-01 15:00:18', events_count: 4, payload: { task_id: 2, device_id: 'dev_pixel_6', action_type: 'LIKE_TRACK', result: true } },
    { run_id: 'run_5c4b3a2f', task_name: 'Skip Track on Failure', device_id: 'dev_oneplus_9', status: 'SUCCESS', start_time: '2026-09-01 14:50:00', end_time: '2026-09-01 14:50:05', events_count: 2, payload: { task_id: 4, device_id: 'dev_oneplus_9', action_type: 'SKIP_TRACK', skipped: true } },
    { run_id: 'run_4b3a2f1e', task_name: 'Save Track to Your Library', device_id: 'dev_infinix_x6817', status: 'SUCCESS', start_time: '2026-09-01 14:35:12', end_time: '2026-09-01 14:35:20', events_count: 3, payload: { task_id: 5, device_id: 'dev_infinix_x6817', action_type: 'SAVE_TRACK', saved: true } },
    { run_id: 'run_3c4d5e6f', task_name: 'Search & Play Track: Arijit Singh', device_id: 'dev_pixel_6', status: 'FAILED', start_time: '2026-09-01 14:45:00', end_time: '2026-09-01 14:45:10', events_count: 3, payload: { task_id: 1, device_id: 'dev_pixel_6', action_type: 'SEARCH_AND_PLAY', reason_code: 'UNEXPECTED_STATE' } },
  ];

  const filteredRuns = runs.filter(run => {
    const matchesSearch = run.task_name.toLowerCase().includes(searchTerm.toLowerCase()) || run.run_id.toLowerCase().includes(searchTerm.toLowerCase()) || run.device_id.toLowerCase().includes(searchTerm.toLowerCase());
    const matchesStatus = statusFilter === 'ALL' || run.status === statusFilter;
    return matchesSearch && matchesStatus;
  });

  return (
    <div>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.5rem' }}>
        <div>
          <h3 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#fff' }}>Automation Execution Run Logs</h3>
          <p style={{ fontSize: '0.85rem', color: '#9ca3af', marginTop: '0.2rem' }}>
            Complete historical record of task runs, step events, and full payload JSON traces
          </p>
        </div>

        <div style={{ display: 'flex', gap: '0.75rem' }}>
          <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
            <Search size={16} color="#6b7280" style={{ position: 'absolute', left: '12px' }} />
            <input
              type="text"
              placeholder="Search run ID, task, device..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              style={{
                backgroundColor: 'rgba(255, 255, 255, 0.04)',
                border: '1px solid rgba(255, 255, 255, 0.08)',
                borderRadius: '8px',
                padding: '0.5rem 1rem 0.5rem 2.25rem',
                color: '#fff',
                fontSize: '0.85rem',
                outline: 'none',
                width: '240px'
              }}
            />
          </div>

          <div style={{ display: 'flex', gap: '0.4rem', backgroundColor: 'rgba(255, 255, 255, 0.04)', padding: '0.2rem', borderRadius: '8px', border: '1px solid rgba(255, 255, 255, 0.08)' }}>
            {['ALL', 'SUCCESS', 'FAILED'].map((st) => (
              <button
                key={st}
                onClick={() => setStatusFilter(st)}
                style={{
                  padding: '0.4rem 0.8rem',
                  borderRadius: '6px',
                  border: 'none',
                  backgroundColor: statusFilter === st ? 'rgba(29, 185, 84, 0.2)' : 'transparent',
                  color: statusFilter === st ? '#1db954' : '#9ca3af',
                  fontWeight: 600,
                  fontSize: '0.78rem',
                  cursor: 'pointer'
                }}
              >
                {st}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Runs Table */}
      <div className="glass-panel" style={{ padding: '0', overflow: 'hidden' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.85rem' }}>
          <thead>
            <tr style={{ backgroundColor: 'rgba(0, 0, 0, 0.4)', borderBottom: '1px solid rgba(255, 255, 255, 0.08)', color: '#6b7280', fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              <th style={{ padding: '1rem 1.25rem' }}>Run Identifier</th>
              <th style={{ padding: '1rem 1.25rem' }}>Task Name</th>
              <th style={{ padding: '1rem 1.25rem' }}>Target Node</th>
              <th style={{ padding: '1rem 1.25rem' }}>Status</th>
              <th style={{ padding: '1rem 1.25rem' }}>Start Time</th>
              <th style={{ padding: '1rem 1.25rem' }}>End Time</th>
              <th style={{ padding: '1rem 1.25rem', textAlign: 'right' }}>Payload Trace</th>
            </tr>
          </thead>
          <tbody>
            {filteredRuns.map((run) => (
              <tr key={run.run_id} style={{ borderBottom: '1px solid rgba(255, 255, 255, 0.04)' }}>
                <td style={{ padding: '1rem 1.25rem' }} className="font-mono">
                  <span style={{ color: '#1db954', fontWeight: 600 }}>{run.run_id}</span>
                </td>

                <td style={{ padding: '1rem 1.25rem' }}>
                  <div style={{ fontWeight: 600, color: '#fff' }}>{run.task_name}</div>
                </td>

                <td style={{ padding: '1rem 1.25rem', color: '#3b82f6' }} className="font-mono">
                  {run.device_id}
                </td>

                <td style={{ padding: '1rem 1.25rem' }}>
                  <span className={`badge ${run.status === 'SUCCESS' ? 'badge-online' : 'badge-offline'}`}>
                    {run.status === 'SUCCESS' ? <CheckCircle2 size={12} /> : <XCircle size={12} />}
                    {run.status}
                  </span>
                </td>

                <td style={{ padding: '1rem 1.25rem', color: '#9ca3af', fontSize: '0.78rem' }} className="font-mono">
                  {run.start_time}
                </td>

                <td style={{ padding: '1rem 1.25rem', color: '#9ca3af', fontSize: '0.78rem' }} className="font-mono">
                  {run.end_time}
                </td>

                <td style={{ padding: '1rem 1.25rem', textAlign: 'right' }}>
                  <button onClick={() => setSelectedRun(run)} className="btn-secondary" style={{ padding: '0.35rem 0.75rem', fontSize: '0.75rem' }}>
                    <Eye size={14} />
                    <span>View JSON</span>
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Payload Modal */}
      {selectedRun && (
        <div style={{
          position: 'fixed',
          top: 0, left: 0, right: 0, bottom: 0,
          backgroundColor: 'rgba(0, 0, 0, 0.75)',
          backdropFilter: 'blur(8px)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 100
        }}>
          <div className="glass-panel glass-panel-glow" style={{ width: '560px', padding: '2rem', border: '1px solid rgba(29, 185, 84, 0.3)' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.25rem' }}>
              <div>
                <h4 style={{ fontSize: '1.1rem', fontWeight: 700, color: '#fff' }}>Run Payload Inspector</h4>
                <div className="font-mono" style={{ fontSize: '0.78rem', color: '#1db954' }}>{selectedRun.run_id}</div>
              </div>
              <X size={20} color="#9ca3af" style={{ cursor: 'pointer' }} onClick={() => setSelectedRun(null)} />
            </div>

            <div style={{
              backgroundColor: '#05070c',
              border: '1px solid rgba(29, 185, 84, 0.2)',
              borderRadius: '8px',
              padding: '1.25rem',
              fontFamily: 'monospace',
              fontSize: '0.82rem',
              color: '#10b981',
              overflowY: 'auto',
              maxHeight: '300px'
            }}>
              <pre style={{ margin: 0, whiteSpace: 'pre-wrap' }}>
                {JSON.stringify(selectedRun.payload, null, 2)}
              </pre>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '1.5rem' }}>
              <button onClick={() => setSelectedRun(null)} className="btn-secondary">
                Close Inspector
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
