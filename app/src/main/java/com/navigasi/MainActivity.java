package com.navigasi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.OnNmeaMessageListener;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

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

    private float kecepatanBicara = 1.0f;     
    private String targetTtsEngine = null; 
    private int selectedAudioStream = AudioManager.STREAM_MUSIC;

    private List<LokasiTersimpan> daftarLokasiTersimpan = new ArrayList<>();
    private LokasiTersimpan lokasiNavigasiAktif = null;
    
    private boolean isNavigating = false;
    
    private boolean isEksplorasiFiturAktif = false;
    private boolean sedangMemindaiOtomatis = false;
    private double lastExplorationLat = 0.0;
    private double lastExplorationLon = 0.0;
    
    private List<String> riwayatTempatDiumumkan = new ArrayList<>();
    private List<InstruksiRute> daftarInstruksi = new ArrayList<>();
    private int indexInstruksiAktif = 0;

    // Variabel pendukung NMEA & Eksplorasi Real-Time
    private OnNmeaMessageListener nmeaListener;
    private int jumlahSatelitAktif = 0;
    private Handler explorationHandler = new Handler();
    private Runnable explorationRunnable;

    private static final int PERMISSION_REQUEST_CODE = 100;

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
        cekDanMintaIzinLokasi();
        muatDataLokasiDariPrefs();
        inisialisasiSensorKompasPro();
        inisialisasiTtsMandiri();
        tampilkanMenuUtama();
    }

    private void cekDanMintaIzinLokasi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                }, PERMISSION_REQUEST_CODE);
            }
        }
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
                    mulaiSensorKompas();
                    mulaiMendengarkanGPS();
                    ucapkanSuara("Eksplorasi real-time diaktifkan.");
                    
                    mulaiEksplorasiRealTime();
                } else {
                    hentikanEksplorasiRealTime();
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

    // =========================================================================
    // FITUR PENGUNCIAN LOKASI (NMEA MENTAH LANGIT, MIN 7 SATELIT, FILTER & TIMEOUT)
    // =========================================================================
    private void kunciLokasiSangatAkurat() {
        ucapkanSuara("Mencari konstelasi satelit murni di langit. Pastikan di area terbuka.");
        info.setText("Memindai satelit langit (Min 7 satelit, Maks 3m)...");

        final LocationManager tempLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (tempLocationManager == null) {
            ucapkanSuara("Layanan lokasi tidak tersedia.");
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ucapkanSuara("Izin lokasi belum diberikan.");
            return;
        }

        if (!tempLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            ucapkanSuara("GPS perangkat Anda nonaktif. Mohon aktifkan GPS.");
            info.setText("Gagal: GPS nonaktif.");
            return;
        }

        jumlahSatelitAktif = 0;
        final Handler timeoutHandler = new Handler();
        final LocationListener[] activeListenerHolder = new LocationListener[1];

        // Pendengar NMEA untuk memvalidasi jumlah satelit asli dari langit
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            nmeaListener = new OnNmeaMessageListener() {
                @Override
                public void onNmeaMessage(String message, long timestamp) {
                    if (message.startsWith("$GPGGA") || message.startsWith("$GNGGA")) {
                        String[] tokens = message.split(",");
                        if (tokens.length > 7 && !tokens[7].isEmpty()) {
                            try {
                                jumlahSatelitAktif = Integer.parseInt(tokens[7]);
                            } catch (NumberFormatException e) {}
                        }
                    }
                }
            };
            try {
                tempLocationManager.addNmeaListener(nmeaListener, null);
            } catch (SecurityException e) {}
        }

        final Runnable timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                if (activeListenerHolder[0] != null) {
                    try {
                        tempLocationManager.removeUpdates(activeListenerHolder[0]);
                    } catch (Exception e) {}
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && nmeaListener != null) {
                    try { tempLocationManager.removeNmeaListener(nmeaListener); } catch (Exception e) {}
                }
                ucapkanSuara("Lokasi akurat tidak ditemukan. Satelit terhalang payung atau atap.");
                info.setText("Penguncian gagal: Satelit tidak cukup (Timeout 20s).");
            }
        };

        // Batas waktu 20 detik
        timeoutHandler.postDelayed(timeoutRunnable, 20000);

        final LocationListener gpsIsolasiListener = new LocationListener() {
            private Location titikSebelumnya = null;

            @Override
            public void onLocationChanged(final Location loc) {
                if (loc == null) return;

                if (!LocationManager.GPS_PROVIDER.equals(loc.getProvider())) return;

                long waktuSekarang = System.currentTimeMillis();
                if (Math.abs(waktuSekarang - loc.getTime()) > 1500) return;

                if (titikSebelumnya != null) {
                    float[] jarakLompat = new float[1];
                    Location.distanceBetween(
                        titikSebelumnya.getLatitude(), titikSebelumnya.getLongitude(),
                        loc.getLatitude(), loc.getLongitude(),
                        jarakLompat
                    );
                    if (jarakLompat[0] > 2.0f) {
                        titikSebelumnya = loc;
                        return; 
                    }
                }
                titikSebelumnya = loc;

                if (!loc.hasAccuracy()) return;
                float akurasi = loc.getAccuracy();

                // Syarat Ketat: Akurasi <= 3m DAN jumlah satelit aktif dari langit minimal 7 buah (payung akan menjatuhkan angka ini)
                if (akurasi <= 3.0f) {
                    if (jumlahSatelitAktif >= 7 || jumlahSatelitAktif == 0) {
                        timeoutHandler.removeCallbacks(timeoutRunnable);
                        try {
                            tempLocationManager.removeUpdates(this);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && nmeaListener != null) {
                                tempLocationManager.removeNmeaListener(nmeaListener);
                            }
                        } catch (Exception e) {}

                        final double finalLat = loc.getLatitude();
                        final double finalLon = loc.getLongitude();

                        ucapkanSuara("Sinyal satelit langit terverifikasi. Akurasi " + (int)akurasi + " meter.");
                        tampilkanDialogKonfirmasiSimpan(finalLat, finalLon, (int)akurasi);
                        return;
                    }
                }
                
                info.setText("Memindai langit... Akurasi: ± " + (int)akurasi + "m | Satelit: " + (jumlahSatelitAktif > 0 ? jumlahSatelitAktif : "Menunggu..."));
            }

            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override public void onProviderEnabled(String provider) {}
            @Override public void onProviderDisabled(String provider) {}
        };

        activeListenerHolder[0] = gpsIsolasiListener;

        try {
            tempLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0.0f, gpsIsolasiListener);
        } catch (SecurityException e) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
            ucapkanSuara("Gagal mengakses GPS.");
        }
    }

    private void tampilkanDialogKonfirmasiSimpan(final double finalLat, final double finalLon, int akurasi) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Lokasi Akurat Terkunci (± " + akurasi + "m)");
        builder.setMessage("Lat: " + finalLat + "\nLon: " + finalLon + "\n\nApakah Anda ingin menyimpan lokasi ini ke daftar tersimpan?");

        final EditText inputNama = new EditText(this);
        inputNama.setText("Lokasi Satelit " + (daftarLokasiTersimpan.size() + 1));
        inputNama.setPadding(40, 20, 40, 20);
        
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(40, 10, 40, 10);
        
        TextView labelNama = new TextView(this);
        labelNama.setText("Nama Lokasi:");
        container.addView(labelNama);
        container.addView(inputNama);
        
        builder.setView(container);

        builder.setPositiveButton("Simpan", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String namaLokasi = inputNama.getText().toString().trim();
                if (namaLokasi.isEmpty()) namaLokasi = "Lokasi Satelit";
                
                daftarLokasiTersimpan.add(new LokasiTersimpan(namaLokasi, finalLat, finalLon));
                simpanDataLokasiKePrefs();
                
                ucapkanSuara("Lokasi " + namaLokasi + " berhasil disimpan.");
                info.setText("Lokasi Tersimpan:\n" + namaLokasi + "\nLat: " + finalLat + ", Lon: " + finalLon);
            }
        });

        builder.setNegativeButton("Tidak", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ucapkanSuara("Penguncian dibatalkan, lokasi tidak disimpan.");
                info.setText("Penguncian selesai (Tidak disimpan).");
            }
        });

        builder.setCancelable(false);
        builder.show();
    }
    // =========================================================================

    private void mulaiEksplorasiRealTime() {
        if (explorationRunnable == null) {
            explorationRunnable = new Runnable() {
                @Override
                public void run() {
                    if (isEksplorasiFiturAktif && !isNavigating) {
                        Location loc = dapatkanLokasiTerakhir();
                        if (loc != null) {
                            double cLat = loc.getLatitude();
                            double cLon = loc.getLongitude();
                            float headingGuna = currentAzimuth;

                            new EksplorasiKompasProTask().execute(cLat, cLon, 40.0, (double) headingGuna);
                        }
                    }
                    explorationHandler.postDelayed(this, 6000);
                }
            };
        }
        explorationHandler.post(explorationRunnable);
    }

    private void hentikanEksplorasiRealTime() {
        if (explorationHandler != null && explorationRunnable != null) {
            explorationHandler.removeCallbacks(explorationRunnable);
        }
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

    private Location dapatkanLokasiTerakhir() {
        Location bestLoc = null;
        try {
            if (locationManager == null) {
                locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            }
            if (locationManager != null && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                List<String> providers = locationManager.getProviders(true);
                for (String provider : providers) {
                    Location l = locationManager.getLastKnownLocation(provider);
                    if (l != null) {
                        if (bestLoc == null || l.getTime() > bestLoc.getTime()) {
                            bestLoc = l;
                        }
                    }
                }
            }
        } catch (Exception e) {}
        return bestLoc;
    }

    private void tampilkanDialogPencarianLokasi() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Cari Lokasi Tujuan");
        final EditText input = new EditText(this);
        input.setHint("Contoh: Taman Karampuang");
        input.setPadding(40, 30, 40, 30);
        builder.setView(input);

        builder.setPositiveButton("Cari", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String query = input.getText().toString().trim();
                if (!query.isEmpty()) {
                    Location loc = dapatkanLokasiTerakhir();
                    double currentLat = (loc != null) ? loc.getLatitude() : 0.0;
                    double currentLon = (loc != null) ? loc.getLongitude() : 0.0;

                    ucapkanSuara("Mencari " + query + "...");
                    new CariLokasiTask(currentLat, currentLon).execute(query);
                }
            }
        });
        builder.setNegativeButton("Batal", null);
        builder.show();
    }

    private class CariLokasiTask extends AsyncTask<String, Void, HasilPencarian> {
        double userLat, userLon;

        public CariLokasiTask(double lat, double lon) {
            this.userLat = lat;
            this.userLon = lon;
        }

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
                    double lat = obj.getDouble("lat");
                    double lon = obj.getDouble("lon");
                    String displayName = obj.getString("display_name");
                    
                    int jarakMeter = 0;
                    if (userLat != 0.0 && userLon != 0.0) {
                        float[] results = new float[1];
                        Location.distanceBetween(userLat, userLon, lat, lon, results);
                        jarakMeter = (int) results[0];
                    }

                    return new HasilPencarian(lat, lon, displayName, jarakMeter);
                }
            } catch (Exception e) {}
            return null;
        }

        @Override
        protected void onPostExecute(HasilPencarian hasil) {
            if (hasil != null) {
                lokasiNavigasiAktif = new LokasiTersimpan(hasil.namaPendek(), hasil.lat, hasil.lon);
                
                String pesanHasil;
                if (hasil.jarakMeter > 0) {
                    pesanHasil = hasil.namaPendek() + " ditemukan, berjarak " + hasil.jarakMeter + " meter dari posisi Anda.";
                } else {
                    pesanHasil = hasil.namaPendek() + " ditemukan.";
                }
                
                ucapkanSuara(pesanHasil);
                info.setText("Hasil Pencarian:\n" + hasil.namaPendek() + "\nJarak: " + hasil.jarakMeter + " meter\nLat: " + hasil.lat + ", Lon: " + hasil.lon);

                AlertDialog.Builder saveBuilder = new AlertDialog.Builder(MainActivity.this);
                saveBuilder.setTitle("Simpan Lokasi Ini?");
                saveBuilder.setMessage("Apakah Anda ingin menyimpan \"" + hasil.namaPendek() + "\" ke daftar lokasi tersimpan?");
                
                saveBuilder.setPositiveButton("Simpan", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        daftarLokasiTersimpan.add(new LokasiTersimpan(hasil.namaPendek(), hasil.lat, hasil.lon));
                        simpanDataLokasiKePrefs();
                        ucapkanSuara("Lokasi " + hasil.namaPendek() + " berhasil disimpan.");
                    }
                });
                saveBuilder.setNegativeButton("Tidak", null);
                saveBuilder.show();

            } else {
                ucapkanSuara("Lokasi tidak ditemukan. Coba masukkan nama tempat dengan lebih spesifik.");
                info.setText("Pencarian gagal.");
            }
        }
    }

    private static class HasilPencarian {
        double lat, lon;
        String displayName;
        int jarakMeter;

        public HasilPencarian(double lat, double lon, String displayName, int jarakMeter) {
            this.lat = lat; 
            this.lon = lon; 
            this.displayName = displayName;
            this.jarakMeter = jarakMeter;
        }

        public String namaPendek() {
            if (displayName != null && displayName.contains(",")) {
                return displayName.split(",")[0].trim();
            }
            return displayName;
        }
    }

    private void tampilkanDialogSimpanLokasiCustom(final String defaultNama) {
        Location loc = dapatkanLokasiTerakhir();
        if (loc == null) {
            ucapkanSuara("GPS belum siap.");
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
        builder.setTitle("Kelola Lokasi Tersimpan");
        builder.setItems(daftarNama, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, final int index) {
                final LokasiTersimpan lokasiPilihan = daftarLokasiTersimpan.get(index);
                
                CharSequence[] opsiAksi = new CharSequence[]{"Jadikan Tujuan Navigasi", "Edit Nama Lokasi", "Hapus Lokasi"};
                AlertDialog.Builder actionBuilder = new AlertDialog.Builder(MainActivity.this);
                actionBuilder.setTitle("Pilih Aksi: " + lokasiPilihan.nama);
                actionBuilder.setItems(opsiAksi, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int aksiIndex) {
                        if (aksiIndex == 0) {
                            lokasiNavigasiAktif = lokasiPilihan;
                            ucapkanSuara("Tujuan diubah ke " + lokasiNavigasiAktif.nama);
                            info.setText("Tujuan Aktif:\n" + lokasiNavigasiAktif.nama + "\nLat: " + lokasiNavigasiAktif.lat + ", Lon: " + lokasiNavigasiAktif.lon);
                        } else if (aksiIndex == 1) {
                            AlertDialog.Builder editBuilder = new AlertDialog.Builder(MainActivity.this);
                            editBuilder.setTitle("Edit Nama Lokasi");
                            final EditText inputEdit = new EditText(MainActivity.this);
                            inputEdit.setText(lokasiPilihan.nama);
                            inputEdit.setPadding(40, 30, 40, 30);
                            editBuilder.setView(inputEdit);

                            editBuilder.setPositiveButton("Simpan", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogEdit, int whichEdit) {
                                    String namaBaru = inputEdit.getText().toString().trim();
                                    if (!namaBaru.isEmpty()) {
                                        lokasiPilihan.nama = namaBaru;
                                        simpanDataLokasiKePrefs();
                                        ucapkanSuara("Nama lokasi diubah menjadi " + namaBaru);
                                    }
                                }
                            });
                            editBuilder.setNegativeButton("Batal", null);
                            editBuilder.show();

                        } else if (aksiIndex == 2) {
                            AlertDialog.Builder hapusBuilder = new AlertDialog.Builder(MainActivity.this);
                            hapusBuilder.setTitle("Hapus Lokasi");
                            hapusBuilder.setMessage("Apakah Anda yakin ingin menghapus \"" + lokasiPilihan.nama + "\"?");
                            hapusBuilder.setPositiveButton("Ya", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogHapus, int whichHapus) {
                                    if (lokasiNavigasiAktif == lokasiPilihan) {
                                        lokasiNavigasiAktif = null;
                                    }
                                    daftarLokasiTersimpan.remove(index);
                                    simpanDataLokasiKePrefs();
                                    ucapkanSuara("Lokasi dihapus.");
                                    info.setText("Total Lokasi Tersimpan: " + daftarLokasiTersimpan.size() + "\nSistem Sensor Pro Siap.");
                                }
                            });
                            hapusBuilder.setNegativeButton("Batal", null);
                            hapusBuilder.show();
                        }
                    }
                });
                actionBuilder.show();
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
                tts.speak(teks, TextToSpeech.QUEUE_ADD, params, null);
            } catch (Exception e) {}
        }
    }

    private void mulaiMendengarkanGPS() {
        try {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 1.0f, this);
                }
            }
        } catch (SecurityException e) {}
    }

    private void cekPosisiAlamatLengkap() {
        Location loc = dapatkanLokasiTerakhir();
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
                int limit = Math.min(result.size(), 2);
                for (int i = 0; i < limit; i++) {
                    TempatPro t = result.get(i);
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
                        if (riwayatTempatDiumumkan.size() > 15) riwayatTempatDiumumkan.remove(0);
                    }
                }
                info.setText("Eksplorasi Real-Time Aktif:\n" + sb.toString());
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
        Location loc = dapatkanLokasiTerakhir();

        double sLat = (loc != null) ? loc.getLatitude() : lokasiNavigasiAktif.lat - 0.001;
        double sLon = (loc != null) ? loc.getLongitude() : lokasiNavigasiAktif.lon - 0.001;

        new AmbilRuteTask().execute(sLat, sLon, lokasiNavigasiAktif.lat, lokasiNavigasiAktif.lon);
    }

    private class AmbilRuteTask extends AsyncTask<Double, Void, List<InstruksiRute>> {
        @Override
        protected List<InstruksiRute> doInBackground(Double... coords) {
            List<InstruksiRute> hasil = new ArrayList<>();
            try {
                String urlStr = "https://router.project-osrm.org/route/v1/foot/" + coords[1] + "," + coords[0] + ";" + coords[3] + "," + coords[2] + "?overview=false&steps=true&geometries=geojson&language=id";
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "NavigasiAplikasiAndroid");
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
                            pesanGabungan += " melalui " + streetName;
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
                ucapkanSuara("Rute berhasil dimuat. Siap memulai panduan navigasi.");
            } else {
                if (lokasiNavigasiAktif != null) {
                    daftarInstruksi.add(new InstruksiRute(lokasiNavigasiAktif.lat, lokasiNavigasiAktif.lon, "Menuju titik akhir tujuan.", "tujuan"));
                }
                mulaiMendengarkanGPS();
                mulaiSensorKompas();
                ucapkanSuara("Rute detail tidak ditemukan, beralih ke navigasi langsung.");
            }
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location != null) {
            double cLat = location.getLatitude();
            double cLon = location.getLongitude();
            
            if (isNavigating && lokasiNavigasiAktif != null) {
                if (!daftarInstruksi.isEmpty() && indexInstruksiAktif < daftarInstruksi.size()) {
                    InstruksiRute instruksi = daftarInstruksi.get(indexInstruksiAktif);
                    float[] hasilJarak = new float[1];
                    Location.distanceBetween(cLat, cLon, instruksi.lat, instruksi.lon, hasilJarak);
                    float jarakMeter = hasilJarak[0];

                    if (jarakMeter <= 40.0f && jarakMeter > 20.0f && !instruksi.sudahDiumumkan) {
                        ucapkanSuara("Kurang lebih 40 meter lagi, " + instruksi.pesanPanduan);
                        instruksi.sudahDiumumkan = true;
                    } else if (jarakMeter <= 20.0f && jarakMeter > 8.0f) {
                        ucapkanSuara((int)jarakMeter + " meter lagi.");
                    } else if (jarakMeter <= 8.0f) {
                        ucapkanSuara("Bersiap, " + instruksi.pesanPanduan);
                        indexInstruksiAktif++;
                        if (indexInstruksiAktif < daftarInstruksi.size()) {
                            InstruksiRute nextInstruksi = daftarInstruksi.get(indexInstruksiAktif);
                            ucapkanSuara("Lanjutkan. " + nextInstruksi.pesanPanduan);
                        } else {
                            ucapkanSuara("Anda telah tiba di tujuan " + lokasiNavigasiAktif.nama + ".");
                            isNavigating = false;
                            info.setText("Tiba di Tujuan:\n" + lokasiNavigasiAktif.nama);
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
        hentikanEksplorasiRealTime();
        if (tts != null) {
            try { tts.stop(); tts.shutdown(); } catch (Exception e) {}
        }
        if (locationManager != null) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.removeUpdates(this);
            }
            if (nmeaListener != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try { locationManager.removeNmeaListener(nmeaListener); } catch (Exception e) {}
            }
        }
        hentikanSensorKompas();
        super.onDestroy();
    }
}