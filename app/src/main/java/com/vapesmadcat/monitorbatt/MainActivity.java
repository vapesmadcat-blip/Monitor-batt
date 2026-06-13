package com.vapesmadcat.monitorbatt;

import android.Manifest;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private TextView batteryText;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        batteryText = findViewById(R.id.batteryText);
        statusText = findViewById(R.id.statusText);
        Button startBtn = findViewById(R.id.startBtn);
        Button stopBtn = findViewById(R.id.stopBtn);

        startBtn.setOnClickListener(v -> {
            requestNotificationPermissionIfNeeded();
            Intent i = new Intent(this, BatteryService.class);
            ContextCompat.startForegroundService(this, i);
            statusText.setText(R.string.status_running);
        });

        stopBtn.setOnClickListener(v -> {
            stopService(new Intent(this, BatteryService.class));
            statusText.setText(R.string.status_stopped);
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
        batteryText.setText(getString(R.string.battery_fmt, pct, charger));
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
