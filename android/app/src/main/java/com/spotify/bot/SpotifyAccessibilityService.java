package com.spotify.bot;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

public class SpotifyAccessibilityService extends AccessibilityService {

    private static final String TAG = "SpotifyBotService";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Lifecycle: Service Created");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "Lifecycle: Service Connected and Bound");

        // Dynamically log configuration details to verify connection settings
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            Log.d(TAG, "Config: Feedback Type: " + info.feedbackType);
            Log.d(TAG, "Config: Event Types: " + info.eventTypes);
            if (info.packageNames != null) {
                for (String pkg : info.packageNames) {
                    Log.d(TAG, "Config: Target Package: " + pkg);
                }
            }
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Log event type and source package to verify listener activity
        String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "Unknown";
        int eventType = event.getEventType();
        Log.d(TAG, String.format("Event: Type [%d] received from package [%s]", eventType, packageName));
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "Lifecycle: Service Interrupted");
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        Log.d(TAG, "Lifecycle: Service Unbound");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Lifecycle: Service Destroyed");
        super.onDestroy();
    }
}
