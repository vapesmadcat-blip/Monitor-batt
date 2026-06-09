package com.batterymonitor;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Esconde a ActionBar para tela mais limpa (HTML já é fullscreen)
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        webView = findViewById(R.id.webview);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        // Para melhor performance e compatibilidade
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);

        // Carrega o HTML local (o mesmo que você forneceu)
        webView.loadUrl("file:///android_asset/index.html");

        // Inicia o serviço de monitoramento residente (foreground service)
        startBatteryMonitorService();

        Toast.makeText(this, "Monitor de Bateria iniciado!\nO alerta sonoro funciona mesmo com o app fechado.", Toast.LENGTH_LONG).show();
    }

    private void startBatteryMonitorService() {
        Intent serviceIntent = new Intent(this, BatteryMonitorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }
}