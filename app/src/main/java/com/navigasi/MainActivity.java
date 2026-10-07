package com.navigasi;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
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

    // Pengaturan Suara & TTS
    private float kecepatanBicara = 1.0f; 
    private float nadaBicara = 1.0f;     
    private int levelVolume = 5; 

    private String targetTtsEngine = null; // null = Default sistem lokal aplikasi
    private String namaEngineAktif = "Default Sistem";

    // Penyimpanan Lokasi
    private double savedLat = 0.0;
    private double savedLon = 0.0;
    private boolean isLocationSaved = false;
    private boolean isNavigating = false;
    private boolean sedangMencariPosisiSekarang = false;

    private boolean sudahPeringatan20m = false;
    private boolean sudahTiba = false;

    @Override 
    public void onCreate(Bundle state) {
        super.onCreate(state);
        inisialisasiTtsMandiri();
        tampilkanMenuUtama();
    }

    private void inisialisasiTtsMandiri() {
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception e) {}
        }
        
        try {
            if (targetTtsEngine != null) {
                tts = new TextToSpeech(this, this, targetTtsEngine);
            } else {
                tts = new TextToSpeech(this, this);
            }
        } catch (Exception e) {
            tts = new TextToSpeech(this, this);
        }
    }

    private void tampilkanMenuUtama() {
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
        
        Button btnCekPosisi = new Button(this);
        btnCekPosisi.setText("DI MANA SAYA SEKARANG");
        btnCekPosisi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cekPosisiSekarangAkurat();
            }
        });
        box.addView(btnCekPosisi);

        Button btnSimpan = new Button(this);
        btnSimpan.setText("SIMPAN LOKASI SAAT INI");
        btnSimpan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                simpanLokasiSaatIni();
            }
        });
        box.addView(btnSimpan);

        Button btnNavigasi = new Button(this);
        btnNavigasi.setText("MULAI NAVIGASI KE LOKASI TERSIMPAN");
        btnNavigasi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mulaiNavigasiTersimpan();
            }
        });
        box.addView(btnNavigasi);

        Button btnPengaturanTts = new Button(this);
        btnPengaturanTts.setText("PENGATURAN SUARA & VOLUME");
        btnPengaturanTts.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tampilkanHalamanPengaturanTts();
            }
        });
        box.addView(btnPengaturanTts);
        
        setContentView(box);
    }

    private void tampilkanHalamanPengaturanTts() {
        LinearLayout boxTts = new LinearLayout(this);
        boxTts.setOrientation(LinearLayout.VERTICAL);
        boxTts.setGravity(Gravity.CENTER);
        boxTts.setPadding(32, 32, 32, 32);

        TextView titleTts = new TextView(this);
        titleTts.setText("Pengaturan Suara Mandiri");
        titleTts.setTextSize(22);
        titleTts.setTextColor(Color.BLACK);
        titleTts.setGravity(Gravity.CENTER);
        boxTts.addView(titleTts);

        final TextView infoTts = new TextView(this);
        infoTts.setText("Engine Aktif: " + namaEngineAktif + "\nKecepatan: " + kecepatanBicara + "x | Volume: " + levelVolume);
        infoTts.setTextSize(15);
        infoTts.setGravity(Gravity.CENTER);
        infoTts.setPadding(0, 24, 0, 24);
        boxTts.addView(infoTts);

        // Tombol 1: Menggunakan Google TTS
        Button btnGoogle = new Button(this);
        btnGoogle.setText("GUNAKAN GOOGLE TTS");
        btnGoogle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                targetTtsEngine = "com.google.android.tts";
                namaEngineAktif = "Google TTS";
                inisialisasiTtsMandiri();
                infoTts.setText("Engine Aktif: " + namaEngineAktif + "\nKecepatan: " + kecepatanBicara + "x | Volume: " + levelVolume);
                ucapkanSuara("Menggunakan Google TTS.");
            }
        });
        boxTts.addView(btnGoogle);

        // Tombol 2: Menggunakan Vocalizer Ex2 (Paket standar Vocalizer)
        Button btnVocalizer = new Button(this);
        btnVocalizer.setText("GUNAKAN VOCALIZER EX2");
        btnVocalizer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                targetTtsEngine = "es.codefactory.vocalizer.en.eFIGS"; 
                namaEngineAktif = "Vocalizer Ex2";
                inisialisasiTtsMandiri();
                infoTts.setText("Engine Aktif: " + namaEngineAktif + "\nKecepatan: " + kecepatanBicara + "x | Volume: " + levelVolume);
                ucapkanSuara("Menggunakan Vocalizer Ex2.");
            }
        });
        boxTts.addView(btnVocalizer);

        // Tombol 3: Menggunakan Default Sistem (Aman untuk pembaca layar lokal)
        Button btnDefault = new Button(this);
        btnDefault.setText("GUNAKAN DEFAULT SISTEM");
        btnDefault.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                targetTtsEngine = null;
                namaEngineAktif = "Default Sistem";
                inisialisasiTtsMandiri();
                infoTts.setText("Engine Aktif: " + namaEngineAktif + "\nKecepatan: " + kecepatanBicara + "x | Volume: " + levelVolume);
                ucapkanSuara("Menggunakan default sistem.");
            }
        });
        boxTts.addView(btnDefault);

        Button btnLebihCepat = new Button(this);
        btnLebihCepat.setText("UBAH KECEPATAN BICARA");
        btnLebihCepat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (kecepatanBicara < 2.0f) {
                    kecepatanBicara += 0.25f;
                } else {
                    kecepatanBicara = 1.0f; 
                }
                terapkanSetelanTts();
                ucapkanSuara("Kecepatan diatur ke " + kecepatanBicara);
            }
        });
        boxTts.addView(btnLebihCepat);

        Button btnAturVolume = new Button(this);
        btnAturVolume.setText("ATUR VOLUME MEDIA SUARA");
        btnAturVolume.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                levelVolume += 3;
                if (levelVolume > maxVolume) {
                    levelVolume = maxVolume / 2; 
                }
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, levelVolume, AudioManager.FLAG_SHOW_UI);
                ucapkanSuara("Volume disesuaikan.");
            }
        });
        boxTts.addView(btnAturVolume);

        Button btnUjiSuara = new Button(this);
        btnUjiSuara.setText("UJI SUARA TTS");
        btnUjiSuara.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ucapkanSuara("Uji coba suara navigasi aktif.");
            }
        });
        boxTts.addView(btnUjiSuara);

        Button btnKembali = new Button(this);
        btnKembali.setText("KEMBALI KE MENU UTAMA");
        btnKembali.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tampilkanMenuUtama();
            }
        });
        boxTts.addView(btnKembali);

        setContentView(boxTts);
        ucapkanSuara("Pengaturan suara dibuka.");
    }

    private void terapkanSetelanTts() {
        if (tts != null) {
            try {
                tts.setSpeechRate(kecepatanBicara);
                tts.setPitch(nadaBicara);
            } catch (Exception e) {}
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            try {
                int result = tts.setLanguage(new Locale("id", "ID"));
                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    isTtsReady = true;
                    terapkanSetelanTts();
                    ucapkanSuara("Suara siap.");
                }
            } catch (Exception e) {}
        }
    }

    private void ucapkanSuara(String teks) {
        if (isTtsReady && tts != null) {
            try {
                tts.speak(teks, TextToSpeech.QUEUE_FLUSH, null, null);
            } catch (Exception e) {}
        }
    }

    private void mulaiMendengarkanGPS() {
        try {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0.5f, this);
            } else {
                ucapkanSuara("GPS belum aktif.");
            }
        } catch (SecurityException e) {
            ucapkanSuara("Izin lokasi ditolak.");
        }
    }

    private void cekPosisiSekarangAkurat() {
        sedangMencariPosisiSekarang = true;
        mulaiMendengarkanGPS();
        ucapkanSuara("Mencari posisi akurat. Berada di luar ruangan.");
        info.setText("Mencari posisi (Target akurasi <= 3 meter)...");
    }

    private void simpanLokasiSaatIni() {
        if (!isLocationSaved) {
            ucapkanSuara("Tekan tombol Di Mana Saya Sekarang terlebih dahulu.");
            info.setText("Belum ada titik akurat!");
            return;
        }
        ucapkanSuara("Lokasi berhasil disimpan.");
        info.setText("Lokasi Tersimpan!\nLat: " + savedLat + "\nLon: " + savedLon);
    }

    private void mulaiNavigasiTersimpan() {
        if (!isLocationSaved) {
            ucapkanSuara("Belum ada lokasi tersimpan.");
            info.setText("Belum ada lokasi!");
            return;
        }
        isNavigating = true;
        sudahPeringatan20m = false;
        sudahTiba = false;
        mulaiMendengarkanGPS();
        ucapkanSuara("Navigasi dimulai.");
        info.setText("Navigasi aktif menuju lokasi tersimpan...");
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location != null) {
            double currentLat = location.getLatitude();
            double currentLon = location.getLongitude();
            float akurasi = location.getAccuracy();
            
            if (sedangMencariPosisiSekarang) {
                if (akurasi <= 3.0f) {
                    savedLat = currentLat;
                    savedLon = currentLon;
                    isLocationSaved = true;
                    sedangMencariPosisiSekarang = false;
                    ucapkanSuara("Posisi terkunci akurat.");
                    info.setText("Posisi Akurat:\nLat: " + currentLat + "\nLon: " + currentLon + "\nAkurasi: ± " + akurasi + " m");
                } else {
                    info.setText("Menyaring sinyal GPS...\nAkurasi: ± " + akurasi + " meter (Target <= 3m)");
                }
                return;
            }

            if (akurasi <= 10.0f) {
                if (isNavigating) {
                    float[] hasilJarak = new float[1];
                    Location.distanceBetween(currentLat, currentLon, savedLat, savedLon, hasilJarak);
                    float jarak = hasilJarak[0];

                    if (jarak <= 20.0f && jarak > 2.0f && !sudahPeringatan20m) {
                        ucapkanSuara("20 meter lagi mendekati tujuan.");
                        sudahPeringatan20m = true;
                    }

                    if (jarak <= 2.0f && !sudahTiba) {
                        ucapkanSuara("Anda telah tiba di tujuan.");
                        info.setText("Tiba di tujuan!");
                        sudahTiba = true;
                        isNavigating = false; 
                    } else if (!sudahTiba) {
                        info.setText("Navigasi GPS Murni\nAkurasi: ± " + (int)akurasi + " m\nJarak: " + (int)jarak + " m");
                    }
                }
            } else {
                info.setText("Menunggu sinyal satelit...\nAkurasi: ± " + akurasi + " m");
            }
        }
    }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    @Override public void onProviderEnabled(String provider) {}
    @Override public void onProviderDisabled(String provider) { ucapkanSuara("GPS dimatikan."); }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception e) {}
        }
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
        super.onDestroy();
    }
}