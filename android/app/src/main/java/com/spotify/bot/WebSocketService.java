package com.spotify.bot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;

public class WebSocketService extends Service {

    private static final String TAG = "SpotifyBotWSService";
    private static final String CHANNEL_ID = "spotify_bot_ws_channel";
    private static final int NOTIFICATION_ID = 2001;

    private WebSocketClientManager clientManager;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Lifecycle: Service Created");
        clientManager = WebSocketClientManager.getInstance(getApplicationContext());
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Lifecycle: Service Start Command Received");

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Spotify Bot Device Worker")
                .setContentText("Maintaining WebSocket connection & command listener")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        try {
            startForeground(NOTIFICATION_ID, notification);
        } catch (Exception e) {
            Log.w(TAG, "Foreground start failed (Android permissions fallback): " + e.getMessage());
        }

        if (clientManager.getCurrentState() == WebSocketClientManager.ConnectionState.DISCONNECTED) {
            clientManager.connect();
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Unbound service
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Lifecycle: Service Destroyed");
        if (clientManager != null) {
            clientManager.disconnect();
        }
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Spotify Bot Connectivity Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Background WebSocket Connectivity Service for Spotify Automation");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
