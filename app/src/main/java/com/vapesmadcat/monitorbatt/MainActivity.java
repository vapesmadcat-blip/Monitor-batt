package com.vapesmadcat.monitorbatt;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private View batteryFill;
    private TextView batteryText;
    private TextView statusText;
    private TextView thresholdValueText;
    private TextView intervalValueText;
    private TextView alertModeText;
    private TextView bipIndicator;
    private SeekBar thresholdSeek;
    private SeekBar intervalSeek;
    private Spinner characterSpinner;
    private SharedPreferences preferences;
    private Button muteBtn;
    private Button saveBtn;
    private Button startBtn;
    private Button stopBtn;
    
    private String[] characters = {"Nenhum", "Lula", "Bolsonaro", "Goku", "Vegeta"};
    private String[] characterKeys = {"none", "lula", "bolsonaro", "goku", "vegeta"};
    private boolean isModified = false;

    private final BroadcastReceiver bipReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            triggerVisualBip();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferences = getSharedPreferences(BatteryService.PREFS_NAME, MODE_PRIVATE);

        batteryFill = findViewById(R.id.batteryFill);
        batteryText = findViewById(R.id.tvLevel);
        statusText = findViewById(R.id.tvStatus);
        thresholdValueText = findViewById(R.id.tvThresholdValue);
        intervalValueText = findViewById(R.id.tvIntervalValue);
        alertModeText = findViewById(R.id.tvAlertMode);
        bipIndicator = findViewById(R.id.tvBipIndicator);
        thresholdSeek = findViewById(R.id.seekThreshold);
        intervalSeek = findViewById(R.id.seekInterval);
        characterSpinner = findViewById(R.id.spinnerCharacter);
        
        saveBtn = findViewById(R.id.btnSave);
        startBtn = findViewById(R.id.btnStart);
        stopBtn = findViewById(R.id.btnStop);
        muteBtn = findViewById(R.id.btnMute);
        Button testBeepBtn = findViewById(R.id.btnTestBeep);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, characters);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        characterSpinner.setAdapter(adapter);

        setupControls();

        saveBtn.setOnClickListener(v -> {
            saveSettings();
            isModified = false;
            saveBtn.setVisibility(View.GONE);
            Toast.makeText(this, "Configurações salvas!", Toast.LENGTH_SHORT).show();
        });

        startBtn.setOnClickListener(v -> {
            requestNotificationPermissionIfNeeded();
            saveSettings();
            Intent i = new Intent(this, BatteryService.class);
            ContextCompat.startForegroundService(this, i);
            updateServiceButtons(true);
        });

        stopBtn.setOnClickListener(v -> {
            stopService(new Intent(this, BatteryService.class));
            updateServiceButtons(false);
        });

        muteBtn.setOnClickListener(v -> {
            boolean muted = !preferences.getBoolean(BatteryService.KEY_MUTED, false);
            preferences.edit().putBoolean(BatteryService.KEY_MUTED, muted).apply();
            updateMuteButton(muted);
        });

        testBeepBtn.setOnClickListener(v -> {
            triggerVisualBip();
            playTestBeep();
        });

        registerReceiver(bipReceiver, new IntentFilter("com.vapesmadcat.monitorbatt.BIP_TRIGGERED"), Context.RECEIVER_NOT_EXPORTED);
        
        updateMuteButton(preferences.getBoolean(BatteryService.KEY_MUTED, false));
        updateBatteryReadout();
        checkServiceRunning();
    }

    private void triggerVisualBip() {
        bipIndicator.setTextColor(Color.RED);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            bipIndicator.setTextColor(Color.WHITE);
        }, 1000);
    }

    private void checkServiceRunning() {
        // Simples verificação via flag ou similar se necessário, por ora usamos o estado visual
    }

    private void updateServiceButtons(boolean running) {
        startBtn.setEnabled(!running);
        startBtn.setAlpha(running ? 0.5f : 1.0f);
        stopBtn.setEnabled(running);
        stopBtn.setAlpha(running ? 1.0f : 0.5f);
    }

    private void setupControls() {
        thresholdSeek.setMax(25); // 5 to 30
        intervalSeek.setMax(11); // 5 to 60 (steps of 5)

        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                checkChanges();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        };
        characterSpinner.setOnItemSelectedListener(listener);

        SeekBar.OnSeekBarChangeListener seekListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) {
                    checkChanges();
                    updateTexts();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        };
        thresholdSeek.setOnSeekBarChangeListener(seekListener);
        intervalSeek.setOnSeekBarChangeListener(seekListener);

        loadSettings();
    }

    private void updateTexts() {
        thresholdValueText.setText(getThresholdFromProgress(thresholdSeek.getProgress()) + "%");
        intervalValueText.setText(getIntervalFromProgress(intervalSeek.getProgress()) + " segundos");
    }

    private void checkChanges() {
        isModified = true;
        saveBtn.setVisibility(View.VISIBLE);
    }

    private void loadSettings() {
        int threshold = preferences.getInt(BatteryService.KEY_THRESHOLD, BatteryService.DEFAULT_THRESHOLD);
        int intervalSeconds = preferences.getInt(BatteryService.KEY_BEEP_INTERVAL_SECONDS, BatteryService.DEFAULT_BEEP_INTERVAL_SECONDS);
        String savedChar = preferences.getString(BatteryService.KEY_CHARACTER_VOICE, "none");

        thresholdSeek.setProgress(Math.max(0, threshold - 5));
        intervalSeek.setProgress(Math.max(0, (intervalSeconds / 5) - 1));

        for (int i = 0; i < characterKeys.length; i++) {
            if (characterKeys[i].equals(savedChar)) {
                characterSpinner.setSelection(i);
                break;
            }
        }
        updateTexts();
        isModified = false;
        saveBtn.setVisibility(View.GONE);
    }

    private void saveSettings() {
        int threshold = getThresholdFromProgress(thresholdSeek.getProgress());
        int intervalSeconds = getIntervalFromProgress(intervalSeek.getProgress());
        String selectedChar = characterKeys[characterSpinner.getSelectedItemPosition()];

        preferences.edit()
                .putInt(BatteryService.KEY_THRESHOLD, threshold)
                .putInt(BatteryService.KEY_BEEP_INTERVAL_SECONDS, intervalSeconds)
                .putString(BatteryService.KEY_CHARACTER_VOICE, selectedChar)
                .apply();
    }

    private void updateMuteButton(boolean muted) {
        muteBtn.setText(muted ? "Desmutar" : "Silenciar");
        muteBtn.setAlpha(muted ? 0.7f : 1.0f);
    }

    private int getThresholdFromProgress(int progress) { return progress + 5; }
    private int getIntervalFromProgress(int progress) { return (progress + 1) * 5; }

    private void updateBatteryReadout() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        if (batteryStatus == null) return;
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int pct = (level >= 0 && scale > 0) ? (int) ((level * 100f) / scale) : -1;

        batteryText.setText(pct + "%");
        int color = getBatteryColor(pct);
        batteryText.setTextColor(color);
        alertModeText.setText(getAlertLabel(pct));
        alertModeText.setTextColor(color);
        updateBatteryFill(pct);
    }

    private void updateBatteryFill(int pct) {
        int safePct = Math.max(0, Math.min(100, pct));
        Drawable background;
        if (safePct <= 10) background = ContextCompat.getDrawable(this, R.drawable.battery_fill_low);
        else if (safePct <= 30) background = ContextCompat.getDrawable(this, R.drawable.battery_fill_mid);
        else background = ContextCompat.getDrawable(this, R.drawable.battery_fill_high);
        
        batteryFill.setBackground(background);
        
        batteryFill.post(() -> {
            int totalHeight = ((View)batteryFill.getParent()).getHeight();
            float scale = Math.max(0.01f, safePct / 100f);
            batteryFill.getLayoutParams().height = (int) (totalHeight * scale);
            batteryFill.requestLayout();
        });
    }

    private String getAlertLabel(int pct) {
        if (pct <= 10) return "CRÍTICO";
        if (pct <= 30) return "ATENÇÃO";
        return "NORMAL";
    }

    private int getBatteryColor(int pct) {
        if (pct <= 10) return 0xFFFF4D4F;
        if (pct <= 30) return 0xFFF59E0B;
        return 0xFF4ADE80;
    }

    private void playTestBeep() {
        try {
            ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
            tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 300);
            tg.release();
        } catch (Exception e) {
            Toast.makeText(this, "Erro no bip", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(bipReceiver); } catch (Exception ignored) {}
        super.onDestroy();
    }
}