package com.navigasi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements LocationListener, TextToSpeech.OnInitListener {
    
    private TextView info;
    private LocationManager locationManager;
    private TextToSpeech tts;
    private boolean isTtsReady = false;

    // Pengaturan Suara & TTS
    private float kecepatanBicara = 1.0f; 
    private float nadaBicara = 1.0f;     
    private String targetTtsEngine = null; 
    private String namaEngineAktif = "Default Sistem";

    // Penyimpanan Lokasi & Rute Belokan
    private double savedLat = 0.0;
    private double savedLon = 0.0;
    private boolean isLocationSaved = false;
    private boolean isNavigating = false;
    private boolean sedangMencariPosisiSekarang = false;

    // Daftar instruksi belokan hasilunduhan rute
    private List<InstruksiRute> daftarInstruksi = new ArrayList<>();
    private int indexInstruksiAktif = 0;

    // Kelas bantu untuk menyimpan data instruksi belokan
    private static class InstruksiRute {
        double lat;
        double lon;
        String pesanPanduan;
        boolean sudahDiumumkan = false;

        public InstruksiRute(double lat, double lon, String pesanPanduan) {
            this.lat = lat;
            this.lon = lon;
            this.pesanPanduan = pesanPanduan;
        }
    }

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
            if (targetTtsEngine != null && !targetTtsEngine.isEmpty()) {
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
        title.setText("Navigasi Panduan Belokan");
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
        btnNavigasi.setText("MULAI NAVIGASI BELOKAN");
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
        infoTts.setText("Engine Aktif: " + namaEngineAktif + "\nKecepatan: " + kecepatanBicara + "x");
        infoTts.setTextSize(15);
        infoTts.setGravity(Gravity.CENTER);
        infoTts.setPadding(0, 24, 0, 24);
        boxTts.addView(infoTts);

        Button btnPilihEngine = new Button(this);
        btnPilihEngine.setText("PILIH MESIN TTS TERINSTAL");
        btnPilihEngine.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tampilkanDialogPilihanTts(infoTts);
            }
        });
        boxTts.addView(btnPilihEngine);

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
                infoTts.setText("Engine Aktif: " + namaEngineAktif + "\nKecepatan: " + kecepatanBicara + "x");
            }
        });
        boxTts.addView(btnLebihCepat);

        Button btnAturVolume = new Button(this);
        btnAturVolume.setText("ATUR VOLUME MEDIA");
        btnAturVolume.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tampilkanDialogVolume();
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

    private void tampilkanDialogPilihanTts(final TextView infoTts) {
        try {
            final List<String> namaEngineList = new ArrayList<>();
            final List<String> packageEngineList = new ArrayList<>();

            namaEngineList.add("Default Sistem");
            packageEngineList.add(null);

            if (tts != null) {
                List<TextToSpeech.EngineInfo> engines = tts.getEngines();
                if (engines != null) {
                    for (TextToSpeech.EngineInfo engine : engines) {
                        namaEngineList.add(engine.label);
                        packageEngineList.add(engine.name);
                    }
                }
            }

            CharSequence[] items = namaEngineList.toArray(new CharSequence[0]);
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Pilih Mesin TTS");
            builder.setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    targetTtsEngine = packageEngineList.get(which);
                    namaEngineAktif = namaEngineList.get(which);
                    inisialisasiTtsMandiri();
                    infoTts.setText("Engine Aktif: " + namaEngineAktif + "\nKecepatan: " + kecepatanBicara + "x");
                    ucapkanSuara("Berhasil beralih ke " + namaEngineAktif);
                }
            });
            builder.show();
        } catch (Exception e) {
            ucapkanSuara("Gagal memuat daftar TTS.");
        }
    }

    private void tampilkanDialogVolume() {
        final AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        final int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        final int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 40, 40, 40);

        final TextView tvVolume = new TextView(this);
        tvVolume.setText("Level Volume Media: " + currentVolume + " / " + maxVolume);
        tvVolume.setTextSize(16);
        tvVolume.setGravity(Gravity.CENTER);
        layout.addView(tvVolume);

        final SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(maxVolume);
        seekBar.setProgress(currentVolume);
        seekBar.setPadding(20, 40, 20, 20);
        layout.addView(seekBar);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0);
                    tvVolume.setText("Level Volume Media: " + progress + " / " + maxVolume);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                ucapkanSuara("Volume diatur.");
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Atur Volume Suara");
        builder.setView(layout);
        builder.setPositiveButton("Tutup", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.show();
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
        indexInstruksiAktif = 0;
        daftarInstruksi.clear();
        
        ucapkanSuara("Mengunduh rute belokan...");
        info.setText("Menghitung rute perjalanan...");
        
        // Memulai pengambilan jalur rute dari layanan terbuka secara latar belakang
        Location loc = null;
        try {
            loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        } catch (Exception e) {}

        double startLat = (loc != null) ? loc.getLatitude() : savedLat - 0.001;
        double startLon = (loc != null) ? loc.getLongitude() : savedLon - 0.001;

        new AmbilRuteTask().execute(startLat, startLon, savedLat, savedLon);
    }

    // Tugas latar belakang untuk mengambil data petunjuk arah jalan (Turn-by-Turn)
    private class AmbilRuteTask extends AsyncTask<Double, Void, List<InstruksiRute>> {
        @Override
        protected List<InstruksiRute> doInBackground(Double... coords) {
            List<InstruksiRute> hasil = new ArrayList<>();
            try {
                double sLat = coords[0];
                double sLon = coords[1];
                double eLat = coords[2];
                double eLon = coords[3];

                // Menggunakan OSRM public routing engine untuk mendapatkan instruksi belokan jalan nyata
                String urlStr = "https://router.project-osrm.org/route/v1/walking/" + sLon + "," + sLat + ";" + eLon + "," + eLat + "?overview=false&steps=true&geometries=geojson&language=id";
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                JSONObject json = new JSONObject(sb.toString());
                JSONArray routes = json.getJSONArray("routes");
                if (routes.length() > 0) {
                    JSONObject route = routes.getJSONObject(0);
                    JSONArray legs = route.getJSONArray("legs");
                    if (legs.length() > 0) {
                        JSONArray steps = legs.getJSONObject(0).getJSONArray("steps");
                        for (int i = 0; i < steps.length(); i++) {
                            JSONObject step = steps.getJSONObject(i);
                            String maneuver = step.getJSONObject("maneuver").getString("instruction");
                            JSONArray locArr = step.getJSONObject("maneuver").getJSONArray("location");
                            double stepLon = locArr.getDouble(0);
                            double stepLat = locArr.getDouble(1);
                            
                            hasil.add(new InstruksiRute(stepLat, stepLon, maneuver));
                        }
                    }
                }
            } catch (Exception e) {}
            return hasil;
        }

        @Override
        protected void onPostExecute(List<InstruksiRute> result) {
            if (result != null && !result.isEmpty()) {
                daftarInstruksi = result;
                mulaiMendengarkanGPS();
                ucapkanSuara("Rute belokan siap. Navigasi dimulai.");
                info.setText("Navigasi Belokan Aktif (" + daftarInstruksi.size() + " titik instruksi)");
            } else {
                // Fallback jika gagal mengambil rute online, gunakan panduan garis lurus
                daftarInstruksi.add(new InstruksiRute(savedLat, savedLon, "Tuju titik akhir tujuan."));
                mulaiMendengarkanGPS();
                ucapkanSuara("Navigasi garis lurus dimulai.");
                info.setText("Navigasi Titik Aktif");
            }
        }
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

            if (isNavigating) {
                // Periksa instruksi belokan aktif
                if (!daftarInstruksi.isEmpty() && indexInstruksiAktif < daftarInstruksi.size()) {
                    InstruksiRute instruksi = daftarInstruksi.get(indexInstruksiAktif);
                    
                    float[] hasilJarak = new float[1];
                    Location.distanceBetween(currentLat, currentLon, instruksi.lat, instruksi.lon, hasilJarak);
                    float jarakKeBelokan = hasilJarak[0];

                    // Berikan peringatan 20 meter sebelum titik belokan/langkah
                    if (jarakKeBelokan <= 20.0f && !instruksi.sudahDiumumkan) {
                        ucapkanSuara("20 meter lagi, " + instruksi.pesanPanduan);
                        instruksi.sudahDiumumkan = true;
                    }

                    // Jika sudah melewati titik instruksi (< 4 meter), pindah ke instruksi berikutnya
                    if (jarakKeBelokan <= 4.0f) {
                        indexInstruksiAktif++;
                        if (indexInstruksiAktif < daftarInstruksi.size()) {
                            InstruksiRute nextInstruksi = daftarInstruksi.get(indexInstruksiAktif);
                            ucapkanSuara(nextInstruksi.pesanPanduan);
                        } else {
                            ucapkanSuara("Anda telah tiba di tujuan.");
                            info.setText("Tiba di tujuan!");
                            isNavigating = false;
                        }
                    } else {
                        info.setText("Panduan Belokan:\n" + instruksi.pesanPanduan + "\nJarak: " + (int)jarakKeBelokan + " m");
                    }
                }
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