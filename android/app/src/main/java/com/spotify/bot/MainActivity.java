package com.spotify.bot;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

public class MainActivity extends AppCompatActivity implements WebSocketClientManager.StateListener {

    private static final String TAG = "SpotifyBotMain";

    private Handler mainHandler;
    private WebSocketClientManager wsManager;

    private TextView statusTextView;
    private TextView deviceIdTextView;
    private EditText serverUrlInput;
    private EditText authTokenInput;
    private Button connectButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainHandler = new Handler(Looper.getMainLooper());
        wsManager = WebSocketClientManager.getInstance(getApplicationContext());
        wsManager.setStateListener(this);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#090B0E"));
        scrollView.setFillViewport(true);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setPadding(50, 50, 50, 50);

        // Header Title
        TextView titleView = new TextView(this);
        titleView.setText("Spotify Automation Node");
        titleView.setTextSize(22);
        titleView.setTextColor(Color.parseColor("#1DB954"));
        titleView.setGravity(Gravity.CENTER);
        titleView.setPadding(0, 0, 0, 10);

        // Device ID Display
        String deviceId = DeviceConfig.getDeviceId(this);
        deviceIdTextView = new TextView(this);
        deviceIdTextView.setText("Device ID: " + deviceId);
        deviceIdTextView.setTextSize(14);
        deviceIdTextView.setTextColor(Color.parseColor("#E5E7EB"));
        deviceIdTextView.setGravity(Gravity.CENTER);
        deviceIdTextView.setPadding(0, 0, 0, 20);

        // Connection Status Badge
        statusTextView = new TextView(this);
        updateStatusBadge(wsManager.getCurrentState(), "Initializing...");
        statusTextView.setTextSize(15);
        statusTextView.setGravity(Gravity.CENTER);
        statusTextView.setPadding(20, 15, 20, 15);

        // Server URL Label & Input
        TextView urlLabel = new TextView(this);
        urlLabel.setText("Backend WebSocket URL:");
        urlLabel.setTextColor(Color.parseColor("#9CA3AF"));
        urlLabel.setTextSize(13);
        urlLabel.setPadding(0, 20, 0, 5);

        serverUrlInput = new EditText(this);
        serverUrlInput.setText(DeviceConfig.getServerUrl(this));
        serverUrlInput.setTextColor(Color.WHITE);
        serverUrlInput.setBackgroundColor(Color.parseColor("#161926"));
        serverUrlInput.setPadding(30, 20, 30, 20);
        serverUrlInput.setTextSize(14);

        // Auth Token Label & Input
        TextView tokenLabel = new TextView(this);
        tokenLabel.setText("Device Auth Shared Secret:");
        tokenLabel.setTextColor(Color.parseColor("#9CA3AF"));
        tokenLabel.setTextSize(13);
        tokenLabel.setPadding(0, 15, 0, 5);

        authTokenInput = new EditText(this);
        authTokenInput.setText(DeviceConfig.getAuthToken(this));
        authTokenInput.setTextColor(Color.WHITE);
        authTokenInput.setBackgroundColor(Color.parseColor("#161926"));
        authTokenInput.setPadding(30, 20, 30, 20);
        authTokenInput.setTextSize(14);

        // Connect / Disconnect Toggle Button
        connectButton = new Button(this);
        updateConnectButtonText();
        connectButton.setBackgroundColor(Color.parseColor("#1DB954"));
        connectButton.setTextColor(Color.BLACK);
        connectButton.setPadding(40, 20, 40, 20);

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        btnParams.setMargins(0, 25, 0, 0);
        connectButton.setLayoutParams(btnParams);

