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
import android.media.ToneGenerator;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;

public class BatteryService extends Service {

    public static final String CHANNEL_ID = "battery_alert_channel";
    public static final int NOTIF_ID = 1;
    public static final String PREFS_NAME = "monitor_batt_prefs";
    public static final String KEY_THRESHOLD = "threshold";
    public static final String KEY_BEEP_INTERVAL_SECONDS = "beep_interval_seconds";
    public static final int DEFAULT_THRESHOLD = 10;
    public static final int DEFAULT_BEEP_INTERVAL_SECONDS = 15;

    private Handler handler;
    private Runnable beepRunnable;
    private ToneGenerator toneGenerator;
    private PowerManager.WakeLock wakeLock;
    private boolean alerting = false;

    private int currentLevel = -1;
    private boolean charging = false;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            currentLevel = (level >= 0 && scale > 0) ? (int) ((level * 100f) / scale) : -1;
            charging = plugged != 0;
            updateNotification();
            evaluateAlertState();
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
        checkInitialBatteryStatus();

        beepRunnable = new Runnable() {
            @Override
            public void run() {
                if (alerting && toneGenerator != null) {
                    try {
                        toneGenerator.startTone(getToneForLevel(), getToneDurationMs());
                    } catch (Exception ignored) { }
                }
                if (alerting) {
                    handler.postDelayed(this, getCurrentIntervalMs());
                }
            }
        };
    }

    private ToneGenerator createToneGenerator() {
        try {
            return new ToneGenerator(AudioManager.STREAM_ALARM, 100);
        } catch (RuntimeException e1) {
            try {
                return new ToneGenerator(AudioManager.STREAM_RING, 100);
            } catch (RuntimeException e2) {
                try {
                    return new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
                } catch (RuntimeException e3) {
                    return null;
                }
            }
        }
    }

    private void checkInitialBatteryStatus() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            currentLevel = (level >= 0 && scale > 0) ? (int) ((level * 100f) / scale) : -1;
            charging = plugged != 0;
            updateNotification();
            evaluateAlertState();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIF_ID, buildNotification(getNotificationText()));
        evaluateAlertState();
        return START_STICKY;
    }

    private void evaluateAlertState() {
        boolean shouldAlert = currentLevel >= 0 && currentLevel <= getThreshold() && !charging;
        if (shouldAlert && !alerting) {
            alerting = true;
            handler.removeCallbacks(beepRunnable);
            handler.post(beepRunnable);
        } else if (!shouldAlert && alerting) {
            alerting = false;
            handler.removeCallbacks(beepRunnable);
        }
    }

    private int getThreshold() {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return preferences.getInt(KEY_THRESHOLD, DEFAULT_THRESHOLD);
    }

    private long getBaseBeepIntervalMs() {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int seconds = preferences.getInt(KEY_BEEP_INTERVAL_SECONDS, DEFAULT_BEEP_INTERVAL_SECONDS);
        return seconds * 1000L;
    }

    private long getCurrentIntervalMs() {
        long base = getBaseBeepIntervalMs();
        int halfThreshold = Math.max(1, getThreshold() / 2);

        if (currentLevel <= 1) {
            return 2000L;
        }
        if (currentLevel <= 2) {
            return 4000L;
        }
        if (currentLevel <= 5) {
            return 8000L;
        }
        if (currentLevel <= halfThreshold) {
            return Math.min(base, 10000L);
        }
        return base;
    }

    private int getToneForLevel() {
        int halfThreshold = Math.max(1, getThreshold() / 2);

        if (currentLevel <= 1) {
            return ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK;
        }
        if (currentLevel <= 2) {
            return ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD;
        }
        if (currentLevel <= 5) {
            return ToneGenerator.TONE_CDMA_HIGH_L;
        }
        if (currentLevel <= halfThreshold) {
            return ToneGenerator.TONE_PROP_BEEP2;
        }
        return ToneGenerator.TONE_PROP_BEEP;
    }

    private int getToneDurationMs() {
        if (currentLevel <= 1) return 1200;
        if (currentLevel <= 2) return 900;
        if (currentLevel <= 5) return 700;
        return 450;
    }

    private String getNotificationText() {
        if (currentLevel < 0) {
            return getString(R.string.notif_initial);
        }
        return "Bateria: " + currentLevel + "% • "
                + (charging ? getString(R.string.charger_on) : getString(R.string.charger_off))
                + " • alerta em " + getThreshold() + "%"
                + " • bip a cada " + (getCurrentIntervalMs() / 1000) + " s";
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification(getNotificationText()));
    }

    private Notification buildNotification(String text) {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setOngoing(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription(getString(R.string.channel_desc));
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    @Override
    public void onDestroy() {
        try { unregisterReceiver(batteryReceiver); } catch (Exception ignored) { }
        if (handler != null) handler.removeCallbacks(beepRunnable);
        if (toneGenerator != null) { toneGenerator.release(); toneGenerator = null; }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
