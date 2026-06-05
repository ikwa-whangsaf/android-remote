package com.remote.android;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.Manifest;
import android.content.pm.PackageManager;

public class MainActivity extends Activity {

    private static final int REQUEST_ADMIN = 1001;
    private static final int REQUEST_PERMISSIONS = 1002;

    private TextView tvStatus;
    private TextView tvAdmin;
    private TextView tvLog;
    private Button btnAdmin;
    private ScrollView scrollLog;

    private BroadcastReceiver logReceiver;
    private BroadcastReceiver adminReceiver;

    private DevicePolicyManager dpm;
    private ComponentName adminComponent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on saat activity terbuka
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, AdminReceiver.class);

        buildUI();
        requestPermissions();
        startService();
        registerReceivers();
    }

    // ==================
    // BUILD UI PROGRAMMATIK
    // ==================
    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#050a0f"));
        root.setPadding(40, 60, 40, 40);

        // Title
        TextView title = new TextView(this);
        title.setText("REMOTE HP · RECEIVER");
        title.setTextColor(Color.parseColor("#00ff88"));
        title.setTextSize(16);
        title.setTypeface(android.graphics.Typeface.MONOSPACE);
        title.setLetterSpacing(0.2f);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        addSpace(root, 8);

        TextView subtitle = new TextView(this);
        subtitle.setText("wss://android-remote.onrender.com");
        subtitle.setTextColor(Color.parseColor("#00f5ff"));
        subtitle.setTextSize(10);
        subtitle.setTypeface(android.graphics.Typeface.MONOSPACE);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle);

        addDivider(root);

        // Status koneksi
        tvStatus = makeInfoRow(root, "STATUS KONEKSI", "Menghubungkan...");

        // Status Device Admin
        tvAdmin = makeInfoRow(root, "DEVICE ADMIN", checkAdminStatus());

        addDivider(root);

        // Tombol aktivasi Device Admin
        btnAdmin = new Button(this);
        updateAdminButton();
        btnAdmin.setOnClickListener(v -> {
            if (dpm.isAdminActive(adminComponent)) {
                // Nonaktifkan
                dpm.removeActiveAdmin(adminComponent);
                updateAdminStatus();
            } else {
                // Aktifkan
                Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
                intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Diperlukan untuk fitur LAYAR MATI BENERAN via remote");
                startActivityForResult(intent, REQUEST_ADMIN);
            }
        });
        root.addView(btnAdmin);

        addSpace(root, 8);

        // Info box
        TextView infoBox = new TextView(this);
        infoBox.setText("ℹ  Aktifkan Device Admin agar\nperintah 'Screen Off' bekerja beneran");
        infoBox.setTextColor(Color.parseColor("#ffcc00"));
        infoBox.setTextSize(11);
        infoBox.setTypeface(android.graphics.Typeface.MONOSPACE);
        infoBox.setBackgroundColor(Color.parseColor("#1a1500"));
        infoBox.setPadding(20, 14, 20, 14);
        infoBox.setLineSpacing(4, 1);
        root.addView(infoBox);

        addDivider(root);

        // Log label
        TextView logLabel = new TextView(this);
        logLabel.setText("// LOG");
        logLabel.setTextColor(Color.parseColor("#00f5ff"));
        logLabel.setTextSize(10);
        logLabel.setTypeface(android.graphics.Typeface.MONOSPACE);
        logLabel.setLetterSpacing(0.2f);
        root.addView(logLabel);

        addSpace(root, 4);

        // Log box
        scrollLog = new ScrollView(this);
        LinearLayout.LayoutParams logParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 300);
        scrollLog.setLayoutParams(logParams);
        scrollLog.setBackgroundColor(Color.parseColor("#0a1520"));

        tvLog = new TextView(this);
        tvLog.setTextColor(Color.parseColor("#b0d4e8"));
        tvLog.setTextSize(10);
        tvLog.setTypeface(android.graphics.Typeface.MONOSPACE);
        tvLog.setPadding(16, 12, 16, 12);
        tvLog.setLineSpacing(4, 1);
        tvLog.setText("Memulai service...\n");
        scrollLog.addView(tvLog);
        root.addView(scrollLog);

        setContentView(root);
    }

    private TextView makeInfoRow(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 12, 0, 12);

        TextView lbl = new TextView(this);
        lbl.setText(label);
        lbl.setTextColor(Color.parseColor("#4a7a96"));
        lbl.setTextSize(10);
        lbl.setTypeface(android.graphics.Typeface.MONOSPACE);
        lbl.setLetterSpacing(0.15f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lbl.setLayoutParams(lp);

        TextView val = new TextView(this);
        val.setText(value);
        val.setTextColor(Color.parseColor("#00f5ff"));
        val.setTextSize(11);
        val.setTypeface(android.graphics.Typeface.MONOSPACE);
        val.setGravity(Gravity.END);

        row.addView(lbl);
        row.addView(val);
        parent.addView(row);

        // Divider
        View div = new View(this);
        div.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        div.setBackgroundColor(Color.parseColor("#0d2535"));
        parent.addView(div);

        return val;
    }

    private void addDivider(LinearLayout parent) {
        View div = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2);
        lp.topMargin = 16;
        lp.bottomMargin = 16;
        div.setLayoutParams(lp);
        div.setBackgroundColor(Color.parseColor("#0d2535"));
        parent.addView(div);
    }

    private void addSpace(LinearLayout parent, int dp) {
        View space = new View(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp * 3));
        parent.addView(space);
    }

    // ==================
    // SERVICE & PERMISSIONS
    // ==================
    private void startService() {
        Intent serviceIntent = new Intent(this, RemoteService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void requestPermissions() {
        String[] perms = {
                Manifest.permission.CAMERA,
                Manifest.permission.VIBRATE,
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            String[] permsNew = {
                    Manifest.permission.CAMERA,
                    Manifest.permission.VIBRATE,
                    Manifest.permission.POST_NOTIFICATIONS
            };
            requestPermissions(permsNew, REQUEST_PERMISSIONS);
        } else {
            requestPermissions(perms, REQUEST_PERMISSIONS);
        }
    }

    // ==================
    // BROADCAST RECEIVERS
    // ==================
    private void registerReceivers() {
        // Log updates dari service
        logReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String log = intent.getStringExtra("log");
                appendLog(log);

                // Update status koneksi
                if (RemoteService.isConnected) {
                    tvStatus.setText("ONLINE ✓");
                    tvStatus.setTextColor(Color.parseColor("#00ff88"));
                } else {
                    tvStatus.setText("OFFLINE");
                    tvStatus.setTextColor(Color.parseColor("#ff3355"));
                }
            }
        };

        // Request admin dari service
        adminReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!dpm.isAdminActive(adminComponent)) {
                    Intent adminIntent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                    adminIntent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
                    adminIntent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "Aktifkan untuk fitur layar mati beneran!");
                    startActivityForResult(adminIntent, REQUEST_ADMIN);
                }
            }
        };

        IntentFilter logFilter = new IntentFilter("com.remote.android.LOG_UPDATE");
        IntentFilter adminFilter = new IntentFilter("com.remote.android.REQUEST_ADMIN");

        registerReceiver(logReceiver, logFilter, Context.RECEIVER_NOT_EXPORTED);
        registerReceiver(adminReceiver, adminFilter, Context.RECEIVER_NOT_EXPORTED);
    }

    // ==================
    // HELPERS
    // ==================
    private void appendLog(String msg) {
        if (msg == null) return;
        String current = tvLog.getText().toString();
        String[] lines = current.split("\n");
        // Keep max 50 lines
        if (lines.length > 50) {
            StringBuilder sb = new StringBuilder();
            for (int i = lines.length - 49; i < lines.length; i++) {
                sb.append(lines[i]).append("\n");
            }
            current = sb.toString();
        }
        tvLog.setText(current + msg + "\n");
        scrollLog.post(() -> scrollLog.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private String checkAdminStatus() {
        return dpm.isAdminActive(adminComponent) ? "AKTIF ✓" : "BELUM AKTIF";
    }

    private void updateAdminStatus() {
        boolean active = dpm.isAdminActive(adminComponent);
        tvAdmin.setText(active ? "AKTIF ✓" : "BELUM AKTIF");
        tvAdmin.setTextColor(active ?
                Color.parseColor("#00ff88") : Color.parseColor("#ff3355"));
        updateAdminButton();
    }

    private void updateAdminButton() {
        boolean active = dpm.isAdminActive(adminComponent);
        btnAdmin.setText(active ? "NONAKTIFKAN DEVICE ADMIN" : "AKTIFKAN DEVICE ADMIN ← WAJIB");
        btnAdmin.setBackgroundColor(active ?
                Color.parseColor("#1a0a0a") : Color.parseColor("#0a1a0a"));
        btnAdmin.setTextColor(active ?
                Color.parseColor("#ff3355") : Color.parseColor("#00ff88"));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ADMIN) {
            updateAdminStatus();
            if (dpm.isAdminActive(adminComponent)) {
                appendLog("Device Admin berhasil diaktifkan!");
                appendLog("Sekarang layar mati beneran siap ✓");
            } else {
                appendLog("Device Admin tidak diaktifkan");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAdminStatus();
        if (RemoteService.isConnected) {
            tvStatus.setText("ONLINE ✓");
            tvStatus.setTextColor(Color.parseColor("#00ff88"));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(logReceiver);
            unregisterReceiver(adminReceiver);
        } catch (Exception ignored) {}
    }
}
