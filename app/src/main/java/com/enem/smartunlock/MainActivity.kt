package com.enem.smartunlock

import android.content.Context
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.widget.Toast
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

class MainActivity : ComponentActivity() {

    private val prefs by lazy { getSharedPreferences("monitor_batt", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            HomeScreen(
                onToggle = { enable ->
                    prefs.edit().putBoolean("monitoring_enabled", enable).apply()
                    Toast.makeText(
                        this,
                        if (enable) "✅ Monitoramento ATIVADO! Bipará forte quando bateria ≤ 5%." 
                        else "Monitoramento desativado.",
                        Toast.LENGTH_LONG
                    ).show()
                },
                getBattery = { getBattery() },
                isEnabled = { prefs.getBoolean("monitoring_enabled", false) }
            )
        }
    }

    private fun getBattery(): Pair<Int, Boolean> {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val lvl = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val pct = if (lvl >= 0 && scale > 0) lvl * 100 / scale else 0
        return pct to charging
    }
}

@Composable
fun HomeScreen(onToggle: (Boolean) -> Unit, getBattery: () -> Pair<Int, Boolean>, isEnabled: () -> Boolean) {
    val deep = Color(0xFF0B1A2F)
    val card = Color(0xFF122B3F)
    val blue = Color(0xFF3F9EF0)
    val green = Color(0xFF21B573)
    val red = Color(0xFFD94F5C)

    var (pct, charging) by remember { mutableStateOf(getBattery()) }
    var enabled by remember { mutableStateOf(isEnabled()) }

    LaunchedEffect(Unit) {
        while (true) {
            pct = getBattery().first
            charging = getBattery().second
            delay(18000)
        }
    }

    Surface(Modifier.fillMaxSize().background(deep), color = deep) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = card)) {
                Column(Modifier.padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔋 Monitor-batt", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(6.dp))
                    Text("Alerta de bateria baixa com bip sonoro", fontSize = 14.sp, color = Color(0xFFA0C4E2))

                    Spacer(Modifier.height(22.dp))

                    Text("$pct%", fontSize = 68.sp, fontWeight = FontWeight.ExtraBold, color = if (pct <= 15) red else if (pct <= 30) Color(0xFFFFB300) else green)
                    Text(if (charging) "⚡ Carregando" else "Descarregando", fontSize = 17.sp, color = if (charging) green else Color(0xFFA0C4E2))

                    Spacer(Modifier.height(18.dp))

                    Text(
                        if (enabled) "MONITORAMENTO ATIVO ✅" else "MONITORAMENTO INATIVO",
                        color = if (enabled) green else red, fontSize = 13.sp, fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(18.dp))

                    Button(
                        onClick = {
                            enabled = !enabled
                            onToggle(enabled)
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (enabled) red else blue)
                    ) {
                        Text(if (enabled) "DESATIVAR MONITORAMENTO" else "ATIVAR MONITORAMENTO", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                    }

                    Spacer(Modifier.height(14.dp))

                    Text("Quando ativado e a bateria cair para 5% ou menos, o celular abre o alerta e bipa forte a cada 25 segundos automaticamente.", color = Color(0xFFA0C4E2), fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
        }
    }
}
