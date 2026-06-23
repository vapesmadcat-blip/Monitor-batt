package com.vapesmadcat.monitorbatt;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import java.util.Locale;

public class BatteryService extends Service {

    public static final String CHANNEL_ID = "battery_alert_channel";
    public static final int NOTIF_ID = 1;
    public static final String PREFS_NAME = "monitor_batt_prefs";

    // Chaves de configuração (granulares)
    public static final String KEY_THRESHOLD = "threshold";
    public static final String KEY_BEEP_INTERVAL_SECONDS = "beep_interval_seconds";
    public static final String KEY_MUTED = "muted";                    // Master mute (desliga tudo)
    public static final String KEY_BEEP_ENABLED = "beep_enabled";      // Ligar/desligar só o bip
    public static final String KEY_TTS_ENABLED = "tts_enabled";        // Ligar/desligar TextToSpeech
    public static final String KEY_CHARACTER_VOICE_ENABLED = "character_voice_enabled"; // Ligar/desligar vozes pré-gravadas
    public static final String KEY_CHARACTER_VOICE = "character_voice"; // Qual personagem ("none", "default", "male", etc.)
    public static final String KEY_VISUAL_STYLE = "visual_style";      // "normal" ou "mascot"

    public static final int DEFAULT_THRESHOLD = 10;
    public static final int DEFAULT_BEEP_INTERVAL_SECONDS = 15;

    private Handler handler;
    private Runnable beepRunnable;
    private ToneGenerator toneGenerator;
    private PowerManager.WakeLock wakeLock;
    private boolean alerting = false;

    private int currentLevel = -1;
    private boolean charging = false;
    private boolean fullNotified = false;

    private long lastVoiceTime = 0;
    private static final long VOICE_COOLDOWN_MS = 60000;

