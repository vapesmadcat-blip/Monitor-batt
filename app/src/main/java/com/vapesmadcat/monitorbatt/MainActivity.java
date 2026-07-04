package com.vapesmadcat.monitorbatt;

import android.Manifest;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
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
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private View batteryFill;
    private TextView statusText, thresholdValueText, intervalValueText;
    private TextView bipIndicator, chargingBolt;
    private TextView tvBigPercentage;
    private SeekBar thresholdSeek, intervalSeek;
    private SeekBar voiceLowSeek, voiceCriticalSeek, voiceVeryLowSeek;
    private SeekBar seekVoiceVolume, seekBeepVolume;
    private TextView voiceLowValueText, voiceCriticalValueText, voiceVeryLowValueText;
    private TextView tvVoiceVolumeValue, tvBeepVolumeValue;
    private Spinner characterSpinner, spinnerVisualStyle;
    private SwitchMaterial switchThemeMode, switchBeep, switchTts, switchCharacterVoice;
    private SwitchMaterial switchAutoStart;
    private Button muteBtn, saveBtn, btnMonitor, btnSobre;
    private Button btnTestBeep, btnTestTTS, btnTestVoice;
    private ImageView ivMascot;
    private LinearLayout batteryContainer;
    private LinearLayout layoutBadContactAlert;
    private LinearLayout layoutStatsCard;
    private Button btnDismissBadContact;
    
    // Áreas de Layout para habilitar/desabilitar (cinza)
    private LinearLayout layoutBipArea, layoutVoiceArea;
    private LinearLayout layoutBeepVolume, layoutBeepThreshold, layoutBeepInterval;
    private LinearLayout layoutCharacterSelection, layoutVoiceVolume, layoutVoiceThresholds;
    
    // Gauge de carregamento
    private ImageView ivChargingSpeedIcon;
    private TextView tvChargingRate, tvChargingTimeRemaining, tvChargingSpeedLabel, tvVisualIndicator;
    private TextView tvBatteryCurrent, tvBatteryPower, tvBatteryTemp, tvBatteryVoltage;
    private ProgressBar pbChargingSpeed;
    private BatteryManager batteryManager;
    private int lastBatteryLevel = -1;
    private long lastBatteryCheckTime = 0;
    private double smoothedRatePerMin = -1;
    private Boolean wasCharging = null;

    private SharedPreferences preferences;
    private boolean isModified = false;
    private boolean isCharging = false;
    private AlphaAnimation boltAnimation;

    private MediaPlayer currentMediaPlayer = null;
    private boolean isPlayingPreview = false;
    private boolean isSwitchUpdatingByCode = false;

    private final Handler volumePreviewHandler = new Handler(Looper.getMainLooper());
    private Runnable volumePreviewRunnable = null;

    private TextToSpeech textToSpeech;
    private boolean ttsInitialized = false;

    private static final String PREFS_NAME = "monitor_batt_prefs";
    public static final String KEY_SERVICE_ENABLED = "service_enabled";
    public static final String KEY_DARK_MODE = "dark_mode_enabled";
    public static final String KEY_AUTO_START_ON_BOOT = "auto_start_on_boot";
    // Removido para usar a constante do BatteryService

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

    private final BroadcastReceiver badContactReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            showBadContactAlert();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        // Modo escuro padrão obrigatório
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        
        setContentView(R.layout.activity_main);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // Vincular views
        batteryFill = findViewById(R.id.batteryFill);
        statusText = findViewById(R.id.tvStatus);
        thresholdValueText = findViewById(R.id.tvThresholdValue);
        intervalValueText = findViewById(R.id.tvIntervalValue);
        bipIndicator = findViewById(R.id.tvBipIndicator);
        chargingBolt = findViewById(R.id.tvChargingBolt);
        thresholdSeek = findViewById(R.id.seekThreshold);
        intervalSeek = findViewById(R.id.seekInterval);
        characterSpinner = findViewById(R.id.spinnerCharacter);
        saveBtn = findViewById(R.id.btnSave);
        btnMonitor = findViewById(R.id.btnMonitor);
        muteBtn = findViewById(R.id.btnMute);
        btnSobre = findViewById(R.id.btnSobre);
        btnTestBeep = findViewById(R.id.btnTestBeep);
        btnTestTTS = findViewById(R.id.btnTestTTS);
        btnTestVoice = findViewById(R.id.btnTestVoice);

        switchBeep = findViewById(R.id.switchBeep);
        switchTts = findViewById(R.id.switchTts);
        switchCharacterVoice = findViewById(R.id.switchCharacterVoice);
        switchAutoStart = findViewById(R.id.switchAutoStart);
        spinnerVisualStyle = findViewById(R.id.spinnerVisualStyle);
        ivMascot = findViewById(R.id.ivMascot);
        tvBigPercentage = findViewById(R.id.tvBigPercentage);
        batteryContainer = findViewById(R.id.batteryContainer);
        voiceLowSeek = findViewById(R.id.seekVoiceLow);
        voiceCriticalSeek = findViewById(R.id.seekVoiceCritical);
        voiceVeryLowSeek = findViewById(R.id.seekVoiceVeryLow);
        voiceLowValueText = findViewById(R.id.tvVoiceLowValue);
        voiceCriticalValueText = findViewById(R.id.tvVoiceCriticalValue);
        voiceVeryLowValueText = findViewById(R.id.tvVoiceVeryLowValue);
        seekVoiceVolume = findViewById(R.id.seekVoiceVolume);
        tvVoiceVolumeValue = findViewById(R.id.tvVoiceVolumeValue);
        seekBeepVolume = findViewById(R.id.seekBeepVolume);
        tvBeepVolumeValue = findViewById(R.id.tvBeepVolumeValue);
        
        layoutBadContactAlert = findViewById(R.id.layoutBadContactAlert);
        layoutStatsCard = findViewById(R.id.layoutStatsCard);
        btnDismissBadContact = findViewById(R.id.btnDismissBadContact);
        
        // Layouts de grupo
        layoutBipArea = findViewById(R.id.layoutBipArea);
        layoutVoiceArea = findViewById(R.id.layoutVoiceArea);
        layoutBeepVolume = findViewById(R.id.layoutBeepVolume);
        layoutBeepThreshold = findViewById(R.id.layoutBeepThreshold);
        layoutBeepInterval = findViewById(R.id.layoutBeepInterval);
        layoutCharacterSelection = findViewById(R.id.layoutCharacterSelection);
        layoutVoiceVolume = findViewById(R.id.layoutVoiceVolume);
        layoutVoiceThresholds = findViewById(R.id.layoutVoiceThresholds);
        
        tvChargingSpeedLabel = findViewById(R.id.tvChargingSpeedLabel);
        tvVisualIndicator = findViewById(R.id.tvVisualIndicator);
        pbChargingSpeed = findViewById(R.id.pbChargingSpeed);
        tvBatteryCurrent = findViewById(R.id.tvBatteryCurrent);
        tvBatteryPower = findViewById(R.id.tvBatteryPower);
        tvBatteryTemp = findViewById(R.id.tvBatteryTemp);
        tvBatteryVoltage = findViewById(R.id.tvBatteryVoltage);
        batteryManager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);

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

        btnMonitor.setOnClickListener(v -> {
            boolean isRunning = isServiceRunning(BatteryService.class);
            if (isRunning) {
                preferences.edit().putBoolean(KEY_SERVICE_ENABLED, false).apply();
                stopBatteryService();
                updateMonitorButton(false);
                statusText.setText("Monitoramento: DESATIVADO");
                statusText.setTextColor(0xFFFF4D4F);
            } else {
                requestNotificationPermissionIfNeeded();
                saveSettings();
                preferences.edit().putBoolean(KEY_SERVICE_ENABLED, true).apply();
                startBatteryService();
                updateMonitorButton(true);
                statusText.setText("Monitoramento: ATIVO");
                statusText.setTextColor(0xFF4ADE80);
            }
        });

        muteBtn.setOnClickListener(v -> {
            boolean muted = !preferences.getBoolean(BatteryService.KEY_MUTED, false);
            preferences.edit().putBoolean(BatteryService.KEY_MUTED, muted).apply();
            updateMuteButton(muted);
            if (muted && isPlayingPreview) stopCurrentAudio();
        });

        btnSobre.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, AboutActivity.class)));
        btnTestBeep.setOnClickListener(v -> { playTestBeep(); triggerVisualBip(); });
        btnTestTTS.setOnClickListener(v -> speakBatteryStatusExample());
        btnTestVoice.setOnClickListener(v -> playCharacterVoiceSample());
        btnDismissBadContact.setOnClickListener(v -> layoutBadContactAlert.setVisibility(View.GONE));

        loadSettings();
        updateMonitorButton(isServiceRunning(BatteryService.class));
        updateMuteButton(preferences.getBoolean(BatteryService.KEY_MUTED, false));
        updateVisualStyle();
        updateTexts();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        registerReceiver(bipReceiver, new IntentFilter("com.vapesmadcat.monitorbatt.BIP_TRIGGERED"), Context.RECEIVER_NOT_EXPORTED);
        registerReceiver(badContactReceiver, new IntentFilter("com.vapesmadcat.monitorbatt.BAD_CONTACT_DETECTED"), Context.RECEIVER_NOT_EXPORTED);
        updateBatteryReadout();
        updateMonitorButton(isServiceRunning(BatteryService.class));
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(batteryReceiver);
            unregisterReceiver(bipReceiver);
            unregisterReceiver(badContactReceiver);
        } catch (Exception e) {
            Log.e("MainActivity", "Erro ao desregistrar receivers", e);
        }
    }

    private void setupControls() {
        thresholdSeek.setMax(25);
        intervalSeek.setMax(11);
        voiceLowSeek.setMax(80);
        voiceCriticalSeek.setMax(80);
        voiceVeryLowSeek.setMax(80);
        seekVoiceVolume.setMax(100);
        seekBeepVolume.setMax(100);

        SeekBar.OnSeekBarChangeListener seekListener = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) {
                    checkChanges();
                    if (s == voiceLowSeek || s == voiceCriticalSeek || s == voiceVeryLowSeek) clampVoiceThresholds(s);
                    updateTexts();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {
                if (s == thresholdSeek || s == seekBeepVolume) scheduleVolumePreviewBeep();
            }
        };

        thresholdSeek.setOnSeekBarChangeListener(seekListener);
        intervalSeek.setOnSeekBarChangeListener(seekListener);
        voiceLowSeek.setOnSeekBarChangeListener(seekListener);
        voiceCriticalSeek.setOnSeekBarChangeListener(seekListener);
        voiceVeryLowSeek.setOnSeekBarChangeListener(seekListener);
        seekBeepVolume.setOnSeekBarChangeListener(seekListener);

        seekVoiceVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) {
                    tvVoiceVolumeValue.setText(p + "%");
                    checkChanges();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) { 
                if (switchCharacterVoice.isChecked()) {
                    playCharacterVoiceSample(); 
                } else if (switchTts.isChecked()) {
                    speakBatteryStatusExample();
                }
            }
        });

        switchBeep.setOnCheckedChangeListener((b, isChecked) -> {
            if (isSwitchUpdatingByCode) return;
            checkChanges();
            updateBipAreaState(isChecked);
            if (isChecked) { triggerVisualBip(); playTestBeep(); }
        });

        switchTts.setOnCheckedChangeListener((b, isChecked) -> {
            if (isSwitchUpdatingByCode) return;
            checkChanges();
            if (isChecked) {
                if (switchCharacterVoice.isChecked()) {
                    isSwitchUpdatingByCode = true;
                    switchCharacterVoice.setChecked(false);
                    isSwitchUpdatingByCode = false;
                }
                speakBatteryStatusExample();
            }
            updateVoiceAreaState();
        });

        switchCharacterVoice.setOnCheckedChangeListener((b, isChecked) -> {
            if (isSwitchUpdatingByCode) return;
            checkChanges();
            if (isChecked) {
                if (switchTts.isChecked()) {
                    isSwitchUpdatingByCode = true;
                    switchTts.setChecked(false);
                    isSwitchUpdatingByCode = false;
                }
                playCharacterVoiceSample();
            }
            updateVoiceAreaState();
        });

        switchAutoStart.setOnCheckedChangeListener((b, isChecked) -> {
            if (isSwitchUpdatingByCode) return;
            checkChanges();
            preferences.edit().putBoolean(KEY_AUTO_START_ON_BOOT, isChecked).apply();
        });
        
        spinnerVisualStyle.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { updateVisualStyle(); checkChanges(); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        
        characterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (isSwitchUpdatingByCode) return;
                checkChanges();
                if (isPlayingPreview) stopCurrentAudio();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    private void updateBipAreaState(boolean enabled) {
        float alpha = enabled ? 1.0f : 0.4f;
        seekBeepVolume.setEnabled(enabled);
        thresholdSeek.setEnabled(enabled);
        intervalSeek.setEnabled(enabled);
        btnTestBeep.setEnabled(enabled);
        
        // Acinzentar tudo dentro da área do bip, exceto o switch principal
        updateAlphaRecursively(layoutBeepVolume, alpha);
        updateAlphaRecursively(layoutBeepThreshold, alpha);
        updateAlphaRecursively(layoutBeepInterval, alpha);
        updateAlphaRecursively(btnTestBeep, alpha);
    }

    private void updateAlphaRecursively(View view, float alpha) {
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                updateAlphaRecursively(group.getChildAt(i), alpha);
            }
        } else if (view instanceof TextView) {
            view.setAlpha(alpha);
        }
    }

    private void updateVoiceAreaState() {
        boolean tts = switchTts.isChecked();
        boolean charVoice = switchCharacterVoice.isChecked();
        boolean anyVoice = tts || charVoice;
        
        float alphaAny = anyVoice ? 1.0f : 0.4f;
        float alphaChar = charVoice ? 1.0f : 0.4f;
        
        seekVoiceVolume.setEnabled(anyVoice);
        voiceLowSeek.setEnabled(anyVoice);
        voiceCriticalSeek.setEnabled(anyVoice);
        voiceVeryLowSeek.setEnabled(anyVoice);
        characterSpinner.setEnabled(charVoice);
        btnTestTTS.setEnabled(true);
        btnTestVoice.setEnabled(true);
        
        // Lógica de desabilitação visual (acinzentar)
        switchTts.setAlpha(charVoice ? 0.4f : 1.0f);
        switchCharacterVoice.setAlpha(tts ? 0.4f : 1.0f);
        
        updateAlphaRecursively(layoutVoiceVolume, alphaAny);
        updateAlphaRecursively(layoutVoiceThresholds, alphaAny);
        updateAlphaRecursively(layoutCharacterSelection, alphaChar);
        updateAlphaRecursively(btnTestTTS, 1.0f);
        updateAlphaRecursively(btnTestVoice, 1.0f);
    }

    private void loadSettings() {
        isSwitchUpdatingByCode = true;
        int threshold = preferences.getInt(BatteryService.KEY_THRESHOLD, BatteryService.DEFAULT_THRESHOLD);
        int intervalSeconds = preferences.getInt(BatteryService.KEY_BEEP_INTERVAL_SECONDS, BatteryService.DEFAULT_BEEP_INTERVAL_SECONDS);
        String savedChar = preferences.getString(BatteryService.KEY_CHARACTER_VOICE, "none");
        int voiceLow = preferences.getInt(BatteryService.KEY_VOICE_LOW_THRESHOLD, BatteryService.DEFAULT_VOICE_LOW_THRESHOLD);
        int voiceCritical = preferences.getInt(BatteryService.KEY_VOICE_CRITICAL_THRESHOLD, BatteryService.DEFAULT_VOICE_CRITICAL_THRESHOLD);
        int voiceVeryLow = preferences.getInt(BatteryService.KEY_VOICE_VERYLOW_THRESHOLD, BatteryService.DEFAULT_VOICE_VERYLOW_THRESHOLD);
        int voiceVolume = preferences.getInt(BatteryService.KEY_VOICE_VOLUME, BatteryService.DEFAULT_VOICE_VOLUME);
        int beepVolume = preferences.getInt(BatteryService.KEY_BEEP_VOLUME, 80);

        thresholdSeek.setProgress(Math.max(0, threshold - 5));
        intervalSeek.setProgress(Math.max(0, (intervalSeconds / 5) - 1));
        voiceLowSeek.setProgress(Math.min(80, voiceLow));
        voiceCriticalSeek.setProgress(Math.min(80, voiceCritical));
        voiceVeryLowSeek.setProgress(Math.min(80, voiceVeryLow));
        seekVoiceVolume.setProgress(Math.min(100, voiceVolume));
        seekBeepVolume.setProgress(Math.min(100, beepVolume));

        for (int i = 0; i < characterKeys.length; i++) {
            if (characterKeys[i].equals(savedChar)) { characterSpinner.setSelection(i); break; }
        }

        switchBeep.setChecked(preferences.getBoolean(BatteryService.KEY_BEEP_ENABLED, false));
        switchTts.setChecked(preferences.getBoolean(BatteryService.KEY_TTS_ENABLED, false));
        switchCharacterVoice.setChecked(preferences.getBoolean(BatteryService.KEY_CHARACTER_VOICE_ENABLED, false));
        switchAutoStart.setChecked(preferences.getBoolean(KEY_AUTO_START_ON_BOOT, false));
        
        updateBipAreaState(switchBeep.isChecked());
        updateVoiceAreaState();
        isSwitchUpdatingByCode = false;
    }

    private void saveSettings() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(BatteryService.KEY_THRESHOLD, getThresholdFromProgress(thresholdSeek.getProgress()));
        editor.putInt(BatteryService.KEY_BEEP_INTERVAL_SECONDS, getIntervalFromProgress(intervalSeek.getProgress()));
        editor.putBoolean(BatteryService.KEY_BEEP_ENABLED, switchBeep.isChecked());
        editor.putBoolean(BatteryService.KEY_TTS_ENABLED, switchTts.isChecked());
        editor.putBoolean(BatteryService.KEY_CHARACTER_VOICE_ENABLED, switchCharacterVoice.isChecked());
        editor.putString(BatteryService.KEY_CHARACTER_VOICE, characterKeys[characterSpinner.getSelectedItemPosition()]);
        editor.putInt(BatteryService.KEY_VOICE_LOW_THRESHOLD, voiceLowSeek.getProgress());
        editor.putInt(BatteryService.KEY_VOICE_CRITICAL_THRESHOLD, voiceCriticalSeek.getProgress());
        editor.putInt(BatteryService.KEY_VOICE_VERYLOW_THRESHOLD, voiceVeryLowSeek.getProgress());
        editor.putInt(BatteryService.KEY_VOICE_VOLUME, seekVoiceVolume.getProgress());
        editor.putInt(BatteryService.KEY_BEEP_VOLUME, seekBeepVolume.getProgress());
        editor.apply();
    }

    private int getThresholdFromProgress(int p) { return p + 5; }
    private int getIntervalFromProgress(int p) { return (p + 1) * 5; }

    private void updateTexts() {
        thresholdValueText.setText(getThresholdFromProgress(thresholdSeek.getProgress()) + "%");
        intervalValueText.setText(getIntervalFromProgress(intervalSeek.getProgress()) + " seg");
        voiceLowValueText.setText(voiceLowSeek.getProgress() + "%");
        voiceCriticalValueText.setText(voiceCriticalSeek.getProgress() + "%");
        voiceVeryLowValueText.setText(voiceVeryLowSeek.getProgress() + "%");
        tvVoiceVolumeValue.setText(seekVoiceVolume.getProgress() + "%");
        tvBeepVolumeValue.setText(seekBeepVolume.getProgress() + "%");
    }

    private void checkChanges() { isModified = true; saveBtn.setVisibility(View.VISIBLE); autoPersistSettings(); }
    private void autoPersistSettings() { saveSettings(); isModified = false; saveBtn.setVisibility(View.GONE); }

    private void playTestBeep() {
        int vol = seekBeepVolume.getProgress();
        try {
            ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_MUSIC, vol);
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 200);
            new Handler(Looper.getMainLooper()).postDelayed(tg::release, 500);
        } catch (Exception e) { Log.e("MainActivity", "Erro bip", e); }
    }

    private void scheduleVolumePreviewBeep() {
        volumePreviewHandler.removeCallbacksAndMessages(null);
        volumePreviewHandler.postDelayed(this::playTestBeep, 300);
    }

    private void playCharacterVoiceSample() {
        stopCurrentAudio();
        int pos = characterSpinner.getSelectedItemPosition();
        if (pos <= 0) return;
        String character = characterKeys[pos];
        int resId = getResources().getIdentifier("voice_" + character + "_low", "raw", getPackageName());
        if (resId == 0) resId = getResources().getIdentifier("voice_" + character + "_charging", "raw", getPackageName());
        if (resId != 0) {
            try {
                currentMediaPlayer = MediaPlayer.create(this, resId);
                if (currentMediaPlayer != null) {
                    float vol = seekVoiceVolume.getProgress() / 100f;
                    currentMediaPlayer.setVolume(vol, vol);
                    currentMediaPlayer.setOnCompletionListener(mp -> { mp.release(); currentMediaPlayer = null; });
                    currentMediaPlayer.start();
                }
            } catch (Exception e) { Log.e("MainActivity", "Erro voz", e); }
        }
    }

    private void stopCurrentAudio() {
        if (currentMediaPlayer != null) {
            try { if (currentMediaPlayer.isPlaying()) currentMediaPlayer.stop(); currentMediaPlayer.release(); } catch (Exception e) {}
            currentMediaPlayer = null;
        }
    }

    private void initTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(new Locale("pt", "BR"));
                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) ttsInitialized = true;
            }
        });
    }

    private void speakBatteryStatusExample() {
        if (!ttsInitialized) return;
        if (textToSpeech.isSpeaking()) textToSpeech.stop();
        
        // Tentar selecionar uma voz masculina disponível no sistema
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                for (android.speech.tts.Voice v : textToSpeech.getVoices()) {
                    if (v.getName().toLowerCase().contains("male") || v.getName().toLowerCase().contains("pt-br-x-abd-local")) {
                        textToSpeech.setVoice(v);
                        break;
                    }
                }
            } catch (Exception e) { Log.e("MainActivity", "Erro ao buscar vozes", e); }
        }
        
        textToSpeech.setPitch(0.8f); // Um pouco mais grave para soar masculino
        textToSpeech.setSpeechRate(1.0f); // Ritmo normal
        
        android.os.Bundle params = new android.os.Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, seekVoiceVolume.getProgress() / 100f);
        textToSpeech.speak("TTS ativado.", TextToSpeech.QUEUE_FLUSH, params, "ex");
    }

    private void updateBatteryReadout() {
        Intent batteryStatus = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryStatus == null) return;
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int pct = (int) ((level * 100f) / scale);
        updateBigPercentage(pct);
        
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
        chargingBolt.setVisibility(isCharging ? View.VISIBLE : View.GONE);
        if (isCharging) chargingBolt.startAnimation(boltAnimation); else chargingBolt.clearAnimation();

        // Atualizar texto de status
        boolean isRunning = isServiceRunning(BatteryService.class);
        if (isRunning) {
            statusText.setText("Monitoramento: ATIVO");
            statusText.setTextColor(0xFF4ADE80);
        } else {
            statusText.setText("Monitoramento: DESATIVADO");
            statusText.setTextColor(0xFFFF4D4F);
        }

        // Corrigir altura da pilha (usando proporção do container de 440dp)
        int maxHeightPx = (int) (440 * getResources().getDisplayMetrics().density);
        int fillHeight = (int) (pct / 100f * (maxHeightPx - (16 * getResources().getDisplayMetrics().density))); 
        batteryFill.getLayoutParams().height = fillHeight;
        batteryFill.requestLayout();
        batteryFill.setBackgroundResource(pct > 60 ? R.drawable.battery_fill_high : (pct > 20 ? R.drawable.battery_fill_mid : R.drawable.battery_fill_low));
        
        // Atualizar estatísticas detalhadas
        updateStats(batteryStatus);
        updateMascotImage();
    }

    private void updateStats(Intent intent) {
        int voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
        int temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
        
        tvBatteryVoltage.setText(String.format(Locale.US, "%.2f V", voltage / 1000.0));
        tvBatteryTemp.setText(String.format(Locale.US, "%.1f °C", temperature / 10.0));
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            long currentNow = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            tvBatteryCurrent.setText(currentNow / 1000 + " mA");
            
            // Cálculo aproximado de potência (P = V * I)
            double power = (voltage / 1000.0) * (Math.abs(currentNow) / 1000000.0);
            tvBatteryPower.setText(String.format(Locale.US, "%.2f W", power));
        }
        
        tvVisualIndicator.setText(isCharging ? "⚡" : "🔋");
        // tvChargingRate e tvChargingTimeRemaining foram removidos do layout para simplificar
    }

    private void updateBigPercentage(int pct) { 
        tvBigPercentage.setText(pct + "%"); 
        boolean isMascot = spinnerVisualStyle.getSelectedItemPosition() == 1;
        if (isMascot) {
            tvBigPercentage.setTextColor(getBatteryColor(pct)); 
        } else {
            // Na pilha, usar Amarelo para destacar no fundo (especialmente no vermelho)
            tvBigPercentage.setTextColor(0xFFFACC15); // Yellow-400
        }
    }
    private int getBatteryColor(int pct) { return pct > 60 ? 0xFF4ADE80 : (pct > 20 ? 0xFFF59E0B : 0xFFEF4444); }
    
    private void updateMascotImage() {
        if (ivMascot == null || ivMascot.getVisibility() != View.VISIBLE) return;
        Intent batteryStatus = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryStatus == null) return;
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int pct = (int) ((level * 100f) / scale);
        int resId;
        if (pct >= 80) resId = R.drawable.battery_mascot_1;
        else if (pct >= 60) resId = R.drawable.battery_mascot_2;
        else if (pct >= 40) resId = R.drawable.battery_mascot_3;
        else if (pct >= 20) resId = R.drawable.battery_mascot_4;
        else resId = R.drawable.battery_mascot_5;
        ivMascot.setImageResource(resId);
    }
    
    private void setupCharacterSpinner() { characterSpinner.setAdapter(new ArrayAdapter<>(this, R.layout.spinner_item, characters)); }
    private void setupVisualStyleSpinner() { spinnerVisualStyle.setAdapter(new ArrayAdapter<>(this, R.layout.spinner_item, new String[]{"Clássico", "Mascote"})); }
    private void applyNightMode(boolean dark) { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES); }
    private void startBatteryService() { ContextCompat.startForegroundService(this, new Intent(this, BatteryService.class)); }
    private void stopBatteryService() { stopService(new Intent(this, BatteryService.class)); }
    private boolean isServiceRunning(Class<?> cls) { ActivityManager m = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE); for (ActivityManager.RunningServiceInfo s : m.getRunningServices(Integer.MAX_VALUE)) if (cls.getName().equals(s.service.getClassName())) return true; return false; }
    private void triggerVisualBip() { bipIndicator.setAlpha(1.0f); new Handler(Looper.getMainLooper()).postDelayed(() -> bipIndicator.setAlpha(0.3f), 1000); }
    private void updateMuteButton(boolean muted) { muteBtn.setText(muted ? "🔊 ATIVAR SOM" : "🔇 SILENCIAR"); muteBtn.setBackgroundTintList(ContextCompat.getColorStateList(this, muted ? R.color.green : R.color.button_secondary_background)); }
    private void showBadContactAlert() { layoutBadContactAlert.setVisibility(View.VISIBLE); }
    private void updateVisualStyle() { boolean mascot = spinnerVisualStyle.getSelectedItemPosition() == 1; batteryContainer.setVisibility(mascot ? View.GONE : View.VISIBLE); ivMascot.setVisibility(mascot ? View.VISIBLE : View.GONE); updateMascotImage(); }
    private void setupAnimations() { boltAnimation = new AlphaAnimation(0.2f, 1.0f); boltAnimation.setDuration(800); boltAnimation.setRepeatMode(Animation.REVERSE); boltAnimation.setRepeatCount(Animation.INFINITE); }
    private void requestNotificationPermissionIfNeeded() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101); }
    private void clampVoiceThresholds(SeekBar s) { int l = voiceLowSeek.getProgress(), c = voiceCriticalSeek.getProgress(), v = voiceVeryLowSeek.getProgress(); if (s == voiceLowSeek) { if (c > l) voiceCriticalSeek.setProgress(l); if (v > voiceCriticalSeek.getProgress()) voiceVeryLowSeek.setProgress(voiceCriticalSeek.getProgress()); } else if (s == voiceCriticalSeek) { if (c > l) voiceCriticalSeek.setProgress(l); if (v > c) voiceVeryLowSeek.setProgress(c); } else if (s == voiceVeryLowSeek && v > c) voiceVeryLowSeek.setProgress(c); }
    
    private void updateMonitorButton(boolean isRunning) {
        if (btnMonitor == null) return;
        if (isRunning) {
            btnMonitor.setText("⏹ PARAR MONITOR");
            btnMonitor.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.red));
        } else {
            btnMonitor.setText("▶ ATIVAR MONITOR");
            btnMonitor.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.green));
        }
    }
}
