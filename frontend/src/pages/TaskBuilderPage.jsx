import React, { useState } from 'react';
import { AlertCircle, Code, Play, Save, Sliders, Terminal, Zap, CheckCircle2, RefreshCw } from 'lucide-react';
import { createTaskApi } from '../services/apiService';

export default function TaskBuilderPage({ onSaveTaskSuccess, onLaunchTask }) {
  const [taskName, setTaskName] = useState('');
  const [actionType, setActionType] = useState('SEARCH_AND_PLAY');
  const [searchQuery, setSearchQuery] = useState('');
  const [playDuration, setPlayDuration] = useState('60');
  const [likeProbability, setLikeProbability] = useState('0.8');
  const [skipProbability, setSkipProbability] = useState('0.2');

  const [errors, setErrors] = useState({});
  const [loading, setLoading] = useState(false);
  const [serverError, setServerError] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  const isSearchRequired = ['SEARCH_AND_PLAY', 'FOLLOW_ARTIST', 'SAVE_TRACK'].includes(actionType);

  const validateForm = () => {
    const errs = {};
    if (!taskName.trim()) {
      errs.taskName = 'Task Designation Name is required';
    }
    if (!actionType) {
      errs.actionType = 'Action Type is required';
    }
    if (isSearchRequired && !searchQuery.trim()) {
      errs.searchQuery = `Target Search Query is required for ${actionType} action type`;
    }
    
    // Validate probabilities if entered
    if (likeProbability !== '' && (isNaN(likeProbability) || Number(likeProbability) < 0 || Number(likeProbability) > 1)) {
      errs.likeProbability = 'Like probability must be between 0.0 and 1.0';
    }
    if (skipProbability !== '' && (isNaN(skipProbability) || Number(skipProbability) < 0 || Number(skipProbability) > 1)) {
      errs.skipProbability = 'Skip probability must be between 0.0 and 1.0';
    }
    if (playDuration !== '' && (isNaN(playDuration) || Number(playDuration) < 0)) {
      errs.playDuration = 'Duration must be a positive number';
    }

    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const jsonPreview = {
    task_name: taskName || 'Unnamed Task',
    action_type: actionType,
    search_query: searchQuery || null,
    action_params: {
      play_duration: playDuration ? Number(playDuration) : 60,
      like_probability: likeProbability ? Number(likeProbability) : 0.8,
      skip_probability: skipProbability ? Number(skipProbability) : 0.2
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setServerError('');
    setSuccessMsg('');

    if (!validateForm()) {
      return;
    }

    setLoading(true);

    const payload = {
      task_name: taskName.trim(),
      action_type: actionType,
      search_query: searchQuery.trim() || null,
      action_params: {
        play_duration: playDuration ? Number(playDuration) : 60,
        like_probability: likeProbability ? Number(likeProbability) : 0.8,
        skip_probability: skipProbability ? Number(skipProbability) : 0.2
      }
    };

    try {
      const createdTask = await createTaskApi(payload);
      setSuccessMsg(`Task "${createdTask.task_name}" created successfully (ID #${createdTask.id})`);
      setTimeout(() => {
        if (onSaveTaskSuccess) {
          onSaveTaskSuccess(createdTask);
        }
      }, 1000);
    } catch (err) {
      if (err.response && err.response.data && err.response.data.detail) {
        setServerError(typeof err.response.data.detail === 'string' ? err.response.data.detail : 'Failed to create task.');
      } else {
        setServerError('Network or backend server error occurred.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      {/* Header */}
      <div style={{ marginBottom: '1.5rem' }}>
        <h3 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#fff' }}>Task Builder & Action Configurator</h3>
        <p style={{ fontSize: '0.85rem', color: '#9ca3af', marginTop: '0.2rem' }}>
          Define Spotify UI automation task definitions, target queries, and action parameters
        </p>
      </div>

      {/* Server Error / Success Banners */}
      {serverError && (
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
          <span>{serverError}</span>
        </div>
      )}

      {successMsg && (
        <div style={{
          backgroundColor: 'rgba(16, 185, 129, 0.12)',
          border: '1px solid rgba(16, 185, 129, 0.3)',
          borderRadius: '8px',
          padding: '0.75rem 1rem',
          color: '#10b981',
          fontSize: '0.82rem',
          marginBottom: '1.25rem',
          display: 'flex',
          alignItems: 'center',
          gap: '0.5rem'
        }}>
          <CheckCircle2 size={18} />
          <span>{successMsg}</span>
        </div>
      )}

      {/* 2-Column Grid */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem' }}>
        {/* Left Column: Form Controls */}
        <div className="glass-panel" style={{ padding: '1.5rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1.25rem' }}>
            <Sliders size={18} color="#1db954" />
            <h4 style={{ fontSize: '1rem', fontWeight: 700, color: '#fff' }}>Pipeline Parameters</h4>
          </div>

          <form onSubmit={handleSubmit} noValidate>
            {/* Task Name */}
            <div style={{ marginBottom: '1.25rem' }}>
              <label style={{ display: 'block', fontSize: '0.78rem', fontWeight: 600, color: '#d1d5db', marginBottom: '0.4rem' }}>
                Task Designation Name <span style={{ color: '#ef4444' }}>*</span>
              </label>
              <input
                type="text"
                value={taskName}
                onChange={(e) => {
                  setTaskName(e.target.value);
                  if (errors.taskName) setErrors({ ...errors, taskName: null });
                }}
                placeholder="e.g. Play Playlist: Chill Hits"
                style={{
                  width: '100%',
                  backgroundColor: 'rgba(0, 0, 0, 0.4)',
                  border: errors.taskName ? '1px solid #ef4444' : '1px solid rgba(255, 255, 255, 0.1)',
                  borderRadius: '8px',
                  padding: '0.75rem',
                  color: '#fff',
                  fontSize: '0.88rem',
                  outline: 'none'
                }}
              />
              {errors.taskName && (
                <div style={{ color: '#ef4444', fontSize: '0.75rem', marginTop: '0.3rem' }}>
                  {errors.taskName}
                </div>
              )}
            </div>

            {/* Action Type */}
            <div style={{ marginBottom: '1.25rem' }}>
              <label style={{ display: 'block', fontSize: '0.78rem', fontWeight: 600, color: '#d1d5db', marginBottom: '0.4rem' }}>
                Action Type Template <span style={{ color: '#ef4444' }}>*</span>
              </label>
              <select
                value={actionType}
                onChange={(e) => {
                  setActionType(e.target.value);
                  if (errors.actionType) setErrors({ ...errors, actionType: null });
                }}
                style={{
                  width: '100%',
                  backgroundColor: 'rgba(0, 0, 0, 0.4)',
                  border: errors.actionType ? '1px solid #ef4444' : '1px solid rgba(255, 255, 255, 0.1)',
                  borderRadius: '8px',
                  padding: '0.75rem',
                  color: '#fff',
                  fontSize: '0.88rem',
                  outline: 'none'
                }}
              >
                <option value="SEARCH_AND_PLAY">SEARCH_AND_PLAY — Search & Play Target</option>
                <option value="LIKE_CURRENT_TRACK">LIKE_CURRENT_TRACK — Like Playing Track</option>
                <option value="FOLLOW_ARTIST">FOLLOW_ARTIST — Follow Target Artist</option>
                <option value="SKIP_TRACK">SKIP_TRACK — Skip Track on Queue</option>
                <option value="SAVE_TRACK">SAVE_TRACK — Save Track to Library</option>
              </select>
              {errors.actionType && (
                <div style={{ color: '#ef4444', fontSize: '0.75rem', marginTop: '0.3rem' }}>
                  {errors.actionType}
                </div>
              )}
            </div>

            {/* Search Query */}
            <div style={{ marginBottom: '1.25rem' }}>
              <label style={{ display: 'block', fontSize: '0.78rem', fontWeight: 600, color: '#d1d5db', marginBottom: '0.4rem' }}>
                Target Search Query {isSearchRequired ? <span style={{ color: '#ef4444' }}>*</span> : '(Optional)'}
              </label>
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => {
                  setSearchQuery(e.target.value);
                  if (errors.searchQuery) setErrors({ ...errors, searchQuery: null });
                }}
                placeholder="e.g. Atif Aslam - Tu Jaane Na"
                style={{
                  width: '100%',
                  backgroundColor: 'rgba(0, 0, 0, 0.4)',
                  border: errors.searchQuery ? '1px solid #ef4444' : '1px solid rgba(255, 255, 255, 0.1)',
                  borderRadius: '8px',
                  padding: '0.75rem',
                  color: '#fff',
                  fontSize: '0.88rem',
                  outline: 'none'
                }}
              />
              {errors.searchQuery && (
                <div style={{ color: '#ef4444', fontSize: '0.75rem', marginTop: '0.3rem' }}>
                  {errors.searchQuery}
                </div>
              )}
            </div>

            {/* Action Parameters Grid */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '0.75rem', marginBottom: '1.5rem' }}>
              <div>
                <label style={{ display: 'block', fontSize: '0.72rem', fontWeight: 600, color: '#d1d5db', marginBottom: '0.3rem' }}>
                  Play Duration (sec)
                </label>
                <input
                  type="number"
                  value={playDuration}
                  onChange={(e) => {
                    setPlayDuration(e.target.value);
                    if (errors.playDuration) setErrors({ ...errors, playDuration: null });
                  }}
                  style={{
                    width: '100%',
                    backgroundColor: 'rgba(0, 0, 0, 0.4)',
                    border: errors.playDuration ? '1px solid #ef4444' : '1px solid rgba(255, 255, 255, 0.1)',
                    borderRadius: '8px',
                    padding: '0.6rem',
                    color: '#fff',
                    outline: 'none',
                    fontSize: '0.82rem'
                  }}
                />
                {errors.playDuration && (
                  <div style={{ color: '#ef4444', fontSize: '0.68rem', marginTop: '0.2rem' }}>
                    {errors.playDuration}
                  </div>
                )}
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.72rem', fontWeight: 600, color: '#d1d5db', marginBottom: '0.3rem' }}>
                  Like Probability
                </label>
                <input
                  type="number"
                  step="0.1"
                  value={likeProbability}
                  onChange={(e) => {
                    setLikeProbability(e.target.value);
                    if (errors.likeProbability) setErrors({ ...errors, likeProbability: null });
                  }}
                  style={{
                    width: '100%',
                    backgroundColor: 'rgba(0, 0, 0, 0.4)',
                    border: errors.likeProbability ? '1px solid #ef4444' : '1px solid rgba(255, 255, 255, 0.1)',
                    borderRadius: '8px',
                    padding: '0.6rem',
                    color: '#fff',
                    outline: 'none',
                    fontSize: '0.82rem'
                  }}
                />
                {errors.likeProbability && (
                  <div style={{ color: '#ef4444', fontSize: '0.68rem', marginTop: '0.2rem' }}>
                    {errors.likeProbability}
                  </div>
                )}
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.72rem', fontWeight: 600, color: '#d1d5db', marginBottom: '0.3rem' }}>
                  Skip Probability
                </label>
                <input
                  type="number"
                  step="0.1"
                  value={skipProbability}
                  onChange={(e) => {
                    setSkipProbability(e.target.value);
                    if (errors.skipProbability) setErrors({ ...errors, skipProbability: null });
                  }}
                  style={{
                    width: '100%',
                    backgroundColor: 'rgba(0, 0, 0, 0.4)',
                    border: errors.skipProbability ? '1px solid #ef4444' : '1px solid rgba(255, 255, 255, 0.1)',
                    borderRadius: '8px',
                    padding: '0.6rem',
                    color: '#fff',
                    outline: 'none',
                    fontSize: '0.82rem'
                  }}
                />
                {errors.skipProbability && (
                  <div style={{ color: '#ef4444', fontSize: '0.68rem', marginTop: '0.2rem' }}>
                    {errors.skipProbability}
                  </div>
                )}
              </div>
            </div>

            <div style={{ display: 'flex', gap: '0.75rem' }}>
              <button
                type="submit"
                disabled={loading}
                className="btn-spotify"
                style={{ flex: 1, justifyContent: 'center', opacity: loading ? 0.7 : 1 }}
              >
                {loading ? (
                  <span>SAVING TASK...</span>
                ) : (
                  <>
                    <Save size={16} fill="#000" />
                    <span>SAVE DEFINITION (POST /tasks)</span>
                  </>
                )}
              </button>
            </div>
          </form>
        </div>

        {/* Right Column: Node Live Terminal JSON Preview */}
        <div className="glass-panel" style={{ padding: '1.5rem', display: 'flex', flexDirection: 'column' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <Terminal size={18} color="#10b981" />
              <h4 style={{ fontSize: '1rem', fontWeight: 700, color: '#fff' }}>POST /tasks Payload Preview</h4>
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
