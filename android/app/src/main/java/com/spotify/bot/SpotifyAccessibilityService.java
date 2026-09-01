package com.spotify.bot;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

public class SpotifyAccessibilityService extends AccessibilityService {

    public static final String TAG = "SpotifyBotService";
    public static final String SPOTIFY_PACKAGE = "com.spotify.music";
    public static final String BOT_PACKAGE = "com.spotify.bot";

    private static volatile SpotifyAccessibilityService instance;
    private static volatile String currentForegroundPackage = "";

    public static SpotifyAccessibilityService getInstance() {
        return instance;
    }

    public static String getCurrentForegroundPackage() {
        return currentForegroundPackage;
    }

    public static boolean isSpotifyForeground() {
        return SPOTIFY_PACKAGE.equals(currentForegroundPackage);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.d(TAG, "Lifecycle: Service Created");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.d(TAG, "Lifecycle: Service Connected and Bound");

        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            Log.d(TAG, "Config: Feedback Type: " + info.feedbackType);
            Log.d(TAG, "Config: Event Types: " + info.eventTypes);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        CharSequence pkgNameChar = event.getPackageName();
        if (pkgNameChar != null) {
            String packageName = pkgNameChar.toString();
            int eventType = event.getEventType();

            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                currentForegroundPackage = packageName;
            }

            if (SPOTIFY_PACKAGE.equals(packageName) || BOT_PACKAGE.equals(packageName)) {
                Log.d(TAG, String.format("Event: Type [%d] received from package [%s]", eventType, packageName));
            }
        }
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "Lifecycle: Service Interrupted");
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        Log.d(TAG, "Lifecycle: Service Unbound");
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Lifecycle: Service Destroyed");
        instance = null;
        super.onDestroy();
    }
}
