import { useState, useEffect, useRef } from 'react';

/**
 * Custom React Hook to connect to WS /ws/frontend for dynamic device status and task progress updates.
 */
export function useFrontendWebSocket() {
  const [isConnected, setIsConnected] = useState(false);
  const [deviceUpdates, setDeviceUpdates] = useState({});
  const [recentEvents, setRecentEvents] = useState([]);
  const socketRef = useRef(null);

  useEffect(() => {
    const apiBase = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8000';
    const wsUrl = apiBase.replace(/^http/, 'ws') + '/ws/frontend';

    let ws = null;
    try {
      ws = new WebSocket(wsUrl);
      socketRef.current = ws;

      ws.onopen = () => {
        setIsConnected(true);
      };

      ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);

          if (data.type === 'DEVICE_STATUS') {
            setDeviceUpdates((prev) => ({
              ...prev,
              [data.device_id]: {
                status: data.status,
                last_seen: data.last_seen || new Date().toISOString()
              }
            }));
          } else if (data.type === 'DEVICE_EVENT') {
            setRecentEvents((prev) => [data, ...prev.slice(0, 49)]);
          }
        } catch (e) {
          console.error('Error parsing WebSocket event:', e);
        }
      };

      ws.onerror = (err) => {
        setIsConnected(false);
      };

      ws.onclose = () => {
        setIsConnected(false);
      };
    } catch (err) {
      console.error('Failed to establish WebSocket connection:', err);
    }

    return () => {
      if (ws) {
        ws.close();
      }
    };
  }, []);

  return { isConnected, deviceUpdates, recentEvents };
}
