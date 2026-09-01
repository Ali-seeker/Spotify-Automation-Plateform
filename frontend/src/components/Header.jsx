import React from 'react';
import { Bell, RefreshCw, Search, ShieldCheck, Zap } from 'lucide-react';

export default function Header({ pageTitle, onRefresh, onQuickLaunch }) {
  return (
    <header style={{
      height: '70px',
      position: 'fixed',
      top: 0,
      left: '260px',
      right: 0,
      backgroundColor: 'rgba(13, 16, 26, 0.9)',
      backdropFilter: 'blur(16px)',
      borderBottom: '1px solid rgba(255, 255, 255, 0.08)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      padding: '0 2rem',
      zIndex: 40
    }}>
      {/* Title & Page Header */}
      <div>
        <h2 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#ffffff', letterSpacing: '-0.01em' }}>
          {pageTitle}
        </h2>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.75rem', color: '#9ca3af' }}>
          <ShieldCheck size={14} color="#10b981" />
          <span>WebSocket Stream Active</span>
          <span style={{ color: '#4b5563' }}>•</span>
          <span className="font-mono" style={{ color: '#1db954' }}>ws://127.0.0.1:8000/ws/device</span>
        </div>
      </div>

      {/* Global Actions */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
        {/* Quick Search */}
        <div style={{
          position: 'relative',
          display: 'flex',
          alignItems: 'center'
        }}>
          <Search size={16} color="#6b7280" style={{ position: 'absolute', left: '12px' }} />
          <input
            type="text"
            placeholder="Search nodes, tasks, runs..."
            style={{
              backgroundColor: 'rgba(255, 255, 255, 0.04)',
              border: '1px solid rgba(255, 255, 255, 0.08)',
              borderRadius: '8px',
              padding: '0.5rem 1rem 0.5rem 2.25rem',
              color: '#fff',
              fontSize: '0.85rem',
              outline: 'none',
              width: '240px',
              transition: 'border-color 0.2s'
            }}
            onFocus={(e) => e.target.style.borderColor = 'rgba(29, 185, 84, 0.5)'}
            onBlur={(e) => e.target.style.borderColor = 'rgba(255, 255, 255, 0.08)'}
          />
        </div>

        {/* Refresh Cluster Data */}
        <button
          onClick={onRefresh}
          className="btn-secondary"
          style={{ padding: '0.5rem 0.875rem', fontSize: '0.85rem' }}
          title="Refresh Node Metrics"
        >
          <RefreshCw size={16} />
          <span>Sync</span>
        </button>

        {/* Quick Launch Trigger */}
        <button
          onClick={onQuickLaunch}
          className="btn-spotify"
          style={{ padding: '0.5rem 1rem', fontSize: '0.85rem' }}
        >
          <Zap size={16} fill="#000" />
          <span>Launch Pipeline</span>
        </button>

        {/* Notification Counter */}
        <button style={{
          width: '38px',
          height: '38px',
          borderRadius: '8px',
          backgroundColor: 'rgba(255, 255, 255, 0.04)',
          border: '1px solid rgba(255, 255, 255, 0.08)',
          color: '#9ca3af',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          cursor: 'pointer',
          position: 'relative'
        }}>
          <Bell size={18} />
          <span style={{
            position: 'absolute',
            top: '6px',
            right: '6px',
            width: '8px',
            height: '8px',
            borderRadius: '50%',
            backgroundColor: '#ef4444'
          }}></span>
        </button>
      </div>
    </header>
  );
}
