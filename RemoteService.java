package com.remote.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

import org.json.JSONObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import java.util.concurrent.TimeUnit;

public class RemoteService extends Service {

    private static final String CHANNEL_ID = "remote_service_channel";
    private static final String SERVER_URL = "wss://android-remote.onrender.com";

    private OkHttpClient httpClient;
    private WebSocket webSocket;
    private Handler mainHandler;
    private boolean torchOn = false;
    private String currentTorchId = null;

    // Expose static reference for MainActivity
    public static RemoteService instance = null;
    public static String lastLog = "Menunggu koneksi...";
    public static boolean isConnected = false;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        mainHandler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
        startForeground(1, buildNotification("Menghubungkan..."));
        connectWebSocket();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (webSocket == null || !isConnected) {
            connectWebSocket();
        }
        return START_STICKY; // restart otomatis kalau killed
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        isConnected = false;
        if (webSocket != null) webSocket.close(1000, "Service destroyed");
        if (httpClient != null) httpClient.dispatcher().executorService().shutdown();
    }

    // ==================
    // WEBSOCKET
    // ==================
    private void connectWebSocket() {
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
        }
        httpClient = new OkHttpClient.Builder()
                .pingInterval(20, TimeUnit.SECONDS)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();

        Request request = new Request.Builder().url(SERVER_URL).build();
        webSocket = httpClient.newWebSocket(request, new WebSocketListener() {

            @Override
            public void onOpen(WebSocket ws, Response response) {
                isConnected = true;
                updateNotification("Terhubung ke server ✓");
                addLog("Terhubung ke server");
                // Register sebagai receiver
                sendJson(ws, "{\"type\":\"register\",\"role\":\"receiver\"}");
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                handleMessage(ws, text);
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                isConnected = false;
                webSocket = null;
                updateNotification("Gagal connect, retry 10s...");
                addLog("Gagal: " + t.getMessage());
                // Retry setelah 10 detik
                mainHandler.postDelayed(() -> connectWebSocket(), 10000);
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                isConnected = false;
                webSocket = null;
                updateNotification("Terputus, retry 10s...");
                addLog("Terputus: " + reason);
                mainHandler.postDelayed(() -> connectWebSocket(), 10000);
            }
        });
    }

    private void handleMessage(WebSocket ws, String text) {
        try {
            JSONObject msg = new JSONObject(text);
            String type = msg.optString("type");

            if ("command".equals(type)) {
                String action = msg.optString("action");
                addLog("Perintah: " + action);

                switch (action) {
                    case "torch":
                        boolean torchState = msg.optBoolean("state", true);
                        String facing = msg.optString("facing", "back");
                        mainHandler.post(() -> {
                            if (torchState) startTorch(facing);
                            else stopTorch();
                        });
                        break;

                    case "torchOff":
                        mainHandler.post(this::stopTorch);
                        break;

                    case "vibrate":
                        mainHandler.post(this::doVibrate);
                        break;

                    case "screenOff":
                        mainHandler.post(this::doScreenLock);
                        break;

                    case "getStatus":
                        sendStatus(ws);
                        break;
                }
            }
        } catch (Exception e) {
            addLog("Parse error: " + e.getMessage());
        }
    }

    // ==================
    // TORCH (SENTER)
    // ==================
    private void startTorch(String facing) {
        try {
            CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            String[] ids = cameraManager.getCameraIdList();

            String targetId = null;
            for (String id : ids) {
                android.hardware.camera2.CameraCharacteristics chars =
                        cameraManager.getCameraCharacteristics(id);
                Integer lensFacing = chars.get(
                        android.hardware.camera2.CameraCharacteristics.LENS_FACING);

                if ("front".equals(facing)) {
                    if (lensFacing != null &&
                            lensFacing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT) {
                        targetId = id;
                        break;
                    }
                } else {
                    if (lensFacing != null &&
                            lensFacing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK) {
                        targetId = id;
                        break;
                    }
                }
            }

            if (targetId != null) {
                stopTorch(); // matiin dulu yang lama
                currentTorchId = targetId;
                cameraManager.setTorchMode(targetId, true);
                torchOn = true;
                addLog("Senter " + ("front".equals(facing) ? "depan" : "belakang") + " ON");
                sendResponse("Senter " + ("front".equals(facing) ? "Depan" : "Belakang") + " ON");
            } else {
                addLog("Kamera " + facing + " tidak ditemukan");
                sendResponse("Kamera " + facing + " tidak ada");
            }
        } catch (CameraAccessException e) {
            addLog("Torch error: " + e.getMessage());
            sendResponse("Error torch: " + e.getMessage());
        }
    }

    private void stopTorch() {
        if (currentTorchId != null) {
            try {
                CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
                cameraManager.setTorchMode(currentTorchId, false);
            } catch (CameraAccessException ignored) {}
            currentTorchId = null;
        }
        torchOn = false;
        addLog("Senter OFF");
        sendResponse("Senter OFF");
    }

    // ==================
    // VIBRATE
    // ==================
    private void doVibrate() {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] pattern = {0, 200, 100, 200, 100, 200};
            VibrationEffect effect = VibrationEffect.createWaveform(pattern, -1);
            vibrator.vibrate(effect);
            addLog("Vibrasi OK");
            sendResponse("Vibrasi OK");
        } else {
            addLog("Vibrator tidak support");
            sendResponse("Vibrator tidak support");
        }
    }

    // ==================
    // SCREEN LOCK (DEVICE ADMIN) — LAYAR MATI BENERAN
    // ==================
    private void doScreenLock() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponent = new ComponentName(this, AdminReceiver.class);

        if (dpm != null && dpm.isAdminActive(adminComponent)) {
            dpm.lockNow(); // Layar mati beneran!
            addLog("Layar mati (Device Admin) ✓");
            sendResponse("Layar OFF (Device Admin)");
        } else {
            addLog("Device Admin belum aktif!");
            sendResponse("GAGAL: Aktifkan Device Admin dulu di app");
            // Beritahu MainActivity untuk minta aktivasi
            Intent intent = new Intent("com.remote.android.REQUEST_ADMIN");
            sendBroadcast(intent);
        }
    }

    // ==================
    // HELPERS
    // ==================
    private void sendJson(WebSocket ws, String json) {
        if (ws != null) ws.send(json);
    }

    private void sendResponse(String msg) {
        if (webSocket != null && isConnected) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("type", "response");
                obj.put("msg", msg);
                webSocket.send(obj.toString());
            } catch (Exception ignored) {}
        }
    }

    private void sendStatus(WebSocket ws) {
        try {
            JSONObject data = new JSONObject();
            data.put("torch", torchOn ? "ON" : "OFF");
            data.put("platform", "Android Native");

            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName adminComponent = new ComponentName(this, AdminReceiver.class);
            data.put("deviceAdmin", dpm != null && dpm.isAdminActive(adminComponent));

            JSONObject response = new JSONObject();
            response.put("type", "status");
            response.put("data", data);
            sendJson(ws, response.toString());
        } catch (Exception ignored) {}
    }

    private void addLog(String msg) {
        lastLog = msg;
        // Notify MainActivity if running
        Intent intent = new Intent("com.remote.android.LOG_UPDATE");
        intent.putExtra("log", msg);
        sendBroadcast(intent);
    }

    // ==================
    // NOTIFICATION
    // ==================
    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(getString(R.string.channel_desc));
        channel.setSound(null, null);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(channel);
    }

    private Notification buildNotification(String text) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(1, buildNotification(text));
    }
}
