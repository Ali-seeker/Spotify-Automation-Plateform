import React from 'react';
import { 
  LayoutDashboard, 
  Smartphone, 
  ListTodo, 
  PlusCircle, 
  Activity, 
  Bug, 
  History, 
  Clock, 
  LogOut,
  Radio
} from 'lucide-react';

export default function Sidebar({ currentPage, setCurrentPage, onLogout }) {
  const navItems = [
    { id: 'overview', label: 'Overview', icon: LayoutDashboard },
    { id: 'devices', label: 'Device Cluster', icon: Smartphone, badge: '12' },
    { id: 'tasks', label: 'Task Library', icon: ListTodo },
    { id: 'builder', label: 'Task Builder', icon: PlusCircle },
    { id: 'telemetry', label: 'Live Telemetry', icon: Activity, live: true },
    { id: 'diagnostics', label: 'Diagnostics', icon: Bug, alert: true },
    { id: 'history', label: 'Run Logs', icon: History },
    { id: 'sessions', label: 'Sessions Control', icon: Clock, badge: '3' },
  ];

  return (
    <aside style={{
      width: '260px',
      height: '100vh',
      position: 'fixed',
      left: 0,
      top: 0,
      backgroundColor: '#0d101a',
      borderRight: '1px solid rgba(255, 255, 255, 0.08)',
      display: 'flex',
      flexDirection: 'column',
      zIndex: 50,
    }}>
      {/* Brand Header */}
      <div style={{
        padding: '1.5rem',
        display: 'flex',
        alignItems: 'center',
        gap: '0.75rem',
        borderBottom: '1px solid rgba(255, 255, 255, 0.06)'
      }}>
        <div style={{
          width: '36px',
          height: '36px',
          borderRadius: '10px',
          background: 'linear-gradient(135deg, #1db954 0%, #10b981 100%)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          boxShadow: '0 0 16px rgba(29, 185, 84, 0.4)'
        }}>
          <Radio size={20} color="#000" strokeWidth={2.5} />
        </div>
        <div>
          <h1 style={{ fontSize: '1rem', fontWeight: 700, letterSpacing: '-0.02em', color: '#fff' }}>
            SPOTIFY<span style={{ color: '#1db954' }}>BOT</span>
          </h1>
          <p style={{ fontSize: '0.7rem', color: '#6b7280', fontFamily: 'monospace' }}>
            NODE CONTROL v1.4
          </p>
        </div>
      </div>

      {/* Navigation List */}
      <nav style={{ flex: 1, padding: '1rem 0.75rem', overflowY: 'auto' }}>
        <div style={{ fontSize: '0.68rem', fontWeight: 700, color: '#4b5563', letterSpacing: '0.08em', padding: '0 0.75rem 0.5rem 0.75rem', textTransform: 'uppercase' }}>
          NAVIGATION
        </div>
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = currentPage === item.id;
          return (
            <button
              key={item.id}
              onClick={() => setCurrentPage(item.id)}
              style={{
                width: '100%',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '0.75rem 0.875rem',
                marginBottom: '0.25rem',
                borderRadius: '8px',
                border: 'none',
                backgroundColor: isActive ? 'rgba(29, 185, 84, 0.12)' : 'transparent',
                color: isActive ? '#1db954' : '#9ca3af',
                fontWeight: isActive ? 600 : 500,
                fontSize: '0.875rem',
                cursor: 'pointer',
                transition: 'all 0.15s ease',
                borderLeft: isActive ? '3px solid #1db954' : '3px solid transparent'
              }}
              onMouseEnter={(e) => {
                if (!isActive) {
                  e.currentTarget.style.backgroundColor = 'rgba(255, 255, 255, 0.04)';
                  e.currentTarget.style.color = '#fff';
                }
              }}
              onMouseLeave={(e) => {
                if (!isActive) {
                  e.currentTarget.style.backgroundColor = 'transparent';
                  e.currentTarget.style.color = '#9ca3af';
                }
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                <Icon size={18} color={isActive ? '#1db954' : '#9ca3af'} />
                <span>{item.label}</span>
              </div>

              {item.live && (
                <span className="badge badge-online" style={{ padding: '0.15rem 0.4rem', fontSize: '0.65rem' }}>
                  <span className="pulse-dot pulse-dot-green" style={{ width: '6px', height: '6px' }}></span> LIVE
                </span>
              )}

              {item.alert && (
                <span className="badge badge-offline" style={{ padding: '0.15rem 0.4rem', fontSize: '0.65rem' }}>
                  2
                </span>
              )}

              {item.badge && !item.live && !item.alert && (
                <span style={{
                  fontSize: '0.7rem',
                  fontWeight: 600,
                  backgroundColor: 'rgba(255, 255, 255, 0.08)',
                  color: '#9ca3af',
                  padding: '0.15rem 0.5rem',
                  borderRadius: '9999px'
                }}>
                  {item.badge}
                </span>
              )}
            </button>
          );
        })}
      </nav>

      {/* Cluster Health Summary Footer */}
      <div style={{
        padding: '1rem',
        margin: '0.75rem',
        borderRadius: '10px',
        backgroundColor: 'rgba(22, 25, 38, 0.8)',
        border: '1px solid rgba(255, 255, 255, 0.06)'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
          <span style={{ fontSize: '0.75rem', fontWeight: 600, color: '#d1d5db' }}>Cluster Health</span>
          <span className="badge badge-online" style={{ padding: '0.1rem 0.4rem', fontSize: '0.65rem' }}>HEALTHY</span>
        </div>
        <div style={{ width: '100%', height: '4px', backgroundColor: 'rgba(255, 255, 255, 0.1)', borderRadius: '2px', overflow: 'hidden' }}>
          <div style={{ width: '92%', height: '100%', backgroundColor: '#10b981', borderRadius: '2px' }}></div>
        </div>
        <div style={{ fontSize: '0.7rem', color: '#6b7280', marginTop: '0.4rem', fontFamily: 'monospace' }}>
          12/12 Devices Operational
        </div>
      </div>

      {/* User Session Logout */}
      <div style={{
        padding: '1rem',
        borderTop: '1px solid rgba(255, 255, 255, 0.06)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.625rem' }}>
          <div style={{
            width: '32px',
            height: '32px',
            borderRadius: '50%',
            backgroundColor: '#1f2937',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#1db954',
            fontWeight: 700,
            fontSize: '0.85rem'
          }}>
            A
          </div>
          <div>
            <div style={{ fontSize: '0.8rem', fontWeight: 600, color: '#f3f4f6' }}>admin</div>
            <div style={{ fontSize: '0.68rem', color: '#6b7280' }}>Master Control</div>
          </div>
        </div>
        <button
          onClick={onLogout}
          title="Sign Out Session"
          style={{
            background: 'none',
            border: 'none',
            color: '#9ca3af',
            cursor: 'pointer',
            padding: '0.375rem',
            borderRadius: '6px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            transition: 'color 0.15s'
          }}
          onMouseEnter={(e) => e.currentTarget.style.color = '#ef4444'}
          onMouseLeave={(e) => e.currentTarget.style.color = '#9ca3af'}
        >
          <LogOut size={18} />
        </button>
      </div>
    </aside>
  );
}
