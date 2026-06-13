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
    private static final int DEFAULT_THRESHOLD = 90; // Para teste rápido
    private static final long BEEP_INTERVAL_MS = 30_000L;

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

        // Força checagem inicial da bateria
        checkInitialBatteryStatus();

        beepRunnable = new Runnable() {
            @Override
            public void run() {
                if (alerting && toneGenerator != null) {
                    try {
                        toneGenerator.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 650);
                    } catch (Exception ignored) {}
                }
                if (alerting) {
                    handler.postDelayed(this, BEEP_INTERVAL_MS);
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
        startForeground(NOTIF_ID, buildNotification(getString(R.string.notif_initial)));
        return START_STICKY;
    }

    private int getThreshold() {
        return getSharedPreferences("battery_prefs", MODE_PRIVATE)
                .getInt("threshold", DEFAULT_THRESHOLD);
    }

    private void evaluateAlertState() {
        int threshold = getThreshold();
        boolean shouldAlert = currentLevel >= 0 && currentLevel <= threshold && !charging;
        if (shouldAlert && !alerting) {
            alerting = true;
            handler.removeCallbacks(beepRunnable);
            handler.post(beepRunnable);
        } else if (!shouldAlert && alerting) {
            alerting = false;
            handler.removeCallbacks(beepRunnable);
        }
    }

    private void updateNotification() {
        String text;
        if (currentLevel < 0) {
            text = getString(R.string.notif_initial);
        } else {
            text = getString(R.string.notif_fmt, currentLevel,
                    charging ? getString(R.string.charger_on) : getString(R.string.charger_off));
        }
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification(text));
    }

    private Notification buildNotification(String text) {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
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
        try { unregisterReceiver(batteryReceiver); } catch (Exception ignored) {}
        if (handler != null) handler.removeCallbacks(beepRunnable);
        if (toneGenerator != null) { toneGenerator.release(); toneGenerator = null; }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
