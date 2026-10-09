package com.navigasi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
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

public class MainActivity extends Activity implements LocationListener, SensorEventListener, TextToSpeech.OnInitListener {
    
    private TextView info;
    private LocationManager locationManager;
    private SensorManager sensorManager;
    private Sensor rotationSensor;
    
    private float currentAzimuth = 0.0f; 

    private TextToSpeech tts;
    private boolean isTtsReady = false;

    // Pengaturan Suara & TTS
    private float kecepatanBicara = 1.0f;     
    private String targetTtsEngine = null; 

    // Pilihan Stream Audio
    private int selectedAudioStream = AudioManager.STREAM_MUSIC;

    // Penyimpanan Multi-Lokasi
    private List<LokasiTersimpan> daftarLokasiTersimpan = new ArrayList<>();
    private LokasiTersimpan lokasiNavigasiAktif = null;
    
    private boolean isNavigating = false;
    
    // Status Eksplorasi Real-Time Dinamis & Cerdas
    private boolean isEksplorasiFiturAktif = false;
    private boolean sedangMemindaiOtomatis = false;
    private double lastExplorationLat = 0.0;
    private double lastExplorationLon = 0.0;
    
    private List<String> riwayatTempatDiumumkan = new ArrayList<>();
    private List<InstruksiRute> daftarInstruksi = new ArrayList<>();
    private int indexInstruksiAktif = 0;

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

    // --- STRUKTUR INSTRUKSI RUTE DIPERLUAS DENGAN NAMA JALAN ---
    private static class InstruksiRute {
        double lat;
        double lon;
        String pesanPanduan;
        String namaJalan;
        boolean sudahDiumumkan = false;

        public InstruksiRute(double lat, double lon, String pesanPanduan, String namaJalan) {
            this.lat = lat;
            this.lon = lon;
            this.pesanPanduan = pesanPanduan;
            this.namaJalan = namaJalan;
        }
    }

