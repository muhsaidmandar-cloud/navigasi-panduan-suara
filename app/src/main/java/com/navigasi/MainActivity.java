package com.navigasi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
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
import java.net.URLEncoder;
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

    // Pilihan Stream Audio untuk TTS / Aplikasi
    private int selectedAudioStream = AudioManager.STREAM_MUSIC;
    private String namaStreamAktif = "Media / Musik";

    // Penyimpanan Lokasi & Rute Belokan
    private double savedLat = 0.0;
    private double savedLon = 0.0;
    private boolean isLocationSaved = false;
    private boolean isNavigating = false;
    private boolean sedangMencariPosisiSekarang = false;
    
    // Status Pengaktifan Fitur Eksplorasi Real-Time
    private boolean isEksplorasiFiturAktif = false;
    private boolean sedangMemindaiOtomatis = false;
    private double lastExplorationLat = 0.0;
    private double lastExplorationLon = 0.0;

    // Daftar instruksi belokan hasil unduhan rute
    private List<InstruksiRute> daftarInstruksi = new ArrayList<>();
    private int indexInstruksiAktif = 0;

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
        setVolumeControlStream(selectedAudioStream);
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
        if (isLocationSaved) {
            info.setText("Lokasi Tersimpan:\nLat: " + savedLat + ", Lon: " + savedLon);
        } else {
            info.setText("Tekan tombol di bawah untuk mulai.");
        }
        info.setTextSize(15);
        info.setGravity(Gravity.CENTER);
        info.setPadding(0, 16, 0, 24);
        box.addView(info);
        
        // --- FITUR ASLI DI MANA SAYA SEKARANG ---
        Button btnCekPosisi = new Button(this);
        btnCekPosisi.setText("DI MANA SAYA SEKARANG");
        btnCekPosisi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cekPosisiSekarangAkurat();
            }
        });
        box.addView(btnCekPosisi);

        // --- TOMBOL SAKLAR EKSPLORASI REAL-TIME (AKTIF / MATI) ---
        final Button btnToggleEksplorasi = new Button(this);
        updateTeksTombolEksplorasi(btnToggleEksplorasi);
        btnToggleEksplorasi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isEksplorasiFiturAktif = !isEksplorasiFiturAktif;
                updateTeksTombolEksplorasi(btnToggleEksplorasi);
                if (isEksplorasiFiturAktif) {
                    ucapkanSuara("Eksplorasi real-time diaktifkan. Anda akan mendengar tempat sekitar saat berjalan.");
                    mulaiMendengarkanGPS(); // Langsung aktifkan GPS untuk pantau jalan
                } else {
                    ucapkanSuara("Eksplorasi real-time dinonaktifkan.");
                }
            }
        });
        box.addView(btnToggleEksplorasi);

        // --- FITUR ASLI SIMPAN LOKASI ---
        Button btnSimpan = new Button(this);
        btnSimpan.setText("SIMPAN LOKASI SAAT INI");
        btnSimpan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                simpanLokasiSaatIni();
            }
        });
        box.addView(btnSimpan);

        // --- FITUR EDIT / KELOLA LOKASI ---
        Button btnEditLokasi = new Button(this);
        btnEditLokasi.setText("EDIT / KELOLA LOKASI TERSIMPAN");
        btnEditLokasi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tampilkanDialogKelolaLokasi();
            }
        });
        box.addView(btnEditLokasi);

        // --- FITUR ASLI NAVIGASI ---
        Button btnNavigasi = new Button(this);
        btnNavigasi.setText("MULAI NAVIGASI BELOKAN");
        btnNavigasi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mulaiNavigasiTersimpan();
            }
        });
        box.addView(btnNavigasi);

        // --- PENGATURAN SUARA & VOLUME ---
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

    private void updateTeksTombolEksplorasi(Button btn) {
        if (isEksplorasiFiturAktif) {
            btn.setText("EKSPLORASI REAL-TIME: AKTIF");
        } else {
            btn.setText("EKSPLORASI REAL-TIME: NONAKTIF");
        }
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
        infoTts.setText("Engine: " + namaEngineAktif + "\nStream: " + namaStreamAktif + "\nKecepatan: " + kecepatanBicara + "x");
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

        Button btnPilihStream = new Button(this);
        btnPilihStream.setText("PILIH JENIS STREAM VOLUME");
        btnPilihStream.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tampilkanDialogPilihanStream(infoTts);
            }
        });
        boxTts.addView(btnPilihStream);

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
                infoTts.setText("Engine: " + namaEngineAktif + "\nStream: " + namaStreamAktif + "\nKecepatan: " + kecepatanBicara + "x");
            }
        });
        boxTts.addView(btnLebihCepat);

        Button btnAturVolume = new Button(this);
        btnAturVolume.setText("ATUR LEVEL VOLUME");
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

    private void tampilkanDialogKelolaLokasi() {
        if (!isLocationSaved) {
            ucapkanSuara("Belum ada lokasi yang tersimpan untuk dikelola.");
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Kelola Lokasi");
            builder.setMessage("Belum ada lokasi tersimpan saat ini.");
            builder.setPositiveButton("Tutup", null);
            builder.show();
            return;
        }

        CharSequence[] opsi = {"Ganti dengan Lokasi Saat Ini", "Hapus Lokasi Tersimpan"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Kelola Lokasi Tersimpan\n(Lat: " + savedLat + ", Lon: " + savedLon + ")");
        builder.setItems(opsi, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    Location loc = null;
                    try {
                        if (locationManager != null) {
                            loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                        }
                    } catch (Exception e) {}

                    if (loc != null) {
                        savedLat = loc.getLatitude();
                        savedLon = loc.getLongitude();
                        isLocationSaved = true;
                        ucapkanSuara("Lokasi berhasil diperbarui dengan posisi terbaru.");
                        info.setText("Lokasi Diperbarui!\nLat: " + savedLat + "\nLon: " + savedLon);
                    } else {
                        ucapkanSuara("Gagal mengambil posisi terbaru. Lakukan 'Di Mana Saya Sekarang' terlebih dahulu.");
                    }
                } else if (which == 1) {
                    savedLat = 0.0;
                    savedLon = 0.0;
                    isLocationSaved = false;
                    ucapkanSuara("Lokasi tersimpan telah dihapus.");
                    info.setText("Tidak ada lokasi tersimpan.");
                }
            }
        });
        builder.setNegativeButton("Batal", null);
        builder.show();
        ucapkanSuara("Menu kelola lokasi dibuka.");
    }

    private void tampilkanDialogPilihanStream(final TextView infoTts) {
        final String[] namaStreamList = {"Media / Musik", "Volume Dering (Ringtone)", "Volume Notifikasi"};
        final int[] streamCodeList = {AudioManager.STREAM_MUSIC, AudioManager.STREAM_RING, AudioManager.STREAM_NOTIFICATION};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Pilih Jenis Stream Volume");
        builder.setItems(namaStreamList, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                selectedAudioStream = streamCodeList[which];
                namaStreamAktif = namaStreamList[which];
                setVolumeControlStream(selectedAudioStream);
                infoTts.setText("Engine: " + namaEngineAktif + "\nStream: " + namaStreamAktif + "\nKecepatan: " + kecepatanBicara + "x");
                ucapkanSuara("Stream volume diubah ke " + namaStreamAktif);
            }
        });
        builder.show();
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
                    infoTts.setText("Engine: " + namaEngineAktif + "\nStream: " + namaStreamAktif + "\nKecepatan: " + kecepatanBicara + "x");
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
        final int maxVolume = audioManager.getStreamMaxVolume(selectedAudioStream);
        final int currentVolume = audioManager.getStreamVolume(selectedAudioStream);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 40, 40, 40);

        final TextView tvVolume = new TextView(this);
        tvVolume.setText(namaStreamAktif + "\nLevel: " + currentVolume + " / " + maxVolume);
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
                    audioManager.setStreamVolume(selectedAudioStream, progress, 0);
                    tvVolume.setText(namaStreamAktif + "\nLevel: " + progress + " / " + maxVolume);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                ucapkanSuara("Volume diatur.");
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Atur " + namaStreamAktif);
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
                Bundle params = new Bundle();
                params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, selectedAudioStream);
                tts.speak(teks, TextToSpeech.QUEUE_FLUSH, params, null);
            } catch (Exception e) {
                try {
                    tts.speak(teks, TextToSpeech.QUEUE_FLUSH, null, null);
                } catch (Exception ex) {}
            }
        }
    }

    private void mulaiMendengarkanGPS() {
        try {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                // Update GPS setiap 3 detik atau setiap perpindahan 2 meter agar real-time
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000, 2.0f, this);
            } else {
                ucapkanSuara("GPS belum aktif.");
            }
        } catch (SecurityException e) {
            ucapkanSuara("Izin lokasi ditolak.");
        }
    }

    private void cekPosisiSekarangAkurat() {
        sedangMencariPosisiSekarang = true;
        sedangMemindaiOtomatis = false;
        mulaiMendengarkanGPS();
        ucapkanSuara("Mencari posisi akurat. Berada di luar ruangan.");
        info.setText("Mencari posisi (Target akurasi <= 3 meter)...");
    }

    // Task Latar Belakang Overpass API untuk Real-Time Eksplorasi
    private class EksplorasiRealtimeTask extends AsyncTask<Double, Void, List<String>> {
        @Override
        protected List<String> doInBackground(Double... coords) {
            List<String> hasilTempat = new ArrayList<>();
            try {
                double lat = coords[0];
                double lon = coords[1];
                
                // Cari fasilitas dalam radius 40 meter sekitar posisi real-time pengguna
                String query = "[out:json];(node(around:40," + lat + "," + lon + ")[amenity];way(around:40," + lat + "," + lon + ")[amenity];);out body 4;";
                String urlStr = "https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(query, "UTF-8");
                
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
                JSONArray elements = json.getJSONArray("elements");
                
                for (int i = 0; i < elements.length(); i++) {
                    JSONObject el = elements.getJSONObject(i);
                    if (el.has("tags")) {
                        JSONObject tags = el.getJSONObject("tags");
                        if (tags.has("name")) {
                            String nama = tags.getString("name");
                            String jenis = tags.optString("amenity", "tempat");
                            hasilTempat.add(nama);
                        }
                    }
                }
            } catch (Exception e) {}
            return hasilTempat;
        }

        @Override
        protected void onPostExecute(List<String> result) {
            sedangMemindaiOtomatis = false;
            if (result != null && !result.isEmpty()) {
                StringBuilder speechText = new StringBuilder("Sekitar Anda: ");
                for (int i = 0; i < result.size(); i++) {
                    speechText.append(result.get(i)).append(". ");
                }
                ucapkanSuara(speechText.toString());
                info.setText("Eksplorasi Real-Time Aktif:\n" + speechText.toString());
            }
        }
    }

    private void simpanLokasiSaatIni() {
        if (!isLocationSaved && savedLat == 0.0) {
            Location loc = null;
            try {
                if (locationManager != null) {
                    loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                }
            } catch (Exception e) {}
            
            if (loc != null) {
                savedLat = loc.getLatitude();
                savedLon = loc.getLongitude();
                isLocationSaved = true;
                ucapkanSuara("Lokasi berhasil disimpan.");
                info.setText("Lokasi Tersimpan!\nLat: " + savedLat + "\nLon: " + savedLon);
                return;
            }

            ucapkanSuara("Tekan tombol Di Mana Saya Sekarang terlebih dahulu.");
            info.setText("Belum ada titik akurat!");
            return;
        }
        ucapkanSuara("Lokasi sudah tersimpan.");
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
        
        Location loc = null;
        try {
            loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        } catch (Exception e) {}

        double startLat = (loc != null) ? loc.getLatitude() : savedLat - 0.001;
        double startLon = (loc != null) ? loc.getLongitude() : savedLon - 0.001;

        new AmbilRuteTask().execute(startLat, startLon, savedLat, savedLon);
    }

    private class AmbilRuteTask extends AsyncTask<Double, Void, List<InstruksiRute>> {
        @Override
        protected List<InstruksiRute> doInBackground(Double... coords) {
            List<InstruksiRute> hasil = new ArrayList<>();
            try {
                double sLat = coords[0];
                double sLon = coords[1];
                double eLat = coords[2];
                double eLon = coords[3];

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

            // LOGIKA EKSPLORASI REAL-TIME OTOMATIS SAAT BERJALAN
            if (isEksplorasiFiturAktif && !isNavigating && !sedangMemindaiOtomatis) {
                float[] jarakPindah = new float[1];
                if (lastExplorationLat == 0.0 && lastExplorationLon == 0.0) {
                    lastExplorationLat = currentLat;
                    lastExplorationLon = currentLon;
                }
                
                Location.distanceBetween(lastExplorationLat, lastExplorationLon, currentLat, currentLon, jarakPindah);
                
                // Jika pengguna sudah berjalan sejauh minimal 30 meter dari titik scan terakhir
                if (jarakPindah[0] >= 30.0f) {
                    lastExplorationLat = currentLat;
                    lastExplorationLon = currentLon;
                    sedangMemindaiOtomatis = true;
                    new EksplorasiRealtimeTask().execute(currentLat, currentLon);
                }
            }

            if (isNavigating) {
                if (!daftarInstruksi.isEmpty() && indexInstruksiAktif < daftarInstruksi.size()) {
                    InstruksiRute instruksi = daftarInstruksi.get(indexInstruksiAktif);
                    
                    float[] hasilJarak = new float[1];
                    Location.distanceBetween(currentLat, currentLon, instruksi.lat, instruksi.lon, hasilJarak);
                    float jarakKeBelokan = hasilJarak[0];

                    if (jarakKeBelokan <= 20.0f && !instruksi.sudahDiumumkan) {
                        ucapkanSuara("20 meter lagi, " + instruksi.pesanPanduan);
                        instruksi.sudahDiumumkan = true;
                    }

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