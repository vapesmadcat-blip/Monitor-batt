package com.vapesmadcat.monitorbatt;

import android.Manifest;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private View batteryFill;
    private TextView batteryText;
    private TextView statusText;
    private TextView thresholdValueText;
    private TextView intervalValueText;
    private TextView alertModeText;
    private SeekBar thresholdSeek;
    private SeekBar intervalSeek;
    private SharedPreferences preferences;
    private Button muteBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferences = getSharedPreferences(BatteryService.PREFS_NAME, MODE_PRIVATE);

        progressBar = findViewById(R.id.progressBar);
        batteryFill = findViewById(R.id.batteryFill);
        batteryText = findViewById(R.id.tvLevel);
        statusText = findViewById(R.id.tvStatus);
        thresholdValueText = findViewById(R.id.tvThresholdValue);
        intervalValueText = findViewById(R.id.tvIntervalValue);
        alertModeText = findViewById(R.id.tvAlertMode);
        thresholdSeek = findViewById(R.id.seekThreshold);
        intervalSeek = findViewById(R.id.seekInterval);
        Button startBtn = findViewById(R.id.btnStart);
        Button stopBtn = findViewById(R.id.btnStop);
        muteBtn = findViewById(R.id.btnMute);
        Button testBeepBtn = findViewById(R.id.btnTestBeep);

        setupControls();

        startBtn.setOnClickListener(v -> {
            requestNotificationPermissionIfNeeded();
            saveSettings();
            preferences.edit().putBoolean(BatteryService.KEY_MUTED, false).apply();
            Intent i = new Intent(this, BatteryService.class);
            ContextCompat.startForegroundService(this, i);
            statusText.setText(getString(R.string.battery_title));
            alertModeText.setText(getString(R.string.alert_mode_active));
            updateMuteButton(false);
        });

        stopBtn.setOnClickListener(v -> {
            stopService(new Intent(this, BatteryService.class));
            statusText.setText(getString(R.string.battery_title));
            alertModeText.setText(getString(R.string.alert_mode_stopped));
        });

        muteBtn.setOnClickListener(v -> {
            boolean muted = !preferences.getBoolean(BatteryService.KEY_MUTED, false);
            preferences.edit().putBoolean(BatteryService.KEY_MUTED, muted).apply();
            updateMuteButton(muted);
            alertModeText.setText(getString(muted ? R.string.alert_mode_muted : R.string.alert_mode_active));
        });

        testBeepBtn.setOnClickListener(v -> {
            playTestBeep();
            Toast.makeText(this, "Bip de teste executado", Toast.LENGTH_SHORT).show();
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
        muteBtn.setText(getString(muted ? R.string.btn_unmute : R.string.btn_mute));
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

        progressBar.setProgress(Math.max(pct, 0));
        batteryText.setText(getString(R.string.battery_percent_fmt, pct));
        batteryText.setTextColor(getBatteryColor(pct));
        statusText.setText(getString(R.string.battery_title));
        alertModeText.setText(getAlertLabel(pct));
        alertModeText.setTextColor(getBatteryColor(pct));
        updateBatteryFill(pct);
    }

    private void updateBatteryFill(int pct) {
        int safePct = Math.max(0, Math.min(100, pct));
        Drawable background;
        if (safePct <= 10) {
            background = ContextCompat.getDrawable(this, R.drawable.battery_fill_low);
        } else if (safePct <= 30) {
            background = ContextCompat.getDrawable(this, R.drawable.battery_fill_mid);
        } else {
            background = ContextCompat.getDrawable(this, R.drawable.battery_fill_high);
        }
        batteryFill.setBackground(background);
        batteryFill.setPivotY(batteryFill.getHeight());
        float scale = Math.max(0.08f, safePct / 100f);
        batteryFill.setScaleY(scale);
        batteryFill.setTranslationY(((1f - scale) * batteryFill.getHeight()) / 2f);
    }

    private String getAlertLabel(int pct) {
        if (pct <= 10) return getString(R.string.alert_level_critical);
        if (pct <= 30) return getString(R.string.alert_level_attention);
        return getString(R.string.alert_level_normal);
    }

    private int getBatteryColor(int pct) {
        if (pct <= 10) return 0xFFFF4D4F;
        if (pct <= 30) return 0xFFF59E0B;
        return 0xFF4ADE80;
    }

    private void playTestBeep() {
        ToneGenerator toneGenerator = null;
        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 300);
        } catch (RuntimeException ignored) {
            try {
                toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 300);
            } catch (RuntimeException ignoredAgain) {
                Toast.makeText(this, "Não foi possível tocar o bip", Toast.LENGTH_SHORT).show();
            }
        }
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
