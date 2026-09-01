import React, { useState } from 'react';
import { 
  CheckCircle2, 
  Plus, 
  RefreshCw, 
  Smartphone, 
  Wifi, 
  WifiOff, 
  X 
} from 'lucide-react';

export default function DevicesPage() {
  const [filter, setFilter] = useState('ALL');
  const [showPairModal, setShowPairModal] = useState(false);
  const [newDeviceId, setNewDeviceId] = useState('');

  const [devices, setDevices] = useState([
    { id: 1, device_id: 'dev_infinix_x6817', name: 'Infinix HOT 12 Pro', status: 'ONLINE', os: 'Android 12', app: 'Spotify v8.8.74', ip: '192.168.1.104', last_seen: '2 mins ago', tags: ['Spotify v8.8', 'Proxy Enabled', 'Rooted'] },
    { id: 2, device_id: 'dev_samsung_s21', name: 'Samsung Galaxy S21', status: 'BUSY', os: 'Android 13', app: 'Spotify v8.8.80', ip: '192.168.1.108', last_seen: 'Just now', tags: ['Spotify v8.8', 'Proxy Enabled'] },
    { id: 3, device_id: 'dev_pixel_6', name: 'Google Pixel 6 Pro', status: 'ONLINE', os: 'Android 14', app: 'Spotify v8.8.74', ip: '192.168.1.112', last_seen: '5 mins ago', tags: ['Spotify v8.8', 'Clean IP'] },
    { id: 4, device_id: 'dev_redmi_note10', name: 'Xiaomi Redmi Note 10', status: 'OFFLINE', os: 'Android 11', app: 'Spotify v8.7.90', ip: '192.168.1.115', last_seen: '2 hours ago', tags: ['Spotify v8.7', 'Disconnected'] },
    { id: 5, device_id: 'dev_oneplus_9', name: 'OnePlus 9 Pro', status: 'ONLINE', os: 'Android 13', app: 'Spotify v8.8.80', ip: '192.168.1.120', last_seen: '1 min ago', tags: ['Spotify v8.8', 'Proxy Enabled', 'Accessibility Active'] },
  ]);

  const filteredDevices = devices.filter(d => filter === 'ALL' || d.status === filter);

  const handlePairDevice = (e) => {
    e.preventDefault();
    if (!newDeviceId.trim()) return;
    const newDev = {
      id: devices.length + 1,
      device_id: newDeviceId.toLowerCase().replace(/\s+/g, '_'),
      name: newDeviceId,
      status: 'ONLINE',
      os: 'Android 12',
      app: 'Spotify v8.8.80',
      ip: '192.168.1.140',
      last_seen: 'Just now',
      tags: ['Spotify v8.8', 'Paired via ADB']
    };
    setDevices([newDev, ...devices]);
    setNewDeviceId('');
    setShowPairModal(false);
  };

  return (
    <div>
      {/* Action Header Banner */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.5rem' }}>
        <div>
          <h3 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#fff' }}>ADB Device Cluster</h3>
          <p style={{ fontSize: '0.85rem', color: '#9ca3af', marginTop: '0.2rem' }}>
            Manage physical Android devices connected via Accessibility Service & WebSocket
          </p>
        </div>

        <div style={{ display: 'flex', gap: '0.75rem' }}>
          <button className="btn-secondary">
            <RefreshCw size={16} />
            <span>RELOAD ADB SERVER</span>
          </button>
          <button onClick={() => setShowPairModal(true)} className="btn-spotify">
            <Plus size={18} fill="#000" />
            <span>PAIR NEW DEVICE</span>
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
            {tab} ({tab === 'ALL' ? devices.length : devices.filter(d => d.status === tab).length})
          </button>
        ))}
      </div>

      {/* Devices Table */}
      <div className="glass-panel" style={{ padding: '0', overflow: 'hidden' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.85rem' }}>
          <thead>
            <tr style={{ backgroundColor: 'rgba(0, 0, 0, 0.4)', borderBottom: '1px solid rgba(255, 255, 255, 0.08)', color: '#6b7280', fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              <th style={{ padding: '1rem 1.25rem' }}>Device Hardware</th>
              <th style={{ padding: '1rem 1.25rem' }}>Device Identifier</th>
              <th style={{ padding: '1rem 1.25rem' }}>Status</th>
              <th style={{ padding: '1rem 1.25rem' }}>Capabilities & Tags</th>
              <th style={{ padding: '1rem 1.25rem' }}>Last Heartbeat</th>
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
                      <div style={{ fontWeight: 600, color: '#fff' }}>{dev.name}</div>
                      <div style={{ fontSize: '0.75rem', color: '#6b7280', fontFamily: 'monospace' }}>IP: {dev.ip}</div>
                    </div>
                  </div>
                </td>

                <td style={{ padding: '1rem 1.25rem' }} className="font-mono">
                  <span style={{ color: '#3b82f6', backgroundColor: 'rgba(59, 130, 246, 0.1)', padding: '0.2rem 0.5rem', borderRadius: '4px' }}>
                    {dev.device_id}
                  </span>
                </td>

                <td style={{ padding: '1rem 1.25rem' }}>
                  <span className={`badge ${dev.status === 'ONLINE' ? 'badge-online' : dev.status === 'BUSY' ? 'badge-running' : 'badge-offline'}`}>
                    {dev.status === 'ONLINE' ? <Wifi size={12} /> : dev.status === 'BUSY' ? <RefreshCw size={12} /> : <WifiOff size={12} />}
                    {dev.status}
                  </span>
                </td>

                <td style={{ padding: '1rem 1.25rem' }}>
                  <div style={{ display: 'flex', gap: '0.4rem', flexWrap: 'wrap' }}>
                    {dev.tags.map((tag, i) => (
                      <span key={i} style={{
                        fontSize: '0.7rem',
                        fontWeight: 500,
                        backgroundColor: 'rgba(255, 255, 255, 0.06)',
                        color: '#d1d5db',
                        padding: '0.15rem 0.4rem',
                        borderRadius: '4px',
                        border: '1px solid rgba(255, 255, 255, 0.08)'
                      }}>
                        {tag}
                      </span>
                    ))}
                  </div>
                </td>

                <td style={{ padding: '1rem 1.25rem', color: '#9ca3af', fontSize: '0.8rem' }} className="font-mono">
                  {dev.last_seen}
                </td>

                <td style={{ padding: '1rem 1.25rem', textAlign: 'right' }}>
                  <button className="btn-secondary" style={{ padding: '0.35rem 0.75rem', fontSize: '0.75rem' }}>
                    Inspect Node
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Pair Device Modal */}
      {showPairModal && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundColor: 'rgba(0, 0, 0, 0.75)',
          backdropFilter: 'blur(8px)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 100
        }}>
          <div className="glass-panel glass-panel-glow" style={{ width: '420px', padding: '2rem', border: '1px solid rgba(29, 185, 84, 0.3)' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.5rem' }}>
              <h3 style={{ fontSize: '1.1rem', fontWeight: 700, color: '#fff' }}>Pair New Android Device</h3>
              <X size={20} color="#9ca3af" style={{ cursor: 'pointer' }} onClick={() => setShowPairModal(false)} />
            </div>

            <form onSubmit={handlePairDevice}>
              <div style={{ marginBottom: '1.25rem' }}>
                <label style={{ display: 'block', fontSize: '0.78rem', fontWeight: 600, color: '#d1d5db', marginBottom: '0.5rem' }}>
                  Device Name or ADB Serial
                </label>
                <input
                  type="text"
                  required
                  value={newDeviceId}
                  onChange={(e) => setNewDeviceId(e.target.value)}
                  placeholder="e.g. Samsung S22 Ultra (dev_samsung_05)"
                  style={{
                    width: '100%',
                    backgroundColor: 'rgba(0, 0, 0, 0.4)',
                    border: '1px solid rgba(255, 255, 255, 0.12)',
                    borderRadius: '8px',
                    padding: '0.75rem',
                    color: '#fff',
                    outline: 'none'
                  }}
                />
              </div>

              <div style={{ display: 'flex', gap: '0.75rem', justifyContent: 'flex-end', marginTop: '1.5rem' }}>
                <button type="button" onClick={() => setShowPairModal(false)} className="btn-secondary">
                  Cancel
                </button>
                <button type="submit" className="btn-spotify">
                  Pair Device
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
