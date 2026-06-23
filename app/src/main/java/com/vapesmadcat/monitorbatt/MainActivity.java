package com.vapesmadcat.monitorbatt;

import android.Manifest;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private View batteryFill;
    private TextView batteryText, statusText, thresholdValueText, intervalValueText;
    private TextView alertModeText, bipIndicator, chargingBolt;
    private TextView tvBigPercentage;
    private SeekBar thresholdSeek, intervalSeek;
    private Spinner characterSpinner, spinnerVisualStyle;
    private Switch switchBeep, switchTts, switchCharacterVoice;
    private Button muteBtn, saveBtn, startBtn, stopBtn, playVoiceBtn, btnTestFullMonitoring, testBeepBtn;
    private ImageView ivMascot;

    private SharedPreferences preferences;
    private boolean isModified = false;
    private boolean isCharging = false;
    private AlphaAnimation boltAnimation;

    private MediaPlayer currentMediaPlayer = null;
    private boolean isPlayingPreview = false;
    private boolean isModifiedByCode = false;

    public static final String KEY_SERVICE_ENABLED = "service_enabled";

    private final BroadcastReceiver bipReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            triggerVisualBip();
        }
    };

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateBatteryReadout();
        }
    };

    private final String[] characters = {
        "Nenhum", "Lula", "Bolsonaro", "Goku", "Vegeta",
        "Faustão", "Silvio Santos", "Homer Simpson",
        "Rock", "Clássico", "Samba", "Reggae", "Sertanejo"
    };
    private final String[] characterKeys = {
        "none", "lula", "bolsonaro", "goku", "vegeta",
        "faustao", "silvio", "homer",
        "rock", "classico", "samba", "reggae", "sertanejo"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferences = getSharedPreferences(BatteryService.PREFS_NAME, MODE_PRIVATE);

        // FindViews
        batteryFill = findViewById(R.id.batteryFill);
        batteryText = findViewById(R.id.tvLevel);
        statusText = findViewById(R.id.tvStatus);
        thresholdValueText = findViewById(R.id.tvThresholdValue);
        intervalValueText = findViewById(R.id.tvIntervalValue);
        alertModeText = findViewById(R.id.tvAlertMode);
        bipIndicator = findViewById(R.id.tvBipIndicator);
        chargingBolt = findViewById(R.id.tvChargingBolt);
        thresholdSeek = findViewById(R.id.seekThreshold);
        intervalSeek = findViewById(R.id.seekInterval);
        characterSpinner = findViewById(R.id.spinnerCharacter);
        saveBtn = findViewById(R.id.btnSave);
        startBtn = findViewById(R.id.btnStart);
        stopBtn = findViewById(R.id.btnStop);
        muteBtn = findViewById(R.id.btnMute);
        playVoiceBtn = findViewById(R.id.btnPlayVoice);
        testBeepBtn = findViewById(R.id.btnTestBeep);

        switchBeep = findViewById(R.id.switchBeep);
        switchTts = findViewById(R.id.switchTts);
        switchCharacterVoice = findViewById(R.id.switchCharacterVoice);
        spinnerVisualStyle = findViewById(R.id.spinnerVisualStyle);
        ivMascot = findViewById(R.id.ivMascot);
        tvBigPercentage = findViewById(R.id.tvBigPercentage);
        btnTestFullMonitoring = findViewById(R.id.btnTestFullMonitoring);

        setupCharacterSpinner();
        setupVisualStyleSpinner();
        setupControls();
        setupAnimations();

        // Listeners
        saveBtn.setOnClickListener(v -> {
            saveSettings();
            isModified = false;
            saveBtn.setVisibility(View.GONE);
            Toast.makeText(this, "Configurações salvas!", Toast.LENGTH_SHORT).show();
        });

        startBtn.setOnClickListener(v -> {
            requestNotificationPermissionIfNeeded();
            saveSettings();
            preferences.edit().putBoolean(KEY_SERVICE_ENABLED, true).apply();
            startBatteryService();
            updateServiceButtons(true);
        });

        stopBtn.setOnClickListener(v -> {
            preferences.edit().putBoolean(KEY_SERVICE_ENABLED, false).apply();
            stopBatteryService();
            updateServiceButtons(false);
        });

        muteBtn.setOnClickListener(v -> {
            boolean muted = !preferences.getBoolean(BatteryService.KEY_MUTED, false);
            preferences.edit().putBoolean(BatteryService.KEY_MUTED, muted).apply();
            updateMuteButton(muted);
            if (muted && isPlayingPreview) stopCurrentAudio();
        });

        playVoiceBtn.setOnClickListener(v -> {
            if (isPlayingPreview) stopCurrentAudio();
            else playVoicePreview();
        });

        testBeepBtn.setOnClickListener(v -> {
            triggerVisualBip();
            playTestBeep();
        });

        btnTestFullMonitoring.setOnClickListener(v -> testFullMonitoring());

        // Broadcasts
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(bipReceiver, new IntentFilter("com.vapesmadcat.monitorbatt.BIP_TRIGGERED"), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(bipReceiver, new IntentFilter("com.vapesmadcat.monitorbatt.BIP_TRIGGERED"));
        }
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        updateMuteButton(preferences.getBoolean(BatteryService.KEY_MUTED, false));
        updateBatteryReadout();
        loadSettings();

        boolean shouldBeRunning = preferences.getBoolean(KEY_SERVICE_ENABLED, false);
        boolean isRunning = isServiceRunning(BatteryService.class);

        if (shouldBeRunning && !isRunning) {
            startBatteryService();
            updateServiceButtons(true);
        } else {
            updateServiceButtons(isRunning);
        }
    }

    private void setupCharacterSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, characters);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        characterSpinner.setAdapter(adapter);
    }

    private void setupVisualStyleSpinner() {
        String[] options = {"Pilha Normal", "Mascote"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerVisualStyle.setAdapter(adapter);
    }

    private void setupControls() {
        thresholdSeek.setMax(25);
        intervalSeek.setMax(11);

        SeekBar.OnSeekBarChangeListener seekListener = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) { checkChanges(); updateTexts(); }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        };
        thresholdSeek.setOnSeekBarChangeListener(seekListener);
        intervalSeek.setOnSeekBarChangeListener(seekListener);

        characterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (isModifiedByCode) return;
                checkChanges();
                if (isPlayingPreview) stopCurrentAudio();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        android.widget.CompoundButton.OnCheckedChangeListener switchListener = (buttonView, isChecked) -> checkChanges();
        switchBeep.setOnCheckedChangeListener(switchListener);
        switchTts.setOnCheckedChangeListener(switchListener);
        switchCharacterVoice.setOnCheckedChangeListener(switchListener);

        spinnerVisualStyle.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                checkChanges();
                updateVisualStyle();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    private void setupAnimations() {
        boltAnimation = new AlphaAnimation(0.2f, 1.0f);
        boltAnimation.setDuration(800);
        boltAnimation.setRepeatMode(Animation.REVERSE);
        boltAnimation.setRepeatCount(Animation.INFINITE);
    }

    // ====================== MASCOTE ======================

    private void updateVisualStyle() {
        boolean useMascot = spinnerVisualStyle.getSelectedItemPosition() == 1;

        if (useMascot) {
            batteryFill.setVisibility(View.GONE);
            batteryText.setVisibility(View.GONE);
            chargingBolt.setVisibility(View.GONE);
            alertModeText.setVisibility(View.GONE);
            ivMascot.setVisibility(View.VISIBLE);
            updateMascotImage();
        } else {
            batteryFill.setVisibility(View.VISIBLE);
            batteryText.setVisibility(View.VISIBLE);
            alertModeText.setVisibility(View.VISIBLE);
            ivMascot.setVisibility(View.GONE);
        }
    }

    private void updateMascotImage() {
        if (ivMascot == null || ivMascot.getVisibility() != View.VISIBLE) return;

        Intent batteryStatus = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryStatus == null) return;

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int pct = (level >= 0 && scale > 0) ? (int) ((level * 100f) / scale) : 50;

        int resId;
        if (pct >= 80) resId = R.drawable.battery_mascot_1;
        else if (pct >= 60) resId = R.drawable.battery_mascot_2;
        else if (pct >= 40) resId = R.drawable.battery_mascot_3;
        else if (pct >= 20) resId = R.drawable.battery_mascot_4;
        else resId = R.drawable.battery_mascot_5;

        ivMascot.setImageResource(resId);
    }

    // ====================== PORCENTAGEM GRANDE ======================

    private void updateBigPercentage(int pct) {
        if (tvBigPercentage == null) return;
        tvBigPercentage.setText(pct + "%");
        tvBigPercentage.setTextColor(getBatteryColor(pct));
    }

    // ====================== TESTE COMPLETO ======================

    private void testFullMonitoring() {
        boolean beepEnabled = switchBeep.isChecked();
        boolean ttsEnabled = switchTts.isChecked();
        boolean voiceEnabled = switchCharacterVoice.isChecked();

        Intent batteryStatus = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, 50) : 50;
        int scale = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, 100) : 100;
        int pct = (int) ((level * 100f) / scale);

        updateBigPercentage(pct);

        if (pct <= 10) {
            if (beepEnabled) triggerVisualBip();
            if (ttsEnabled) speakSmartAlert(pct);
            if (voiceEnabled) playCharacterVoice(pct);
        } else if (pct <= 30) {
            if (beepEnabled) triggerVisualBip();
            if (ttsEnabled) speakSmartAlert(pct);
        } else {
            Toast.makeText(this, "Bateria normal (" + pct + "%). Nenhum alerta disparado.", Toast.LENGTH_SHORT).show();
        }
    }

    // ====================== MÉTODOS EXISTENTES ======================

    private void stopCurrentAudio() {
        if (currentMediaPlayer != null) {
            try {
                if (currentMediaPlayer.isPlaying()) currentMediaPlayer.stop();
                currentMediaPlayer.release();
            } catch (Exception e) { Log.e("MainActivity", "Erro ao parar áudio", e); }
            currentMediaPlayer = null;
        }
        isPlayingPreview = false;
        playVoiceBtn.setText("▶");
        playVoiceBtn.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.holo_blue_light));
    }

    private void playVoicePreview() {
        boolean muted = preferences.getBoolean(BatteryService.KEY_MUTED, false);
        if (muted) {
            Toast.makeText(this, "Som silenciado. Desmute para ouvir.", Toast.LENGTH_SHORT).show();
            return;
        }
        stopCurrentAudio();

        int pos = characterSpinner.getSelectedItemPosition();
        if (pos < 0 || pos >= characterKeys.length) return;

        String character = characterKeys[pos];
        if ("none".equals(character)) {
            Toast.makeText(this, "Nenhuma opção selecionada", Toast.LENGTH_SHORT).show();
            return;
        }

        int resId = getResources().getIdentifier("voice_" + character + "_low", "raw", getPackageName());
        if (resId == 0) resId = getResources().getIdentifier("voice_" + character + "_charging", "raw", getPackageName());

        if (resId != 0) {
            try {
                currentMediaPlayer = MediaPlayer.create(this, resId);
                if (currentMediaPlayer != null) {
                    isPlayingPreview = true;
                    playVoiceBtn.setText("■");
                    playVoiceBtn.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.holo_red_light));
                    currentMediaPlayer.setOnCompletionListener(mp -> stopCurrentAudio());
                    currentMediaPlayer.start();
                }
            } catch (Exception e) {
                Log.e("MainActivity", "Erro ao tocar preview", e);
                stopCurrentAudio();
            }
        }
    }

    private void startBatteryService() {
        ContextCompat.startForegroundService(this, new Intent(this, BatteryService.class));
    }

    private void stopBatteryService() {
        stopService(new Intent(this, BatteryService.class));
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return false;
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) return true;
        }
        return false;
    }

    private void triggerVisualBip() {
        bipIndicator.setTextColor(Color.RED);
        new Handler(Looper.getMainLooper()).postDelayed(() -> bipIndicator.setTextColor(Color.WHITE), 1000);
    }

    private void updateServiceButtons(boolean running) {
        startBtn.setEnabled(!running);
        startBtn.setAlpha(running ? 0.5f : 1.0f);
        stopBtn.setEnabled(running);
        stopBtn.setAlpha(running ? 1.0f : 0.5f);
        statusText.setText(running ? "Monitoramento: ATIVO" : "Monitoramento: DESATIVADO");
        statusText.setTextColor(running ? 0xFF4ADE80 : 0xFFFF4D4F);
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
        isModifiedByCode = true;

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

        switchBeep.setChecked(preferences.getBoolean(BatteryService.KEY_BEEP_ENABLED, true));
        switchTts.setChecked(preferences.getBoolean(BatteryService.KEY_TTS_ENABLED, true));
        switchCharacterVoice.setChecked(preferences.getBoolean(BatteryService.KEY_CHARACTER_VOICE_ENABLED, true));

        String visual = preferences.getString(BatteryService.KEY_VISUAL_STYLE, "normal");
        spinnerVisualStyle.setSelection("mascot".equals(visual) ? 1 : 0);

        updateTexts();
        isModified = false;
        saveBtn.setVisibility(View.GONE);
        isModifiedByCode = false;

        updateVisualStyle();
    }

    private void saveSettings() {
        int threshold = getThresholdFromProgress(thresholdSeek.getProgress());
        int intervalSeconds = getIntervalFromProgress(intervalSeek.getProgress());
        String selectedChar = characterKeys[characterSpinner.getSelectedItemPosition()];

        preferences.edit()
                .putInt(BatteryService.KEY_THRESHOLD, threshold)
                .putInt(BatteryService.KEY_BEEP_INTERVAL_SECONDS, intervalSeconds)
                .putString(BatteryService.KEY_CHARACTER_VOICE, selectedChar)
                .putBoolean(BatteryService.KEY_BEEP_ENABLED, switchBeep.isChecked())
                .putBoolean(BatteryService.KEY_TTS_ENABLED, switchTts.isChecked())
                .putBoolean(BatteryService.KEY_CHARACTER_VOICE_ENABLED, switchCharacterVoice.isChecked())
                .putString(BatteryService.KEY_VISUAL_STYLE, spinnerVisualStyle.getSelectedItemPosition() == 1 ? "mascot" : "normal")
                .apply();
    }

    private void updateMuteButton(boolean muted) {
        muteBtn.setText(muted ? "🔇 Desmutar" : "🔊 Silenciar");
        muteBtn.setBackgroundTintList(ContextCompat.getColorStateList(this,
                muted ? android.R.color.holo_red_dark : android.R.color.holo_green_dark));
    }

    private int getThresholdFromProgress(int progress) { return progress + 5; }
    private int getIntervalFromProgress(int progress) { return (progress + 1) * 5; }

    private void updateBatteryReadout() {
        Intent batteryStatus = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryStatus == null) return;

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);

        int pct = (level >= 0 && scale > 0) ? (int) ((level * 100f) / scale) : -1;
        isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;

        batteryText.setText(pct + "%");
        int color = getBatteryColor(pct);
        batteryText.setTextColor(color);
        alertModeText.setText(getAlertLabel(pct, isCharging));
        alertModeText.setTextColor(color);
        updateBatteryFill(pct);
        updateChargingUI(isCharging);

        updateBigPercentage(pct);

        if (ivMascot != null && ivMascot.getVisibility() == View.VISIBLE) {
            updateMascotImage();
        }
    }

    private void updateChargingUI(boolean charging) {
        if (charging) {
            chargingBolt.setVisibility(View.VISIBLE);
            if (chargingBolt.getAnimation() == null) chargingBolt.startAnimation(boltAnimation);
        } else {
            chargingBolt.clearAnimation();
            chargingBolt.setVisibility(View.GONE);
        }
    }

    private void updateBatteryFill(int pct) {
        int safePct = Math.max(0, Math.min(100, pct));
        Drawable background = ContextCompat.getDrawable(this,
                safePct <= 10 ? R.drawable.battery_fill_low :
                safePct <= 30 ? R.drawable.battery_fill_mid : R.drawable.battery_fill_high);

        batteryFill.setBackground(background);
        batteryFill.post(() -> {
            int totalHeight = ((View) batteryFill.getParent()).getHeight();
            batteryFill.getLayoutParams().height = (int) (totalHeight * Math.max(0.01f, safePct / 100f));
            batteryFill.requestLayout();
        });
    }

    private String getAlertLabel(int pct, boolean charging) {
        if (charging && pct >= 100) return "CARGA COMPLETA";
        if (charging) return "CARREGANDO...";
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
        boolean muted = preferences.getBoolean(BatteryService.KEY_MUTED, false);
        if (muted) {
            Toast.makeText(this, "Som silenciado.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
            tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 300);
            tg.release();
        } catch (Exception ignored) {}
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateBatteryReadout();
        updateServiceButtons(isServiceRunning(BatteryService.class));
    }

    @Override
    protected void onDestroy() {
        stopCurrentAudio();
        try { unregisterReceiver(bipReceiver); } catch (Exception ignored) {}
        try { unregisterReceiver(batteryReceiver); } catch (Exception ignored) {}
        super.onDestroy();
    }
}