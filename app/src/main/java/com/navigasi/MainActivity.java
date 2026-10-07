package com.navigasi;

import android.app.Activity;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends Activity {
    
    private static final int PERMISSION_REQUEST_CODE = 100;
    private TextView info;

    @Override 
    public void onCreate(Bundle state) {
        super.onCreate(state);
        
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(32, 32, 32, 32);
        
        TextView title = new TextView(this);
        title.setText("Navigasi Panduan Suara");
        title.setTextSize(26);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER);
        box.addView(title);
        
        info = new TextView(this);
        info.setText("Aplikasi Android native siap dikembangkan.");
        info.setTextSize(16);
        info.setGravity(Gravity.CENTER);
        info.setPadding(0, 24, 0, 32);
        box.addView(info);
        
        Button btnMulai = new Button(this);
        btnMulai.setText("MULAI NAVIGASI");
        
        btnMulai.setOnClickListener(new View.NavigatorOnClickListener() { // atau View.OnClickListener
            @Override
            public void onClick(View v) {
                cekDanMintaIzinLokasi();
            }
        });
        
        // Perbaikan listener klik standar Android
        btnMulai.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cekDanMintaIzinLokasi();
            }
        });
        
        box.addView(btnMulai);
        setContentView(box);
    }

    private void cekDanMintaIzinLokasi() {
        // Memeriksa apakah izin akses lokasi sudah diberikan
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED) {
            
            info.setText("Meminta izin akses lokasi GPS...");
            // Meminta izin secara langsung ke pengguna
            ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 
                    PERMISSION_REQUEST_CODE);
        } else {
            info.setText("Navigasi dimulai... Lokasi terdeteksi, mencari rute.");
            // Di sinilah nanti logika Google Maps atau pelacakan koordinat GPS dijalankan
        }
    }
}