    private TextToSpeech tts;
    private SharedPreferences prefs;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);

            int newLevel = (level >= 0 && scale > 0) ? (int) ((level * 100f) / scale) : -1;
            boolean newCharging = plugged != 0;

            if (newCharging && !charging) {
                onChargerConnected();
            }

            if (newLevel >= 100 && !fullNotified) {
                onBatteryFull();
                fullNotified = true;
            } else if (newLevel < 95) {
                fullNotified = false;
            }

            currentLevel = newLevel;
            charging = newCharging;
            evaluateAlertState();
            updateNotification();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        toneGenerator = createToneGenerator();

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MonitorBatt::wl");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire();

        createChannel();
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        // TextToSpeech
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(new Locale("pt", "BR"));
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w("BatteryService", "pt-BR não disponível no TTS");
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    tts.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build());
                }
            }
        });

        beepRunnable = () -> {
            if (alerting) {
                triggerBip();
                playCharacterVoiceIfNeeded(currentLevel);
                speakSmartAlert(currentLevel);
                handler.postDelayed(beepRunnable, getCurrentIntervalMs());
            }
        };

        checkInitialBatteryStatus();
    }

    private void onChargerConnected() {
        Log.d("BatteryService", "Carregador conectado!");
        if (isCharacterVoiceEnabled() && !isMuted()) {
            playSpecificVoice("charging");
        }
        if (isTtsEnabled() && !isMuted() && tts != null) {
            tts.speak("Carregador conectado. Monitoramento pausado.", TextToSpeech.QUEUE_FLUSH, null, "charging");
        }
        // Mostrar toast de confirmação
        showToast("Carregador conectado - Monitoramento pausado");
    }

    private void onBatteryFull() {
        Log.d("BatteryService", "Bateria cheia! 100%");
        if (isCharacterVoiceEnabled() && !isMuted()) {
            playSpecificVoice("full");
        }
        if (isTtsEnabled() && !isMuted() && tts != null) {
            tts.speak("Bateria cheia! 100%. Muito bem!", TextToSpeech.QUEUE_FLUSH, null, "full");
        }
        showToast("Bateria cheia! 100%");
    }

    private void triggerBip() {
        if (isMuted() || !isBeepEnabled()) {
            updateNotification();
            return;
        }
        if (toneGenerator != null) {
            try {
                toneGenerator.startTone(getToneForLevel(), getToneDurationMs());
            } catch (Exception e) {
                Log.e("BatteryService", "Erro ao tocar tom", e);
            }
        }
        sendBroadcast(new Intent("com.vapesmadcat.monitorbatt.BIP_TRIGGERED"));
        updateNotification();
    }

    private boolean isMuted() {
        return prefs.getBoolean(KEY_MUTED, false);
    }

    private boolean isBeepEnabled() {
        return prefs.getBoolean(KEY_BEEP_ENABLED, true);
    }

    private boolean isTtsEnabled() {
        return prefs.getBoolean(KEY_TTS_ENABLED, true);
    }

    private boolean isCharacterVoiceEnabled() {
        return prefs.getBoolean(KEY_CHARACTER_VOICE_ENABLED, true);
    }

    private ToneGenerator createToneGenerator() {
        int[] streams = {AudioManager.STREAM_ALARM, AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_MUSIC};
        for (int stream : streams) {
            try { 
                ToneGenerator tg = new ToneGenerator(stream, 100);
                if (tg != null) return tg;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private void checkInitialBatteryStatus() {
        Intent batteryStatus = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            currentLevel = (level >= 0 && scale > 0) ? (int) ((level * 100f) / scale) : -1;
            charging = plugged != 0;
            evaluateAlertState();
            updateNotification();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIF_ID, buildNotification("Monitor Ativo", "Aguardando nível de bateria..."));
        evaluateAlertState();
        return START_STICKY;
    }

    private void evaluateAlertState() {
        boolean shouldAlert = currentLevel >= 0 && currentLevel <= getThreshold() && !charging;
        if (shouldAlert && !alerting) {
            alerting = true;
            handler.removeCallbacks(beepRunnable);
            handler.post(beepRunnable);
            playCharacterVoiceIfNeeded(currentLevel);
            speakSmartAlert(currentLevel);
            showToast("Alerta de bateria baixa: " + currentLevel + "%");
        } else if (!shouldAlert && alerting) {
            alerting = false;
            handler.removeCallbacks(beepRunnable);
            showToast("Alerta desativado - bateria normal");
        }
    }

    private void playCharacterVoiceIfNeeded(int level) {
        if (isMuted() || !isCharacterVoiceEnabled()) return;
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastVoiceTime < VOICE_COOLDOWN_MS) return;

        String type = (level <= 5 ? "critical" : "low");
        if (playSpecificVoice(type)) {
            lastVoiceTime = currentTime;
        }
    }

    private boolean playSpecificVoice(String type) {
        String character = prefs.getString(KEY_CHARACTER_VOICE, "none");
        if ("none".equals(character) || character.isEmpty()) return false;

        int resId = getResources().getIdentifier("voice_" + character + "_" + type, "raw", getPackageName());
        if (resId != 0) {
            try {
                MediaPlayer mp = MediaPlayer.create(this, resId);
                if (mp != null) {
                    mp.setOnCompletionListener(MediaPlayer::release);
                    mp.start();
                    Log.d("BatteryService", "Tocando voz: " + character + "_" + type);
                    return true;
                }
            } catch (Exception e) {
                Log.e("BatteryService", "Erro ao tocar voz pré-gravada: " + type, e);
            }
        } else {
            Log.w("BatteryService", "Arquivo de voz não encontrado: voice_" + character + "_" + type);
        }
        return false;
    }

    private void speakSmartAlert(int level) {
        if (tts == null || isMuted() || !isTtsEnabled()) return;

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastVoiceTime < VOICE_COOLDOWN_MS) return;

        String message;
        if (level <= 5) {
            message = "Alerta crítico! A bateria está em apenas " + level + " por cento. Conecte o carregador imediatamente.";
        } else if (level <= 15) {
            message = "Atenção! Bateria baixa em " + level + " por cento. Recomendo conectar o carregador agora.";
        } else {
            message = "Monitor de bateria. Nível atual: " + level + " por cento.";
        }

        try {
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "battery_tts_" + level);
            lastVoiceTime = currentTime;
            Log.d("BatteryService", "TTS: " + message);
        } catch (Exception e) {
            Log.e("BatteryService", "Erro no TTS", e);
        }
    }

    private int getThreshold() {
        return prefs.getInt(KEY_THRESHOLD, DEFAULT_THRESHOLD);
    }

    private long getCurrentIntervalMs() {
        int seconds = prefs.getInt(KEY_BEEP_INTERVAL_SECONDS, DEFAULT_BEEP_INTERVAL_SECONDS);
        return Math.max(1, seconds) * 1000L;
    }

    private int getToneForLevel() {
        if (currentLevel <= 2) return ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK;
        if (currentLevel <= 5) return ToneGenerator.TONE_CDMA_HIGH_L;
        return ToneGenerator.TONE_PROP_BEEP;
    }

    private int getToneDurationMs() {
        if (currentLevel <= 5) return 800;
        return 400;
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            String statusText = charging ? "🔌 Carregando" : "📱 Descarregando";
            String text = "Bateria " + currentLevel + "% • " + statusText;
            String title = alerting ? "⚠️ ALERTA ATIVO" : "✅ Monitorando";
            
            // Adicionar informações extras
            if (isMuted()) {
                text += " 🔇";
            }
            
            nm.notify(NOTIF_ID, buildNotification(title, text));
        }
    }

    private Notification buildNotification(String title, String text) {
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        int smallIcon = android.R.drawable.ic_lock_idle_low_battery;

        // Verificar estilo visual
        String style = prefs.getString(KEY_VISUAL_STYLE, "normal");
        if ("mascot".equals(style)) {
            // Tentar usar ícone do mascote se existir
            int mascotIcon = getResources().getIdentifier("ic_mascot_battery", "drawable", getPackageName());
            if (mascotIcon != 0) {
                smallIcon = mascotIcon;
            }
        }

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(smallIcon)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Alertas de Bateria", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Canal para notificações de monitoramento de bateria");
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private void showToast(String message) {
        // Mostrar toast apenas se não estiver mudo (ou mostrar mesmo assim?)
        // Vamos mostrar sempre para feedback do usuário
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Usar Handler para mostrar na UI thread
            handler.post(() -> Toast.makeText(BatteryService.this, message, Toast.LENGTH_SHORT).show());
        } else {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroy() {
        try { 
            unregisterReceiver(batteryReceiver); 
        } catch (Exception ignored) { }
        
        if (handler != null) {
            handler.removeCallbacks(beepRunnable);
        }
        
        if (toneGenerator != null) { 
            toneGenerator.release(); 
            toneGenerator = null; 
        }
        
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        
        showToast("Serviço de monitoramento encerrado");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { 
        return null; 
    }
}