import React, { useState, useEffect } from 'react';
import { 
  CheckCircle2, 
  Plus, 
  RefreshCw, 
  Smartphone, 
  Wifi, 
  WifiOff, 
  X,
  AlertCircle
} from 'lucide-react';
import { getDevicesApi } from '../services/apiService';
import { useFrontendWebSocket } from '../hooks/useFrontendWebSocket';

export default function DevicesPage() {
  const [filter, setFilter] = useState('ALL');
  const [devices, setDevices] = useState([]);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState('');
  const [showPairModal, setShowPairModal] = useState(false);

  const { deviceUpdates } = useFrontendWebSocket();

  useEffect(() => {
    fetchDevices();
  }, []);

  const fetchDevices = async () => {
    setLoading(true);
    setErrorMsg('');
    try {
      const data = await getDevicesApi();
      setDevices(data);
    } catch (err) {
      setErrorMsg('Failed to load device list from backend API.');
    } finally {
      setLoading(false);
    }
  };

  // Merge dynamic WebSocket status updates into backend devices list
  const mergedDevices = devices.map((d) => {
    const update = deviceUpdates[d.device_id];
    return update ? { ...d, status: update.status, last_seen: update.last_seen } : d;
  });

  const filteredDevices = mergedDevices.filter(d => filter === 'ALL' || d.status === filter);

  return (
    <div>
      {/* Action Header Banner */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.5rem' }}>
        <div>
          <h3 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#fff' }}>ADB Device Cluster Registry</h3>
          <p style={{ fontSize: '0.85rem', color: '#9ca3af', marginTop: '0.2rem' }}>
            Registered physical Android devices connected via Accessibility Service & WebSocket
          </p>
        </div>

        <div style={{ display: 'flex', gap: '0.75rem' }}>
          <button onClick={fetchDevices} className="btn-secondary">
            <RefreshCw size={16} />
            <span>RELOAD DEVICES</span>
          </button>
        </div>
      </div>

      {/* Filter Tabs */}
      <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1.25rem' }}>
        {['ALL', 'ONLINE', 'BUSY', 'OFFLINE'].map((tab) => (
          <button
            key={tab}
            onClick={() => setFilter(tab)}
            style={{
              padding: '0.5rem 1rem',
              borderRadius: '8px',
              border: '1px solid',
              borderColor: filter === tab ? '#1db954' : 'rgba(255, 255, 255, 0.08)',
              backgroundColor: filter === tab ? 'rgba(29, 185, 84, 0.12)' : 'rgba(255, 255, 255, 0.02)',
              color: filter === tab ? '#1db954' : '#9ca3af',
              fontWeight: 600,
              fontSize: '0.8rem',
              cursor: 'pointer',
              transition: 'all 0.2s'
            }}
          >
            {tab} ({tab === 'ALL' ? mergedDevices.length : mergedDevices.filter(d => d.status === tab).length})
          </button>
        ))}
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
          <div>Loading registered devices from backend database...</div>
        </div>
      ) : mergedDevices.length === 0 ? (
        /* Empty State */
        <div className="glass-panel" style={{ padding: '4rem 2rem', textAlign: 'center' }}>
          <Smartphone size={48} color="#6b7280" style={{ marginBottom: '1rem' }} />
          <h4 style={{ color: '#fff', fontSize: '1.1rem', fontWeight: 700 }}>No Registered Devices Found</h4>
          <p style={{ color: '#9ca3af', fontSize: '0.85rem', marginTop: '0.3rem', maxWidth: '480px', margin: '0.3rem auto 1.5rem auto' }}>
            There are no Android devices currently registered in the database. When an Android phone opens the WebSocket app or sends a request, it will appear here automatically.
          </p>
        </div>
      ) : (
        /* Devices Table */
        <div className="glass-panel" style={{ padding: '0', overflow: 'hidden' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.85rem' }}>
            <thead>
              <tr style={{ backgroundColor: 'rgba(0, 0, 0, 0.4)', borderBottom: '1px solid rgba(255, 255, 255, 0.08)', color: '#6b7280', fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                <th style={{ padding: '1rem 1.25rem' }}>Device Identifier</th>
                <th style={{ padding: '1rem 1.25rem' }}>Database ID</th>
                <th style={{ padding: '1rem 1.25rem' }}>Status</th>
                <th style={{ padding: '1rem 1.25rem' }}>Last Seen</th>
                <th style={{ padding: '1rem 1.25rem', textAlign: 'right' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filteredDevices.map((dev) => (
                <tr key={dev.id} style={{ borderBottom: '1px solid rgba(255, 255, 255, 0.04)', transition: 'background-color 0.15s' }}>
                  <td style={{ padding: '1rem 1.25rem' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                      <div style={{
                        width: '36px',
                        height: '36px',
                        borderRadius: '8px',
                        backgroundColor: 'rgba(255, 255, 255, 0.04)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        color: dev.status === 'OFFLINE' ? '#6b7280' : '#1db954'
                      }}>
                        <Smartphone size={20} />
                      </div>
                      <div>
                        <div style={{ fontWeight: 600, color: '#fff' }} className="font-mono">{dev.device_id}</div>
                      </div>
                    </div>
                  </td>

                  <td style={{ padding: '1rem 1.25rem' }} className="font-mono">
                    <span style={{ color: '#3b82f6', backgroundColor: 'rgba(59, 130, 246, 0.1)', padding: '0.2rem 0.5rem', borderRadius: '4px' }}>
                      #{dev.id}
                    </span>
                  </td>

                  <td style={{ padding: '1rem 1.25rem' }}>
                    <span className={`badge ${dev.status === 'ONLINE' || dev.status === 'IDLE' ? 'badge-online' : dev.status === 'BUSY' ? 'badge-running' : 'badge-offline'}`}>
                      {dev.status === 'ONLINE' || dev.status === 'IDLE' ? <Wifi size={12} /> : dev.status === 'BUSY' ? <RefreshCw size={12} /> : <WifiOff size={12} />}
                      {dev.status}
                    </span>
                  </td>

                  <td style={{ padding: '1rem 1.25rem', color: '#9ca3af', fontSize: '0.8rem' }} className="font-mono">
                    {dev.last_seen || 'N/A'}
                  </td>

                  <td style={{ padding: '1rem 1.25rem', textAlign: 'right' }}>
                    <button className="btn-secondary" style={{ padding: '0.35rem 0.75rem', fontSize: '0.75rem' }}>
                      Inspect Device
                    </button>
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
