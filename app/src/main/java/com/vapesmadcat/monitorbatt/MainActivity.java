package com.vapesmadcat.monitorbatt;

import android.Manifest;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private TextView tvLevel;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        progressBar = findViewById(R.id.progressBar);
        tvLevel = findViewById(R.id.tvLevel);
        tvStatus = findViewById(R.id.tvStatus);

        Button btnStart = findViewById(R.id.btnStart);
        Button btnStop = findViewById(R.id.btnStop);

        btnStart.setOnClickListener(v -> {
            requestNotificationPermissionIfNeeded();
            Intent i = new Intent(this, BatteryService.class);
            ContextCompat.startForegroundService(this, i);
            tvStatus.setText(R.string.status_running);
        });

        btnStop.setOnClickListener(v -> {
            stopService(new Intent(this, BatteryService.class));
            tvStatus.setText(R.string.status_stopped);
        });

        updateBatteryReadout();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateBatteryReadout();
    }

    private void updateBatteryReadout() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        if (batteryStatus == null) return;

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);

        int pct = (level >= 0 && scale > 0) ? (int) ((level * 100f) / scale) : -1;
        String charger = plugged != 0 ? getString(R.string.charger_on) : getString(R.string.charger_off);

        if (pct >= 0) {
            progressBar.setProgress(pct);
            tvLevel.setText(pct + "%");
        } else {
            tvLevel.setText("--%");
        }

        tvStatus.setText(getString(R.string.battery_fmt, pct >= 0 ? pct : 0, charger));
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }
    }
}
