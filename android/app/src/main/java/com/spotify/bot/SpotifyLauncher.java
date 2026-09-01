package com.spotify.bot;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

public class SpotifyLauncher {

    private static final String TAG = SpotifyAccessibilityService.TAG;
    private static final String SPOTIFY_PACKAGE = SpotifyAccessibilityService.SPOTIFY_PACKAGE;
    private static final long TIMEOUT_MS = 10000; // 10 seconds timeout
    private static final long POLL_INTERVAL_MS = 500; // Poll every 500ms

    public interface LaunchCallback {
        void onResult(boolean success, String message);
    }

    public static boolean isSpotifyInstalled(Context context) {
        if (context == null) return false;
        try {
            context.getPackageManager().getPackageInfo(SPOTIFY_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(SPOTIFY_PACKAGE);
            return launchIntent != null;
        }
    }

    public static void launchSpotify(Context context, LaunchCallback callback) {
        new Thread(() -> {
            Log.d(TAG, "Spotify launch requested");

            // 1. Check if Spotify is installed
            if (!isSpotifyInstalled(context)) {
                Log.e(TAG, "Spotify unavailable");
                Log.e(TAG, "Spotify launch failed");
                if (callback != null) callback.onResult(false, "Spotify is not installed on this device.");
                return;
            }

            // 2. Check if Spotify is ALREADY currently visible in the foreground
            if (SpotifyAccessibilityService.isSpotifyForeground()) {
                Log.d(TAG, "Spotify already foreground");
                Log.d(TAG, "Spotify stable state detected");
                Log.d(TAG, "Spotify launch successful");
                if (callback != null) callback.onResult(true, "Spotify is already in foreground.");
                return;
            }

            Log.d(TAG, "Spotify not foreground");

            // 3. Initiate launch / bring Spotify to the front
            Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(SPOTIFY_PACKAGE);
            if (launchIntent == null) {
                launchIntent = new Intent(Intent.ACTION_MAIN);
                launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                launchIntent.setPackage(SPOTIFY_PACKAGE);
            }

            // Bring existing running activity to front without resetting state
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK);
            Log.d(TAG, "Spotify launch initiated");
            context.startActivity(launchIntent);

            // 4. Poll and wait for Spotify to appear on screen and reach stable state
            Log.d(TAG, "Waiting for Spotify");
            long startTime = System.currentTimeMillis();
            boolean foregroundDetected = false;
            boolean stableStateDetected = false;

            while (System.currentTimeMillis() - startTime < TIMEOUT_MS) {
                if (SpotifyAccessibilityService.isSpotifyForeground()) {
                    if (!foregroundDetected) {
                        foregroundDetected = true;
                        Log.d(TAG, "Spotify foreground detected");
                    }

                    SpotifyAccessibilityService service = SpotifyAccessibilityService.getInstance();
                    if (service != null) {
                        AccessibilityNodeInfo rootNode = service.getRootInActiveWindow();
                        if (rootNode != null) {
                            rootNode.recycle();
                            stableStateDetected = true;
                            Log.d(TAG, "Spotify stable state detected");
                            break;
                        }
                    } else {
                        stableStateDetected = true;
                        Log.d(TAG, "Spotify stable state detected");
                        break;
                    }
                }

                try {
                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.e(TAG, "Spotify launch interrupted during wait");
                    break;
                }
            }

            if (stableStateDetected) {
                Log.d(TAG, "Spotify launch successful");
                if (callback != null) callback.onResult(true, "Spotify launched successfully.");
            } else {
                Log.e(TAG, "Spotify readiness timeout");
                Log.e(TAG, "Spotify launch failed");
                if (callback != null) callback.onResult(false, "Spotify failed to reach stable state within timeout.");
            }
        }).start();
    }
}
