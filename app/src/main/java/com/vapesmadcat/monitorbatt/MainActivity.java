package com.vapesmadcat.monitorbatt;

import android.Manifest;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private TextView batteryText;
    private TextView statusText;
    private TextView thresholdValueText;
    private TextView intervalValueText;
    private TextView alertModeText;
    private SeekBar thresholdSeek;
    private SeekBar intervalSeek;
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferences = getSharedPreferences(BatteryService.PREFS_NAME, MODE_PRIVATE);

        progressBar = findViewById(R.id.progressBar);
        batteryText = findViewById(R.id.tvLevel);
        statusText = findViewById(R.id.tvStatus);
        thresholdValueText = findViewById(R.id.tvThresholdValue);
        intervalValueText = findViewById(R.id.tvIntervalValue);
        alertModeText = findViewById(R.id.tvAlertMode);
        thresholdSeek = findViewById(R.id.seekThreshold);
        intervalSeek = findViewById(R.id.seekInterval);
        Button startBtn = findViewById(R.id.btnStart);
        Button stopBtn = findViewById(R.id.btnStop);
        Button muteBtn = findViewById(R.id.btnMute);

        setupControls();

        startBtn.setOnClickListener(v -> {
            requestNotificationPermissionIfNeeded();
            saveSettings();
            preferences.edit().putBoolean(BatteryService.KEY_MUTED, false).apply();
            Intent i = new Intent(this, BatteryService.class);
            ContextCompat.startForegroundService(this, i);
            statusText.setText(R.string.status_running);
            alertModeText.setText(R.string.alert_mode_active);
        });

        stopBtn.setOnClickListener(v -> {
            stopService(new Intent(this, BatteryService.class));
            statusText.setText(R.string.status_stopped);
            alertModeText.setText(R.string.alert_mode_stopped);
        });

        muteBtn.setOnClickListener(v -> {
            boolean muted = !preferences.getBoolean(BatteryService.KEY_MUTED, false);
            preferences.edit().putBoolean(BatteryService.KEY_MUTED, muted).apply();
            updateMuteButton(muted);
            alertModeText.setText(muted ? R.string.alert_mode_muted : R.string.alert_mode_active);
        });

        updateMuteButton(preferences.getBoolean(BatteryService.KEY_MUTED, false));
        updateBatteryReadout();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSettings();
        updateMuteButton(preferences.getBoolean(BatteryService.KEY_MUTED, false));
        updateBatteryReadout();
    }

    private void setupControls() {
        thresholdSeek.setMax(25);
        intervalSeek.setMax(11);

        thresholdSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                thresholdValueText.setText(getString(R.string.threshold_value_fmt, getThresholdFromProgress(progress)));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        intervalSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                intervalValueText.setText(getString(R.string.interval_value_fmt, getIntervalFromProgress(progress)));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        loadSettings();
    }

    private void loadSettings() {
        int threshold = preferences.getInt(BatteryService.KEY_THRESHOLD, BatteryService.DEFAULT_THRESHOLD);
        int intervalSeconds = preferences.getInt(BatteryService.KEY_BEEP_INTERVAL_SECONDS, BatteryService.DEFAULT_BEEP_INTERVAL_SECONDS);

        thresholdSeek.setProgress(Math.max(0, threshold - 5));
        intervalSeek.setProgress(Math.max(0, (intervalSeconds / 5) - 1));

        thresholdValueText.setText(getString(R.string.threshold_value_fmt, threshold));
        intervalValueText.setText(getString(R.string.interval_value_fmt, intervalSeconds));
    }

    private void saveSettings() {
        int threshold = getThresholdFromProgress(thresholdSeek.getProgress());
        int intervalSeconds = getIntervalFromProgress(intervalSeek.getProgress());

        preferences.edit()
                .putInt(BatteryService.KEY_THRESHOLD, threshold)
                .putInt(BatteryService.KEY_BEEP_INTERVAL_SECONDS, intervalSeconds)
                .apply();
    }

    private void updateMuteButton(boolean muted) {
        Button muteBtn = findViewById(R.id.btnMute);
        muteBtn.setText(muted ? R.string.btn_unmute : R.string.btn_mute);
    }

    private int getThresholdFromProgress(int progress) {
        return progress + 5;
    }

    private int getIntervalFromProgress(int progress) {
        return (progress + 1) * 5;
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

        progressBar.setProgress(Math.max(pct, 0));
        progressBar.getProgressDrawable().setColorFilter(getBatteryColor(pct), PorterDuff.Mode.SRC_IN);
        batteryText.setText(getString(R.string.battery_percent_fmt, pct));
        batteryText.setTextColor(getBatteryColor(pct));
        statusText.setText(getString(R.string.battery_fmt, pct, charger));
        alertModeText.setText(getAlertLabel(pct));
        alertModeText.setTextColor(getBatteryColor(pct));
    }

    private String getAlertLabel(int pct) {
        if (pct <= 10) return getString(R.string.alert_level_critical);
        if (pct <= 30) return getString(R.string.alert_level_attention);
        return getString(R.string.alert_level_normal);
    }

    private int getBatteryColor(int pct) {
        if (pct <= 10) return 0xFFE11D48;
        if (pct <= 30) return 0xFFF59E0B;
        return 0xFF22C55E;
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