    @Override 
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setVolumeControlStream(selectedAudioStream);
        muatDataLokasiDariPrefs();
        inisialisasiSensorKompasPro();
        inisialisasiTtsMandiri();
        tampilkanMenuUtama();
    }

    private void inisialisasiSensorKompasPro() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            if (rotationSensor == null) {
                rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
            }
        }
    }

    private void mulaiSensorKompas() {
        if (sensorManager != null && rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    private void hentikanSensorKompas() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR || event.sensor.getType() == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            float[] rotationMatrix = new float[9];
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            float[] orientation = new float[3];
            SensorManager.getOrientation(rotationMatrix, orientation);
            
            float azimuthInDegrees = (float) Math.toDegrees(orientation[0]);
            currentAzimuth = (azimuthInDegrees + 360) % 360; 
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

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
            try { tts.stop(); tts.shutdown(); } catch (Exception e) {}
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
        title.setText("Navigasi Kompas Sempurna");
        title.setTextSize(22);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER);
        box.addView(title);
        
        info = new TextView(this);
        if (lokasiNavigasiAktif != null) {
            info.setText("Tujuan Aktif:\n" + lokasiNavigasiAktif.nama + "\nLat: " + lokasiNavigasiAktif.lat + ", Lon: " + lokasiNavigasiAktif.lon);
        } else {
            info.setText("Total Lokasi Tersimpan: " + daftarLokasiTersimpan.size() + "\nSistem Sensor Pro Siap.");
        }
        info.setTextSize(15);
        info.setGravity(Gravity.CENTER);
        info.setPadding(0, 16, 0, 24);
        box.addView(info);
        
        Button btnCekPosisi = new Button(this);
        btnCekPosisi.setText("DI MANA SAYA SEKARANG");
        btnCekPosisi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cekPosisiAlamatLengkap();
            }
        });
        box.addView(btnCekPosisi);

        // --- FITUR PENGUNCIAN LOKASI 100% UTUH TIDAK DIGANGGU ---
        Button btnKunciAkurat = new Button(this);
        btnKunciAkurat.setText("KUNCI POSISI AKURAT (MAX 3M)");
        btnKunciAkurat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                kunciLokasiSangatAkurat();
            }
        });
        box.addView(btnKunciAkurat);

        Button btnCariLokasi = new Button(this);
        btnCariLokasi.setText("CARI LOKASI TUJUAN (TEKS)");
        btnCariLokasi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tampilkanDialogPencarianLokasi();
            }
        });
        box.addView(btnCariLokasi);

        final Button btnToggleEksplorasi = new Button(this);
        updateTeksTombolEksplorasi(btnToggleEksplorasi);
        btnToggleEksplorasi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isEksplorasiFiturAktif = !isEksplorasiFiturAktif;
                updateTeksTombolEksplorasi(btnToggleEksplorasi);
                if (isEksplorasiFiturAktif) {
                    riwayatTempatDiumumkan.clear();
                    lastExplorationLat = 0.0;
                    lastExplorationLon = 0.0;
                    mulaiSensorKompas();
                    mulaiMendengarkanGPS();
                    ucapkanSuara("Eksplorasi pro diaktifkan.");
                    
                    try {
                        if (locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                            Location loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                            if (loc != null) {
                                float initialSpeed = loc.hasSpeed() ? loc.getSpeed() : 0.0f;
                                float initialHeading = (initialSpeed > 3.0f && loc.hasBearing()) ? loc.getBearing() : currentAzimuth;
                                new EksplorasiKompasProTask().execute(loc.getLatitude(), loc.getLongitude(), 40.0, (double)initialHeading);
                            }
                        }
                    } catch (Exception e) {}
                } else {
                    hentikanSensorKompas();
                    ucapkanSuara("Eksplorasi dinonaktifkan.");
                }
            }
        });
        box.addView(btnToggleEksplorasi);

        Button btnSimpan = new Button(this);
        btnSimpan.setText("SIMPAN LOKASI SAAT INI");
        btnSimpan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tampilkanDialogSimpanLokasiCustom("Lokasi Saya " + (daftarLokasiTersimpan.size() + 1));
            }
        });
        box.addView(btnSimpan);

        Button btnEditLokasi = new Button(this);
        btnEditLokasi.setText("KELOLA LOKASI TERSIMPAN (" + daftarLokasiTersimpan.size() + ")");
        btnEditLokasi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tampilkanDialogKelolaDaftarLokasi();
            }
        });
        box.addView(btnEditLokasi);

        Button btnNavigasi = new Button(this);
        btnNavigasi.setText("MULAI NAVIGASI BELOKAN");
        btnNavigasi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mulaiNavigasiTersimpan();
            }
        });
        box.addView(btnNavigasi);

        Button btnHentikanNavigasi = new Button(this);
        btnHentikanNavigasi.setText("HENTIKAN NAVIGASI");
        btnHentikanNavigasi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hentikanNavigasiTotal();
            }
        });
        box.addView(btnHentikanNavigasi);

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
        info.setText("Navigasi Berhenti.");
    }

    private void updateTeksTombolEksplorasi(Button btn) {
        if (isEksplorasiFiturAktif) {
            btn.setText("EKSPLORASI PRO: AKTIF");
        } else {
            btn.setText("EKSPLORASI PRO: NONAKTIF");
        }
    }

    // --- KUNCI LOKASI AKURAT (DIJAMIN TIDAK DISENTUH/DIUBAH) ---
    private void kunciLokasiSangatAkurat() {
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
        } catch (SecurityException e) {
            ucapkanSuara("Izin lokasi ditolak.");
            return;
        }

        if (loc == null || (System.currentTimeMillis() - loc.getTime() > 7000)) {
            ucapkanSuara("Sinyal satelit tidak valid atau basi. Pastikan Anda berada di luar ruangan di bawah langit terbuka.");
            info.setText("Gagal mengunci: Tidak ada sinyal satelit langsung baru.");
            return;
        }

        float akurasi = loc.getAccuracy();
        if (akurasi > 3.0f) {
            ucapkanSuara("Sinyal satelit terhalang atau berupa pantulan. Akurasi saat ini plus minus " + (int)akurasi + " meter. Pindah ke tempat terbuka.");
            info.setText("Gagal mengunci: Akurasi buruk (± " + (int)akurasi + " m). Batas maksimal adalah 3 meter.");
            return;
        }

        String provider = loc.getProvider();
        if (provider == null || !provider.equals(LocationManager.GPS_PROVIDER)) {
            ucapkanSuara("Terdeteksi bukan sinyal satelit murni. Pindah ke area terbuka.");
            info.setText("Gagal mengunci: Sumber bukan sinyal satelit langsung.");
            return;
        }

        final double finalLat = loc.getLatitude();
        final double finalLon = loc.getLongitude();

        ucapkanSuara("Posisi terkunci akurat dengan akurasi " + (int)akurasi + " meter. Masukkan nama untuk disimpan ke daftar lokasi.");
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Simpan Lokasi Terkunci");
        
        final EditText input = new EditText(this);
        input.setText("Lokasi Akurat " + (daftarLokasiTersimpan.size() + 1));
        input.setPadding(40, 30, 40, 30);
        builder.setView(input);

        builder.setPositiveButton("Simpan ke Daftar", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String namaLokasi = input.getText().toString().trim();
                if (namaLokasi.isEmpty()) namaLokasi = "Lokasi Akurat";
                
                daftarLokasiTersimpan.add(new LokasiTersimpan(namaLokasi, finalLat, finalLon));
                simpanDataLokasiKePrefs();
                
                ucapkanSuara("Lokasi " + namaLokasi + " berhasil disimpan ke daftar.");
                info.setText("Lokasi Tersimpan:\n" + namaLokasi + "\nLat: " + finalLat + ", Lon: " + finalLon + "\n(Silakan cek melalui menu Kelola Lokasi)");
            }
        });
        builder.setNegativeButton("Batal", null);
        builder.show();
    }

    private void tampilkanDialogPencarianLokasi() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Cari Lokasi Tujuan");
        final EditText input = new EditText(this);
        input.setHint("Contoh: Masjid Raya Sinjai");
        input.setPadding(40, 30, 40, 30);
        builder.setView(input);

        builder.setPositiveButton("Cari", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String query = input.getText().toString().trim();
                if (!query.isEmpty()) {
                    ucapkanSuara("Mencari " + query + "...");
                    new CariLokasiTask().execute(query);
                }
            }
        });
        builder.setNegativeButton("Batal", null);
        builder.show();
    }

    private class CariLokasiTask extends AsyncTask<String, Void, HasilPencarian> {
        @Override
        protected HasilPencarian doInBackground(String... params) {
            try {
                String urlStr = "https://nominatim.openstreetmap.org/search?q=" + URLEncoder.encode(params[0], "UTF-8") + "&format=json&limit=1&addressdetails=1";
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "NavigasiAplikasiAndroid");
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                JSONArray jsonArray = new JSONArray(sb.toString());
                if (jsonArray.length() > 0) {
                    JSONObject obj = jsonArray.getJSONObject(0);
                    return new HasilPencarian(obj.getDouble("lat"), obj.getDouble("lon"), obj.getString("display_name"));
                }
            } catch (Exception e) {}
            return null;
        }

        @Override
        protected void onPostExecute(HasilPencarian hasil) {
            if (hasil != null) {
                ucapkanSuara("Lokasi ditemukan: " + hasil.namaPendek());
                lokasiNavigasiAktif = new LokasiTersimpan(hasil.namaPendek(), hasil.lat, hasil.lon);
                mulaiNavigasiTersimpan();
            } else {
                ucapkanSuara("Lokasi tidak ditemukan.");
            }
        }
    }

    private static class HasilPencarian {
        double lat, lon;
        String displayName;
        public HasilPencarian(double lat, double lon, String displayName) {
            this.lat = lat; this.lon = lon; this.displayName = displayName;
        }
        public String namaPendek() {
            if (displayName != null && displayName.contains(",")) return displayName.split(",")[0];
            return displayName;
        }
    }

    private void tampilkanDialogSimpanLokasiCustom(final String defaultNama) {
        Location loc = null;
        try {
            if (locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
        } catch (Exception e) {}

        if (loc == null) {
            ucapkanSuara("GPS belum aktif.");
            return;
        }

        final double cLat = loc.getLatitude();
        final double cLon = loc.getLongitude();

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Simpan Lokasi");
        final EditText input = new EditText(this);
        input.setText(defaultNama);
        input.setPadding(40, 30, 40, 30);
        builder.setView(input);

        builder.setPositiveButton("Simpan", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String nama = input.getText().toString().trim();
                daftarLokasiTersimpan.add(new LokasiTersimpan(nama, cLat, cLon));
                simpanDataLokasiKePrefs();
                ucapkanSuara("Lokasi " + nama + " disimpan.");
            }
        });
        builder.setNegativeButton("Batal", null);
        builder.show();
    }

    private void tampilkanDialogKelolaDaftarLokasi() {
        if (daftarLokasiTersimpan.isEmpty()) {
            ucapkanSuara("Belum ada lokasi.");
            return;
        }
        final CharSequence[] daftarNama = new CharSequence[daftarLokasiTersimpan.size()];
        for (int i = 0; i < daftarLokasiTersimpan.size(); i++) {
            daftarNama[i] = (i + 1) + ". " + daftarLokasiTersimpan.get(i).nama;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Daftar Lokasi");
        builder.setItems(daftarNama, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int index) {
                lokasiNavigasiAktif = daftarLokasiTersimpan.get(index);
                ucapkanSuara("Tujuan diubah ke " + lokasiNavigasiAktif.nama);
            }
        });
        builder.show();
    }

    private void tampilkanHalamanPengaturanTts() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(32, 32, 32, 32);

        TextView tv = new TextView(this);
        tv.setText("Kecepatan: " + kecepatanBicara + "x");
        tv.setTextSize(18);
        tv.setGravity(Gravity.CENTER);
        box.addView(tv);

        Button btnCepat = new Button(this);
        btnCepat.setText("UBAH KECEPATAN");
        btnCepat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                kecepatanBicara = (kecepatanBicara < 2.0f) ? (kecepatanBicara + 0.25f) : 1.0f;
                terapkanSetelanTts();
                tv.setText("Kecepatan: " + kecepatanBicara + "x");
                ucapkanSuara("Kecepatan " + kecepatanBicara);
            }
        });
        box.addView(btnCepat);

        Button btnKembali = new Button(this);
        btnKembali.setText("KEMBALI");
        btnKembali.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tampilkanMenuUtama();
            }
        });
        box.addView(btnKembali);
        setContentView(box);
    }

    private void terapkanSetelanTts() {
        if (tts != null) {
            try { tts.setSpeechRate(kecepatanBicara); } catch (Exception e) {}
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            try {
                if (tts.setLanguage(new Locale("id", "ID")) >= 0) {
                    isTtsReady = true;
                    terapkanSetelanTts();
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
            } catch (Exception e) {}
        }
    }

    private void mulaiMendengarkanGPS() {
        try {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 1.0f, this);
            }
        } catch (SecurityException e) {}
    }

    private void cekPosisiAlamatLengkap() {
        Location loc = null;
        try {
            if (locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
        } catch (Exception e) {}

        if (loc == null) {
            ucapkanSuara("Sinyal GPS belum siap.");
            return;
        }

        ucapkanSuara("Mengambil alamat lengkap...");
        new CekAlamatTask().execute(loc.getLatitude(), loc.getLongitude());
    }

    private class CekAlamatTask extends AsyncTask<Double, Void, String> {
        @Override
        protected String doInBackground(Double... params) {
            try {
                String urlStr = "https://nominatim.openstreetmap.org/reverse?lat=" + params[0] + "&lon=" + params[1] + "&format=json&addressdetails=1";
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "NavigasiAplikasiAndroid");
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                JSONObject json = new JSONObject(sb.toString());
                if (json.has("display_name")) return json.getString("display_name");
            } catch (Exception e) {}
            return null;
        }

        @Override
        protected void onPostExecute(String alamat) {
            if (alamat != null) {
                ucapkanSuara("Anda berada di " + alamat);
                info.setText("Posisi Anda:\n" + alamat);
            } else {
                ucapkanSuara("Gagal mengambil alamat.");
            }
        }
    }

    private class EksplorasiKompasProTask extends AsyncTask<Double, Void, List<TempatPro>> {
        double cLat, cLon;
        int radius = 40;
        float headingAcuan = 0.0f;

        @Override
        protected List<TempatPro> doInBackground(Double... coords) {
            cLat = coords[0];
            cLon = coords[1];
            if (coords.length > 2) radius = coords[2].intValue();
            if (coords.length > 3) headingAcuan = coords[3].floatValue();

            List<TempatPro> hasil = new ArrayList<>();
            try {
                String query = "[out:json][timeout:3];(" +
                               "node(around:" + radius + "," + cLat + "," + cLon + ")[name];" +
                               "way(around:" + radius + "," + cLat + "," + cLon + ")[name];" +
                               ");out body 10;";
                
                String urlStr = "https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(query, "UTF-8");
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "NavigasiAplikasiAndroid");
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject json = new JSONObject(sb.toString());
                JSONArray elements = json.getJSONArray("elements");
                
                for (int i = 0; i < elements.length(); i++) {
                    JSONObject el = elements.getJSONObject(i);
                    if (el.has("tags")) {
                        JSONObject tags = el.getJSONObject("tags");
                        if (tags.has("name")) {
                            String nama = tags.getString("name");
                            if (!riwayatTempatDiumumkan.contains(nama)) {
                                double lat = el.has("lat") ? el.getDouble("lat") : cLat;
                                double lon = el.has("lon") ? el.getDouble("lon") : cLon;
                                hasil.add(new TempatPro(nama, lat, lon));
                            }
                        }
                    }
                }
            } catch (Exception e) {}
            return hasil;
        }

        @Override
        protected void onPostExecute(List<TempatPro> result) {
            sedangMemindaiOtomatis = false;
            if (result != null && !result.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (TempatPro t : result) {
                    float[] dist = new float[1];
                    Location.distanceBetween(cLat, cLon, t.lat, t.lon, dist);
                    int jarak = (int) dist[0];
                    if (jarak == 0) jarak = 3;

                    double dLon = Math.toRadians(t.lon - cLon);
                    double y = Math.sin(dLon) * Math.cos(Math.toRadians(t.lat));
                    double x = Math.cos(Math.toRadians(cLat)) * Math.sin(Math.toRadians(t.lat)) -
                               Math.sin(Math.toRadians(cLat)) * Math.cos(Math.toRadians(t.lat)) * Math.cos(dLon);
                    double bearing = Math.toDegrees(Math.atan2(y, x));
                    bearing = (bearing + 360) % 360;

                    double selisih = bearing - headingAcuan;
                    while (selisih < -180) selisih += 360;
                    while (selisih > 180) selisih -= 360;

                    String posisi = "di depan Anda";
                    if (selisih > 40 && selisih <= 135) {
                        posisi = "di sebelah kanan Anda";
                    } else if (selisih > 135 || selisih < -135) {
                        posisi = "di belakang Anda";
                    } else if (selisih >= -135 && selisih < -40) {
                        posisi = "di sebelah kiri Anda";
                    }

                    String teks = t.nama + ", " + jarak + " meter " + posisi + ". ";
                    ucapkanSuara(teks);
                    sb.append(teks).append("\n");

                    if (!riwayatTempatDiumumkan.contains(t.nama)) {
                        riwayatTempatDiumumkan.add(t.nama);
                        if (riwayatTempatDiumumkan.size() > 40) riwayatTempatDiumumkan.remove(0);
                    }
                }
                info.setText("Eksplorasi Pro Aktif:\n" + sb.toString());
            }
        }
    }

    private static class TempatPro {
        String nama;
        double lat, lon;
        public TempatPro(String nama, double lat, double lon) {
            this.nama = nama; this.lat = lat; this.lon = lon;
        }
    }

    private void mulaiNavigasiTersimpan() {
        if (lokasiNavigasiAktif == null) {
            ucapkanSuara("Pilih tujuan terlebih dahulu.");
            return;
        }
        isNavigating = true;
        indexInstruksiAktif = 0;
        daftarInstruksi.clear();
        
        ucapkanSuara("Memuat rute detail ke " + lokasiNavigasiAktif.nama + "...");
        Location loc = null;
        try {
            if (locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
        } catch (Exception e) {}

        double sLat = (loc != null) ? loc.getLatitude() : lokasiNavigasiAktif.lat - 0.001;
        double sLon = (loc != null) ? loc.getLongitude() : lokasiNavigasiAktif.lon - 0.001;

        new AmbilRuteTask().execute(sLat, sLon, lokasiNavigasiAktif.lat, lokasiNavigasiAktif.lon);
    }

    // --- PENGAMBILAN RUTE OSRM DENGAN DETAIL NAMA JALAN & PERSIMPANGAN ---
    private class AmbilRuteTask extends AsyncTask<Double, Void, List<InstruksiRute>> {
        @Override
        protected List<InstruksiRute> doInBackground(Double... coords) {
            List<InstruksiRute> hasil = new ArrayList<>();
            try {
                String urlStr = "https://router.project-osrm.org/route/v1/driving/" + coords[1] + "," + coords[0] + ";" + coords[3] + "," + coords[2] + "?overview=false&steps=true&geometries=geojson&language=id";
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                
                JSONObject json = new JSONObject(sb.toString());
                JSONArray routes = json.getJSONArray("routes");
                if (routes.length() > 0) {
                    JSONArray steps = routes.getJSONObject(0).getJSONArray("legs").getJSONObject(0).getJSONArray("steps");
                    for (int i = 0; i < steps.length(); i++) {
                        JSONObject step = steps.getJSONObject(i);
                        JSONObject maneuver = step.getJSONObject("maneuver");
                        String instruction = maneuver.optString("instruction", "Lanjutkan");
                        String streetName = step.optString("name", "");
                        
                        JSONArray locArr = maneuver.getJSONArray("location");
                        double stepLat = locArr.getDouble(1);
                        double stepLon = locArr.getDouble(0);
                        
                        String pesanGabungan = instruction;
                        if (!streetName.isEmpty() && !pesanGabungan.contains(streetName)) {
                            pesanGabungan += " ke " + streetName;
                        }
                        
                        hasil.add(new InstruksiRute(stepLat, stepLon, pesanGabungan, streetName));
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
                mulaiSensorKompas();
                ucapkanSuara("Navigasi detail rute dimulai. Siap memandu persimpangan dan nama jalan.");
            } else {
                if (lokasiNavigasiAktif != null) {
                    daftarInstruksi.add(new InstruksiRute(lokasiNavigasiAktif.lat, lokasiNavigasiAktif.lon, "Tuju titik akhir.", "tujuan"));
                }
                mulaiMendengarkanGPS();
                mulaiSensorKompas();
                ucapkanSuara("Navigasi garis lurus dimulai.");
            }
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location != null) {
            double cLat = location.getLatitude();
            double cLon = location.getLongitude();
            
            float speed = location.hasSpeed() ? location.getSpeed() : 0.0f;
            
            float thresholdJarakPicu;
            int radiusPencarianApi;
            float headingGuna;
            
            if (speed > 3.0f && location.hasBearing()) {
                thresholdJarakPicu = 20.0f; 
                radiusPencarianApi = 100;   
                headingGuna = location.getBearing(); 
            } else {
                thresholdJarakPicu = 5.0f;  
                radiusPencarianApi = 40;    
                headingGuna = currentAzimuth;        
            }

            if (isEksplorasiFiturAktif && !isNavigating && !sedangMemindaiOtomatis) {
                float[] dist = new float[1];
                if (lastExplorationLat == 0.0) {
                    lastExplorationLat = cLat; lastExplorationLon = cLon;
                }
                Location.distanceBetween(lastExplorationLat, lastExplorationLon, cLat, cLon, dist);
                if (dist[0] >= thresholdJarakPicu) {
                    lastExplorationLat = cLat; lastExplorationLon = cLon;
                    sedangMemindaiOtomatis = true;
                    new EksplorasiKompasProTask().execute(cLat, cLon, (double) radiusPencarianApi, (double) headingGuna);
                }
            }

            // --- NAVIGASI BELOKAN & NAMA JALAN REAL-TIME ---
            if (isNavigating && lokasiNavigasiAktif != null) {
                if (!daftarInstruksi.isEmpty() && indexInstruksiAktif < daftarInstruksi.size()) {
                    InstruksiRute instruksi = daftarInstruksi.get(indexInstruksiAktif);
                    float[] hasilJarak = new float[1];
                    Location.distanceBetween(cLat, cLon, instruksi.lat, instruksi.lon, hasilJarak);
                    
                    if (hasilJarak[0] <= 35.0f && !instruksi.sudahDiumumkan) {
                        ucapkanSuara((int)hasilJarak[0] + " meter lagi, " + instruksi.pesanPanduan);
                        instruksi.sudahDiumumkan = true;
                    }

                    if (hasilJarak[0] <= 6.0f) {
                        indexInstruksiAktif++;
                        if (indexInstruksiAktif < daftarInstruksi.size()) {
                            InstruksiRute nextInstruksi = daftarInstruksi.get(indexInstruksiAktif);
                            ucapkanSuara("Lewati persimpangan. " + nextInstruksi.pesanPanduan);
                        } else {
                            ucapkanSuara("Anda telah tiba di tujuan.");
                            isNavigating = false;
                        }
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
            try { tts.stop(); tts.shutdown(); } catch (Exception e) {}
        }
        if (locationManager != null) locationManager.removeUpdates(this);
        hentikanSensorKompas();
        super.onDestroy();
    }
}