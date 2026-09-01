import { useState, useEffect } from 'react'
import api from './api'
import './App.css'

function App() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [backendStatus, setBackendStatus] = useState('checking') // 'checking' | 'connected' | 'disconnected'

  // Run backend connectivity test on page load
  useEffect(() => {
    testBackendConnection()
  }, [])

  const testBackendConnection = async () => {
    setBackendStatus('checking')
    try {
      const response = await api.get('/health')
      if (response.data && response.data.status === 'healthy') {
        setBackendStatus('connected')
      } else {
        setBackendStatus('disconnected')
      }
    } catch (err) {
      console.error('Backend connection test failed:', err)
      setBackendStatus('disconnected')
    }
  }

  const handleLogin = async (e) => {
    e.preventDefault()
    setError('')
    setSuccess('')

    if (!email || !password) {
      setError('Please fill in all fields.')
      return
    }

    setLoading(true)
    try {
      // In M1-B.1 we only have /health. We do not have /login endpoint yet.
      // So we will perform a mock verification using /health for connectivity
      // and display the appropriate success message.
      const response = await api.get('/health')
      
      if (response.status === 200) {
        setSuccess('Successfully connected to backend! Login simulation successful.')
      } else {
        setError('Login failed: Invalid server response.')
      }
    } catch (err) {
      console.error('Login request failed:', err)
      setError(err.response?.data?.detail || 'Connection to authentication server failed.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login-container">
      {/* Animated Glowing Backgrounds */}
      <div className="glow glow-1"></div>
      <div className="glow glow-2"></div>

      <div className="login-card">
        <div className="logo-section">
          {/* Custom SVG Spotify-themed Logo */}
          <svg className="spotify-icon" viewBox="0 0 24 24" width="48" height="48">
            <path
              fill="#1DB954"
              d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm4.59 14.42c-.18.29-.56.38-.85.2-.29-.18-.38-.56-.2-.85 1.14-1.84 1.77-3.99 1.77-6.22 0-2.23-.63-4.38-1.77-6.22-.18-.29-.09-.67.2-.85.29-.18.67-.09.85.2C17.9 4.8 18.6 7.34 18.6 10c0 2.66-.7 5.2-2.01 7.42zm-2.73-1.61c-.15.25-.47.33-.72.18-.25-.15-.33-.47-.18-.72.93-1.5 1.44-3.26 1.44-5.09s-.51-3.59-1.44-5.09c-.15-.25-.07-.57.18-.72.25-.15.57-.07.72.18 1.05 1.71 1.63 3.73 1.63 5.63s-.58 3.92-1.63 5.63zm-2.52-1.65c-.12.21-.39.28-.6.16-.21-.12-.28-.39-.16-.6.67-1.12 1.03-2.43 1.03-3.79s-.36-2.67-1.03-3.79c-.12-.21-.05-.48.16-.6.21-.12.48-.05.6.16.79 1.33 1.22 2.87 1.22 4.23s-.43 2.9-1.22 4.23z"
            />
          </svg>
          <h2>Spotify Automation</h2>
          <p className="subtitle">Platform Control Center</p>
        </div>

        {/* Backend Status Indicator */}
        <div className="status-indicator-wrapper">
          <span className={`status-indicator ${backendStatus}`}>
            <span className="dot"></span>
            {backendStatus === 'checking' && 'Checking API connection...'}
            {backendStatus === 'connected' && 'API Connected'}
            {backendStatus === 'disconnected' && 'API Offline (Check Terminal)'}
          </span>
          {backendStatus === 'disconnected' && (
            <button className="retry-btn" onClick={testBackendConnection}>
              Retry
            </button>
          )}
        </div>

        <form onSubmit={handleLogin} className="login-form">
          <div className="input-group">
            <label htmlFor="email">Email Address</label>
            <input
              type="email"
              id="email"
              placeholder="name@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              disabled={loading}
              required
            />
          </div>

          <div className="input-group">
            <label htmlFor="password">Password</label>
            <input
              type="password"
              id="password"
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              disabled={loading}
              required
            />
          </div>

          {error && <div className="message-box error">{error}</div>}
          {success && <div className="message-box success">{success}</div>}

          <button type="submit" className="login-btn" disabled={loading}>
            {loading ? <span className="spinner"></span> : 'Sign In'}
          </button>
        </form>
      </div>
    </div>
  )
}

export default App
