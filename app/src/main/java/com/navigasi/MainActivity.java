package com.navigasi;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override 
    public void onCreate(Bundle state) {
        super.onCreate(state);
        
        // Membuat wadah utama (Layout Vertikal)
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(32, 32, 32, 32);
        
        // Komponen Judul
        TextView title = new TextView(this);
        title.setText("Navigasi Panduan Suara");
        title.setTextSize(26);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER);
        box.addView(title);
        
        // Komponen Status / Informasi (diberi ID atau referensi agar bisa diubah)
        final TextView info = new TextView(this);
        info.setText("Aplikasi Android native siap dikembangkan.");
        info.setTextSize(16);
        info.setGravity(Gravity.CENTER);
        info.setPadding(0, 24, 0, 32);
        box.addView(info);
        
        // Menambahkan Tombol Interaktif
        Button btnMulai = new Button(this);
        btnMulai.setText("Mulai Navigasi");
        
        // Aksi ketika tombol ditekan
        btnMulai.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                info.setText("Navigasi dimulai... Mencari rute terdekat.");
            }
        });
        
        box.addView(btnMulai);
        
        // Menampilkan seluruh komponen ke layar
        setContentView(box);
    }
}