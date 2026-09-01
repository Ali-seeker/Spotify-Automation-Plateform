import React, { useState } from 'react';
import { Calendar, Clock, OctagonAlert, Play, Plus, RefreshCw, StopCircle, X } from 'lucide-react';

export default function SessionsControlPage() {
  const [showScheduleModal, setShowScheduleModal] = useState(false);
  const [cronExpr, setCronExpr] = useState('0 12 * * *');
  const [taskSelect, setTaskSelect] = useState('Play Playlist: Chill Hits');

  const [activeSessions, setActiveSessions] = useState([
    { id: 'SESS-4902', name: 'Chill Hits Playlist Streamer', device: 'Infinix HOT 12 Pro (dev_infinix_x6817)', progress: 57, started: '15:32:00', duration: '1m 45s' },
    { id: 'SESS-4903', name: 'Like Track & Save to Library', device: 'Samsung Galaxy S21 (dev_samsung_s21)', progress: 85, started: '15:31:00', duration: '2m 10s' },
    { id: 'SESS-4904', name: 'Follow Artist Page: Atif Aslam', device: 'Google Pixel 6 Pro (dev_pixel_6)', progress: 30, started: '15:33:15', duration: '0m 45s' },
  ]);

  const [schedules, setSchedules] = useState([
    { id: 1, name: 'Daily Morning Playlist Streamer', cron: '0 09 * * *', target_task: 'Play Playlist: Chill Hits', target_device: 'All Cluster Devices', status: 'ACTIVE' },
    { id: 2, name: 'Hourly Like & Save Verification', cron: '0 * * * *', target_task: 'Search & Like Track: Atif Aslam', target_device: 'dev_infinix_x6817', status: 'ACTIVE' },
    { id: 3, name: 'Weekly Artist Follow Expansion', cron: '0 12 * * 1', target_task: 'Follow Artist: Arijit Singh', target_device: 'dev_samsung_s21', status: 'PAUSED' },
  ]);

  const handleStopSession = (sessionId) => {
    setActiveSessions(activeSessions.filter(s => s.id !== sessionId));
  };

  const handleAddSchedule = (e) => {
    e.preventDefault();
    const newSch = {
      id: schedules.length + 1,
      name: `Scheduled ${taskSelect}`,
      cron: cronExpr,
      target_task: taskSelect,
      target_device: 'dev_infinix_x6817',
      status: 'ACTIVE'
    };
    setSchedules([...schedules, newSch]);
    setShowScheduleModal(false);
  };

  return (
    <div>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.5rem' }}>
        <div>
          <h3 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#fff' }}>Cluster Sessions & Cron Control</h3>
          <p style={{ fontSize: '0.85rem', color: '#9ca3af', marginTop: '0.2rem' }}>
            Monitor active real-time cluster sessions and configure recurring cron pipeline schedules
          </p>
        </div>

        <button onClick={() => setShowScheduleModal(true)} className="btn-spotify">
          <Plus size={18} fill="#000" />
          <span>SCHEDULE NEW SESSION</span>
        </button>
      </div>

      {/* Active Running Cluster Sessions */}
      <div style={{ marginBottom: '2rem' }}>
        <h4 style={{ fontSize: '1.05rem', fontWeight: 700, color: '#fff', marginBottom: '1rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <Clock size={18} color="#1db954" />
          <span>Active Session Control</span>
        </h4>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '1.25rem' }}>
          {activeSessions.map((sess) => (
            <div key={sess.id} className="glass-panel" style={{ padding: '1.25rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.75rem' }}>
                <div>
                  <span className="font-mono" style={{ fontSize: '0.78rem', color: '#1db954', fontWeight: 700 }}>
                    #{sess.id}
                  </span>
                  <h5 style={{ fontSize: '0.95rem', fontWeight: 700, color: '#fff', marginTop: '0.1rem' }}>
                    {sess.name}
                  </h5>
                </div>

                <span className="badge badge-running">
                  <span className="pulse-dot pulse-dot-green" style={{ width: '6px', height: '6px' }}></span> RUNNING
                </span>
              </div>

              <div style={{ fontSize: '0.78rem', color: '#9ca3af', marginBottom: '0.75rem' }}>
                Target: <strong style={{ color: '#d1d5db' }}>{sess.device}</strong>
              </div>

              {/* Progress */}
              <div style={{ marginBottom: '1rem' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.75rem', color: '#6b7280', marginBottom: '0.3rem' }}>
                  <span>Elapsed: {sess.duration}</span>
                  <span className="font-mono" style={{ color: '#3b82f6', fontWeight: 600 }}>{sess.progress}%</span>
                </div>
                <div style={{ width: '100%', height: '6px', backgroundColor: 'rgba(255, 255, 255, 0.08)', borderRadius: '3px', overflow: 'hidden' }}>
                  <div style={{ width: `${sess.progress}%`, height: '100%', backgroundColor: '#3b82f6', borderRadius: '3px' }}></div>
                </div>
              </div>

              <button
                onClick={() => handleStopSession(sess.id)}
                className="btn-danger"
                style={{ width: '100%', justifyContent: 'center', padding: '0.4rem', fontSize: '0.78rem' }}
              >
                <StopCircle size={14} />
                <span>STOP ACTIVE SESSION</span>
              </button>
            </div>
          ))}
        </div>
      </div>

      {/* Scheduled Pipelines Table */}
      <div>
        <h4 style={{ fontSize: '1.05rem', fontWeight: 700, color: '#fff', marginBottom: '1rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <Calendar size={18} color="#3b82f6" />
          <span>Scheduled Automation Cron Jobs</span>
        </h4>

        <div className="glass-panel" style={{ padding: '0', overflow: 'hidden' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.85rem' }}>
            <thead>
              <tr style={{ backgroundColor: 'rgba(0, 0, 0, 0.4)', borderBottom: '1px solid rgba(255, 255, 255, 0.08)', color: '#6b7280', fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                <th style={{ padding: '1rem 1.25rem' }}>Schedule Name</th>
                <th style={{ padding: '1rem 1.25rem' }}>Cron Expression</th>
                <th style={{ padding: '1rem 1.25rem' }}>Pipeline Task</th>
                <th style={{ padding: '1rem 1.25rem' }}>Target Nodes</th>
                <th style={{ padding: '1rem 1.25rem' }}>Status</th>
                <th style={{ padding: '1rem 1.25rem', textAlign: 'right' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {schedules.map((sch) => (
                <tr key={sch.id} style={{ borderBottom: '1px solid rgba(255, 255, 255, 0.04)' }}>
                  <td style={{ padding: '1rem 1.25rem' }}>
                    <div style={{ fontWeight: 600, color: '#fff' }}>{sch.name}</div>
                  </td>

                  <td style={{ padding: '1rem 1.25rem' }} className="font-mono">
                    <span style={{ color: '#10b981', backgroundColor: 'rgba(16, 185, 129, 0.1)', padding: '0.2rem 0.5rem', borderRadius: '4px' }}>
                      {sch.cron}
                    </span>
                  </td>

                  <td style={{ padding: '1rem 1.25rem', color: '#d1d5db' }}>
                    {sch.target_task}
                  </td>

                  <td style={{ padding: '1rem 1.25rem', color: '#9ca3af', fontSize: '0.8rem' }} className="font-mono">
                    {sch.target_device}
                  </td>

                  <td style={{ padding: '1rem 1.25rem' }}>
                    <span className={`badge ${sch.status === 'ACTIVE' ? 'badge-online' : 'badge-warning'}`}>
                      {sch.status}
                    </span>
                  </td>

                  <td style={{ padding: '1rem 1.25rem', textAlign: 'right' }}>
                    <button className="btn-secondary" style={{ padding: '0.35rem 0.75rem', fontSize: '0.75rem' }}>
                      {sch.status === 'ACTIVE' ? 'Pause Cron' : 'Resume Cron'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Schedule Modal */}
      {showScheduleModal && (
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
          <div className="glass-panel glass-panel-glow" style={{ width: '450px', padding: '2rem', border: '1px solid rgba(59, 130, 246, 0.3)' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.5rem' }}>
              <h3 style={{ fontSize: '1.1rem', fontWeight: 700, color: '#fff' }}>Schedule Automation Pipeline</h3>
              <X size={20} color="#9ca3af" style={{ cursor: 'pointer' }} onClick={() => setShowScheduleModal(false)} />
            </div>

            <form onSubmit={handleAddSchedule}>
              <div style={{ marginBottom: '1.25rem' }}>
                <label style={{ display: 'block', fontSize: '0.78rem', fontWeight: 600, color: '#d1d5db', marginBottom: '0.5rem' }}>
                  Select Pipeline Task
                </label>
                <select
                  value={taskSelect}
                  onChange={(e) => setTaskSelect(e.target.value)}
                  style={{
                    width: '100%',
                    backgroundColor: 'rgba(0, 0, 0, 0.4)',
                    border: '1px solid rgba(255, 255, 255, 0.12)',
                    borderRadius: '8px',
                    padding: '0.75rem',
                    color: '#fff',
                    outline: 'none'
                  }}
                >
                  <option value="Play Playlist: Chill Hits">Play Playlist: Chill Hits</option>
                  <option value="Search & Like Track: Atif Aslam">Search & Like Track: Atif Aslam</option>
                  <option value="Follow Artist: Arijit Singh">Follow Artist: Arijit Singh</option>
                </select>
              </div>

              <div style={{ marginBottom: '1.5rem' }}>
                <label style={{ display: 'block', fontSize: '0.78rem', fontWeight: 600, color: '#d1d5db', marginBottom: '0.5rem' }}>
                  Cron Schedule Expression (5-field)
                </label>
                <input
                  type="text"
                  required
                  value={cronExpr}
                  onChange={(e) => setCronExpr(e.target.value)}
                  placeholder="e.g. 0 12 * * *"
                  style={{
                    width: '100%',
                    backgroundColor: 'rgba(0, 0, 0, 0.4)',
                    border: '1px solid rgba(255, 255, 255, 0.12)',
                    borderRadius: '8px',
                    padding: '0.75rem',
                    color: '#fff',
                    fontFamily: 'monospace',
                    outline: 'none'
                  }}
                />
                <span style={{ fontSize: '0.72rem', color: '#6b7280', marginTop: '0.3rem', display: 'block' }}>
                  Format: minute hour day-of-month month day-of-week
                </span>
              </div>

              <div style={{ display: 'flex', gap: '0.75rem', justifyContent: 'flex-end' }}>
                <button type="button" onClick={() => setShowScheduleModal(false)} className="btn-secondary">
                  Cancel
                </button>
                <button type="submit" className="btn-spotify">
                  Schedule Cron
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
