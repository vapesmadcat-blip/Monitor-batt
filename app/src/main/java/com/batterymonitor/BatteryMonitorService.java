package com.batterymonitor;

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

import androidx.core.app.NotificationCompat;

public class BatteryMonitorService extends Service {

    private static final String CHANNEL_ID = "battery_monitor_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final long BEEP_INTERVAL_MS = 30000; // 30 segundos

    private ToneGenerator toneGenerator;
    private Handler handler;
    private Runnable beepRunnable;
    private boolean isBeepingActive = false;

    private int lastLevel = -1;
    private boolean lastCharging = false;

    private BroadcastReceiver batteryReceiver;
    private NotificationManager notificationManager;

    @Override
    public void onCreate() {
        super.onCreate();

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();

        toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 75);
        handler = new Handler(Looper.getMainLooper());

        beepRunnable = new Runnable() {
            @Override
            public void run() {
                if (isBeepingActive && lastLevel < 5 && !lastCharging) {
                    playBeep();
                    // Agenda o próximo bipe
                    handler.postDelayed(this, BEEP_INTERVAL_MS);
                } else {
                    stopBeeping();
                }
            }
        };

        registerBatteryReceiver();

        // Inicia como Foreground Service (obrigatório no Android 8+)
        startForeground(NOTIFICATION_ID, buildNotification(false));

        // Pega o nível inicial de bateria
        updateInitialBatteryStatus();
    }

    private void updateInitialBatteryStatus() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, filter);
        if (batteryStatus != null) {
            updateBatteryStatus(batteryStatus);
        }
    }

    private void registerBatteryReceiver() {
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                    updateBatteryStatus(intent);
                }
            }
        };

        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryReceiver, filter);
    }

    private void updateBatteryStatus(Intent intent) {
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int percent = (int) ((level / (float) scale) * 100);

        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                           status == BatteryManager.BATTERY_STATUS_FULL;

        lastLevel = percent;
        lastCharging = charging;

        // Atualiza notificação sempre
        boolean isLow = (percent < 5 && !charging);
        notificationManager.notify(NOTIFICATION_ID, buildNotification(isLow));

        // Controla o bipe
        if (isLow && !isBeepingActive) {
            startBeeping();
        } else if (!isLow && isBeepingActive) {
            stopBeeping();
        }
    }

    private void startBeeping() {
        if (!isBeepingActive) {
            isBeepingActive = true;
            handler.post(beepRunnable); // Inicia imediatamente o primeiro bipe
        }
    }

    private void stopBeeping() {
        isBeepingActive = false;
        handler.removeCallbacks(beepRunnable);
    }

    private void playBeep() {
        if (toneGenerator != null) {
            // Tom de alerta distinto (pode trocar por TONE_PROP_BEEP se preferir mais suave)
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 280);
        }
    }

    private Notification buildNotification(boolean isLowBattery) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        String title = isLowBattery ?
                getString(R.string.notification_low_title) :
                getString(R.string.notification_title);

        String text = isLowBattery ?
                getString(R.string.notification_low_text) :
                getString(R.string.notification_text);

        if (!isLowBattery && lastLevel > 0) {
            text = "Bateria: " + lastLevel + "% • " + (lastCharging ? "Carregando" : "Em uso");
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(isLowBattery ? android.R.drawable.ic_dialog_alert : android.R.drawable.ic_lock_idle_low_battery)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setPriority(isLowBattery ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .setCategory(NotificationCompat.CATEGORY_SERVICE);

        if (isLowBattery) {
            builder.setStyle(new NotificationCompat.BigTextStyle().bigText(text));
        }

        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW // LOW para não incomodar muito, HIGH só quando bateria baixa
            );
            channel.setDescription(getString(R.string.notification_channel_desc));
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // Reinicia o serviço se o sistema matar
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Não é serviço bound
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopBeeping();
        if (batteryReceiver != null) {
            unregisterReceiver(batteryReceiver);
        }
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
        // Para de ser foreground
        stopForeground(true);
    }
}