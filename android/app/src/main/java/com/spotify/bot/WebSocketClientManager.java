package com.spotify.bot;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.TimeUnit;

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
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final long INITIAL_RECONNECT_DELAY_MS = 2000;

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

    private void updateState(ConnectionState state, String info) {
        this.currentState = state;
        Log.i(TAG, String.format("State transition -> [%s]: %s", state.name(), info != null ? info : ""));

        if (listener != null) {
            mainHandler.post(() -> listener.onStateChanged(state, info));
        }
    }

    public synchronized void connect() {
        if (currentState == ConnectionState.CONNECTING || currentState == ConnectionState.READY) {
            Log.d(TAG, "Connect ignored: connection already active or connecting.");
            return;
        }

        isUserDisconnect = false;
        allowReconnect = true;

        String serverUrl = DeviceConfig.getServerUrl(context);
        String deviceId = DeviceConfig.getDeviceId(context);

        Log.i(TAG, String.format("WEBSOCKET_CONNECTING to '%s' for device '%s'", serverUrl, deviceId));
        updateState(ConnectionState.CONNECTING, "Connecting to " + serverUrl);

        Request request = new Request.Builder()
                .url(serverUrl)
                .build();

        webSocket = httpClient.newWebSocket(request, new CustomWebSocketListener());
    }

    public synchronized void disconnect() {
        isUserDisconnect = true;
        allowReconnect = false;
        reconnectAttempts = 0;

        if (webSocket != null) {
            Log.i(TAG, "WEBSOCKET_DISCONNECTED by user request.");
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

            Log.i(TAG, String.format("DEVICE_HELLO_SENT for device_id='%s', version='%s'", deviceId, appVersion));
            updateState(ConnectionState.AUTHENTICATING, "Authenticating device identity...");
            webSocket.send(helloJson.toString());

        } catch (JSONException e) {
            Log.e(TAG, "Failed to build DEVICE_HELLO JSON payload: " + e.getMessage());
        }
    }

    public void sendEventPayload(JSONObject eventPayload) {
        if (webSocket == null || currentState != ConnectionState.READY) {
            Log.w(TAG, "Cannot send event: WebSocket is not in READY state.");
            return;
        }
        webSocket.send(eventPayload.toString());
    }

    private void scheduleReconnect() {
        if (isUserDisconnect || !allowReconnect) {
            Log.d(TAG, "Reconnect skipped: user requested disconnect or authentication rejected.");
            return;
        }

        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.w(TAG, String.format("Max reconnect attempts (%d) reached. Pausing auto-reconnect.", MAX_RECONNECT_ATTEMPTS));
            updateState(ConnectionState.DISCONNECTED, "Max reconnect attempts reached. Tap Connect to retry.");
            return;
        }

        reconnectAttempts++;
        long delayMs = (long) (INITIAL_RECONNECT_DELAY_MS * Math.pow(2, reconnectAttempts - 1));
        delayMs = Math.min(delayMs, 30000); // Cap at 30 seconds

        Log.i(TAG, String.format("Scheduling reconnect attempt %d/%d in %d ms", reconnectAttempts, MAX_RECONNECT_ATTEMPTS, delayMs));
        mainHandler.postDelayed(this::connect, delayMs);
    }

    private class CustomWebSocketListener extends WebSocketListener {

        @Override
        public void onOpen(WebSocket ws, Response response) {
            Log.i(TAG, "WEBSOCKET_CONNECTED successfully.");
            mainHandler.post(() -> {
                reconnectAttempts = 0; // Reset reconnect counter on successful TCP open
                sendDeviceHello();
            });
        }

        @Override
        public void onMessage(WebSocket ws, String text) {
            Log.d(TAG, "Incoming WebSocket message: " + text);

            try {
                JSONObject json = new JSONObject(text);
                String msgType = json.optString("type", "");

                if ("HELLO_ACK".equals(msgType)) {
                    Log.i(TAG, "HELLO_ACK_RECEIVED: Device authenticated and ready for commands.");
                    updateState(ConnectionState.READY, "Authenticated & Ready");

                } else if ("HELLO_REJECT".equals(msgType)) {
                    String reason = json.optString("reason", "UNAUTHORIZED");
                    Log.e(TAG, String.format("HELLO_REJECT_RECEIVED: Authentication rejected. Reason: %s", reason));
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
            Log.i(TAG, String.format("WEBSOCKET_DISCONNECTED onClosing code=%d, reason=%s", code, reason));
        }

        @Override
        public void onClosed(WebSocket ws, int code, String reason) {
            Log.i(TAG, String.format("WEBSOCKET_DISCONNECTED onClosed code=%d, reason=%s", code, reason));
            mainHandler.post(() -> {
                if (currentState != ConnectionState.REJECTED) {
                    updateState(ConnectionState.DISCONNECTED, "Connection closed");
                    scheduleReconnect();
                }
            });
        }

        @Override
        public void onFailure(WebSocket ws, Throwable t, Response response) {
            Log.e(TAG, "WEBSOCKET_ERROR Failure: " + t.getMessage(), t);
            mainHandler.post(() -> {
                if (currentState != ConnectionState.REJECTED) {
                    updateState(ConnectionState.DISCONNECTED, "Connection error: " + t.getMessage());
                    scheduleReconnect();
                }
            });
        }
    }
}
