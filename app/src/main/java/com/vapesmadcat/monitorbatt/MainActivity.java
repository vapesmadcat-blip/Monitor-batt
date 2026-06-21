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
    private TextView chargingBolt;
    private SeekBar thresholdSeek;
    private SeekBar intervalSeek;
    private Spinner characterSpinner;
    private SharedPreferences preferences;
    private Button muteBtn;
    private Button saveBtn;
    private Button startBtn;
    private Button stopBtn;
    private Button playVoiceBtn;
    
    private String[] characters = {
        "Nenhum", "Lula", "Bolsonaro", "Goku", "Vegeta", 
        "Faustão", "Silvio Santos", "Homer Simpson",
        "Rock", "Clássico", "Samba", "Reggae", "Sertanejo"
    };
    private String[] characterKeys = {
        "none", "lula", "bolsonaro", "goku", "vegeta", 
        "faustao", "silvio", "homer",
        "rock", "classico", "samba", "reggae", "sertanejo"
    };
    private boolean isModified = false;
    private boolean isCharging = false;
    private AlphaAnimation boltAnimation;

    // Gerenciamento de Áudio
    private MediaPlayer currentMediaPlayer = null;
    private boolean isPlayingPreview = false;

    // Chave para persistência do estado do serviço
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
        chargingBolt = findViewById(R.id.tvChargingBolt);
        thresholdSeek = findViewById(R.id.seekThreshold);
        intervalSeek = findViewById(R.id.seekInterval);
        characterSpinner = findViewById(R.id.spinnerCharacter);
        
        saveBtn = findViewById(R.id.btnSave);
        startBtn = findViewById(R.id.btnStart);
        stopBtn = findViewById(R.id.btnStop);
        muteBtn = findViewById(R.id.btnMute);
        playVoiceBtn = findViewById(R.id.btnPlayVoice);
        Button testBeepBtn = findViewById(R.id.btnTestBeep);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, characters);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        characterSpinner.setAdapter(adapter);

        setupControls();
        setupAnimations();

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
        });

        playVoiceBtn.setOnClickListener(v -> {
            if (isPlayingPreview) {
                stopCurrentAudio();
            } else {
                playVoicePreview();
            }
        });

        testBeepBtn.setOnClickListener(v -> {
            triggerVisualBip();
            playTestBeep();
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(bipReceiver, new IntentFilter("com.vapesmadcat.monitorbatt.BIP_TRIGGERED"), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(bipReceiver, new IntentFilter("com.vapesmadcat.monitorbatt.BIP_TRIGGERED"));
        }

        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        
        updateMuteButton(preferences.getBoolean(BatteryService.KEY_MUTED, false));
        updateBatteryReadout();
        
        boolean shouldBeRunning = preferences.getBoolean(KEY_SERVICE_ENABLED, false);
        boolean isRunning = isServiceRunning(BatteryService.class);
        
        if (shouldBeRunning && !isRunning) {
            startBatteryService();
            updateServiceButtons(true);
        } else {
            updateServiceButtons(isRunning);
        }
    }

    private void setupAnimations() {
        boltAnimation = new AlphaAnimation(0.2f, 1.0f);
        boltAnimation.setDuration(800);
        boltAnimation.setRepeatMode(Animation.REVERSE);
        boltAnimation.setRepeatCount(Animation.INFINITE);
    }

    private void stopCurrentAudio() {
        if (currentMediaPlayer != null) {
            try {
                if (currentMediaPlayer.isPlaying()) {
                    currentMediaPlayer.stop();
                }
                currentMediaPlayer.release();
            } catch (Exception e) {
                Log.e("MainActivity", "Erro ao parar áudio", e);
            }
            currentMediaPlayer = null;
        }
        isPlayingPreview = false;
        playVoiceBtn.setText("▶");
        playVoiceBtn.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.holo_blue_light));
    }

    private void playVoicePreview() {
        // Para qualquer áudio que já esteja tocando
        stopCurrentAudio();

        int pos = characterSpinner.getSelectedItemPosition();
        if (pos < 0 || pos >= characterKeys.length) return;
        
        String character = characterKeys[pos];
        if ("none".equals(character)) {
            Toast.makeText(this, "Nenhuma opção selecionada", Toast.LENGTH_SHORT).show();
            return;
        }

        // Tenta áudio de bateria baixa ou carregamento para o preview
        int resId = getResources().getIdentifier("voice_" + character + "_low", "raw", getPackageName());
        if (resId == 0) resId = getResources().getIdentifier("voice_" + character + "_charging", "raw", getPackageName());
        
        if (resId != 0) {
            try {
                currentMediaPlayer = MediaPlayer.create(this, resId);
                if (currentMediaPlayer != null) {
                    isPlayingPreview = true;
                    playVoiceBtn.setText("■"); // Ícone de Stop
                    playVoiceBtn.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.holo_red_light));

                    currentMediaPlayer.setOnCompletionListener(mp -> {
                        stopCurrentAudio();
                    });
                    currentMediaPlayer.start();
                    Toast.makeText(this, "Ouvindo: " + characters[pos], Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e("MainActivity", "Erro ao tocar preview", e);
                stopCurrentAudio();
            }
        } else {
            Toast.makeText(this, "Áudio de prévia não disponível", Toast.LENGTH_SHORT).show();
        }
    }

    private void startBatteryService() {
        Intent i = new Intent(this, BatteryService.class);
        ContextCompat.startForegroundService(this, i);
    }

    private void stopBatteryService() {
        stopService(new Intent(this, BatteryService.class));
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return false;
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void triggerVisualBip() {
        bipIndicator.setTextColor(Color.RED);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            bipIndicator.setTextColor(Color.WHITE);
        }, 1000);
    }

    private void updateServiceButtons(boolean running) {
        startBtn.setEnabled(!running);
        startBtn.setAlpha(running ? 0.5f : 1.0f);
        stopBtn.setEnabled(running);
        stopBtn.setAlpha(running ? 1.0f : 0.5f);
        statusText.setText(running ? "Monitoramento: ATIVO" : "Monitoramento: DESATIVADO");
        statusText.setTextColor(running ? 0xFF4ADE80 : 0xFFFF4D4F);
    }

    private void setupControls() {
        thresholdSeek.setMax(25); // 5 to 30
        intervalSeek.setMax(11); // 5 to 60 (steps of 5)

        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (isModifiedByCode) return;
                checkChanges();
                // Se mudar o personagem enquanto ouve, para o áudio anterior
                if (isPlayingPreview) {
                    stopCurrentAudio();
                }
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

    private boolean isModifiedByCode = false;

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
        updateTexts();
        isModified = false;
        saveBtn.setVisibility(View.GONE);
        isModifiedByCode = false;
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
    }

    private void updateChargingUI(boolean charging) {
        if (charging) {
            chargingBolt.setVisibility(View.VISIBLE);
            if (chargingBolt.getAnimation() == null) {
                chargingBolt.startAnimation(boltAnimation);
            }
        } else {
            chargingBolt.clearAnimation();
            chargingBolt.setVisibility(View.GONE);
        }
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
    protected void onResume() {
        super.onResume();
        updateBatteryReadout();
        updateServiceButtons(isServiceRunning(BatteryService.class));
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Opcional: parar o áudio se o usuário sair do app
        // stopCurrentAudio();
    }

    @Override
    protected void onDestroy() {
        stopCurrentAudio();
        try { unregisterReceiver(bipReceiver); } catch (Exception ignored) {}
        try { unregisterReceiver(batteryReceiver); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
