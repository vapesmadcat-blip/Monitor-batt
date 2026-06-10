package com.enem.smartunlock

import android.content.Context
import android.content.IntentFilter
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class LockScreenActivity : ComponentActivity() {

    private val prefs by lazy { getSharedPreferences("monitor_batt", Context.MODE_PRIVATE) }
    private val vibrator by lazy { getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }
    private val tone by lazy { ToneGenerator(AudioManager.STREAM_ALARM, 95) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val enabled = prefs.getBoolean("monitoring_enabled", false)
        val snoozed = prefs.getLong("snoozed_until", 0L)
        val level = getBatteryLevel()

        if (!enabled || System.currentTimeMillis() < snoozed || level > 5) {
            finish(); return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true); setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        setContent {
            AlertScreen(
                initialLevel = level,
                onSnooze = {
                    prefs.edit().putLong("snoozed_until", System.currentTimeMillis() + 15 * 60_000L).apply()
                    vibrator?.vibrate(80)
                    finish()
                },
                onClose = { finish() },
                vibrator = vibrator,
                tone = tone
            )
        }
    }

    private fun getBatteryLevel(): Int {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val l = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val s = i?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (l >= 0 && s > 0) l * 100 / s else 4
    }

    override fun onBackPressed() {}
    override fun onDestroy() { super.onDestroy(); try { tone.release() } catch (_: Exception) {} }
}

@Composable
fun AlertScreen(initialLevel: Int, onSnooze: () -> Unit, onClose: () -> Unit, vibrator: Vibrator?, tone: ToneGenerator?) {
    val deep = Color(0xFF0B1A2F)
    val card = Color(0xFF122B3F)
    val blue = Color(0xFF3F9EF0)
    val red = Color(0xFFD94F5C)

    var level by remember { mutableStateOf(initialLevel) }

    LaunchedEffect(Unit) {
        while (isActive) {
            // Bip + vibra a cada 25s
            try {
                vibrator?.vibrate(longArrayOf(0, 140, 70, 140), -1)
                tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
            } catch (_: Exception) {}
            delay(25000)
        }
    }

    Surface(Modifier.fillMaxSize().background(deep), color = deep) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("⚠️ BATERIA CRÍTICA", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = red)
            Spacer(Modifier.height(16.dp))
            Text("$level%", fontSize = 64.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text("Conecte o carregador agora!", fontSize = 18.sp, color = Color(0xFFA0C4E2))

            Spacer(Modifier.height(32.dp))

            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = card)) {
                Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("O app vai continuar BIPANDO a cada 25 segundos até você carregar o celular.", color = Color(0xFFA0C4E2), fontSize = 15.sp)

                    Spacer(Modifier.height(20.dp))

                    Button(onClick = onSnooze, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = blue)) {
                        Text("SILENCIAR POR 15 MINUTOS", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp)) {
                        Text("FECHAR (só se já estiver carregando)")
                    }
                }
            }
        }
    }
}
