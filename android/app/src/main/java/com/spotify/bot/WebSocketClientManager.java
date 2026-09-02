package com.spotify.bot;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class WebSocketClientManager {

    private static final String TAG = "SpotifyBotWS";

    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        AUTHENTICATING,
        READY,
        REJECTED
    }

    public interface StateListener {
        void onStateChanged(ConnectionState state, String infoMessage);
        void onCommandReceived(JSONObject commandPayload);
    }

    private static volatile WebSocketClientManager instance;

    private final Context context;
    private final Handler mainHandler;
    private OkHttpClient httpClient;
    private WebSocket webSocket;

    private ConnectionState currentState = ConnectionState.DISCONNECTED;
    private StateListener listener;

    private boolean isUserDisconnect = false;
    private boolean allowReconnect = true;
    private int reconnectAttempts = 0;
    private final AtomicBoolean isReconnectScheduled = new AtomicBoolean(false);
    private final ConcurrentLinkedQueue<JSONObject> pendingEventsQueue = new ConcurrentLinkedQueue<>();

    private WebSocketClientManager(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        initOkHttpClient();
    }

    public static synchronized WebSocketClientManager getInstance(Context context) {
        if (instance == null) {
            instance = new WebSocketClientManager(context);
        }
        return instance;
    }

    private void initOkHttpClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS) // Native 30-second ping keep-alive
                .build();
    }

    public void setStateListener(StateListener listener) {
        this.listener = listener;
    }

    public ConnectionState getCurrentState() {
        return currentState;
    }

    public boolean isReady() {
        return currentState == ConnectionState.READY;
    }

    private String getIsoUtcTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

    private void updateState(ConnectionState state, String info) {
        this.currentState = state;
        Log.i(TAG, String.format("[%s] State transition -> [%s]: %s",
                getIsoUtcTimestamp(), state.name(), info != null ? info : ""));

        if (listener != null) {
            mainHandler.post(() -> listener.onStateChanged(state, info));
        }
    }

    public synchronized void connect() {
        isReconnectScheduled.set(false); // Reset schedule lock when connect starts

        if (currentState == ConnectionState.CONNECTING || currentState == ConnectionState.READY) {
            Log.d(TAG, "Connect ignored: connection already active or connecting.");
            return;
        }

        isUserDisconnect = false;
        allowReconnect = true;

        String serverUrl = DeviceConfig.getServerUrl(context);
        String deviceId = DeviceConfig.getDeviceId(context);

        Log.i(TAG, String.format("WEBSOCKET_RECONNECT_ATTEMPT device_id=%s attempt=%d timestamp=%s url=%s",
                deviceId, reconnectAttempts + 1, getIsoUtcTimestamp(), serverUrl));
        updateState(ConnectionState.CONNECTING, "Connecting to " + serverUrl);

        Request request = new Request.Builder()
                .url(serverUrl)
                .build();

        webSocket = httpClient.newWebSocket(request, new CustomWebSocketListener());
    }

    public synchronized void disconnect() {
        isUserDisconnect = true;
        allowReconnect = false;
        isReconnectScheduled.set(false);
        reconnectAttempts = 0;
        pendingEventsQueue.clear();

        if (webSocket != null) {
            String deviceId = DeviceConfig.getDeviceId(context);
            Log.i(TAG, String.format("WEBSOCKET_DISCONNECTED device_id=%s reason=user_requested timestamp=%s",
                    deviceId, getIsoUtcTimestamp()));
            webSocket.close(1000, "User disconnected");
            webSocket = null;
        }
        updateState(ConnectionState.DISCONNECTED, "Disconnected");
    }

    private void sendDeviceHello() {
        if (webSocket == null) return;

        String deviceId = DeviceConfig.getDeviceId(context);
        String authToken = DeviceConfig.getAuthToken(context);
        String appVersion = DeviceConfig.getAppVersion(context);
        List<String> capabilities = DeviceConfig.getCapabilities();

        try {
            JSONObject helloJson = new JSONObject();
            helloJson.put("type", "DEVICE_HELLO");
            helloJson.put("device_id", deviceId);
            helloJson.put("device_auth_token", authToken);
            helloJson.put("app_version", appVersion);

            JSONArray capsArray = new JSONArray();
            for (String cap : capabilities) {
                capsArray.put(cap);
            }
            helloJson.put("capabilities", capsArray);

            Log.i(TAG, String.format("DEVICE_HELLO_SENT device_id=%s version=%s timestamp=%s",
                    deviceId, appVersion, getIsoUtcTimestamp()));
            updateState(ConnectionState.AUTHENTICATING, "Authenticating device identity...");
            webSocket.send(helloJson.toString());

        } catch (JSONException e) {
            Log.e(TAG, "Failed to build DEVICE_HELLO JSON payload: " + e.getMessage());
        }
    }

    public void sendEventPayload(JSONObject eventPayload) {
        if (eventPayload == null) return;

        if (currentState == ConnectionState.READY && webSocket != null) {
            webSocket.send(eventPayload.toString());
        } else {
            Log.i(TAG, "Buffering event while socket unauthenticated: " + eventPayload.optString("type"));
            pendingEventsQueue.offer(eventPayload);
        }
    }

    private void flushPendingEvents() {
        if (webSocket == null || currentState != ConnectionState.READY) return;

        int flushedCount = 0;
        while (!pendingEventsQueue.isEmpty()) {
            JSONObject event = pendingEventsQueue.poll();
            if (event != null) {
                webSocket.send(event.toString());
                flushedCount++;
            }
        }
        if (flushedCount > 0) {
            Log.i(TAG, String.format("Flushed %d pending buffered events after reconnection timestamp=%s",
                    flushedCount, getIsoUtcTimestamp()));
        }
    }

    private void scheduleReconnect() {
        if (isUserDisconnect || !allowReconnect) {
            Log.d(TAG, "Reconnect skipped: user requested disconnect or authentication rejected.");
            return;
        }

        // IDEMPOTENT SINGLE RECONNECT SCHEDULER LOCK
        if (!isReconnectScheduled.compareAndSet(false, true)) {
            Log.d(TAG, "Reconnect already scheduled. Skipping duplicate reconnect trigger.");
            return;
        }

        reconnectAttempts++;

        // EXACT EXPONENTIAL BACKOFF: 1s -> 2s -> 4s -> 8s -> 16s -> 30s max cap
        long delaySeconds;
        if (reconnectAttempts == 1) {
            delaySeconds = 1;
        } else if (reconnectAttempts == 2) {
            delaySeconds = 2;
        } else if (reconnectAttempts == 3) {
            delaySeconds = 4;
        } else if (reconnectAttempts == 4) {
            delaySeconds = 8;
        } else if (reconnectAttempts == 5) {
            delaySeconds = 16;
        } else {
            delaySeconds = 30; // Cap at 30 seconds max
        }
        long delayMs = delaySeconds * 1000L;

        String deviceId = DeviceConfig.getDeviceId(context);
        Log.i(TAG, String.format("WEBSOCKET_RECONNECT_SCHEDULED device_id=%s attempt=%d delay_ms=%d timestamp=%s",
                deviceId, reconnectAttempts, delayMs, getIsoUtcTimestamp()));

        mainHandler.postDelayed(this::connect, delayMs);
    }

    private class CustomWebSocketListener extends WebSocketListener {

        @Override
        public void onOpen(WebSocket ws, Response response) {
            String deviceId = DeviceConfig.getDeviceId(context);
            Log.i(TAG, String.format("WEBSOCKET_CONNECTED device_id=%s timestamp=%s", deviceId, getIsoUtcTimestamp()));
            mainHandler.post(WebSocketClientManager.this::sendDeviceHello);
        }

        @Override
        public void onMessage(WebSocket ws, String text) {
            Log.d(TAG, "Incoming WebSocket message: " + text);

            try {
                JSONObject json = new JSONObject(text);
                String msgType = json.optString("type", "");

                if ("HELLO_ACK".equals(msgType)) {
                    String deviceId = DeviceConfig.getDeviceId(context);
                    Log.i(TAG, String.format("HELLO_ACK_RECEIVED device_id=%s attempt_reset=%d timestamp=%s",
                            deviceId, reconnectAttempts, getIsoUtcTimestamp()));

                    reconnectAttempts = 0; // RESET ATTEMPT COUNTER ONLY UPON HELLO_ACK
                    updateState(ConnectionState.READY, "Authenticated & Ready");
                    flushPendingEvents();
                    CommandQueueManager.getInstance(context).notifySocketReconnected();

                } else if ("HELLO_REJECT".equals(msgType)) {
                    String reason = json.optString("reason", "UNAUTHORIZED");
                    String deviceId = DeviceConfig.getDeviceId(context);
                    Log.e(TAG, String.format("HELLO_REJECT_RECEIVED device_id=%s reason=%s timestamp=%s",
                            deviceId, reason, getIsoUtcTimestamp()));

                    allowReconnect = false; // STOP infinite retries on credential rejection
                    updateState(ConnectionState.REJECTED, "Rejected: " + reason);
                    ws.close(1008, "Authentication Rejected");

                } else {
                    // COMMAND ACCEPTANCE GATE CHECK
                    if (currentState != ConnectionState.READY) {
                        Log.w(TAG, "COMMAND_REJECTED_UNAUTHENTICATED: Message ignored before HELLO_ACK.");
                        return;
                    }

                    Log.i(TAG, "Valid command payload received: " + msgType);
                    if (listener != null) {
                        mainHandler.post(() -> listener.onCommandReceived(json));
                    }
                }

            } catch (JSONException e) {
                Log.e(TAG, "Failed to parse incoming WebSocket message as JSON: " + text, e);
            }
        }

        @Override
        public void onClosing(WebSocket ws, int code, String reason) {
            String deviceId = DeviceConfig.getDeviceId(context);
            Log.i(TAG, String.format("WEBSOCKET_DISCONNECTED onClosing device_id=%s code=%d reason=%s timestamp=%s",
                    deviceId, code, reason, getIsoUtcTimestamp()));
        }

        @Override
        public void onClosed(WebSocket ws, int code, String reason) {
            String deviceId = DeviceConfig.getDeviceId(context);
            Log.i(TAG, String.format("WEBSOCKET_DISCONNECTED onClosed device_id=%s code=%d reason=%s timestamp=%s",
                    deviceId, code, reason, getIsoUtcTimestamp()));

            mainHandler.post(() -> {
                if (currentState != ConnectionState.REJECTED && !isUserDisconnect) {
                    updateState(ConnectionState.DISCONNECTED, "Connection closed");
                    scheduleReconnect();
                }
            });
        }

        @Override
        public void onFailure(WebSocket ws, Throwable t, Response response) {
            String deviceId = DeviceConfig.getDeviceId(context);
            Log.e(TAG, String.format("WEBSOCKET_ERROR Failure device_id=%s error=%s timestamp=%s",
                    deviceId, t.getMessage(), getIsoUtcTimestamp()), t);

            mainHandler.post(() -> {
                if (currentState != ConnectionState.REJECTED && !isUserDisconnect) {
                    updateState(ConnectionState.DISCONNECTED, "Connection error: " + t.getMessage());
                    scheduleReconnect();
                }
            });
        }
    }
}
