package com.spotify.bot;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class CommandQueueManager {

    private static final String TAG = "SpotifyBotQueue";

    private static volatile CommandQueueManager instance;

    private final Context context;
    private final Handler mainHandler;
    private final LinkedBlockingQueue<JSONObject> commandQueue;
    private final Set<String> seenCommandIds;
    private final ExecutorService queueExecutor;
    private final AtomicBoolean isProcessing;

    private CommandQueueManager(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.commandQueue = new LinkedBlockingQueue<>();
        this.seenCommandIds = ConcurrentHashMap.newKeySet();
        this.queueExecutor = Executors.newSingleThreadExecutor(); // Strictly 1 command at a time
        this.isProcessing = new AtomicBoolean(false);
    }

    public static synchronized CommandQueueManager getInstance(Context context) {
        if (instance == null) {
            instance = new CommandQueueManager(context);
        }
        return instance;
    }

    /**
     * Enqueues incoming command payload into FIFO queue after duplicate & authentication checks.
     */
    public void enqueueCommand(JSONObject commandPayload) {
        if (commandPayload == null) return;

        String commandId = commandPayload.optString("command_id", null);
        if (commandId == null || commandId.trim().isEmpty()) {
            commandId = "cmd_" + UUID.randomUUID().toString().substring(0, 8);
        }

        // 1. ATOMIC DUPLICATE CHECK
        if (!seenCommandIds.add(commandId)) {
            Log.w(TAG, "COMMAND_DUPLICATE: Command ID '" + commandId + "' already processed/enqueued. Skipping duplicate.");
            return;
        }

        // 2. CHECK READY CONNECTION STATE
        if (!WebSocketClientManager.getInstance(context).isReady()) {
            Log.w(TAG, "COMMAND_REJECTED_UNAUTHENTICATED: Device is not in READY state when command arrived.");
            seenCommandIds.remove(commandId);
            return;
        }

        Log.i(TAG, String.format("COMMAND_QUEUED: Enqueued command_id='%s', task_name='%s' (Queue length: %d)",
                commandId, commandPayload.optString("task_name", "Task"), commandQueue.size() + 1));

        commandQueue.offer(commandPayload);
        triggerQueueProcessing();
    }

    private void triggerQueueProcessing() {
        if (isProcessing.compareAndSet(false, true)) {
            queueExecutor.submit(this::processQueueLoop);
        }
    }

    private void processQueueLoop() {
        while (!commandQueue.isEmpty()) {
            JSONObject command = commandQueue.poll();
            if (command == null) continue;

            String commandId = command.optString("command_id", "unknown");
            String runId = command.optString("run_id", "");
            String actionType = command.optString("action_type", "CLICK");

            Log.i(TAG, String.format("COMMAND_STARTED: Processing command_id='%s', run_id='%s', action='%s'",
                    commandId, runId, actionType));

            CountDownLatch latch = new CountDownLatch(1);

            try {
                // 3. PRE-EXECUTION TTL CHECK
                if (isCommandExpired(command)) {
                    Log.w(TAG, "COMMAND_EXPIRED: TTL expired prior to execution for command_id=" + commandId);
                    emitStepFailed(runId, 1, "COMMAND_EXPIRED");
                    emitCommandDone(runId, "FAILED", false);
                    continue;
                }

                // 4. EXECUTE COMMAND LIFE-CYCLE
                executeCommandWithLatch(command, latch);
                latch.await(30, TimeUnit.SECONDS); // Wait for async UI callbacks

            } catch (Throwable t) {
                Log.e(TAG, "UNHANDLED_EXCEPTION during command execution for run_id=" + runId, t);
                emitStepFailed(runId, 1, "UNHANDLED_EXCEPTION");
                emitCommandDone(runId, "FAILED", false);
            }
        }
        isProcessing.set(false);
    }

    private boolean isCommandExpired(JSONObject command) {
        String issuedAtStr = command.optString("issued_at", null);
        long ttlMs = command.optLong("ttl_ms", 180000); // Default 3 minutes

        if (issuedAtStr == null || issuedAtStr.trim().isEmpty()) {
            return false;
        }

        try {
            long issuedAtMs = parseIsoTimestamp(issuedAtStr);
            long currentTimeMs = System.currentTimeMillis();
            long expirationTimeMs = issuedAtMs + ttlMs;

            return currentTimeMs > expirationTimeMs;
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse issued_at timestamp '" + issuedAtStr + "': " + e.getMessage());
            return false;
        }
    }

    private long parseIsoTimestamp(String timestampStr) throws ParseException {
        // Clean microsecond precision if present (e.g. .870458+00:00 -> .870+00:00)
        String cleaned = timestampStr.replaceAll("(\\.\\d{3})\\d+", "$1");
        cleaned = cleaned.replace("Z", "+0000");

        String[] formats = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ssZ"
        };

        for (String formatStr : formats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(formatStr, Locale.US);
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date date = sdf.parse(cleaned);
                if (date != null) return date.getTime();
            } catch (ParseException ignored) {}
        }

        // Fallback to epoch timestamp parse
        return System.currentTimeMillis();
    }

    private void executeCommandWithLatch(JSONObject command, CountDownLatch latch) {
        String runId = command.optString("run_id", "");
        String actionType = command.optString("action_type", "CLICK");

        // Step 1: Initiating Spotify Launch
        emitStepStarted(runId, 1, "Initiating Spotify Launch");

        SpotifyLauncher.launchSpotify(context, (launchSuccess, launchMsg) -> {
            if (!launchSuccess) {
                emitStepFailed(runId, 1, "SPOTIFY_LAUNCH_FAILED");
                emitCommandDone(runId, "FAILED", false);
                latch.countDown();
                return;
            }

            emitStepOk(runId, 1, "Spotify Launched & Foreground Verified");

            // Step 2: Executing Action
            emitStepStarted(runId, 2, "Executing " + actionType + " UI action");

            try { Thread.sleep(500); } catch (InterruptedException ignored) {}

            SpotifyClicker.clickSearchWithRetry((clickSuccess, clickMsg, reasonCode) -> {
                if (clickSuccess) {
                    emitStepOk(runId, 2, clickMsg);
                    emitCommandDone(runId, "SUCCESS", true);
                } else {
                    emitStepFailed(runId, 2, reasonCode != null ? reasonCode : "UI_ELEMENT_NOT_FOUND");
                    emitCommandDone(runId, "FAILED", false);
                }
                latch.countDown();
            });
        });
    }

    // --- EVENT EMITTERS ---

    private void emitStepStarted(String runId, int stepIndex, String stepName) {
        try {
            JSONObject event = new JSONObject();
            event.put("type", "STEP_STARTED");
            event.put("run_id", runId);

            JSONObject payload = new JSONObject();
            payload.put("step_index", stepIndex);
            payload.put("step_name", stepName);
            event.put("payload", payload);

            Log.i(TAG, String.format("STEP_STARTED [Step %d]: %s", stepIndex, stepName));
            WebSocketClientManager.getInstance(context).sendEventPayload(event);
        } catch (JSONException e) {
            Log.e(TAG, "Error emitting STEP_STARTED", e);
        }
    }

    private void emitStepOk(String runId, int stepIndex, String stepName) {
        try {
            JSONObject event = new JSONObject();
            event.put("type", "STEP_OK");
            event.put("run_id", runId);

            JSONObject payload = new JSONObject();
            payload.put("step_index", stepIndex);
            payload.put("step_name", stepName);
            event.put("payload", payload);

            Log.i(TAG, String.format("STEP_OK [Step %d]: %s", stepIndex, stepName));
            WebSocketClientManager.getInstance(context).sendEventPayload(event);
        } catch (JSONException e) {
            Log.e(TAG, "Error emitting STEP_OK", e);
        }
    }

    private void emitStepFailed(String runId, int stepIndex, String reasonCode) {
        try {
            JSONObject event = new JSONObject();
            event.put("type", "STEP_FAILED");
            event.put("run_id", runId);

            JSONObject payload = new JSONObject();
            payload.put("step_index", stepIndex);
            payload.put("reason_code", reasonCode);
            event.put("payload", payload);

            Log.e(TAG, String.format("STEP_FAILED [Step %d]: reason_code=%s", stepIndex, reasonCode));
            WebSocketClientManager.getInstance(context).sendEventPayload(event);
        } catch (JSONException e) {
            Log.e(TAG, "Error emitting STEP_FAILED", e);
        }
    }

    private void emitCommandDone(String runId, String status, boolean result) {
        try {
            JSONObject event = new JSONObject();
            event.put("type", "COMMAND_DONE");
            event.put("run_id", runId);
            event.put("status", status);

            JSONObject payload = new JSONObject();
            payload.put("result", result);
            event.put("payload", payload);

            Log.i(TAG, String.format("COMMAND_DONE: status=%s, result=%b", status, result));
            WebSocketClientManager.getInstance(context).sendEventPayload(event);
        } catch (JSONException e) {
            Log.e(TAG, "Error emitting COMMAND_DONE", e);
        }
    }
}
