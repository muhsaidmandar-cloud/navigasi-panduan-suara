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
import android.widget.EditText;
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
        
        // --- FITUR DI MANA SAYA SEKARANG ---
        Button btnCekPosisi = new Button(this);
        btnCekPosisi.setText("DI MANA SAYA SEKARANG");
        btnCekPosisi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cekPosisiSekarangAkurat();
            }
        });
        box.addView(btnCekPosisi);

        // --- FITUR PENCARIAN LOKASI TUJUAN ---
        Button btnCariLokasi = new Button(this);
        btnCariLokasi.setText("CARI LOKASI TUJUAN (TEKS)");
        btnCariLokasi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tampilkanDialogPencarianLokasi();
            }
        });
        box.addView(btnCariLokasi);

        // --- TOMBOL SAKLAR EKSPLORASI REAL-TIME ---
        final Button btnToggleEksplorasi = new Button(this);
        updateTeksTombolEksplorasi(btnToggleEksplorasi);
        btnToggleEksplorasi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isEksplorasiFiturAktif = !isEksplorasiFiturAktif;
                updateTeksTombolEksplorasi(btnToggleEksplorasi);
                if (isEksplorasiFiturAktif) {
                    ucapkanSuara("Eksplorasi real-time diaktifkan.");
                    mulaiMendengarkanGPS();
                } else {
                    ucapkanSuara("Eksplorasi real-time dinonaktifkan.");
                }
            }
        });
        box.addView(btnToggleEksplorasi);

        // --- FITUR SIMPAN LOKASI SAAT INI ---
        Button btnSimpan = new Button(this);
        btnSimpan.setText("SIMPAN LOKASI SAAT INI");
        btnSimpan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                simpanLokasiSaatIni();
            }
        });
        box.addView(btnSimpan);

        // --- FITUR KELOLA LOKASI ---
        Button btnEditLokasi = new Button(this);
        btnEditLokasi.setText("KELOLA LOKASI TERSIMPAN");
        btnEditLokasi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tampilkanDialogKelolaLokasi();
            }
        });
        box.addView(btnEditLokasi);

        // --- FITUR NAVIGASI ---
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

    private void tampilkanDialogPencarianLokasi() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Cari Lokasi Tujuan");
        
        final EditText input = new EditText(this);
        input.setHint("Contoh: Kantor Bupati Sinjai / Masjid Raya");
        input.setPadding(40, 30, 40, 30);
        builder.setView(input);

        builder.setPositiveButton("Cari", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String queryPencarian = input.getText().toString().trim();
                if (!queryPencarian.isEmpty()) {
                    ucapkanSuara("Mencari lokasi " + queryPencarian + ". Mohon tunggu.");
                    info.setText("Mencari lokasi: " + queryPencarian + "...");
                    new CariLokasiTask().execute(queryPencarian);
                } else {
                    ucapkanSuara("Nama lokasi tidak boleh kosong.");
                }
            }
        });
        builder.setNegativeButton("Batal", null);
        builder.show();
        ucapkanSuara("Silakan ketik nama lokasi yang ingin dituju.");
    }

    private class CariLokasiTask extends AsyncTask<String, Void, HasilPencarian> {
        @Override
        protected HasilPencarian doInBackground(String... params) {
            try {
                String query = params[0];
                String urlStr = "https://nominatim.openstreetmap.org/search?q=" + URLEncoder.encode(query, "UTF-8") + "&format=json&limit=1&addressdetails=1";
                
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "NavigasiAplikasiAndroid");

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                JSONArray jsonArray = new JSONArray(sb.toString());
                if (jsonArray.length() > 0) {
                    JSONObject obj = jsonArray.getJSONObject(0);
                    double lat = obj.getDouble("lat");
                    double lon = obj.getDouble("lon");
                    String displayName = obj.getString("display_name");
                    return new HasilPencarian(lat, lon, displayName);
                }
            } catch (Exception e) {}
            return null;
        }

        @Override
        protected void onPostExecute(HasilPencarian hasil) {
            if (hasil != null) {
                savedLat = hasil.lat;
                savedLon = hasil.lon;
                isLocationSaved = true;

                ucapkanSuara("Lokasi ditemukan: " + hasil.namaPendek() + ". Berhasil disimpan.");
                info.setText("Lokasi Ditemukan & Disimpan!\n" + hasil.displayName);
                
                AlertDialog.Builder konfirmasi = new AlertDialog.Builder(MainActivity.this);
                konfirmasi.setTitle("Lokasi Tersimpan Akurat");
                konfirmasi.setMessage("Hasil Ditemukan:\n" + hasil.displayName + "\n\nApakah Anda ingin langsung memulai Navigasi ke tempat ini?");
                konfirmasi.setPositiveButton("Mulai Navigasi", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        mulaiNavigasiTersimpan();
                    }
                });
                konfirmasi.setNegativeButton("Tutup Saja", null);
                konfirmasi.show();
            } else {
                ucapkanSuara("Maaf, lokasi tidak ditemukan. Coba ketik nama yang lebih spesifik.");
                info.setText("Pencarian gagal. Lokasi tidak ditemukan.");
            }
        }
    }

    private static class HasilPencarian {
        double lat;
        double lon;
        String displayName;

        public HasilPencarian(double lat, double lon, String displayName) {
            this.lat = lat;
            this.lon = lon;
            this.displayName = displayName;
        }

        public String namaPendek() {
            if (displayName != null && displayName.contains(",")) {
                return displayName.split(",")[0];
            }
            return displayName;
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
            ucapkanSuara("Belum ada lokasi yang tersimpan.");
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
                        ucapkanSuara("Lokasi berhasil diperbarui.");
                        info.setText("Lokasi Diperbarui!\nLat: " + savedLat + "\nLon: " + savedLon);
                    } else {
                        ucapkanSuara("Gagal mengambil posisi terbaru.");
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
                public void onClick(DialogInterface dialog