package com.enem.smartunlock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

class ScreenReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("monitor_batt", Context.MODE_PRIVATE)
        val monitoringEnabled = prefs.getBoolean("monitoring_enabled", false)
        if (!monitoringEnabled) return

        val snoozedUntil = prefs.getLong("snoozed_until", 0L)
        if (System.currentTimeMillis() < snoozedUntil) return

        when (intent.action) {
            Intent.ACTION_BATTERY_LOW -> startAlert(context)
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_SCREEN_ON -> {
                val level = getBatteryLevel(context)
                if (level in 0..5) startAlert(context)
            }
        }
    }

    private fun getBatteryLevel(context: Context): Int {
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val lvl = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scl = i?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (lvl >= 0 && scl > 0) (lvl * 100 / scl) else -1
    }

    private fun startAlert(context: Context) {
        val i = Intent(context, LockScreenActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        context.startActivity(i)
    }
}
