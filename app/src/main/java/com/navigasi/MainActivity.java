package com.navigasi;

import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;

public class MainActivity extends Activity implements LocationListener, TextToSpeech.OnInitListener {
    
    private TextView info;
    private LocationManager locationManager;
    private TextToSpeech tts;
    private boolean isTtsReady = false;

    // Variabel untuk menyimpan lokasi favorit (Titik Karet/Tujuan)
    private double savedLat = 0.0;
    private double savedLon = 0.0;
    private boolean isLocationSaved = false;
    private boolean isNavigating = false;

    // Status pemicu suara agar tidak terulang-ulang terus menerus
    private boolean sudahPeringatan20m = false;
    private boolean sudahTitikBelok = false;
    private boolean sudahTiba = false;

    @Override 
    public void onCreate(Bundle state) {
        super.onCreate(state);
        
        tts = new TextToSpeech(this, this);
        
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(32, 32, 32, 32);
        
        TextView title = new TextView(this);
        title.setText("Navigasi Panduan Suara");
        title.setTextSize(22);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER);
        box.addView(title);
        
        info = new TextView(this);
        info.setText("Tekan tombol di bawah untuk mulai.");
        info.setTextSize(15);
        info.setGravity(Gravity.CENTER);
        info.setPadding(0, 16, 0, 24);
        box.addView(info);
        
        // Tombol 1: Simpan Lokasi Saat Ini
        Button btnSimpan = new Button(this);
        btnSimpan.setText("SIMPAN LOKASI SAAT INI");
        btnSimpan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                simpanLokasiSaatIni();
            }
        });
        box.addView(btnSimpan);

        // Tombol 2: Mulai Navigasi ke Lokasi Tersimpan
        Button btnNavigasi = new Button(this);
        btnNavigasi.setText("MULAI NAVIGASI KE LOKASI TERSIMPAN");
        btnNavigasi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mulaiNavigasiTersimpan();
            }
        });
        box.addView(btnNavigasi);
        
        setContentView(box);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(new Locale("id", "ID"));
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsReady = true;
                ucapkanSuara("Aplikasi navigasi siap digunakan.");
            }
        }
    }

    private void ucapkanSuara(String teks) {
        if (isTtsReady && tts != null) {
            tts.speak(teks, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    private void mulaiMendengarkanGPS() {
        try {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 
                    1000, // Update tiap 1 detik
                    0.5f, // Jarak minimal 0.5 meter
                    this
                );
            } else {
                ucapkanSuara("GPS belum aktif. Mohon aktifkan GPS.");
            }
        } catch (SecurityException e) {
            ucapkanSuara("Izin lokasi ditolak.");
        }
    }

    private void simpanLokasiSaatIni() {
        mulaiMendengarkanGPS();
        info.setText("Mencari titik GPS murni untuk disimpan...");
        ucapkanSuara("Mencari titik akurat untuk disimpan.");
    }

    private void mulaiNavigasiTersimpan() {
        if (!isLocationSaved) {
            ucapkanSuara("Belum ada lokasi yang tersimpan. Silakan simpan lokasi terlebih dahulu.");
            info.setText("Belum ada lokasi tersimpan!");
            return;
        }
        
        isNavigating = true;
        sudahPeringatan20m = false;
        sudahTitikBelok = false;
        sudahTiba = false;
        
        mulaiMendengarkanGPS();
        ucapkanSuara("Navigasi ke lokasi tersimpan dimulai.");
        info.setText("Navigasi aktif menuju lokasi tersimpan...");
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location != null) {
            double currentLat = location.getLatitude();
            double currentLon = location.getLongitude();
            float akurasi = location.getAccuracy();
            
            // Saring ketat akurasi GPS di bawah 10 meter agar stabil
            if (akurasi <= 10.0f) {
                
                // Jika tombol simpan ditekan dan lokasi belum terkunci permanen
                if (!isLocationSaved && !isNavigating) {
                    savedLat = currentLat;
                    savedLon = currentLon;
                    isLocationSaved = true;
                    ucapkanSuara("Lokasi berhasil disimpan.");
                    info.setText("Lokasi Tersimpan!\nLat: " + savedLat + "\nLon: " + savedLon);
                    return;
                }

                // Jika sedang dalam mode navigasi menuju lokasi tersimpan
                if (isNavigating) {
                    float[] hasilJarak = new float[1];
                    Location.distanceBetween(currentLat, currentLon, savedLat, savedLon, hasilJarak);
                    float jarak = hasilJarak[0];

                    // 1. Peringatan 20 Meter Sebelum Belok/Tujuan
                    if (jarak <= 20.0f && jarak > 5.0f && !sudahPeringatan20m) {
                        ucapkanSuara("Perhatian, 20 meter lagi bersiap belok.");
                        sudahPeringatan20m = true;
                    }

                    // 2. Instruksi Tepat di Titik Belok (sekitar 3-5 meter)
                    if (jarak <= 5.0f && jarak > 2.0f && !sudahTitikBelok) {
                        ucapkanSuara("Belok sekarang.");
                        sudahTitikBelok = true;
                    }

                    // 3. Tiba di Tujuan (Sangat presisi: 1 sampai 2 meter)
                    if (jarak <= 2.0f && !sudahTiba) {
                        ucapkanSuara("Anda telah tiba di tujuan.");
                        info.setText("Anda telah tiba di tujuan!");
                        sudahTiba = true;
                        isNavigating = false; // Matikan navigasi setelah tiba
                    } else if (!sudahTiba) {
                        info.setText("Navigasi Aktif\n" +
                                     "Akurasi GPS: ± " + (int)akurasi + " m\n" +
                                     "Sisa Jarak: " + (int)jarak + " meter");
                    }
                }
            } else {
                info.setText("Menunggu sinyal satelit stabil...\nAkurasi saat ini: ± " + akurasi + " m");
            }
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}

    @Override
    public void onProviderEnabled(String provider) {}

    @Override
    public void onProviderDisabled(String provider) {
        ucapkanSuara("GPS dimatikan.");
        info.setText("GPS dimatikan.");
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
        super.onDestroy();
    }
}