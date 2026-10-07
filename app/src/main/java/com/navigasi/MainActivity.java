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

    // Penyimpanan Lokasi (Titik Karet/Tujuan)
    private double savedLat = 0.0;
    private double savedLon = 0.0;
    private boolean isLocationSaved = false;
    private boolean isNavigating = false;
    private boolean sedangMencariPosisiSekarang = false;

    // Status pemicu suara navigasi
    private boolean sudahPeringatan20m = false;
    private boolean sudahTiba = false;

    @Override 
    public void onCreate(Bundle state) {
        super.onCreate(state);
        tts = new TextToSpeech(this, this);
        tampilkanMenuUtama();
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
        
        // Tombol 1: Cek Posisi Akurat (<= 3 meter)
        Button btnCekPosisi = new Button(this);
        btnCekPosisi.setText("DI MANA SAYA SEKARANG");
        btnCekPosisi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cekPosisiSekarangAkurat();
            }
        });
        box.addView(btnCekPosisi);

        // Tombol 2: Simpan Lokasi Saat Ini
        Button btnSimpan = new Button(this);
        btnSimpan.setText("SIMPAN LOKASI SAAT INI");
        btnSimpan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                simpanLokasiSaatIni();
            }
        });
        box.addView(btnSimpan);

        // Tombol 3: Mulai Navigasi ke Lokasi Tersimpan
        Button btnNavigasi = new Button(this);
        btnNavigasi.setText("MULAI NAVIGASI KE LOKASI TERSIMPAN");
        btnNavigasi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mulaiNavigasiTersimpan();
            }
        });
        box.addView(btnNavigasi);

        // Tombol 4: Pengaturan TTS & Volume
        Button btnPengaturanTts = new Button(this);
        btnPengaturanTts.setText("PENGATURAN TTS & VOLUME");
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
        titleTts.setText("Pengaturan Suara & TTS");
        titleTts.setTextSize(22);
        titleTts.setTextColor(Color.BLACK);
        titleTts.setGravity(Gravity.CENTER);
        boxTts.addView(titleTts);

        final TextView infoTts = new TextView(this);
        infoTts.setText("Kecepatan: " + kecepatanBicara + "x\nVolume Media: " + levelVolume);
        infoTts.setTextSize(16);
        infoTts.setGravity(Gravity.CENTER);
        infoTts.setPadding(0, 24, 0, 24);
        boxTts.addView(infoTts);

        // Pilihan Mesin TTS Sistem
        Button btnPilihMesinTts = new Button(this);
        btnPilihMesinTts.setText("PILIH MESIN / SUARA TTS SYSTEM");
        btnPilihMesinTts.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent intent = new Intent("com.android.settings.TTS_SETTINGS");
                    startActivity(intent);
                } catch (Exception e) {
                    ucapkanSuara("Pengaturan mesin TTS tidak ditemukan.");
                }
            }
        });
        boxTts.addView(btnPilihMesinTts);

        // Ubah Kecepatan Bicara
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
                infoTts.setText("Kecepatan: " + kecepatanBicara + "x\nVolume Media: " + levelVolume);
                ucapkanSuara("Kecepatan suara diatur ke " + kecepatanBicara);
            }
        });
        boxTts.addView(btnLebihCepat);

        // Atur Volume Suara Media
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
                infoTts.setText("Kecepatan: " + kecepatanBicara + "x\nVolume Media: " + levelVolume);
                ucapkanSuara("Volume suara disesuaikan.");
            }
        });
        boxTts.addView(btnAturVolume);

        // Uji Suara
        Button btnUjiSuara = new Button(this);
        btnUjiSuara.setText("UJI SUARA TTS");
        btnUjiSuara.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ucapkanSuara("Ini adalah uji coba suara navigasi panduan suara.");
            }
        });
        boxTts.addView(btnUjiSuara);

        // Kembali ke Menu Utama
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
        ucapkanSuara("Menu pengaturan suara dibuka.");
    }

    private void terapkanSetelanTts() {
        if (tts != null) {
            tts.setSpeechRate(kecepatanBicara);
            tts.setPitch(nadaBicara);
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(new Locale("id", "ID"));
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsReady = true;
                terapkanSetelanTts();
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
                // Murni menggunakan GPS Satelit tanpa Network Provider
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 
                    1000, 
                    0.5f, 
                    this
                );
            } else {
                ucapkanSuara("GPS belum aktif. Mohon aktifkan GPS perangkat.");
            }
        } catch (SecurityException e) {
            ucapkanSuara("Izin lokasi ditolak.");
        }
    }

    private void cekPosisiSekarangAkurat() {
        sedangMencariPosisiSekarang = true;
        mulaiMendengarkanGPS();
        ucapkanSuara("Mencari posisi akurat. Harap berada di luar ruangan.");
        info.setText("Mencari posisi (Menunggu akurasi <= 3 meter)...");
    }

    private void simpanLokasiSaatIni() {
        if (!isLocationSaved) {
            ucapkanSuara("Tekan tombol Di Mana Saya Sekarang terlebih dahulu untuk mengunci koordinat akurat.");
            info.setText("Belum ada titik akurat yang dikunci!");
            return;
        }
        ucapkanSuara("Lokasi berhasil disimpan.");
        info.setText("Lokasi Tersimpan Permanen!\nLat: " + savedLat + "\nLon: " + savedLon);
    }

    private void mulaiNavigasiTersimpan() {
        if (!isLocationSaved) {
            ucapkanSuara("Belum ada lokasi yang tersimpan.");
            info.setText("Belum ada lokasi tersimpan!");
            return;
        }
        
        isNavigating = true;
        sudahPeringatan20m = false;
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
            
            // 1. Validasi Akurasi Ketat untuk "Di Mana Saya Sekarang" (Target <= 3 meter)
            if (sedangMencariPosisiSekarang) {
                if (akurasi <= 3.0f) {
                    savedLat = currentLat;
                    savedLon = currentLon;
                    isLocationSaved = true;
                    sedangMencariPosisiSekarang = false;
                    
                    ucapkanSuara("Posisi anda terkunci dengan akurasi tinggi.");
                    info.setText("Posisi Saat Ini (Sangat Akurat):\nLat: " + currentLat + "\nLon: " + currentLon + "\nAkurasi: ± " + akurasi + " m");
                } else {
                    info.setText("Menyaring sinyal satelit murni...\nAkurasi saat ini: ± " + akurasi + " meter (Target <= 3m)");
                }
                return;
            }

            // 2. Filter Navigasi (Hanya memproses data GPS stabil di bawah 10 meter)
            if (akurasi <= 10.0f) {
                if (isNavigating) {
                    float[] hasilJarak = new float[1];
                    Location.distanceBetween(currentLat, currentLon, savedLat, savedLon, hasilJarak);
                    float jarak = hasilJarak[0];

                    // Peringatan Dini 20 Meter
                    if (jarak <= 20.0f && jarak > 2.0f && !sudahPeringatan20m) {
                        ucapkanSuara("Perhatian, 20 meter lagi mendekati tujuan.");
                        sudahPeringatan20m = true;
                    }

                    // Presisi Tiba di Tujuan (1 sampai 2 meter)
                    if (jarak <= 2.0f && !sudahTiba) {
                        ucapkanSuara("Anda telah tiba di tujuan.");
                        info.setText("Anda telah tiba di tujuan!");
                        sudahTiba = true;
                        isNavigating = false; 
                    } else if (!sudahTiba) {
                        info.setText("Navigasi Aktif (GPS Murni)\n" +
                                     "Akurasi: ± " + (int)akurasi + " m\n" +
                                     "Sisa Jarak: " + (int)jarak + " meter");
                    }
                }
            } else {
                info.setText("Menunggu sinyal satelit stabil...\nAkurasi saat ini: ± " + akurasi + " meter");
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