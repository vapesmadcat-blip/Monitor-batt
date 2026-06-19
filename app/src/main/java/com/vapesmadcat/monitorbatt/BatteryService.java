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
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class BatteryService extends Service {

    public static final String CHANNEL_ID = "battery_alert_channel";
    public static final int NOTIF_ID = 1;
    public static final String ACTION_BIP = "com.vapesmadcat.monitorbatt.BIP_TRIGGERED";
    public static final String PREFS_NAME = "monitor_batt_prefs";

    private Handler handler;
    private Runnable debugRunnable;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MonitorBatt::DebugWL");
        wakeLock.acquire();

        createChannel();

        // VERSÃO RADICAL: Apita e pisca a cada 5 segundos SEMPRE que estiver ligado
        debugRunnable = new Runnable() {
            @Override
            public void run() {
                // Tenta tocar o som
                try {
                    ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
                    tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 300);
                    new Handler(Looper.getMainLooper()).postDelayed(tg::release, 1000);
                } catch (Exception e) {
                    Log.e("BatteryService", "Erro som debug", e);
                }

                // Envia sinal visual (Vermelho)
                Intent intent = new Intent(ACTION_BIP);
                intent.putExtra("state", "red");
                intent.setPackage(getPackageName());
                sendBroadcast(intent);

                handler.postDelayed(this, 5000); // 5 segundos cravados
            }
        };
        handler.post(debugRunnable);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIF_ID, buildNotification("DEBUG ATIVO", "O app deve apitar a cada 5 segundos."));
        return START_STICKY;
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
                .setPriority(NotificationCompat.PRIORITY_MAX) // Prioridade máxima para teste
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Debug Alertas", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    @Override
    public void onDestroy() {
        if (handler != null) handler.removeCallbacks(debugRunnable);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
