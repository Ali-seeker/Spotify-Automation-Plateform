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
        if (SPOTIFY_PACKAGE.equals(currentForegroundPackage)) {
            return true;
        }
        if (instance != null) {
            android.view.accessibility.AccessibilityNodeInfo root = instance.getRootInActiveWindow();
            if (root != null) {
                CharSequence pkg = root.getPackageName();
                root.recycle();
                if (pkg != null && SPOTIFY_PACKAGE.contentEquals(pkg)) {
                    currentForegroundPackage = SPOTIFY_PACKAGE;
                    return true;
                }
            }
        }
        return false;
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

            if (SPOTIFY_PACKAGE.equals(packageName)) {
                currentForegroundPackage = SPOTIFY_PACKAGE;
            } else if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
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

    public boolean clickCoordinates(float x, float y) {
        try {
            android.graphics.Path path = new android.graphics.Path();
            path.moveTo(x, y);
            android.accessibilityservice.GestureDescription.Builder builder = new android.accessibilityservice.GestureDescription.Builder();
            builder.addStroke(new android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 50));
            return dispatchGesture(builder.build(), null, null);
        } catch (Exception e) {
            Log.e(TAG, "Failed to dispatch tap gesture at (" + x + ", " + y + ")", e);
            return false;
        }
    }

    public boolean clickCoordinatesRatio(float xRatio, float yRatio) {
        try {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            float x = dm.widthPixels * xRatio;
            float y = dm.heightPixels * yRatio;
            return clickCoordinates(x, y);
        } catch (Exception e) {
            Log.e(TAG, "Failed to dispatch ratio tap gesture", e);
            return false;
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Lifecycle: Service Destroyed");
        instance = null;
        super.onDestroy();
    }
}
