package com.vapesmadcat.monitorbatt;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.core.content.ContextCompat;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SharedPreferences prefs = context.getSharedPreferences(BatteryService.PREFS_NAME, Context.MODE_PRIVATE);
            boolean isEnabled = prefs.getBoolean(MainActivity.KEY_SERVICE_ENABLED, false);
            
            if (isEnabled) {
                Intent svc = new Intent(context, BatteryService.class);
                ContextCompat.startForegroundService(context, svc);
            }
        }
    }
}
