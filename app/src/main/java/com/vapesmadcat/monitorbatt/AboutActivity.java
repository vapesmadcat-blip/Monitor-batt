package com.vapesmadcat.monitorbatt;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        TextView tvEmail = findViewById(R.id.tvEmail);

        if (tvEmail != null) {
            tvEmail.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
                    emailIntent.setData(Uri.parse("mailto:contato.nexusbr@gmail.com"));
                    emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Contato - Monitor de Bateria Pro");
                    startActivity(Intent.createChooser(emailIntent, "Enviar e-mail"));
                }
            });
        }
    }
}