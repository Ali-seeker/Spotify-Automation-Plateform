import React from 'react';
import { ListTodo, Play, Plus, Search, Sliders, Trash2 } from 'lucide-react';

export default function TasksPage({ onNavigateToBuilder, onLaunchTask }) {
  const tasks = [
    { id: 1, name: 'Play Playlist: Chill Hits', type: 'SEARCH_AND_PLAY', query: 'Chill Hits Playlist', params: { timeout_sec: 10, auto_play: true, volume: 80 }, runs: 42, successRate: '100%' },
    { id: 2, name: 'Search & Like Track: Atif Aslam', type: 'LIKE_TRACK', query: 'Atif Aslam - Tu Jaane Na', params: { verify_state: true, retry_count: 2 }, runs: 38, successRate: '97.3%' },
    { id: 3, name: 'Follow Artist: Arijit Singh', type: 'FOLLOW_ARTIST', query: 'Arijit Singh', params: { check_following: true }, runs: 29, successRate: '96.5%' },
    { id: 4, name: 'Skip Track on Failure', type: 'SKIP_TRACK', query: 'N/A', params: { timeout_sec: 5 }, runs: 18, successRate: '100%' },
    { id: 5, name: 'Save Track to Your Library', type: 'SAVE_TRACK', query: 'Rahat Fateh Ali Khan', params: { verify_saved: true }, runs: 15, successRate: '93.3%' },
  ];

  return (
    <div>
      {/* Header Banner */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.5rem' }}>
        <div>
          <h3 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#fff' }}>Automation Task Library</h3>
          <p style={{ fontSize: '0.85rem', color: '#9ca3af', marginTop: '0.2rem' }}>
            Pre-configured automation action pipelines ready for cluster execution
          </p>
        </div>

        <button onClick={onNavigateToBuilder} className="btn-spotify">
          <Plus size={18} fill="#000" />
          <span>CREATE NEW TASK</span>
        </button>
      </div>

      {/* Task List Table */}
      <div className="glass-panel" style={{ padding: '0', overflow: 'hidden' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.85rem' }}>
          <thead>
            <tr style={{ backgroundColor: 'rgba(0, 0, 0, 0.4)', borderBottom: '1px solid rgba(255, 255, 255, 0.08)', color: '#6b7280', fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              <th style={{ padding: '1rem 1.25rem' }}>Task Designation</th>
              <th style={{ padding: '1rem 1.25rem' }}>Action Type</th>
              <th style={{ padding: '1rem 1.25rem' }}>Target Search Query</th>
              <th style={{ padding: '1rem 1.25rem' }}>Parameters</th>
              <th style={{ padding: '1rem 1.25rem' }}>Total Runs</th>
              <th style={{ padding: '1rem 1.25rem', textAlign: 'right' }}>Actions</th>
            </tr>
          </thead>
          <tbody>
            {tasks.map((task) => (
              <tr key={task.id} style={{ borderBottom: '1px solid rgba(255, 255, 255, 0.04)', transition: 'background-color 0.15s' }}>
                <td style={{ padding: '1rem 1.25rem' }}>
                  <div style={{ fontWeight: 600, color: '#fff' }}>{task.name}</div>
                  <div style={{ fontSize: '0.75rem', color: '#6b7280', fontFamily: 'monospace' }}>ID: task_0{task.id}</div>
                </td>

                <td style={{ padding: '1rem 1.25rem' }}>
                  <span className="badge badge-running" style={{ fontSize: '0.7rem' }}>
                    {task.type}
                  </span>
                </td>

                <td style={{ padding: '1rem 1.25rem', color: '#d1d5db' }} className="font-mono">
                  {task.query}
                </td>

                <td style={{ padding: '1rem 1.25rem' }}>
                  <span style={{
                    fontSize: '0.75rem',
                    fontFamily: 'monospace',
                    color: '#9ca3af',
                    backgroundColor: 'rgba(0, 0, 0, 0.4)',
                    padding: '0.2rem 0.5rem',
                    borderRadius: '4px',
                    border: '1px solid rgba(255, 255, 255, 0.06)'
                  }}>
                    {JSON.stringify(task.params)}
                  </span>
                </td>

                <td style={{ padding: '1rem 1.25rem' }}>
                  <div style={{ fontWeight: 600, color: '#fff' }}>{task.runs} runs</div>
                  <div style={{ fontSize: '0.75rem', color: '#10b981' }}>{task.successRate} pass</div>
                </td>

                <td style={{ padding: '1rem 1.25rem', textAlign: 'right' }}>
                  <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end' }}>
                    <button
                      onClick={() => onLaunchTask(task)}
                      className="btn-spotify"
                      style={{ padding: '0.35rem 0.75rem', fontSize: '0.78rem' }}
                    >
                      <Play size={14} fill="#000" />
                      <span>Execute</span>
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
