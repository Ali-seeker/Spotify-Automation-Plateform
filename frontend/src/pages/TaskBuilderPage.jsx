import React, { useState } from 'react';
import { Code, Play, Save, Sliders, Terminal, Zap } from 'lucide-react';

export default function TaskBuilderPage({ onSaveTask, onLaunchTask }) {
  const [taskName, setTaskName] = useState('Search & Play Top Track');
  const [actionType, setActionType] = useState('SEARCH_AND_PLAY');
  const [searchQuery, setSearchQuery] = useState('Atif Aslam');
  const [timeoutSec, setTimeoutSec] = useState(10);
  const [autoPlay, setAutoPlay] = useState(true);
  const [verifyState, setVerifyState] = useState(true);
  const [targetDevice, setTargetDevice] = useState('dev_infinix_x6817');

  const jsonPreview = {
    task_name: taskName,
    action_type: actionType,
    search_query: searchQuery,
    action_params: {
      timeout_sec: Number(timeoutSec),
      auto_play: autoPlay,
      verify_state: verifyState,
      target_device: targetDevice
    },
    created_at: new Date().toISOString()
  };

  const handleSave = (e) => {
    e.preventDefault();
    onSaveTask(jsonPreview);
  };

  return (
    <div>
      {/* Header */}
      <div style={{ marginBottom: '1.5rem' }}>
        <h3 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#fff' }}>Task Builder & Action Configurator</h3>
        <p style={{ fontSize: '0.85rem', color: '#9ca3af', marginTop: '0.2rem' }}>
          Define Spotify UI automation parameters, node action targets, and payload schemas
        </p>
      </div>

      {/* 2-Column Grid */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem' }}>
        {/* Left Column: Form Controls */}
        <div className="glass-panel" style={{ padding: '1.5rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1.25rem' }}>
            <Sliders size={18} color="#1db954" />
            <h4 style={{ fontSize: '1rem', fontWeight: 700, color: '#fff' }}>Pipeline Parameters</h4>
          </div>

          <form onSubmit={handleSave}>
            <div style={{ marginBottom: '1.25rem' }}>
              <label style={{ display: 'block', fontSize: '0.78rem', fontWeight: 600, color: '#d1d5db', marginBottom: '0.4rem' }}>
                Task Designation Name
              </label>
              <input
                type="text"
                required
                value={taskName}
                onChange={(e) => setTaskName(e.target.value)}
                style={{
                  width: '100%',
                  backgroundColor: 'rgba(0, 0, 0, 0.4)',
                  border: '1px solid rgba(255, 255, 255, 0.1)',
                  borderRadius: '8px',
                  padding: '0.75rem',
                  color: '#fff',
                  fontSize: '0.88rem',
                  outline: 'none'
                }}
              />
            </div>

            <div style={{ marginBottom: '1.25rem' }}>
              <label style={{ display: 'block', fontSize: '0.78rem', fontWeight: 600, color: '#d1d5db', marginBottom: '0.4rem' }}>
                Action Type Template
              </label>
              <select
                value={actionType}
                onChange={(e) => setActionType(e.target.value)}
                style={{
                  width: '100%',
                  backgroundColor: 'rgba(0, 0, 0, 0.4)',
                  border: '1px solid rgba(255, 255, 255, 0.1)',
                  borderRadius: '8px',
                  padding: '0.75rem',
                  color: '#fff',
                  fontSize: '0.88rem',
                  outline: 'none'
                }}
              >
                <option value="SEARCH_AND_PLAY">SEARCH_AND_PLAY — Search & Play Track</option>
                <option value="LIKE_TRACK">LIKE_TRACK — Like Track & Save to Library</option>
                <option value="FOLLOW_ARTIST">FOLLOW_ARTIST — Follow Artist Page</option>
                <option value="SKIP_TRACK">SKIP_TRACK — Skip Next Track</option>
                <option value="SAVE_TRACK">SAVE_TRACK — Save Track to Liked Songs</option>
              </select>
            </div>

            <div style={{ marginBottom: '1.25rem' }}>
              <label style={{ display: 'block', fontSize: '0.78rem', fontWeight: 600, color: '#d1d5db', marginBottom: '0.4rem' }}>
                Target Search Query
              </label>
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="e.g. Atif Aslam - Tu Jaane Na"
                style={{
                  width: '100%',
                  backgroundColor: 'rgba(0, 0, 0, 0.4)',
                  border: '1px solid rgba(255, 255, 255, 0.1)',
                  borderRadius: '8px',
                  padding: '0.75rem',
                  color: '#fff',
                  fontSize: '0.88rem',
                  outline: 'none'
                }}
              />
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem', marginBottom: '1.25rem' }}>
              <div>
                <label style={{ display: 'block', fontSize: '0.78rem', fontWeight: 600, color: '#d1d5db', marginBottom: '0.4rem' }}>
                  Execution Timeout (sec)
                </label>
                <input
                  type="number"
                  value={timeoutSec}
                  onChange={(e) => setTimeoutSec(e.target.value)}
                  style={{
                    width: '100%',
                    backgroundColor: 'rgba(0, 0, 0, 0.4)',
                    border: '1px solid rgba(255, 255, 255, 0.1)',
                    borderRadius: '8px',
                    padding: '0.75rem',
                    color: '#fff',
                    outline: 'none'
                  }}
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.78rem', fontWeight: 600, color: '#d1d5db', marginBottom: '0.4rem' }}>
                  Target Android Node
                </label>
                <select
                  value={targetDevice}
                  onChange={(e) => setTargetDevice(e.target.value)}
                  style={{
                    width: '100%',
                    backgroundColor: 'rgba(0, 0, 0, 0.4)',
                    border: '1px solid rgba(255, 255, 255, 0.1)',
                    borderRadius: '8px',
                    padding: '0.75rem',
                    color: '#fff',
                    outline: 'none'
                  }}
                >
                  <option value="dev_infinix_x6817">Infinix HOT 12 Pro (ONLINE)</option>
                  <option value="dev_samsung_s21">Samsung Galaxy S21 (BUSY)</option>
                  <option value="dev_pixel_6">Google Pixel 6 Pro (ONLINE)</option>
                </select>
              </div>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', marginBottom: '1.5rem' }}>
              <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.82rem', color: '#d1d5db', cursor: 'pointer' }}>
                <input
                  type="checkbox"
                  checked={autoPlay}
                  onChange={(e) => setAutoPlay(e.target.checked)}
                  style={{ accentColor: '#1db954', width: '16px', height: '16px' }}
                />
                <span>Auto-start playback on target item click</span>
              </label>

              <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.82rem', color: '#d1d5db', cursor: 'pointer' }}>
                <input
                  type="checkbox"
                  checked={verifyState}
                  onChange={(e) => setVerifyState(e.target.checked)}
                  style={{ accentColor: '#1db954', width: '16px', height: '16px' }}
                />
                <span>Verify post-click UI state change (Bounded 1s Retry)</span>
              </label>
            </div>

            <div style={{ display: 'flex', gap: '0.75rem' }}>
              <button type="submit" className="btn-secondary" style={{ flex: 1, justifyContent: 'center' }}>
                <Save size={16} />
                <span>SAVE DEFINITION</span>
              </button>
              <button
                type="button"
                onClick={() => onLaunchTask(jsonPreview)}
                className="btn-spotify"
                style={{ flex: 1, justifyContent: 'center' }}
              >
                <Zap size={16} fill="#000" />
                <span>EXECUTE NOW</span>
              </button>
            </div>
          </form>
        </div>

        {/* Right Column: Node Live Terminal JSON Preview */}
        <div className="glass-panel" style={{ padding: '1.5rem', display: 'flex', flexDirection: 'column' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <Terminal size={18} color="#10b981" />
              <h4 style={{ fontSize: '1rem', fontWeight: 700, color: '#fff' }}>Node Output Feed Preview</h4>
            </div>
            <span className="font-mono" style={{ fontSize: '0.72rem', color: '#10b981' }}>JSON PROTOCOL v1.0</span>
          </div>

          <div style={{
            flex: 1,
            backgroundColor: '#07090e',
            border: '1px solid rgba(29, 185, 84, 0.2)',
            borderRadius: '8px',
            padding: '1.25rem',
            fontFamily: 'monospace',
            fontSize: '0.82rem',
            color: '#10b981',
            overflowY: 'auto',
            boxShadow: 'inset 0 0 20px rgba(0, 0, 0, 0.8)'
          }}>
            <pre style={{ margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
              {JSON.stringify(jsonPreview, null, 2)}
            </pre>
          </div>
        </div>
      </div>
    </div>
  );
}
