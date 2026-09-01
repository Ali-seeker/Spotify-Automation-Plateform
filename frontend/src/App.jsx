import { useState, useEffect } from 'react';
import Sidebar from './components/Sidebar';
import Header from './components/Header';
import LoginPage from './pages/LoginPage';
import DashboardOverview from './pages/DashboardOverview';
import DevicesPage from './pages/DevicesPage';
import TasksPage from './pages/TasksPage';
import TaskBuilderPage from './pages/TaskBuilderPage';
import LiveTelemetryPage from './pages/LiveTelemetryPage';
import DiagnosticsPage from './pages/DiagnosticsPage';
import AutomationHistoryPage from './pages/AutomationHistoryPage';
import SessionsControlPage from './pages/SessionsControlPage';
import { getCurrentUserApi } from './services/apiService';
import './index.css';

export default function App() {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [authChecking, setAuthChecking] = useState(true);
  const [currentPage, setCurrentPage] = useState('overview');
  const [notification, setNotification] = useState(null);

  useEffect(() => {
    checkInitialAuth();

    // Listen for global 401 Unauthorized events from api.js interceptor
    const handleUnauthorized = () => {
      setIsAuthenticated(false);
      localStorage.removeItem('access_token');
      showNotification('Session expired or unauthorized. Please sign in again.');
    };

    window.addEventListener('unauthorized_access', handleUnauthorized);
    return () => {
      window.removeEventListener('unauthorized_access', handleUnauthorized);
    };
  }, []);

  const checkInitialAuth = async () => {
    const token = localStorage.getItem('access_token');
    if (!token) {
      setIsAuthenticated(false);
      setAuthChecking(false);
      return;
    }

    try {
      await getCurrentUserApi();
      setIsAuthenticated(true);
    } catch (err) {
      console.warn('Initial token validation failed:', err);
      localStorage.removeItem('access_token');
      setIsAuthenticated(false);
    } finally {
      setAuthChecking(false);
    }
  };

  const showNotification = (msg) => {
    setNotification(msg);
    setTimeout(() => setNotification(null), 4000);
  };

  const handleLaunchTask = (task) => {
    setCurrentPage('telemetry');
    showNotification(`Pipeline Launched: ${task.task_name || task.name}`);
  };

  const handleRetryPipeline = (diagItem) => {
    setCurrentPage('telemetry');
    showNotification(`Pipeline Retried: ${diagItem.task_name}`);
  };

  const handleLogout = () => {
    localStorage.removeItem('access_token');
    setIsAuthenticated(false);
    showNotification('Logged out successfully.');
  };

  if (authChecking) {
    return (
      <div style={{
        minHeight: '100vh',
        backgroundColor: '#0b0e17',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        color: '#1db954',
        fontFamily: 'monospace',
        fontSize: '1rem'
      }}>
        Initializing Session & Authenticating Node...
      </div>
    );
  }

  if (!isAuthenticated) {
    return <LoginPage onLoginSuccess={() => setIsAuthenticated(true)} />;
  }

  const pageTitles = {
    overview: 'Cluster Master Overview',
    devices: 'Android Device Cluster Registry',
    tasks: 'Automation Task Library',
    builder: 'Task & Action Configurator',
    telemetry: 'Real-Time Session Telemetry',
    diagnostics: 'Post-Execution Diagnostics',
    history: 'Automation Run Execution Logs',
    sessions: 'Cluster Sessions & Cron Control'
  };

  return (
    <div style={{ display: 'flex', minHeight: '100vh', backgroundColor: '#0b0e17' }}>
      {/* Sidebar Navigation */}
      <Sidebar
        currentPage={currentPage}
        setCurrentPage={setCurrentPage}
        onLogout={handleLogout}
      />

      {/* Main Content Area */}
      <div style={{ flex: 1, marginLeft: '260px', display: 'flex', flexDirection: 'column' }}>
        {/* Top Header */}
        <Header
          pageTitle={pageTitles[currentPage] || 'Dashboard'}
          onRefresh={() => showNotification('Node cluster synced successfully!')}
          onQuickLaunch={() => setCurrentPage('builder')}
        />

        {/* Floating Notification Banner */}
        {notification && (
          <div style={{
            position: 'fixed',
            top: '80px',
            right: '2rem',
            backgroundColor: '#10b981',
            color: '#000',
            fontWeight: 700,
            fontSize: '0.85rem',
            padding: '0.75rem 1.25rem',
            borderRadius: '8px',
            boxShadow: '0 0 20px rgba(16, 185, 129, 0.4)',
            zIndex: 100,
            display: 'flex',
            alignItems: 'center',
            gap: '0.5rem'
          }}>
            <span>⚡ {notification}</span>
          </div>
        )}

        {/* Active Page View */}
        <main style={{ marginTop: '70px', padding: '2rem', flex: 1 }}>
          {currentPage === 'overview' && (
            <DashboardOverview onNavigate={setCurrentPage} />
          )}

          {currentPage === 'devices' && (
            <DevicesPage />
          )}

          {currentPage === 'tasks' && (
            <TasksPage
              onNavigateToBuilder={() => setCurrentPage('builder')}
              onLaunchTask={handleLaunchTask}
            />
          )}

          {currentPage === 'builder' && (
            <TaskBuilderPage
              onSaveTask={() => {
                showNotification('Task definition saved successfully!');
                setCurrentPage('tasks');
              }}
              onLaunchTask={handleLaunchTask}
            />
          )}

          {currentPage === 'telemetry' && (
            <LiveTelemetryPage />
          )}

          {currentPage === 'diagnostics' && (
            <DiagnosticsPage onRetryPipeline={handleRetryPipeline} />
          )}

          {currentPage === 'history' && (
            <AutomationHistoryPage />
          )}

          {currentPage === 'sessions' && (
            <SessionsControlPage />
          )}
        </main>
      </div>
    </div>
  );
}