        connectButton.setOnClickListener(v -> {
            if (wsManager.getCurrentState() == WebSocketClientManager.ConnectionState.READY ||
                wsManager.getCurrentState() == WebSocketClientManager.ConnectionState.CONNECTING ||
                wsManager.getCurrentState() == WebSocketClientManager.ConnectionState.AUTHENTICATING) {

                wsManager.disconnect();
            } else {
                // Save configurations and connect
                String url = serverUrlInput.getText().toString();
                String token = authTokenInput.getText().toString();
                DeviceConfig.setServerUrl(MainActivity.this, url);
                DeviceConfig.setAuthToken(MainActivity.this, token);

                // Start Foreground Service
                Intent serviceIntent = new Intent(MainActivity.this, WebSocketService.class);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }

                wsManager.connect();
            }
        });

        // Enable Accessibility Button
        Button settingsButton = new Button(this);
        settingsButton.setText("Enable Accessibility Service");
        settingsButton.setBackgroundColor(Color.parseColor("#212529"));
        settingsButton.setTextColor(Color.WHITE);
        settingsButton.setPadding(40, 20, 40, 20);
        settingsButton.setLayoutParams(btnParams);
        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });

        // Launch Spotify Manual Action Button
        Button launchButton = new Button(this);
        launchButton.setText("Launch Spotify App");
        launchButton.setBackgroundColor(Color.parseColor("#212529"));
        launchButton.setTextColor(Color.WHITE);
        launchButton.setPadding(40, 20, 40, 20);
        launchButton.setLayoutParams(btnParams);

        launchButton.setOnClickListener(v -> {
            Toast.makeText(MainActivity.this, "Initiating Spotify Launch...", Toast.LENGTH_SHORT).show();
            SpotifyLauncher.launchSpotify(MainActivity.this, (success, message) -> {
                mainHandler.post(() -> {
                    Toast.makeText(MainActivity.this, message, success ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
                });
            });
        });

        layout.addView(titleView);
        layout.addView(deviceIdTextView);
        layout.addView(statusTextView);
        layout.addView(urlLabel);
        layout.addView(serverUrlInput);
        layout.addView(tokenLabel);
        layout.addView(authTokenInput);
        layout.addView(connectButton);
        layout.addView(settingsButton);
        layout.addView(launchButton);

        scrollView.addView(layout);
        setContentView(scrollView);

        // Auto-connect if configured
        if (wsManager.getCurrentState() == WebSocketClientManager.ConnectionState.DISCONNECTED) {
            Intent serviceIntent = new Intent(MainActivity.this, WebSocketService.class);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            wsManager.connect();
        }
    }

    private void updateStatusBadge(WebSocketClientManager.ConnectionState state, String infoMessage) {
        String text;
        int color;

        switch (state) {
            case READY:
                text = "● READY / AUTHENTICATED";
                color = Color.parseColor("#10B981");
                break;
            case AUTHENTICATING:
                text = "● AUTHENTICATING (DEVICE_HELLO)...";
                color = Color.parseColor("#F59E0B");
                break;
            case CONNECTING:
                text = "● CONNECTING TO WEBSOCKET...";
                color = Color.parseColor("#3B82F6");
                break;
            case REJECTED:
                text = "✖ HELLO_REJECTED (" + (infoMessage != null ? infoMessage : "") + ")";
                color = Color.parseColor("#EF4444");
                break;
            case DISCONNECTED:
            default:
                text = "○ DISCONNECTED";
                color = Color.parseColor("#6B7280");
                break;
        }

        statusTextView.setText(text);
        statusTextView.setTextColor(color);
        updateConnectButtonText();
    }

    private void updateConnectButtonText() {
        if (connectButton == null) return;
        WebSocketClientManager.ConnectionState state = wsManager.getCurrentState();
        if (state == WebSocketClientManager.ConnectionState.READY ||
            state == WebSocketClientManager.ConnectionState.CONNECTING ||
            state == WebSocketClientManager.ConnectionState.AUTHENTICATING) {
            connectButton.setText("Disconnect WebSocket");
            connectButton.setBackgroundColor(Color.parseColor("#EF4444"));
            connectButton.setTextColor(Color.WHITE);
        } else {
            connectButton.setText("Connect WebSocket");
            connectButton.setBackgroundColor(Color.parseColor("#1DB954"));
            connectButton.setTextColor(Color.BLACK);
        }
    }

    @Override
    public void onStateChanged(WebSocketClientManager.ConnectionState state, String infoMessage) {
        mainHandler.post(() -> updateStatusBadge(state, infoMessage));
    }

    @Override
    public void onCommandReceived(JSONObject commandPayload) {
        Log.i(TAG, "Command payload received on device: " + commandPayload.toString());
        String actionType = commandPayload.optString("action_type", "Task");

        mainHandler.post(() ->
            Toast.makeText(this, "Enqueued Command: " + actionType, Toast.LENGTH_SHORT).show()
        );

        // Delegate command execution to FIFO Command Queue Manager
        CommandQueueManager.getInstance(this).enqueueCommand(commandPayload);
    }
}
