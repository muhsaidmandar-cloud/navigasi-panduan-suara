package com.navigasi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
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

    // Penyimpanan Multi-Lokasi
    private List<LokasiTersimpan> daftarLokasiTersimpan = new ArrayList<>();
    private LokasiTersimpan lokasiNavigasiAktif = null;
    
    private boolean isNavigating = false;
    private boolean sedangMencariPosisiSekarang = false;
    
    // Status Pengaktifan Fitur Eksplorasi Real-Time (Gaya Lazarillo: Scan Radius 25m, Update tiap pindah 15m)
    private boolean isEksplorasiFiturAktif = false;
    private boolean sedangMemindaiOtomatis = false;
    private double lastExplorationLat = 0.0;
    private double lastExplorationLon = 0.0;

    // Daftar instruksi belokan hasil unduhan rute
    private List<InstruksiRute> daftarInstruksi = new ArrayList<>();
    private int indexInstruksiAktif = 0;

    // Struktur Data Lokasi Tersimpan
    public static class LokasiTersimpan {
        String nama;
        double lat;
        double lon;

        public LokasiTersimpan(String nama, double lat, double lon) {
            this.nama = nama;
            this.lat = lat;
            this.lon = lon;
        }
    }

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
        muatDataLokasiDariPrefs();
        inisialisasiTtsMandiri();
        tampilkanMenuUtama();
    }

    // --- MANAJEMEN PENYIMPANAN PREFERENCES (MULTI-LOKASI) ---
    private void muatDataLokasiDariPrefs() {
        daftarLokasiTersimpan.clear();
        SharedPreferences prefs = getSharedPreferences("NavigasiPrefs", MODE_PRIVATE);
        int jumlah = prefs.getInt("jumlah_lokasi", 0);
        for (int i = 0; i < jumlah; i++) {
            String nama = prefs.getString("nama_" + i, "Lokasi " + (i + 1));
            double lat = Double.longBitsToDouble(prefs.getLong("lat_" + i, 0));
            double lon = Double.longBitsToDouble(prefs.getLong("lon_" + i, 0));
            daftarLokasiTersimpan.add(new LokasiTersimpan(nama, lat, lon));
        }
    }

    private void simpanDataLokasiKePrefs() {
        SharedPreferences prefs = getSharedPreferences("NavigasiPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("jumlah_lokasi", daftarLokasiTersimpan.size());
        for (int i = 0; i < daftarLokasiTersimpan.size(); i++) {
            LokasiTersimpan loc = daftarLokasiTersimpan.get(i);
            editor.putString("nama_" + i, loc.nama);
            editor.putLong("lat_" + i, Double.doubleToRawLongBits(loc.lat));
            editor.putLong("lon_" + i, Double.doubleToRawLongBits(loc.lon));
        }
        editor.apply();
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
        if (lokasiNavigasiAktif != null) {
            info.setText("Tujuan Aktif:\n" + lokasiNavigasiAktif.nama + "\nLat: " + lokasiNavigasiAktif.lat + ", Lon: " + lokasiNavigasiAktif.lon);
        } else {
            info.setText("Total Lokasi Tersimpan: " + daftarLokasiTersimpan.size() + "\nTekan tombol di bawah untuk mulai.");
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

        // --- TOMBOL SAKLAR EKSPLORASI REAL-TIME (Gaya Lazarillo) ---
        final Button btnToggleEksplorasi = new Button(this);
        updateTeksTombolEksplorasi(btnToggleEksplorasi);
        btnToggleEksplorasi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isEksplorasiFiturAktif = !isEksplorasiFiturAktif;
                updateTeksTombolEksplorasi(btnToggleEksplorasi);
                if (isEksplorasiFiturAktif) {
                    ucapkanSuara("Eksplorasi sekitar diaktifkan.");
                    mulaiMendengarkanGPS();
                } else {
                    ucapkanSuara("Eksplorasi sekitar dinonaktifkan.");
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
                tampilkanDialogSimpanLokasiCustom("Lokasi Saya " + (daftarLokasiTersimpan.size() + 1));
            }
        });
        box.addView(btnSimpan);

        // --- FITUR KELOLA LOKASI ---
        Button btnEditLokasi = new Button(this);
        btnEditLokasi.setText("KELOLA LOKASI TERSIMPAN (" + daftarLokasiTersimpan.size() + ")");
        btnEditLokasi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tampilkanDialogKelolaDaftarLokasi();
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

        // --- FITUR HENTIKAN NAVIGASI ---
        Button btnHentikanNavigasi = new Button(this);
        btnHentikanNavigasi.setText("HENTIKAN NAVIGASI");
        btnHentikanNavigasi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hentikanNavigasiTotal();
            }
        });
        box.addView(btnHentikanNavigasi);

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

    private void hentikanNavigasiTotal() {
        isNavigating = false;
        daftarInstruksi.clear();
        indexInstruksiAktif = 0;
        ucapkanSuara("Navigasi dihentikan.");
        if (lokasiNavigasiAktif != null) {
            info.setText("Navigasi Berhenti.\nTujuan Aktif: " + lokasiNavigasiAktif.nama);
        } else {
            info.setText("Navigasi Berhenti. Belum ada tujuan dipilih.");
        }
    }

    private void updateTeksTombolEksplorasi(Button btn) {
        if (isEksplorasiFiturAktif) {
            btn.setText("EKSPLORASI SEKITAR: AKTIF");
        } else {
            btn.setText("EKSPLORASI SEKITAR: NONAKTIF");
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
        protected void onPostExecute(final HasilPencarian hasil) {
            if (hasil != null) {
                String infoJarakDetail = "";
                String teksUcapanJarak = "";
                try {
                    Location lastLoc = null;
                    if (locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        lastLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    }
                    if (lastLoc != null) {
                        float[] results = new float[1];
                        Location.distanceBetween(lastLoc.getLatitude(), lastLoc.getLongitude(), hasil.lat, hasil.lon, results);
                        float jarakMeter = results[0];
                        if (jarakMeter >= 1000) {
                            infoJarakDetail = String.format(Locale.getDefault(), "Jarak: %.2f km", (jarakMeter / 1000.0f));
                            teksUcapanJarak = String.format(Locale.getDefault(), " Jarak %.2f kilometer.", (jarakMeter / 1000.0f));
                        } else {
                            infoJarakDetail = String.format(Locale.getDefault(), "Jarak: %d meter", (int) jarakMeter);
                            teksUcapanJarak = String.format(Locale.getDefault(), " Jarak %d meter.", (int) jarakMeter);
                        }
                    }
                } catch (Exception e) {}

                // Menyebutkan nama tempat beserta jaraknya via TTS dan menampilkan pilihan dialog
                ucapkanSuara("Lokasi ditemukan: " + hasil.namaPendek() + "." + teksUcapanJarak + " Pilih opsi untuk menyimpan atau bernavigasi.");
                info.setText("Lokasi Ditemukan:\n" + hasil.displayName + (infoJarakDetail.isEmpty() ? "" : "\n" + infoJarakDetail));
                
                AlertDialog.Builder konfirmasi = new AlertDialog.Builder(MainActivity.this);
                konfirmasi.setTitle("Hasil Pencarian Lokasi");
                konfirmasi.setMessage("Ditemukan:\n" + hasil.displayName + (infoJarakDetail.isEmpty() ? "" : "\n\n" + infoJarakDetail) + "\n\nApa yang ingin Anda lakukan dengan lokasi ini?");
                
                konfirmasi.setPositiveButton("Simpan ke Daftar", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        daftarLokasiTersimpan.add(new LokasiTersimpan(hasil.namaPendek(), hasil.lat, hasil.lon));
                        simpanDataLokasiKePrefs();
                        ucapkanSuara("Lokasi berhasil disimpan ke daftar.");
                        info.setText("Lokasi Tersimpan:\n" + hasil.namaPendek());
                    }
                });

                konfirmasi.setNeutralButton("Langsung Navigasi", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        lokasiNavigasiAktif = new LokasiTersimpan(hasil.namaPendek(), hasil.lat, hasil.lon);
                        mulaiNavigasiTersimpan();
                    }
                });

                konfirmasi.setNegativeButton("Abaikan", null);
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

    // --- VALIDASI KETAT: MENCEGAH DATA CACHE LAMA / RUANGAN TERTUTUP ---
    private void tampilkanDialogSimpanLokasiCustom(final String defaultNama) {
        Location loc = null;
        try {
            if (locationManager != null) {
                if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    ucapkanSuara("GPS belum aktif. Aktifkan GPS terlebih dahulu.");
                    info.setText("Gagal: GPS tidak aktif.");
                    return;
                }
                loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
        } catch (Exception e) {}

        if (loc == null || (System.currentTimeMillis() - loc.getTime() > 10000)) {
            ucapkanSuara("Sinyal satelit tidak valid. Pastikan Anda berada di luar ruangan dan GPS aktif.");
            info.setText("Gagal menyimpan: Tidak ada sinyal satelit baru atau Anda berada di dalam ruangan.");
            return;
        }

        final double currentLat = loc.getLatitude();
        final double currentLon = loc.getLongitude();
        float akurasi = loc.getAccuracy();

        if (akurasi > 20.0f) {
            ucapkanSuara("Sinyal GPS terlalu lemah atau berada di dalam ruangan.");
            info.setText("Gagal menyimpan: Akurasi buruk (± " + (int)akurasi + " m). Pindah ke luar ruangan.");
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Simpan Lokasi Baru");

        final EditText input = new EditText(this);
        input.setText(defaultNama);
        input.setPadding(40, 30, 40, 30);
        builder.setView(input);

        builder.setPositiveButton("Simpan", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String namaLokasi = input.getText().toString().trim();
                if (namaLokasi.isEmpty()) namaLokasi = "Lokasi Tersimpan";
                
                daftarLokasiTersimpan.add(new LokasiTersimpan(namaLokasi, currentLat, currentLon));
                simpanDataLokasiKePrefs();
                lokasiNavigasiAktif = daftarLokasiTersimpan.get(daftarLokasiTersimpan.size() - 1);
                
                ucapkanSuara("Lokasi " + namaLokasi + " berhasil disimpan.");
                info.setText("Lokasi Tersimpan:\n" + namaLokasi + "\nLat: " + currentLat + ", Lon: " + currentLon);
            }
        });
        builder.setNegativeButton("Batal", null);
        builder.show();
        ucapkanSuara("Masukkan nama untuk lokasi ini.");
    }

    private void tampilkanDialogKelolaDaftarLokasi() {
        if (daftarLokasiTersimpan.isEmpty()) {
            ucapkanSuara("Belum ada lokasi yang tersimpan.");
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Kelola Lokasi");
            builder.setMessage("Belum ada lokasi tersimpan di dalam aplikasi.");
            builder.setPositiveButton("Tutup", null);
            builder.show();
            return;
        }

        final CharSequence[] daftarNama = new CharSequence[daftarLokasiTersimpan.size()];
        for (int i = 0; i < daftarLokasiTersimpan.size(); i++) {
            daftarNama[i] = (i + 1) + ". " + daftarLokasiTersimpan.get(i).nama + " (" + daftarLokasiTersimpan.get(i).lat + ", " + daftarLokasiTersimpan.get(i).lon + ")";
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Daftar Lokasi Tersimpan (" + daftarLokasiTersimpan.size() + ")");
        builder.setItems(daftarNama, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, final int pilihanIndex) {
                final LokasiTersimpan dipilih = daftarLokasiTersimpan.get(pilihanIndex);
                
                AlertDialog.Builder opsiItem = new AlertDialog.Builder(MainActivity.this);
                opsiItem.setTitle("Pilihan: " + dipilih.nama);
                CharSequence[] aksi = {"Jadikan Tujuan Navigasi", "Hapus Lokasi Ini"};
                opsiItem.setItems(aksi, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int a) {
                        if (a == 0) {
                            lokasiNavigasiAktif = dipilih;
                            ucapkanSuara("Tujuan aktif diubah ke " + dipilih.nama);
                            info.setText("Tujuan Aktif:\n" + dipilih.nama);
                        } else if (a == 1) {
                            daftarLokasiTersimpan.remove(pilihanIndex);
                            simpanDataLokasiKePrefs();
                            if (lokasiNavigasiAktif != null && lokasiNavigasiAktif.equals(dipilih)) {
                                lokasiNavigasiAktif = null;
                            }
                            ucapkanSuara("Lokasi dihapus.");
                            info.setText("Lokasi dihapus. Total tersimpan: " + daftarLokasiTersimpan.size());
                        }
                    }
                });
                opsiItem.show();
            }
        });
        builder.setNegativeButton("Tutup", null);
        builder.show();
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
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000, 2.0f, this);
            } else {
                ucapkanSuara("GPS belum aktif.");
            }
        } catch (SecurityException e) {
            ucapkanSuara("Izin lokasi ditolak.");
        }
    }

    // --- TOMBOL DI MANA SAYA SEKARANG (Validasi Ketat: Tolak Ruangan / Cache Lama) ---
    private void cekPosisiSekarangAkurat() {
        Location loc = null;
        try {
            if (locationManager != null) {
                if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    ucapkanSuara("GPS belum aktif. Aktifkan GPS terlebih dahulu.");
                    info.setText("Gagal: GPS tidak aktif.");
                    return;
                }
                loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
        } catch (Exception e) {}

        // Validasi ketat: Tolak jika data null atau umur data lebih dari 10 detik (indikasi data basi/indoor)
        if (loc == null || (System.currentTimeMillis() - loc.getTime() > 10000)) {
            ucapkanSuara("Sinyal satelit tidak valid. Pastikan Anda berada di luar ruangan dan terhalang langsung ke langit.");
            info.setText("Gagal mengunci: Tidak ada sinyal satelit baru atau Anda berada di dalam ruangan.");
            return;
        }

        float akurasi = loc.getAccuracy();
        // Validasi ketat: Harus <= 3 meter (sinyal satelit murni di luar ruangan)
        if (akurasi > 3.0f) {
            ucapkanSuara("Sinyal GPS lemah atau terhalang. Akurasi saat ini plus minus " + (int)akurasi + " meter. Pindah ke tempat terbuka.");
            info.setText("Gagal mengunci: Akurasi buruk (± " + (int)akurasi + " m). Target harus <= 3 meter.");
            return;
        }

        // Jika lolos validasi murni di luar ruangan
        double currentLat = loc.getLatitude();
        double currentLon = loc.getLongitude();
        ucapkanSuara("Posisi terkunci akurat.");
        info.setText("Posisi Akurat:\nLat: " + currentLat + "\nLon: " + currentLon + "\nAkurasi: ± " + akurasi + " m");
    }

    // --- FITUR EKSPLORASI REAL-TIME (Gaya Lazarillo: Radius 25m, Update tiap pindah 15m) ---
    private class EksplorasiRealtimeTask extends AsyncTask<Double, Void, List<String>> {
        @Override
        protected List<String> doInBackground(Double... coords) {
            List<String> hasilTempat = new ArrayList<>();
            try {
                double lat = coords[0];
                double lon = coords[1];
                String query = "[out:json];(node(around:25," + lat + "," + lon + ")[amenity];way(around:25," + lat + "," + lon + ")[amenity];node(around:25," + lat + "," + lon + ")[shop];);out body 5;";
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
                            String namaTempat = tags.getString("name");
                            if (!hasilTempat.contains(namaTempat)) {
                                hasilTempat.add(namaTempat);
                            }
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
                info.setText("Eksplorasi Sekitar Aktif:\n" + speechText.toString());
            }
        }
    }

    private void mulaiNavigasiTersimpan() {
        if (lokasiNavigasiAktif == null) {
            ucapkanSuara("Belum ada tujuan navigasi yang dipilih.");
            info.setText("Pilih atau tetapkan lokasi tujuan terlebih dahulu!");
            return;
        }
        isNavigating = true;
        indexInstruksiAktif = 0;
        daftarInstruksi.clear();
        
        ucapkanSuara("Mengunduh rute belokan ke " + lokasiNavigasiAktif.nama + "...");
        info.setText("Menghitung rute ke: " + lokasiNavigasiAktif.nama);
        
        Location loc = null;
        try {
            if (locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
        } catch (Exception e) {}

        double startLat = (loc != null) ? loc.getLatitude() : lokasiNavigasiAktif.lat - 0.001;
        double startLon = (loc != null) ? loc.getLongitude() : lokasiNavigasiAktif.lon - 0.001;

        new AmbilRuteTask().execute(startLat, startLon, lokasiNavigasiAktif.lat, lokasiNavigasiAktif.lon);
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
                            hasil.add(new InstruksiRute(locArr.getDouble(1), locArr.getDouble(0), maneuver));
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
                info.setText("Navigasi ke " + lokasiNavigasiAktif.nama + " (" + daftarInstruksi.size() + " titik)");
            } else {
                if (lokasiNavigasiAktif != null) {
                    daftarInstruksi.add(new InstruksiRute(lokasiNavigasiAktif.lat, lokasiNavigasiAktif.lon, "Tuju titik akhir tujuan."));
                }
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
            
            // Eksplorasi Real-Time Gaya Lazarillo (Trigger saat berpindah minimal 15 meter)
            if (isEksplorasiFiturAktif && !isNavigating && !sedangMemindaiOtomatis) {
                float[] jarakPindah = new float[1];
                if (lastExplorationLat == 0.0 && lastExplorationLon == 0.0) {
                    lastExplorationLat = currentLat;
                    lastExplorationLon = currentLon;
                }
                
                Location.distanceBetween(lastExplorationLat, lastExplorationLon, currentLat, currentLon, jarakPindah);
                
                if (jarakPindah[0] >= 15.0f) {
                    lastExplorationLat = currentLat;
                    lastExplorationLon = currentLon;
                    sedangMemindaiOtomatis = true;
                    new EksplorasiRealtimeTask().execute(currentLat, currentLon);
                }
            }

            if (isNavigating && lokasiNavigasiAktif != null) {
                float[] jarakTotalArr = new float[1];
                Location.distanceBetween(currentLat, currentLon, lokasiNavigasiAktif.lat, lokasiNavigasiAktif.lon, jarakTotalArr);
                float jarakTotalMeter = jarakTotalArr[0];
                
                String teksJarakTotal = "";
                if (jarakTotalMeter >= 1000) {
                    teksJarakTotal = String.format(Locale.getDefault(), "Sisa Jarak Total: %.2f km", (jarakTotalMeter / 1000.0f));
                } else {
                    teksJarakTotal = String.format(Locale.getDefault(), "Sisa Jarak Total: %d m", (int) jarakTotalMeter);
                }

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
                            info.setText("Tiba di tujuan: " + lokasiNavigasiAktif.nama);
                            isNavigating = false;
                        }
                    } else {
                        info.setText("Tujuan: " + lokasiNavigasiAktif.nama + "\nPanduan:\n" + instruksi.pesanPanduan + "\nJarak Belokan: " + (int)jarakKeBelokan + " m\n" + teksJarakTotal);
                    }
                } else {
                    info.setText("Navigasi ke " + lokasiNavigasiAktif.nama + "\n" + teksJarakTotal);
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