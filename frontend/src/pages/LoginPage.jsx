import React, { useState } from 'react';
import { Lock, Radio, ShieldCheck, Terminal, User } from 'lucide-react';

export default function LoginPage({ onLoginSuccess }) {
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('admin123');
  const [rememberNode, setRememberNode] = useState(true);
  const [loading, setLoading] = useState(false);

  const handleSubmit = (e) => {
    e.preventDefault();
    setLoading(true);
    setTimeout(() => {
      setLoading(false);
      onLoginSuccess();
    }, 600);
  };

  return (
    <div style={{
      minHeight: '100vh',
      width: '100vw',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      backgroundColor: '#0b0e17',
      backgroundImage: `
        radial-gradient(circle at 50% 30%, rgba(29, 185, 84, 0.12) 0%, transparent 60%),
        radial-gradient(circle at 10% 90%, rgba(59, 130, 246, 0.08) 0%, transparent 50%)
      `,
      padding: '2rem'
    }}>
      <div className="glass-panel glass-panel-glow" style={{
        width: '100%',
        maxWidth: '440px',
        padding: '2.5rem',
        border: '1px solid rgba(29, 185, 84, 0.25)',
        boxShadow: '0 0 40px rgba(0, 0, 0, 0.8), 0 0 20px rgba(29, 185, 84, 0.15)'
      }}>
        {/* Logo Banner */}
        <div style={{ textAlign: 'center', marginBottom: '2rem' }}>
          <div style={{
            width: '56px',
            height: '56px',
            borderRadius: '16px',
            background: 'linear-gradient(135deg, #1db954 0%, #10b981 100%)',
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow: '0 0 24px rgba(29, 185, 84, 0.5)',
            marginBottom: '1rem'
          }}>
            <Radio size={30} color="#000" strokeWidth={2.5} />
          </div>
          <h2 style={{ fontSize: '1.5rem', fontWeight: 800, color: '#fff', letterSpacing: '-0.02em' }}>
            SPOTIFY<span style={{ color: '#1db954' }}>BOT</span>
          </h2>
          <p style={{ fontSize: '0.85rem', color: '#9ca3af', marginTop: '0.25rem' }}>
            Session Initialization & Master Node Control
          </p>
        </div>

        {/* Login Form */}
        <form onSubmit={handleSubmit}>
          <div style={{ marginBottom: '1.25rem' }}>
            <label style={{ display: 'block', fontSize: '0.78rem', fontWeight: 600, color: '#d1d5db', marginBottom: '0.5rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              Node Identifier / Username
            </label>
            <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
              <User size={18} color="#6b7280" style={{ position: 'absolute', left: '14px' }} />
              <input
                type="text"
                required
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="e.g. admin"
                style={{
                  width: '100%',
                  backgroundColor: 'rgba(0, 0, 0, 0.4)',
                  border: '1px solid rgba(255, 255, 255, 0.12)',
                  borderRadius: '10px',
                  padding: '0.75rem 1rem 0.75rem 2.6rem',
                  color: '#fff',
                  fontSize: '0.9rem',
                  outline: 'none',
                  transition: 'all 0.2s'
                }}
                onFocus={(e) => e.target.style.borderColor = '#1db954'}
                onBlur={(e) => e.target.style.borderColor = 'rgba(255, 255, 255, 0.12)'}
              />
            </div>
          </div>

          <div style={{ marginBottom: '1.5rem' }}>
            <label style={{ display: 'block', fontSize: '0.78rem', fontWeight: 600, color: '#d1d5db', marginBottom: '0.5rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              Secret Key / Access Password
            </label>
            <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
              <Lock size={18} color="#6b7280" style={{ position: 'absolute', left: '14px' }} />
              <input
                type="password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••••••"
                style={{
                  width: '100%',
                  backgroundColor: 'rgba(0, 0, 0, 0.4)',
                  border: '1px solid rgba(255, 255, 255, 0.12)',
                  borderRadius: '10px',
                  padding: '0.75rem 1rem 0.75rem 2.6rem',
                  color: '#fff',
                  fontSize: '0.9rem',
                  outline: 'none',
                  transition: 'all 0.2s'
                }}
                onFocus={(e) => e.target.style.borderColor = '#1db954'}
                onBlur={(e) => e.target.style.borderColor = 'rgba(255, 255, 255, 0.12)'}
              />
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.75rem' }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.8rem', color: '#9ca3af', cursor: 'pointer' }}>
              <input
                type="checkbox"
                checked={rememberNode}
                onChange={(e) => setRememberNode(e.target.checked)}
                style={{ accentColor: '#1db954', cursor: 'pointer', width: '16px', height: '16px' }}
              />
              <span>Remember Node Session</span>
            </label>

            <span style={{ fontSize: '0.78rem', color: '#1db954', cursor: 'pointer', textDecoration: 'none', fontWeight: 500 }}>
              Reset Credentials
            </span>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="btn-spotify"
            style={{
              width: '100%',
              justifyContent: 'center',
              padding: '0.875rem',
              fontSize: '0.95rem',
              borderRadius: '10px'
            }}
          >
            {loading ? (
              <span>INITIALIZING SESSION...</span>
            ) : (
              <>
                <ShieldCheck size={20} fill="#000" />
                <span>INITIALIZE SESSION</span>
              </>
            )}
          </button>
        </form>

        {/* Footer info */}
        <div style={{
          marginTop: '2rem',
          paddingTop: '1.25rem',
          borderTop: '1px solid rgba(255, 255, 255, 0.08)',
          textAlign: 'center',
          fontSize: '0.75rem',
          color: '#6b7280',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          gap: '0.4rem'
        }}>
          <Terminal size={14} color="#10b981" />
          <span>Secured via JWT HS256 & WebSocket Protocol</span>
        </div>
      </div>
    </div>
  );
}
