package com.spotify.bot;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DeviceConfig {

    private static final String PREF_NAME = "spotify_bot_config";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_AUTH_TOKEN = "device_auth_token";

    public static final String DEFAULT_SERVER_URL = "ws://192.168.1.35:8000/ws/device";
    public static final String DEFAULT_AUTH_TOKEN = "device_shared_secret_for_auth_123";

    /**
     * Retrieves or generates a stable persistent device ID.
     */
    public static synchronized String getDeviceId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String deviceId = prefs.getString(KEY_DEVICE_ID, null);

        if (deviceId == null || deviceId.trim().isEmpty()) {
            String model = Build.MODEL != null ? Build.MODEL.replaceAll("[^a-zA-Z0-9]", "_").toLowerCase() : "android";
            String randomShort = UUID.randomUUID().toString().substring(0, 6);
            deviceId = "dev_" + model + "_" + randomShort;
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply();
        }

        return deviceId;
    }

    /**
     * Retrieves the configured backend WebSocket server URL.
     */
    public static String getServerUrl(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL);
    }

    public static void setServerUrl(Context context, String url) {
        if (url != null && !url.trim().isEmpty()) {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_SERVER_URL, url.trim())
                    .apply();
        }
    }

    /**
     * Retrieves the device authentication shared secret token.
     */
    public static String getAuthToken(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_AUTH_TOKEN, DEFAULT_AUTH_TOKEN);
    }

    public static void setAuthToken(Context context, String token) {
        if (token != null && !token.trim().isEmpty()) {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_AUTH_TOKEN, token.trim())
                    .apply();
        }
    }

    /**
     * Retrieves application version string dynamically.
     */
    public static String getAppVersion(Context context) {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pInfo.versionName != null ? pInfo.versionName : "1.0";
        } catch (PackageManager.NameNotFoundException e) {
            return "1.0";
        }
    }

    /**
     * Returns supported device capabilities.
     */
    public static List<String> getCapabilities() {
        List<String> caps = new ArrayList<>();
        caps.add("ACCESSIBILITY_SERVICE");
        caps.add("SPOTIFY_LAUNCH");
        caps.add("UI_CLICK");
        caps.add("SEARCH_AND_PLAY");
        return caps;
    }
}
