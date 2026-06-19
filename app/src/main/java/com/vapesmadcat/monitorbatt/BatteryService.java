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
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

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
    public static final String ACTION_BIP = "com.vapesmadcat.monitorbatt.BIP_TRIGGERED";

    private Handler handler;
    private Runnable beepRunnable;
    private PowerManager.WakeLock wakeLock;
    private boolean alerting = false;
    private int currentLevel = -1;
    private boolean charging = false;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateBatteryInfo(intent);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MonitorBatt::wl");
        wakeLock.acquire();

        createChannel();
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        beepRunnable = new Runnable() {
            @Override
            public void run() {
                if (alerting) {
                    triggerBip();
                    handler.postDelayed(this, getCurrentIntervalMs());
                }
            }
        };
    }

    private void triggerBip() {
        if (!isMuted()) {
            // Toca o som usando a mesma lógica do botão de teste
            try {
                ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
                tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 300);
                // Liberar após um pequeno delay para garantir que tocou
                new Handler(Looper.getMainLooper()).postDelayed(tg::release, 1000);
            } catch (Exception e) {
                Log.e("BatteryService", "Erro no som", e);
            }
            
            // Envia o sinal visual para a tela
            Intent intent = new Intent(ACTION_BIP);
            intent.setPackage(getPackageName()); // Garante que só este app receba
            sendBroadcast(intent);
        }
        updateNotification();
    }

    private void updateBatteryInfo(Intent intent) {
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        currentLevel = (level >= 0 && scale > 0) ? (int) ((level * 100f) / scale) : -1;
        charging = plugged != 0;
        evaluateAlertState();
        updateNotification();
    }

    private boolean isMuted() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_MUTED, false);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIF_ID, buildNotification("Monitor Ativo", "Aguardando bateria..."));
        
        // Forçar leitura inicial
        Intent batteryStatus = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryStatus != null) updateBatteryInfo(batteryStatus);
        
        return START_STICKY;
    }

    private void evaluateAlertState() {
        int threshold = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(KEY_THRESHOLD, DEFAULT_THRESHOLD);
        boolean shouldAlert = currentLevel >= 0 && currentLevel <= threshold && !charging;
        
        if (shouldAlert && !alerting) {
            alerting = true;
            handler.removeCallbacks(beepRunnable);
            handler.post(beepRunnable);
            playCharacterVoice();
        } else if (!shouldAlert && alerting) {
            alerting = false;
            handler.removeCallbacks(beepRunnable);
        }
    }

    private void playCharacterVoice() {
        if (isMuted()) return;
        String character = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_CHARACTER_VOICE, "none");
        if ("none".equals(character)) return;

        int resId = getResources().getIdentifier("voice_" + character + "_" + (currentLevel <= 5 ? "critical" : "low"), "raw", getPackageName());
        if (resId != 0) {
            try {
                MediaPlayer mp = MediaPlayer.create(this, resId);
                if (mp != null) {
                    mp.setOnCompletionListener(MediaPlayer::release);
                    mp.start();
                }
            } catch (Exception ignored) {}
        }
    }

    private long getCurrentIntervalMs() {
        int seconds = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(KEY_BEEP_INTERVAL_SECONDS, DEFAULT_BEEP_INTERVAL_SECONDS);
        return Math.max(1, seconds) * 1000L;
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            String text = "Bateria " + currentLevel + "% • " + (charging ? "Carregando" : "Monitorando");
            nm.notify(NOTIF_ID, buildNotification(alerting ? "ALERTA ATIVO" : "Monitor de Bateria", text));
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
        handler.removeCallbacks(beepRunnable);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
