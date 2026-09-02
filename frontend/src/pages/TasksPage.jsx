import React, { useState, useEffect } from 'react';
import { ListTodo, Play, Plus, RefreshCw, AlertCircle, Clock } from 'lucide-react';
import { getTasksApi } from '../services/apiService';

export default function TasksPage({ onNavigateToBuilder, onLaunchTask }) {
  const [tasks, setTasks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState('');

  useEffect(() => {
    fetchTasks();
  }, []);

  const fetchTasks = async () => {
    setLoading(true);
    setErrorMsg('');
    try {
      const data = await getTasksApi();
      setTasks(data);
    } catch (err) {
      setErrorMsg('Failed to load task library from backend API.');
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

        <div style={{ display: 'flex', gap: '0.75rem' }}>
          <button onClick={fetchTasks} className="btn-secondary">
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
          <div>Loading task definitions from backend database...</div>
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
        /* Task List Table */
        <div className="glass-panel" style={{ padding: '0', overflow: 'hidden' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.85rem' }}>
            <thead>
              <tr style={{ backgroundColor: 'rgba(0, 0, 0, 0.4)', borderBottom: '1px solid rgba(255, 255, 255, 0.08)', color: '#6b7280', fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                <th style={{ padding: '1rem 1.25rem' }}>Task Designation</th>
                <th style={{ padding: '1rem 1.25rem' }}>Action Type</th>
                <th style={{ padding: '1rem 1.25rem' }}>Target Search Query</th>
                <th style={{ padding: '1rem 1.25rem' }}>Parameters</th>
                <th style={{ padding: '1rem 1.25rem' }}>Created At</th>
                <th style={{ padding: '1rem 1.25rem', textAlign: 'right' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {tasks.map((task) => (
                <tr key={task.id} style={{ borderBottom: '1px solid rgba(255, 255, 255, 0.04)', transition: 'background-color 0.15s' }}>
                  <td style={{ padding: '1rem 1.25rem' }}>
                    <div style={{ fontWeight: 600, color: '#fff' }}>{task.task_name}</div>
                    <div style={{ fontSize: '0.75rem', color: '#6b7280', fontFamily: 'monospace' }}>ID: #{task.id}</div>
                  </td>

                  <td style={{ padding: '1rem 1.25rem' }}>
                    <span className="badge badge-running" style={{ fontSize: '0.7rem' }}>
                      {task.action_type}
                    </span>
                  </td>

                  <td style={{ padding: '1rem 1.25rem', color: '#d1d5db' }} className="font-mono">
                    {task.search_query || 'N/A'}
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
                      {task.action_params ? JSON.stringify(task.action_params) : '{}'}
                    </span>
                  </td>

                  <td style={{ padding: '1rem 1.25rem', color: '#9ca3af', fontSize: '0.78rem' }} className="font-mono">
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.3rem' }}>
                      <Clock size={12} color="#6b7280" />
                      <span>{formatDate(task.created_at)}</span>
                    </div>
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
      )}
    </div>
  );
}
