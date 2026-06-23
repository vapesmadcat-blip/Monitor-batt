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

import androidx.core.app.NotificationCompat;

import java.util.Locale;

public class BatteryService extends Service {

    public static final String CHANNEL_ID = "battery_alert_channel";
    public static final int NOTIF_ID = 1;
    public static final String PREFS_NAME = "monitor_batt_prefs";
    public static final String KEY_THRESHOLD = "threshold";
    public static final String KEY_BEEP_INTERVAL_SECONDS = "beep_interval_seconds";
    public static final String KEY_MUTED = "muted";
    public static final String KEY_CHARACTER_VOICE = "character_voice";
    public static final int DEFAULT_THRESHOLD = 10;
    public static final int DEFAULT_BEEP_INTERVAL_SECONDS = 15;

    private Handler handler;
    private Runnable beepRunnable;
    private ToneGenerator toneGenerator;
    private PowerManager.WakeLock wakeLock;
    private boolean alerting = false;

    private int currentLevel = -1;
    private boolean charging = false;
    private boolean fullNotified = false; // Evita repetir o áudio de 100% várias vezes

    private long lastVoiceTime = 0;
    private static final long VOICE_COOLDOWN_MS = 60000;

    // NOVO: TextToSpeech para alertas inteligentes por voz
    private TextToSpeech tts;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            
            int newLevel = (level >= 0 && scale > 0) ? (int) ((level * 100f) / scale) : -1;
            boolean newCharging = plugged != 0;

            if (newCharging && !charging) {
                onChargerConnected();
            }

            // Detecta bateria cheia (100%)
            if (newLevel >= 100 && !fullNotified) {
                onBatteryFull();
                fullNotified = true;
            } else if (newLevel < 95) {
                fullNotified = false; // Reseta quando a bateria cai um pouco
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
        toneGenerator = createToneGenerator();

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MonitorBatt::wl");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire();

        createChannel();
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        // ==================== TEXT TO SPEECH (NOVO) ====================
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    Locale locale = new Locale("pt", "BR");
                    int result = tts.setLanguage(locale);
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.w("BatteryService", "Idioma Português (Brasil) não disponível no TTS. Usuário pode precisar baixar voz.");
                    } else {
                        Log.d("BatteryService", "TextToSpeech inicializado com sucesso em pt-BR");
                    }

                    // Tenta usar STREAM_ALARM para ficar mais audível (mesmo espírito do bip)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        tts.setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build());
                    }
                } else {
                    Log.e("BatteryService", "Falha ao inicializar TextToSpeech");
                }
            }
        });
        // ============================================================

        beepRunnable = new Runnable() {
            @Override
            public void run() {
                if (alerting) {
                    triggerBip();
                    playCharacterVoiceIfNeeded(currentLevel);
                    speakSmartAlert(currentLevel); // NOVO: TTS inteligente
                    handler.postDelayed(this, getCurrentIntervalMs());
                }
            }
        };

        checkInitialBatteryStatus();
    }

    private void onChargerConnected() {
        Log.d("BatteryService", "Carregador conectado!");
        playSpecificVoice("charging");
        // Opcional: falar também via TTS
        if (tts != null && !isMuted()) {
            tts.speak("Carregador conectado. Monitoramento pausado.", TextToSpeech.QUEUE_FLUSH, null, "charging");
        }
    }

    private void onBatteryFull() {
        Log.d("BatteryService", "Bateria cheia! 100%");
        playSpecificVoice("full");
        if (tts != null && !isMuted()) {
            tts.speak("Bateria cheia! 100%. Muito bem!", TextToSpeech.QUEUE_FLUSH, null, "full");
        }
    }

    private void triggerBip() {
        if (!isMuted()) {
            if (toneGenerator != null) {
                try {
                    toneGenerator.startTone(getToneForLevel(), getToneDurationMs());
                } catch (Exception e) {
                    Log.e("BatteryService", "Erro ao tocar tom", e);
                }
            }
            sendBroadcast(new Intent("com.vapesmadcat.monitorbatt.BIP_TRIGGERED"));
        }
        updateNotification();
    }

    private boolean isMuted() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_MUTED, false);
    }

    private ToneGenerator createToneGenerator() {
        int[] streams = {AudioManager.STREAM_ALARM, AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_MUSIC};
        for (int stream : streams) {
            try { return new ToneGenerator(stream, 100); } catch (Exception ignored) {}
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
            speakSmartAlert(currentLevel); // NOVO: TTS no momento que entra em alerta
        } else if (!shouldAlert && alerting) {
            alerting = false;
            handler.removeCallbacks(beepRunnable);
        }
    }

    private void playCharacterVoiceIfNeeded(int level) {
        if (isMuted()) return;
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastVoiceTime < VOICE_COOLDOWN_MS) return;

        String type = (level <= 5 ? "critical" : "low");
        if (playSpecificVoice(type)) {
            lastVoiceTime = currentTime;
        }
    }

    private boolean playSpecificVoice(String type) {
        if (isMuted()) return false;
        String character = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_CHARACTER_VOICE, "none");
        if ("none".equals(character)) return false;

        int resId = getResources().getIdentifier("voice_" + character + "_" + type, "raw", getPackageName());
        if (resId != 0) {
            try {
                MediaPlayer mp = MediaPlayer.create(this, resId);
                if (mp != null) {
                    mp.setOnCompletionListener(MediaPlayer::release);
                    mp.start();
                    return true;
                }
            } catch (Exception e) {
                Log.e("BatteryService", "Erro ao tocar voz: " + type, e);
            }
        }
        return false;
    }

    // ==================== NOVO MÉTODO: TextToSpeech Inteligente ====================
    private void speakSmartAlert(int level) {
        if (tts == null || isMuted()) return;

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastVoiceTime < VOICE_COOLDOWN_MS) return;

        String message;
        if (level <= 5) {
            message = "Alerta crítico! A bateria está em apenas " + level + " por cento. Conecte o carregador imediatamente para evitar o desligamento.";
        } else if (level <= 15) {
            message = "Atenção! Bateria baixa em " + level + " por cento. É recomendável conectar o carregador agora.";
        } else {
            message = "Monitor de bateria ativo. Nível atual em " + level + " por cento.";
        }

        try {
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "battery_tts_" + level);
            lastVoiceTime = currentTime;
            Log.d("BatteryService", "TTS falando: " + message);
        } catch (Exception e) {
            Log.e("BatteryService", "Erro ao falar via TextToSpeech", e);
        }
    }
    // ======================================================================

    private int getThreshold() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(KEY_THRESHOLD, DEFAULT_THRESHOLD);
    }

    private long getCurrentIntervalMs() {
        int seconds = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(KEY_BEEP_INTERVAL_SECONDS, DEFAULT_BEEP_INTERVAL_SECONDS);
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
            String text = "Bateria " + currentLevel + "% • " + (charging ? "Carregando" : "Descarregando");
            nm.notify(NOTIF_ID, buildNotification(alerting ? "ALERTA ATIVO" : "Monitorando", text));
        }
    }

    private Notification buildNotification(String title, String text) {
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
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
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    @Override
    public void onDestroy() {
        try { unregisterReceiver(batteryReceiver); } catch (Exception ignored) { }
        if (handler != null) handler.removeCallbacks(beepRunnable);
        if (toneGenerator != null) { toneGenerator.release(); toneGenerator = null; }

        // NOVO: limpa o TextToSpeech
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }

        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
