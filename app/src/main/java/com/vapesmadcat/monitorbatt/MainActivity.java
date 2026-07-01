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
    private SeekBar seekVoiceVolume;
    private TextView voiceLowValueText, voiceCriticalValueText, voiceVeryLowValueText;
    private TextView tvVoiceVolumeValue;
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
    // Flag para evitar que os listeners de switch disparem uns aos outros
    private boolean isSwitchUpdatingByCode = false;

    private TextToSpeech textToSpeech;
    private boolean ttsInitialized = false;

    private static final String PREFS_NAME = "monitor_batt_prefs";
    public static final String KEY_SERVICE_ENABLED = "service_enabled";
    public static final String KEY_DARK_MODE = "dark_mode_enabled";
    public static final String KEY_AUTO_START_ON_BOOT = "auto_start_on_boot";

    private final Random random = new Random();
    private final Queue<String> dischargingTipsQueue = new ArrayDeque<>();
    private final Queue<String> chargingTipsQueue = new ArrayDeque<>();
    private static final List<String> DISCHARGING_TIPS = Arrays.asList(
            "Reduza o brilho da tela para economizar bateria rapidamente.",
            "Prefira Wi-Fi estável ao invés de sinal móvel fraco.",
            "Desative Bluetooth, GPS e NFC quando não estiver usando.",
            "Feche apps em segundo plano que fazem sincronização contínua.",
            "Ative o modo de economia de energia em níveis críticos.",
            "Evite vídeos longos em brilho máximo fora da tomada.",
            "Use tema escuro em telas OLED para reduzir consumo.",
            "Configure bloqueio automático da tela em tempo curto.",
            "Desative atualização automática de apps fora do carregador.",
            "Baixe músicas e mapas antes de sair de casa.",
            "Evite widgets em excesso atualizando toda hora.",
            "Mantenha apps e sistema atualizados para melhor eficiência.",
            "Use carregadores certificados para reduzir perdas de energia.",
            "Remova capas muito quentes durante uso pesado.",
            "Evite deixar muitos apps com permissão de localização constante.",
            "Reduza vibração e feedback tátil quando a bateria estiver baixa.",
            "Limite notificações de apps que não são essenciais.",
            "Quando possível, prefira mensagens de texto a chamadas de vídeo longas.",
            "Se não houver rede, ative modo avião temporariamente.",
            "Economizar bateria também aumenta a vida útil do aparelho."
    );
    private static final List<String> CHARGING_TIPS = Arrays.asList(
            "Prefira manter a carga entre 20% e 80% no uso diário.",
            "Evite usar o aparelho em jogos pesados enquanto carrega.",
            "Carregue em local ventilado para reduzir aquecimento.",
            "Use carregador e cabo de boa qualidade e certificados.",
            "Desconecte após carga completa para reduzir estresse térmico.",
            "Se possível, retire capas grossas durante carregamento intenso.",
            "Evite deixar o celular descarregar até 0% com frequência.",
            "Cargas curtas ao longo do dia podem ser melhores que ciclos extremos.",
            "Mantenha a porta USB limpa para evitar mau contato e perdas.",
            "Evite exposição direta ao sol durante o carregamento.",
            "Carregamento noturno é mais seguro com carregador original.",
            "Evite dobrar o cabo próximo ao conector para aumentar durabilidade.",
            "Não cubra o aparelho enquanto carrega para não acumular calor.",
            "Uma bateria mais fria tende a durar mais ciclos.",
            "Desative recursos pesados durante carga para ganhar eficiência.",
            "Sustentabilidade: carregue fora de horários de pico quando possível.",
            "Economia de energia: retire da tomada ao terminar de carregar.",
            "Acompanhe temperatura: calor excessivo acelera desgaste da bateria.",
            "Prefira tomadas estáveis e evite adaptadores ruins.",
            "Boa prática: mantenha o sistema atualizado para gestão térmica melhor."
    );

    // ----------------------------------------------------------------
    // Receiver para piscar o indicador de bip
    // ----------------------------------------------------------------
    private final BroadcastReceiver bipReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            triggerVisualBip();
        }
    };

    // ----------------------------------------------------------------
    // Receiver para atualizar leitura de bateria
    // ----------------------------------------------------------------
    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateBatteryReadout();
        }
    };

    // ----------------------------------------------------------------
    // Receiver para alerta de mau contato vindo do BatteryService
    // ----------------------------------------------------------------
    private final BroadcastReceiver badContactReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            showBadContactAlert();
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
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        applyNightMode(getStoredDarkModeEnabled(preferences));
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

        switchThemeMode = findViewById(R.id.switchThemeMode);
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
        layoutBadContactAlert = findViewById(R.id.layoutBadContactAlert);
        layoutStatsCard = findViewById(R.id.layoutStatsCard);
        btnDismissBadContact = findViewById(R.id.btnDismissBadContact);
        
        // Gauge de carregamento
        ivChargingSpeedIcon = findViewById(R.id.ivChargingSpeedIcon);
        tvChargingRate = findViewById(R.id.tvChargingRate);
        tvChargingTimeRemaining = findViewById(R.id.tvChargingTimeRemaining);
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

        spinnerVisualStyle.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateVisualStyle();
                checkChanges();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        setupThemeToggle();
        setupControls();
        setupAnimations();
        initTextToSpeech();

        // Botão Salvar
        saveBtn.setOnClickListener(v -> {
            saveSettings();
            isModified = false;
            saveBtn.setVisibility(View.GONE);
            Toast.makeText(this, "Configurações salvas!", Toast.LENGTH_SHORT).show();
        });

        // Botão Monitor
        btnMonitor.setOnClickListener(v -> {
            boolean isRunning = isServiceRunning(BatteryService.class);
            if (isRunning) {
                preferences.edit().putBoolean(KEY_SERVICE_ENABLED, false).apply();
                stopBatteryService();
                updateMonitorButton(false);
                statusText.setText("Monitoramento: DESATIVADO");
                statusText.setTextColor(0xFFFF4D4F);
                Toast.makeText(this, "Monitor desativado", Toast.LENGTH_SHORT).show();
            } else {
                requestNotificationPermissionIfNeeded();
                saveSettings();
                preferences.edit().putBoolean(KEY_SERVICE_ENABLED, true).apply();
                startBatteryService();
                updateMonitorButton(true);
                statusText.setText("Monitoramento: ATIVO");
                statusText.setTextColor(0xFF4ADE80);
                Toast.makeText(this, "Monitor ativado", Toast.LENGTH_SHORT).show();
            }
        });

        // Botão Silenciar
        muteBtn.setOnClickListener(v -> {
            boolean muted = !preferences.getBoolean(BatteryService.KEY_MUTED, false);
            preferences.edit().putBoolean(BatteryService.KEY_MUTED, muted).apply();
            updateMuteButton(muted);
            if (muted && isPlayingPreview) stopCurrentAudio();
        });

        // Botão Sobre
        btnSobre.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, AboutActivity.class)));

        // Botões de teste
        btnTestBeep.setOnClickListener(v -> {
            playTestBeep();
            triggerVisualBip();
            Toast.makeText(this, "🔊 Testando Bip...", Toast.LENGTH_SHORT).show();
        });

        btnTestTTS.setOnClickListener(v -> {
            speakBatteryStatusExample();
            Toast.makeText(this, "🗣️ Testando TTS...", Toast.LENGTH_SHORT).show();
        });

        btnTestVoice.setOnClickListener(v -> {
            playCharacterVoiceSample();
            Toast.makeText(this, "🎭 Testando Voz do Personagem...", Toast.LENGTH_SHORT).show();
        });

        // Botão fechar alerta de mau contato
        btnDismissBadContact.setOnClickListener(v -> {
            layoutBadContactAlert.setVisibility(View.GONE);
        });

        if (layoutStatsCard != null) {
            layoutStatsCard.setOnClickListener(v -> showRandomTipsPanel());
        }

        // Registro de Broadcasts
        IntentFilter bipFilter = new IntentFilter("com.vapesmadcat.monitorbatt.BIP_TRIGGERED");
        IntentFilter badContactFilter = new IntentFilter("com.vapesmadcat.monitorbatt.BAD_CONTACT_DETECTED");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(bipReceiver, bipFilter, Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(badContactReceiver, badContactFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(bipReceiver, bipFilter);
            registerReceiver(badContactReceiver, badContactFilter);
        }
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        updateMuteButton(preferences.getBoolean(BatteryService.KEY_MUTED, false));
        updateBatteryReadout();
        loadSettings();

        // Verifica estado do serviço ao iniciar
        boolean shouldBeRunning = preferences.getBoolean(KEY_SERVICE_ENABLED, false);
        boolean isRunning = isServiceRunning(BatteryService.class);

        if (shouldBeRunning && !isRunning) {
            startBatteryService();
            updateMonitorButton(true);
            statusText.setText("Monitoramento: ATIVO");
            statusText.setTextColor(0xFF4ADE80);
        } else {
            updateMonitorButton(isRunning);
            if (isRunning) {
                statusText.setText("Monitoramento: ATIVO");
                statusText.setTextColor(0xFF4ADE80);
            } else {
                statusText.setText("Monitoramento: DESATIVADO");
                statusText.setTextColor(0xFFFF4D4F);
            }
        }
    }

    // ----------------------------------------------------------------
    // Exibir alerta visual de mau contato
    // ----------------------------------------------------------------
    private void showBadContactAlert() {
        if (layoutBadContactAlert != null) {
            layoutBadContactAlert.setVisibility(View.VISIBLE);
            // Auto-dismiss após 15 segundos
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (layoutBadContactAlert != null) {
                    layoutBadContactAlert.setVisibility(View.GONE);
                }
            }, 15000);
        }
    }

    private void setupCharacterSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_item, characters);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        characterSpinner.setAdapter(adapter);
    }

    private void setupThemeToggle() {
        switchThemeMode.setChecked(getStoredDarkModeEnabled(preferences));
        switchThemeMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!preferences.contains(KEY_DARK_MODE)
                    || preferences.getBoolean(KEY_DARK_MODE, !isChecked) != isChecked) {
                preferences.edit().putBoolean(KEY_DARK_MODE, isChecked).apply();
            }
            if (applyNightMode(isChecked)) {
                recreate();
            }
        });
    }

    private void setupVisualStyleSpinner() {
        String[] options = {"Pilha Normal", "Mascote"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_item, options);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerVisualStyle.setAdapter(adapter);
    }

    private boolean getStoredDarkModeEnabled(SharedPreferences sharedPreferences) {
        if (sharedPreferences.contains(KEY_DARK_MODE)) {
            return sharedPreferences.getBoolean(KEY_DARK_MODE, false);
        }
        int defaultNightMode = AppCompatDelegate.getDefaultNightMode();
        if (defaultNightMode == AppCompatDelegate.MODE_NIGHT_YES) return true;
        if (defaultNightMode == AppCompatDelegate.MODE_NIGHT_NO) return false;
        int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    private boolean applyNightMode(boolean darkModeEnabled) {
        int selectedMode = darkModeEnabled ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
        if (AppCompatDelegate.getDefaultNightMode() == selectedMode) return false;
        AppCompatDelegate.setDefaultNightMode(selectedMode);
        return true;
    }

    private void setupControls() {
        thresholdSeek.setMax(25);
        intervalSeek.setMax(11);
        voiceLowSeek.setMax(80);
        voiceCriticalSeek.setMax(80);
        voiceVeryLowSeek.setMax(80);
        seekVoiceVolume.setMax(100);

        SeekBar.OnSeekBarChangeListener seekListener = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) {
                    checkChanges();
                    clampVoiceThresholds(s);
                    updateTexts();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        };
        thresholdSeek.setOnSeekBarChangeListener(seekListener);
        intervalSeek.setOnSeekBarChangeListener(seekListener);
        voiceLowSeek.setOnSeekBarChangeListener(seekListener);
        voiceCriticalSeek.setOnSeekBarChangeListener(seekListener);
        voiceVeryLowSeek.setOnSeekBarChangeListener(seekListener);

        // SeekBar de volume
        seekVoiceVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) {
                    tvVoiceVolumeValue.setText(p + "%");
                    checkChanges();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        characterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (isSwitchUpdatingByCode) return;
                checkChanges();
                if (isPlayingPreview) stopCurrentAudio();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        // ----------------------------------------------------------------
        // Switch do BIP — independente, pode coexistir com qualquer voz
        // ----------------------------------------------------------------
        switchBeep.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isSwitchUpdatingByCode) return;
            checkChanges();
            if (isChecked) {
                triggerVisualBip();
                playTestBeep();
                Toast.makeText(this, "🔊 Bip ativado!", Toast.LENGTH_SHORT).show();
            }
        });

        // ----------------------------------------------------------------
        // Switch do TTS — exclusivo com Personagem
        // ----------------------------------------------------------------
        switchTts.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isSwitchUpdatingByCode) return;
            checkChanges();
            if (isChecked) {
                // Desligar Personagem se estiver ligado
                if (switchCharacterVoice.isChecked()) {
                    isSwitchUpdatingByCode = true;
                    switchCharacterVoice.setChecked(false);
                    isSwitchUpdatingByCode = false;
                }
                speakBatteryStatusExample();
                Toast.makeText(this, "🗣️ TTS ativado!", Toast.LENGTH_SHORT).show();
            }
        });

        // ----------------------------------------------------------------
        // Switch do Personagem — exclusivo com TTS
        // ----------------------------------------------------------------
        switchCharacterVoice.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isSwitchUpdatingByCode) return;
            checkChanges();
            if (isChecked) {
                // Desligar TTS se estiver ligado
                if (switchTts.isChecked()) {
                    isSwitchUpdatingByCode = true;
                    switchTts.setChecked(false);
                    isSwitchUpdatingByCode = false;
                }
                playCharacterVoiceSample();
                Toast.makeText(this, "🎭 Voz do personagem ativada!", Toast.LENGTH_SHORT).show();
            }
        });

        switchAutoStart.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isSwitchUpdatingByCode) return;
            checkChanges();
            preferences.edit().putBoolean(KEY_AUTO_START_ON_BOOT, isChecked).apply();
        });
    }

    private void clampVoiceThresholds(SeekBar changedSeek) {
        int low = voiceLowSeek.getProgress();
        int critical = voiceCriticalSeek.getProgress();
        int veryLow = voiceVeryLowSeek.getProgress();

        if (changedSeek == voiceLowSeek) {
            if (critical > low) voiceCriticalSeek.setProgress(low);
            if (veryLow > voiceCriticalSeek.getProgress()) voiceVeryLowSeek.setProgress(voiceCriticalSeek.getProgress());
        } else if (changedSeek == voiceCriticalSeek) {
            if (critical > low) voiceCriticalSeek.setProgress(low);
            if (veryLow > critical) voiceVeryLowSeek.setProgress(critical);
        } else if (changedSeek == voiceVeryLowSeek && veryLow > critical) {
            voiceVeryLowSeek.setProgress(critical);
        }
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
        if (spinnerVisualStyle == null || batteryContainer == null || ivMascot == null) return;
        boolean useMascot = spinnerVisualStyle.getSelectedItemPosition() == 1;
        if (useMascot) {
            batteryContainer.setVisibility(View.GONE);
            ivMascot.setVisibility(View.VISIBLE);
            updateMascotImage();
        } else {
            batteryContainer.setVisibility(View.VISIBLE);
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

    private void updateBigPercentage(int pct) {
        if (tvBigPercentage == null) return;
        tvBigPercentage.setText(pct + "%");
        tvBigPercentage.setTextColor(getBatteryColor(pct));
    }

    private void speakBatteryStatusExample() {
        if (textToSpeech == null || !ttsInitialized) {
            Toast.makeText(this, "TTS não está pronto. Tente novamente em alguns segundos.", Toast.LENGTH_SHORT).show();
            initTextToSpeech();
            return;
        }
        String message = "Monitor de Bateria Pro. Bateria em 87 por cento. Status: Normal.";
        textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, "battery_example");
    }

    private void playCharacterVoiceSample() {
        stopCurrentAudio();
        int pos = characterSpinner.getSelectedItemPosition();
        if (pos < 0 || pos >= characterKeys.length) return;
        String character = characterKeys[pos];
        if ("none".equals(character)) {
            Toast.makeText(this, "Nenhum personagem selecionado", Toast.LENGTH_SHORT).show();
            return;
        }
        int resId = getResources().getIdentifier("voice_" + character + "_low", "raw", getPackageName());
        if (resId == 0) resId = getResources().getIdentifier("voice_" + character + "_charging", "raw", getPackageName());
        if (resId != 0) {
            try {
                if (currentMediaPlayer != null) currentMediaPlayer.release();
                currentMediaPlayer = MediaPlayer.create(this, resId);
                if (currentMediaPlayer != null) {
                    // Aplicar volume configurado
                    float vol = seekVoiceVolume.getProgress() / 100f;
                    currentMediaPlayer.setVolume(vol, vol);
                    currentMediaPlayer.setOnCompletionListener(mp -> currentMediaPlayer = null);
                    currentMediaPlayer.start();
                }
            } catch (Exception e) {
                Log.e("MainActivity", "Erro ao tocar voz do personagem", e);
            }
        }
    }

    private void stopCurrentAudio() {
        if (currentMediaPlayer != null) {
            try {
                if (currentMediaPlayer.isPlaying()) currentMediaPlayer.stop();
                currentMediaPlayer.release();
            } catch (Exception e) { Log.e("MainActivity", "Erro ao parar áudio", e); }
            currentMediaPlayer = null;
        }
        isPlayingPreview = false;
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
        if (bipIndicator == null) return;

        // Feedback forte e claro
        bipIndicator.setText("🔊");
        bipIndicator.setTextColor(Color.RED);
        bipIndicator.setTextSize(32);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (bipIndicator != null) {
                bipIndicator.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                bipIndicator.setTextSize(24);
            }
        }, 1200);
    }

    private void updateMonitorButton(boolean isRunning) {
        if (isRunning) {
            btnMonitor.setText("⏹ PARAR MONITOR");
            btnMonitor.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.red));
        } else {
            btnMonitor.setText("▶ ATIVAR MONITOR");
            btnMonitor.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.green));
        }
    }

    private void updateTexts() {
        thresholdValueText.setText(getThresholdFromProgress(thresholdSeek.getProgress()) + "%");
        intervalValueText.setText(getIntervalFromProgress(intervalSeek.getProgress()) + " segundos");
        voiceLowValueText.setText(voiceLowSeek.getProgress() + "%");
        voiceCriticalValueText.setText(voiceCriticalSeek.getProgress() + "%");
        voiceVeryLowValueText.setText(voiceVeryLowSeek.getProgress() + "%");
        tvVoiceVolumeValue.setText(seekVoiceVolume.getProgress() + "%");
    }

    private void checkChanges() {
        isModified = true;
        saveBtn.setVisibility(View.VISIBLE);
        autoPersistSettings();
    }

    private void autoPersistSettings() {
        saveSettings();
        isModified = false;
        saveBtn.setVisibility(View.GONE);
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

        thresholdSeek.setProgress(Math.max(0, threshold - 5));
        intervalSeek.setProgress(Math.max(0, (intervalSeconds / 5) - 1));
        voiceLowSeek.setProgress(Math.min(80, voiceLow));
        voiceCriticalSeek.setProgress(Math.min(80, voiceCritical));
        voiceVeryLowSeek.setProgress(Math.min(80, voiceVeryLow));
        seekVoiceVolume.setProgress(Math.min(100, voiceVolume));

        for (int i = 0; i < characterKeys.length; i++) {
            if (characterKeys[i].equals(savedChar)) {
                characterSpinner.setSelection(i);
                break;
            }
        }

        // Carregar estado dos switches (padrão = false = desligado)
        boolean beepEnabled = preferences.getBoolean(BatteryService.KEY_BEEP_ENABLED, false);
        boolean ttsEnabled = preferences.getBoolean(BatteryService.KEY_TTS_ENABLED, false);
        boolean charVoiceEnabled = preferences.getBoolean(BatteryService.KEY_CHARACTER_VOICE_ENABLED, false);
        boolean autoStartEnabled = preferences.getBoolean(KEY_AUTO_START_ON_BOOT, false);

        // Garantir exclusividade ao carregar: se ambos estiverem salvos como true, priorizar TTS
        if (ttsEnabled && charVoiceEnabled) {
            charVoiceEnabled = false;
        }

        switchBeep.setChecked(beepEnabled);
        switchTts.setChecked(ttsEnabled);
        switchCharacterVoice.setChecked(charVoiceEnabled);
        switchAutoStart.setChecked(autoStartEnabled);

        String visual = preferences.getString(BatteryService.KEY_VISUAL_STYLE, "normal");
        spinnerVisualStyle.setSelection("mascot".equals(visual) ? 1 : 0);

        updateTexts();
        isModified = false;
        saveBtn.setVisibility(View.GONE);
        isSwitchUpdatingByCode = false;

        updateVisualStyle();
    }

    private void saveSettings() {
        int threshold = getThresholdFromProgress(thresholdSeek.getProgress());
        int intervalSeconds = getIntervalFromProgress(intervalSeek.getProgress());
        String selectedChar = characterKeys[characterSpinner.getSelectedItemPosition()];
        int voiceLow = voiceLowSeek.getProgress();
        int voiceCritical = Math.min(voiceCriticalSeek.getProgress(), voiceLow);
        int voiceVeryLow = Math.min(voiceVeryLowSeek.getProgress(), voiceCritical);
        int voiceVolume = seekVoiceVolume.getProgress();

        // Garantir exclusividade ao salvar
        boolean ttsOn = switchTts.isChecked();
        boolean charVoiceOn = switchCharacterVoice.isChecked();
        if (ttsOn && charVoiceOn) charVoiceOn = false;

        preferences.edit()
                .putInt(BatteryService.KEY_THRESHOLD, threshold)
                .putInt(BatteryService.KEY_BEEP_INTERVAL_SECONDS, intervalSeconds)
                .putString(BatteryService.KEY_CHARACTER_VOICE, selectedChar)
                .putInt(BatteryService.KEY_VOICE_LOW_THRESHOLD, voiceLow)
                .putInt(BatteryService.KEY_VOICE_CRITICAL_THRESHOLD, voiceCritical)
                .putInt(BatteryService.KEY_VOICE_VERYLOW_THRESHOLD, voiceVeryLow)
                .putInt(BatteryService.KEY_VOICE_VOLUME, voiceVolume)
                .putBoolean(BatteryService.KEY_BEEP_ENABLED, switchBeep.isChecked())
                .putBoolean(BatteryService.KEY_TTS_ENABLED, ttsOn)
                .putBoolean(BatteryService.KEY_CHARACTER_VOICE_ENABLED, charVoiceOn)
                .putBoolean(KEY_AUTO_START_ON_BOOT, switchAutoStart.isChecked())
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

        int color = getBatteryColor(pct);
        updateBatteryFill(pct);
        updateChargingUI(isCharging);
        updateBigPercentage(pct);
        updateChargingGauge(batteryStatus, pct, isCharging);

        if (ivMascot != null && ivMascot.getVisibility() == View.VISIBLE) {
            updateMascotImage();
        }
    }

    private void showRandomTipsPanel() {
        List<String> tips = getRandomTips(isCharging, 4);
        StringBuilder message = new StringBuilder();
        for (int i = 0; i < tips.size(); i++) {
            message.append("• ").append(tips.get(i));
            if (i < tips.size() - 1) message.append("\n\n");
        }

        String title = isCharging
                ? "Dicas de carregamento inteligente"
                : "Dicas para economizar bateria";

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message.toString())
                .setPositiveButton("Fechar", null)
                .show();
    }

    private List<String> getRandomTips(boolean chargingNow, int count) {
        Queue<String> queue = chargingNow ? chargingTipsQueue : dischargingTipsQueue;
        List<String> source = chargingNow ? CHARGING_TIPS : DISCHARGING_TIPS;
        List<String> tips = new ArrayList<>();

        while (tips.size() < count) {
            if (queue.isEmpty()) refillTipsQueue(queue, source);
            String tip = queue.poll();
            if (tip == null) break;
            tips.add(tip);
        }
        return tips;
    }

    private void refillTipsQueue(Queue<String> queue, List<String> source) {
        List<String> shuffled = new ArrayList<>(source);
        Collections.shuffle(shuffled, random);
        queue.addAll(shuffled);
    }

    private void persistCurrentState() {
        saveSettings();
        preferences.edit()
                .putBoolean(KEY_AUTO_START_ON_BOOT, switchAutoStart != null && switchAutoStart.isChecked())
                .apply();
    }

    private void adjustAppVolume(int direction) {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) return;

        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI);
        audioManager.adjustStreamVolume(AudioManager.STREAM_ALARM, direction, AudioManager.FLAG_SHOW_UI);

        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        if (maxVolume <= 0 || seekVoiceVolume == null) return;
        int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int volumePercent = Math.round((currentVolume * 100f) / maxVolume);
        seekVoiceVolume.setProgress(volumePercent);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP) {
                adjustAppVolume(AudioManager.ADJUST_RAISE);
                return true;
            }
            if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN) {
                adjustAppVolume(AudioManager.ADJUST_LOWER);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
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
        } catch (Exception e) {
            Log.e("MainActivity", "Erro ao tocar bip de teste", e);
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

    @Override
    protected void onResume() {
        super.onResume();
        updateBatteryReadout();
        boolean isRunning = isServiceRunning(BatteryService.class);
        updateMonitorButton(isRunning);
        if (isRunning) {
            statusText.setText("Monitoramento: ATIVO");
            statusText.setTextColor(0xFF4ADE80);
        } else {
            statusText.setText("Monitoramento: DESATIVADO");
            statusText.setTextColor(0xFFFF4D4F);
        }
    }

    @Override
    protected void onPause() {
        persistCurrentState();
        super.onPause();
    }

    /**
     * Atualiza o painel de estatísticas com:
     * - Corrente (mA), potência (W), temperatura (°C) e tensão (V) instantâneas
     * - Taxa de carga/descarga (%/min) e tempo estimado
     * - Ícone e cor do Gauge (Lento/Normal/Rápido)
     */
    private void updateChargingGauge(Intent batteryStatus, int currentLevel, boolean isCharging) {
        // Ao trocar entre carregando/descarregando, zera o histórico para não
        // arrastar uma taxa antiga (que distorceria o tempo estimado).
        if (wasCharging == null || wasCharging != isCharging) {
            smoothedRatePerMin = -1;
            lastBatteryLevel = -1;
            lastBatteryCheckTime = 0;
            wasCharging = isCharging;
        }

        // ---- Estatísticas instantâneas ----
        int currentNowUa = readCurrentNowMicroAmps();
        double currentMa = Math.abs(currentNowUa) / 1000.0;
        int voltageMv = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
        double voltageV = voltageMv > 0 ? voltageMv / 1000.0 : -1;
        int tempTenths = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
        double tempC = tempTenths != Integer.MIN_VALUE ? tempTenths / 10.0 : Double.NaN;
        double powerW = (voltageV > 0 && currentMa > 0) ? voltageV * (currentMa / 1000.0) : -1;
        updateStatTexts(currentMa, powerW, tempC, voltageV);

        // ---- Taxa (%/min): corrente + capacidade (instantâneo) com fallback ----
        double ratePerMin = computeRateFromCurrent(currentNowUa, currentLevel);
        if (Double.isNaN(ratePerMin)) {
            ratePerMin = computeRateFromLevelDelta(currentLevel);
        }
        if (Double.isNaN(ratePerMin)) {
            // Ainda sem dados suficientes para estimar
            tvChargingRate.setText("--");
            tvChargingTimeRemaining.setText("--");
            return;
        }

        // Suavização (média móvel exponencial) para evitar saltos bruscos
        double absRate = Math.abs(ratePerMin);
        if (smoothedRatePerMin < 0) smoothedRatePerMin = absRate;
        else smoothedRatePerMin = smoothedRatePerMin * 0.7 + absRate * 0.3;

        if (isCharging) {
            updateChargingIndicators(currentLevel, smoothedRatePerMin);
        } else {
            updateDischargingIndicators(currentLevel, smoothedRatePerMin);
        }
    }

    /** Corrente instantânea da bateria em microampères (0 se não suportado). */
    private int readCurrentNowMicroAmps() {
        if (batteryManager == null) return 0;
        int value = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        return value == Integer.MIN_VALUE ? 0 : value;
    }

    /**
     * Calcula a taxa (%/min) a partir da corrente instantânea e da capacidade
     * total estimada da bateria. Retorna NaN quando o dispositivo não expõe
     * esses dados.
     */
    private double computeRateFromCurrent(int currentNowUa, int level) {
        if (batteryManager == null || currentNowUa == 0 || level <= 0) return Double.NaN;
        long chargeCounterUah = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
        if (chargeCounterUah <= 0) return Double.NaN;
        double fullCapacityUah = chargeCounterUah / (level / 100.0);
        if (fullCapacityUah <= 0) return Double.NaN;
        double rate = (Math.abs((double) currentNowUa) / fullCapacityUah) * 100.0 / 60.0;
        if (rate <= 0 || Double.isInfinite(rate)) return Double.NaN;
        return rate;
    }

    /**
     * Fallback: estima a taxa pela variação do nível (%) ao longo do tempo.
     * Acumula o tempo até que o nível realmente mude, evitando taxas falsas de
     * 0%/min quando duas leituras consecutivas têm o mesmo percentual.
     */
    private double computeRateFromLevelDelta(int currentLevel) {
        long now = System.currentTimeMillis();
        if (lastBatteryLevel == -1) {
            lastBatteryLevel = currentLevel;
            lastBatteryCheckTime = now;
            return Double.NaN;
        }
        int levelDiff = currentLevel - lastBatteryLevel;
        if (levelDiff == 0) return Double.NaN;
        long timeDiffMs = now - lastBatteryCheckTime;
        if (timeDiffMs < 1000) return Double.NaN;
        double rate = levelDiff / (timeDiffMs / 60000.0);
        lastBatteryLevel = currentLevel;
        lastBatteryCheckTime = now;
        return rate;
    }

    private void updateStatTexts(double currentMa, double powerW, double tempC, double voltageV) {
        if (tvBatteryCurrent != null) {
            tvBatteryCurrent.setText(currentMa > 0
                    ? String.format(Locale.getDefault(), "%.0f mA", currentMa) : "--");
        }
        if (tvBatteryPower != null) {
            tvBatteryPower.setText(powerW > 0
                    ? String.format(Locale.getDefault(), "%.1f W", powerW) : "--");
        }
        if (tvBatteryTemp != null) {
            if (!Double.isNaN(tempC)) {
                tvBatteryTemp.setText(String.format(Locale.getDefault(), "%.1f°C", tempC));
                tvBatteryTemp.setTextColor(tempC >= 40 ? 0xFFEF4444
                        : tempC >= 38 ? 0xFFF59E0B
                        : ContextCompat.getColor(this, R.color.text_primary));
            } else {
                tvBatteryTemp.setText("--");
                tvBatteryTemp.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            }
        }
        if (tvBatteryVoltage != null) {
            tvBatteryVoltage.setText(voltageV > 0
                    ? String.format(Locale.getDefault(), "%.2f V", voltageV) : "--");
        }
    }

    private void updateChargingIndicators(int currentLevel, double chargeRatePerMin) {
        // Calcular tempo estimado para 100%
        int remainingPercent = 100 - currentLevel;
        double estimatedMinutes = remainingPercent / Math.max(Math.abs(chargeRatePerMin), 0.1);

        // Formatar taxa de carregamento
        String rateText = String.format(Locale.getDefault(), "%.2f%%/min", chargeRatePerMin);
        tvChargingRate.setText(rateText);

        // Formatar tempo estimado
        String timeText;
        if (estimatedMinutes > 60) {
            int hours = (int) (estimatedMinutes / 60);
            int minutes = (int) (estimatedMinutes % 60);
            timeText = String.format(Locale.getDefault(), "~%dh %dmin", hours, minutes);
        } else {
            timeText = String.format(Locale.getDefault(), "~%dmin", (int) estimatedMinutes);
        }
        tvChargingTimeRemaining.setText(timeText);

        // Determinar velocidade de carga
        // < 0.3%/min = Lento (Vermelho/Tartaruga)
        // 0.3 - 0.7%/min = Normal (Verde)
        // > 0.7%/min = Rápido (Amarelo/Raio)
        String speedLabel;
        int gaugeColor;
        int iconResource;
        int gaugeProgress;

        if (chargeRatePerMin < 0.3) {
            speedLabel = "Lento";
            gaugeColor = 0xFFEF4444; // Vermelho
            iconResource = R.drawable.ic_charging_slow; // Tartaruga
            gaugeProgress = 25;
        } else if (chargeRatePerMin < 0.7) {
            speedLabel = "Normal";
            gaugeColor = 0xFF22C55E; // Verde
            iconResource = android.R.drawable.ic_media_play; // Ícone neutro
            gaugeProgress = 50;
        } else {
            speedLabel = "Rápido";
            gaugeColor = 0xFFFCD34D; // Amarelo
            iconResource = R.drawable.ic_charging_fast; // Raio
            gaugeProgress = 100;
        }

        tvChargingSpeedLabel.setText(speedLabel);
        tvChargingSpeedLabel.setTextColor(gaugeColor);
        pbChargingSpeed.setProgress(gaugeProgress);
        pbChargingSpeed.getProgressDrawable().setTint(gaugeColor);
        ivChargingSpeedIcon.setImageResource(iconResource);

        if (tvVisualIndicator != null) {
            tvVisualIndicator.setText("⚡");
            tvVisualIndicator.setTextColor(0xFF4ADE80); // Verde
        }
    }

    private void updateDischargingIndicators(int currentLevel, double dischargeRatePerMin) {
        // Calcular tempo estimado para 0%
        double estimatedMinutes = currentLevel / Math.max(Math.abs(dischargeRatePerMin), 0.1);

        // Formatar taxa de descarregamento
        String rateText = String.format(Locale.getDefault(), "%.2f%%/min", Math.abs(dischargeRatePerMin));
        tvChargingRate.setText(rateText);

        // Formatar tempo estimado
        String timeText;
        if (estimatedMinutes > 60) {
            int hours = (int) (estimatedMinutes / 60);
            int minutes = (int) (estimatedMinutes % 60);
            timeText = String.format(Locale.getDefault(), "~%dh %dmin", hours, minutes);
        } else {
            timeText = String.format(Locale.getDefault(), "~%dmin", (int) estimatedMinutes);
        }
        tvChargingTimeRemaining.setText(timeText);

        // Determinar velocidade de descarregamento
        // < 0.3%/min = Lento (Verde/Tartaruga)
        // 0.3 - 0.7%/min = Normal (Branco)
        // > 0.7%/min = Rápido (Vermelho/Raio)
        String speedLabel;
        int gaugeColor;
        int iconResource;
        int gaugeProgress;
        double absRate = Math.abs(dischargeRatePerMin);

        if (absRate < 0.3) {
            speedLabel = "Lento";
            gaugeColor = 0xFF22C55E; // Verde
            iconResource = R.drawable.ic_charging_slow; // Tartaruga
            gaugeProgress = 25;
        } else if (absRate < 0.7) {
            speedLabel = "Normal";
            gaugeColor = 0xFFFFFFFF; // Branco
            iconResource = android.R.drawable.ic_media_play; // Ícone neutro
            gaugeProgress = 50;
        } else {
            speedLabel = "Rápido";
            gaugeColor = 0xFFEF4444; // Vermelho
            iconResource = R.drawable.ic_charging_fast; // Raio
            gaugeProgress = 100;
        }

        tvChargingSpeedLabel.setText(speedLabel);
        tvChargingSpeedLabel.setTextColor(gaugeColor);
        pbChargingSpeed.setProgress(gaugeProgress);
        pbChargingSpeed.getProgressDrawable().setTint(gaugeColor);
        ivChargingSpeedIcon.setImageResource(iconResource);

        if (tvVisualIndicator != null) {
            tvVisualIndicator.setText("⬇️");
            tvVisualIndicator.setTextColor(0xFFEF4444); // Vermelho
        }
    }

    @Override
    protected void onDestroy() {
        persistCurrentState();
        try { unregisterReceiver(bipReceiver); } catch (Exception ignored) {}
        try { unregisterReceiver(batteryReceiver); } catch (Exception ignored) {}
        try { unregisterReceiver(badContactReceiver); } catch (Exception ignored) {}
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
        stopCurrentAudio();
        super.onDestroy();
    }
}
