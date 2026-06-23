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
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private View batteryFill;
    private TextView batteryText, statusText, thresholdValueText, intervalValueText;
    private TextView bipIndicator, chargingBolt;
    private TextView tvBigPercentage;
    private SeekBar thresholdSeek, intervalSeek;
    private Spinner characterSpinner, spinnerVisualStyle;
    private Switch switchBeep, switchTts, switchCharacterVoice;
    private Button muteBtn, saveBtn, startBtn, stopBtn, playVoiceBtn, testBeepBtn, btnSobre;
    private ImageView ivMascot;
    private LinearLayout batteryContainer;   // ← ADICIONADO

    private SharedPreferences preferences;
    private boolean isModified = false;
    private boolean isCharging = false;
    private AlphaAnimation boltAnimation;

    private MediaPlayer currentMediaPlayer = null;
    private boolean isPlayingPreview = false;
    private boolean isModifiedByCode = false;

    private TextToSpeech textToSpeech;
    private boolean ttsInitialized = false;

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

        batteryFill = findViewById(R.id.batteryFill);
        batteryText = findViewById(R.id.tvLevel);
        statusText = findViewById(R.id.tvStatus);
        thresholdValueText = findViewById(R.id.tvThresholdValue);
        intervalValueText = findViewById(R.id.tvIntervalValue);
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
        btnSobre = findViewById(R.id.btnSobre);

        switchBeep = findViewById(R.id.switchBeep);
        switchTts = findViewById(R.id.switchTts);
        switchCharacterVoice = findViewById(R.id.switchCharacterVoice);
        spinnerVisualStyle = findViewById(R.id.spinnerVisualStyle);
        ivMascot = findViewById(R.id.ivMascot);
        tvBigPercentage = findViewById(R.id.tvBigPercentage);
        batteryContainer = findViewById(R.id.batteryContainer);   // ← ADICIONADO

        setupCharacterSpinner();
        setupVisualStyleSpinner();
        setupControls();
        setupAnimations();
        initTextToSpeech();

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

        btnSobre.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AboutActivity.class);
            startActivity(intent);
        });

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

        // ===================== LÓGICA DOS SWITCHES =====================
        switchBeep.setOnCheckedChangeListener((buttonView, isChecked) -> {
            checkChanges();
            if (isChecked) {
                triggerVisualBip();
                playTestBeep();
            }
        });

        switchTts.setOnCheckedChangeListener((buttonView, isChecked) -> {
            checkChanges();
            if (isChecked) {
                speakBatteryStatusExample();
            }
        });

        switchCharacterVoice.setOnCheckedChangeListener((buttonView, isChecked) -> {
            checkChanges();
            if (isChecked) {
                playCharacterVoiceSample();
            }
        });
        // ============================================================
    }

    private void setupAnimations() {
        boltAnimation = new AlphaAnimation(0.2f, 1.0f);
        boltAnimation.setDuration(800);
        boltAnimation.setRepeatMode(Animation.REVERSE);
        boltAnimation.setRepeatCount(Animation.INFINITE);
    }

    private void initTextToSpeech() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }

        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(new Locale("pt", "BR"));
                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    ttsInitialized = true;
                } else {
                    Toast.makeText(this, "Idioma português não disponível no TTS", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Erro ao iniciar o TTS", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateVisualStyle() {
        if (spinnerVisualStyle == null || batteryContainer == null || ivMascot == null) {
            return;
        }

        boolean useMascot = spinnerVisualStyle.getSelectedItemPosition() == 1;

        if (useMascot) {
            batteryContainer.setVisibility(View.GONE);   // esconde a pilha inteira
            ivMascot.setVisibility(View.VISIBLE);
            updateMascotImage();
        } else {
            batteryContainer.setVisibility(View.VISIBLE); // mostra a pilha
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

    // ... (o resto do arquivo continua igual, só copiei as partes que mudaram)
    // [continua com todos os outros métodos iguais que você já tem]