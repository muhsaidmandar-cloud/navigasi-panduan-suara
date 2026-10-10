package com.navigasi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
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

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {

    private static final String PREF_NAME = "jieshuo_maps_config";
    private static final String KEY_API = "locationiq_access_token";
    private static final String KEY_BOOKMARKS = "saved_locations_json";
    private static final String KEY_TTS_ENGINE = "selected_tts_engine";
    
    private TextToSpeech ttsEngine;
    private boolean isTtsReady = false;
    private LocationManager locationManager;
    private LocationListener activeLocationListener;
    
    // Status Navigasi Aktif
    private BookmarkItem activeTargetItem = null;
    private StartLoc activeStartLoc = null;
    private boolean isNavigating = false;

    public static class BookmarkItem {
        String name;
        double lat;
        double lng;
        String note;

        public BookmarkItem(String name, double lat, double lng, String note) {
            this.name = name;
            this.lat = lat;
            this.lng = lng;
            this.note = note != null ? note : "";
        }
    }

    public static class StartLoc {
        double lat;
        double lng;

        public StartLoc(double lat, double lng) {
            this.lat = lat;
            this.lng = lng;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        initTtsEngine(null);
        menuUtama();
    }

    // ========================================================
    // KONTROL TTS
    // ========================================================
    private String getSavedTtsEngine() {
        SharedPreferences sp = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        return sp.getString(KEY_TTS_ENGINE, "");
    }

    private void saveTtsEngine(String pkgName) {
        SharedPreferences sp = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        sp.edit().putString(KEY_TTS_ENGINE, pkgName).apply();
    }

    private void initTtsEngine(final Runnable onReadyCallback) {
        String selectedPkg = getSavedTtsEngine();
        if (ttsEngine != null) {
            try {
                ttsEngine.stop();
                ttsEngine.shutdown();
            } catch (Exception e) {}
            ttsEngine = null;
        }
        isTtsReady = false;

        try {
            if (selectedPkg != null && !selectedPkg.isEmpty()) {
                ttsEngine = new TextToSpeech(this, this, selectedPkg);
            } else {
                ttsEngine = new TextToSpeech(this, this);
            }
        } catch (Exception e) {
            ttsEngine = new TextToSpeech(this, this);
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS && ttsEngine != null) {
            try {
                int result = ttsEngine.setLanguage(new Locale("id", "ID"));
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    ttsEngine.setLanguage(Locale.getDefault());
                }
                isTtsReady = true;
            } catch (Exception e) {
                isTtsReady = false;
            }
        } else {
            isTtsReady = false;
        }
    }

    private void suaraNavigasi(String teks) {
        if (isTtsReady && ttsEngine != null) {
            try {
                ttsEngine.speak(teks, TextToSpeech.QUEUE_FLUSH, null, "NavID_" + System.currentTimeMillis());
            } catch (Exception e) {}
        }
    }

    private void stopSuaraTts() {
        if (ttsEngine != null) {
            try { ttsEngine.stop(); } catch (Exception e) {}
        }
    }

    private void pilihEngineTtsDialog() {
        if (ttsEngine == null) {
            suaraNavigasi("TTS belum siap.");
            menuUtama();
            return;
        }
        
        List<TextToSpeech.EngineInfo> engines = ttsEngine.getEngines();
        final List<String> engineList = new ArrayList<>();
        final List<String> pkgList = new ArrayList<>();

        if (engines != null) {
            for (TextToSpeech.EngineInfo eng : engines) {
                engineList.add(eng.label);
                pkgList.add(eng.name);
            }
        }

        if (engineList.isEmpty()) {
            suaraNavigasi("Tidak ditemukan mesin TTS lain di HP Anda.");
            menuUtama();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Pilih Mesin Suara (TTS) Navigasi");
        builder.setItems(engineList.toArray(new CharSequence[0]), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String selectedPkg = pkgList.get(which);
                saveTtsEngine(selectedPkg);
                initTtsEngine(new Runnable() {
                    @Override
                    public void run() {
                        suaraNavigasi("Suara navigasi berhasil diubah.");
                    }
                });
                menuUtama();
            }
        });
        builder.setNegativeButton("Kembali", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                menuUtama();
            }
        });
        builder.show();
    }

    // ========================================================
    // PENYIMPANAN API KEY & BOOKMARK
    // ========================================================
    private String getSavedApiKey() {
        SharedPreferences sp = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        return sp.getString(KEY_API, "");
    }

    private void saveApiKey(String key) {
        SharedPreferences sp = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        sp.edit().putString(KEY_API, key).apply();
    }

    private List<BookmarkItem> getSavedBookmarks() {
        List<BookmarkItem> list = new ArrayList<>();
        SharedPreferences sp = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String rawJson = sp.getString(KEY_BOOKMARKS, "[]");
        try {
            JSONArray arr = new JSONArray(rawJson);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                list.add(new BookmarkItem(
                    obj.getString("name"),
                    obj.getDouble("lat"),
                    obj.getDouble("lng"),
                    obj.optString("note", "")
                ));
            }
        } catch (Exception e) {}
        return list;
    }

    private void saveBookmarksTable(List<BookmarkItem> list) {
        SharedPreferences sp = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        try {
            JSONArray arr = new JSONArray();
            for (BookmarkItem item : list) {
                JSONObject obj = new JSONObject();
                obj.put("name", item.name);
                obj.put("lat", item.lat);
                obj.put("lng", item.lng);
                obj.put("note", item.note);
                arr.put(obj);
            }
            sp.edit().putString(KEY_BOOKMARKS, arr.toString()).apply();
        } catch (Exception e) {}
    }

    private BookmarkItem simpanLokasiBaru(String nama, double lat, double lng, String note) {
        List<BookmarkItem> list = getSavedBookmarks();
        BookmarkItem newItem = new BookmarkItem(nama, lat, lng, note);
        list.add(newItem);
        saveBookmarksTable(list);
        suaraNavigasi("Lokasi " + nama + " berhasil disimpan.");
        return newItem;
    }

    private int hitungJarakMeter(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (int) Math.floor(R * c);
    }

    // ========================================================
    // KONTROL NAVIGASI OTOMATIS & CEK JARAK
    // ========================================================
    private void stopLocationUpdates() {
        if (locationManager != null && activeLocationListener != null) {
            try {
                locationManager.removeUpdates(activeLocationListener);
            } catch (Exception e) {}
        }
        activeLocationListener = null;
    }

    private void hentikanNavigasiOtomatis() {
        isNavigating = false;
        activeTargetItem = null;
        activeStartLoc = null;
        stopLocationUpdates();
        stopSuaraTts();
        suaraNavigasi("Navigasi dihentikan.");
        menuUtama();
    }

    private void cekSisaJarakDanCatatan() {
        if (!isNavigating || activeTargetItem == null) {
            suaraNavigasi("Navigasi otomatis sedang tidak aktif.");
            menuUtama();
            return;
        }

        suaraNavigasi("Mengecek jarak perjalanan...");
        if (locationManager == null || ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            suaraNavigasi("Izin atau layanan lokasi tidak tersedia.");
            menuUtama();
            return;
        }

        final LocationListener checkListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location loc) {
                if (loc != null) {
                    try {
                        locationManager.removeUpdates(this);
                    } catch (Exception e) {}

                    int sisaJarak = hitungJarakMeter(loc.getLatitude(), loc.getLongitude(), activeTargetItem.lat, activeTargetItem.lng);
                    String info = "";
                    if (activeStartLoc != null && (activeStartLoc.lat != 0 || activeStartLoc.lng != 0)) {
                        int sudahDitempuh = hitungJarakMeter(activeStartLoc.lat, activeStartLoc.lng, loc.getLatitude(), loc.getLongitude());
                        info = "Anda sudah berjalan " + sudahDitempuh + " meter. Sisa jarak ke " + activeTargetItem.name + " adalah " + sisaJarak + " meter lagi.";
                    } else {
                        info = "Sisa jarak ke " + activeTargetItem.name + " adalah " + sisaJarak + " meter lagi.";
                    }

                    if (activeTargetItem.note != null && !activeTargetItem.note.isEmpty()) {
                        info += " Catatan panduan: " + activeTargetItem.note;
                    }

                    suaraNavigasi(info);
                    menuUtama();
                }
            }
            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override public void onProviderEnabled(String provider) {}
            @Override public void onProviderDisabled(String provider) {}
        };

        try {
            locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, checkListener, Looper.getMainLooper());
        } catch (Exception e) {}
    }

    private void mulaiNavigasiOtomatis(BookmarkItem targetItem) {
        activeTargetItem = targetItem;
        isNavigating = true;

        if (locationManager == null || !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            suaraNavigasi("GPS belum aktif.");
            isNavigating = false;
            activeTargetItem = null;
            menuUtama();
            return;
        }

        activeStartLoc = new StartLoc(0, 0);
        stopLocationUpdates();
        suaraNavigasi("Navigasi otomatis dimulai menuju " + targetItem.name + ".");

        activeLocationListener = new LocationListener() {
            private double lastValidLat = 0, lastValidLng = 0;
            private boolean hasAnchor = false;
            private int maxSudahDitempuh = 0;

            @Override
            public void onLocationChanged(Location loc) {
                if (!isNavigating || activeTargetItem == null) {
                    stopLocationUpdates();
                    stopSuaraTts();
                    return;
                }

                if (loc != null && LocationManager.GPS_PROVIDER.equals(loc.getProvider())) {
                    float acc = loc.hasAccuracy() ? loc.getAccuracy() : 99;
                    if (acc <= 3) {
                        double lat = loc.getLatitude();
                        double lng = loc.getLongitude();

                        if (!hasAnchor) {
                            if (lastValidLat != 0 && lastValidLng != 0) {
                                int jarakAwal = hitungJarakMeter(lastValidLat, lastValidLng, lat, lng);
                                if (jarakAwal < 5) {
                                    hasAnchor = true;
                                    activeStartLoc = new StartLoc(lat, lng);
                                    suaraNavigasi("Titik awal terkunci stabil pada jarak " + jarakAwal + " meter.");
                                }
                            } else {
                                lastValidLat = lat;
                                lastValidLng = lng;
                                return;
                            }
                        }

                        if (!hasAnchor) {
                            lastValidLat = lat;
                            lastValidLng = lng;
                            return;
                        }

                        int jarakDrift = hitungJarakMeter(lastValidLat, lastValidLng, lat, lng);
                        if (jarakDrift < 3 || jarakDrift > 30) return;

                        lastValidLat = lat;
                        lastValidLng = lng;
                        int sisaJarak = hitungJarakMeter(lat, lng, targetItem.lat, targetItem.lng);

                        if (sisaJarak <= 4) {
                            String pesanTiba = "Anda telah tiba di tujuan " + targetItem.name;
                            if (targetItem.note != null && !targetItem.note.isEmpty()) {
                                pesanTiba += ". Catatan panduan: " + targetItem.note;
                            }
                            stopLocationUpdates();
                            isNavigating = false;
                            activeTargetItem = null;
                            activeStartLoc = null;
                            stopSuaraTts();
                            suaraNavigasi(pesanTiba);
                        } else {
                            String pesanProgres = "";
                            if (activeStartLoc != null && (activeStartLoc.lat != 0 || activeStartLoc.lng != 0)) {
                                int hitungTemp = hitungJarakMeter(activeStartLoc.lat, activeStartLoc.lng, lat, lng);
                                if (hitungTemp > maxSudahDitempuh) {
                                    maxSudahDitempuh = hitungTemp;
                                }
                                pesanProgres = "Sudah berjalan " + maxSudahDitempuh + " meter. Sisa jarak ke " + targetItem.name + " adalah " + sisaJarak + " meter lagi.";
                            } else {
                                pesanProgres = "Sisa jarak ke " + targetItem.name + " adalah " + sisaJarak + " meter lagi.";
                            }
                            suaraNavigasi(pesanProgres);
                        }
                    }
                }
            }
            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override public void onProviderEnabled(String provider) {}
            @Override public void onProviderDisabled(String provider) {}
        };

        try {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0.5f, activeLocationListener, Looper.getMainLooper());
            }
        } catch (Exception e) {}
    }

    private void bukaAplikasiNavigasi(double lat, double lng) {
        try {
            Uri geoUri = Uri.parse("geo:" + lat + "," + lng + "?q=" + lat + "," + lng);
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, geoUri);
            mapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Intent chooser = Intent.createChooser(mapIntent, "Pilih Aplikasi Navigasi");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(chooser);
        } catch (Exception e) {
            suaraNavigasi("Gagal membuka aplikasi peta.");
        }
    }

    // ========================================================
    // KUNCI LOKASI 100% PARABOLA DENGAN PESAN KETAT
    // ========================================================
    private void kunciLokasiSaatIni() {
        suaraNavigasi("Mengaktifkan mode parabola satelit. Pastikan di bawah langit terbuka tanpa penghalang.");

        if (locationManager == null || !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            suaraNavigasi("GPS belum aktif.");
            menuUtama();
            return;
        }

        final Handler timeoutHandler = new Handler(Looper.getMainLooper());
        final LocationListener[] tempListenerHolder = new LocationListener[1];
        final boolean[] isLocked = {false};

        tempListenerHolder[0] = new LocationListener() {
            private int sampleCount = 0;
            private double accumLat = 0, accumLng = 0;
            private double lastLat = 0, lastLng = 0;

            @Override
            public void onLocationChanged(Location loc) {
                if (isLocked[0]) return;
                if (loc != null && LocationManager.GPS_PROVIDER.equals(loc.getProvider())) {
                    long currentTime = System.currentTimeMillis();
                    long timeLoc = loc.getTime();
                    if (timeLoc > 0 && (currentTime - timeLoc > 800)) return;

                    float acc = loc.hasAccuracy() ? loc.getAccuracy() : 99;
                    if (acc <= 3) {
                        int satCount = 0;
                        Bundle extras = loc.getExtras();
                        if (extras != null && extras.containsKey("satellites")) {
                            satCount = extras.getInt("satellites");
                        }
                        if (satCount > 0 && satCount < 5) return;

                        double lat = loc.getLatitude();
                        double lng = loc.getLongitude();

                        if (sampleCount == 0) {
                            lastLat = lat;
                            lastLng = lng;
                            accumLat = lat;
                            accumLng = lng;
                            sampleCount = 1;
                            suaraNavigasi("Sinyal satelit parabola mendeteksi celah terbuka, mengunci kestabilan...");
                        } else {
                            int selisihJarak = hitungJarakMeter(lastLat, lastLng, lat, lng);
                            if (selisihJarak <= 1) {
                                sampleCount++;
                                accumLat += lat;
                                accumLng += lng;
                                lastLat = lat;
                                lastLng = lng;

                                if (sampleCount >= 8) {
                                    isLocked[0] = true;
                                    try { locationManager.removeUpdates(tempListenerHolder[0]); } catch (Exception e) {}
                                    timeoutHandler.removeCallbacksAndMessages(null);

                                    double finalLat = accumLat / 8;
                                    double finalLng = accumLng / 8;
                                    String teksInfo = "Sinyal parabola terkunci sempurna! Akurasi " + (int) acc + " meter.";
                                    pilihAksiLokasi(teksInfo, finalLat, finalLng, "Titik Berdiri Saya");
                                }
                            } else {
                                sampleCount = 1;
                                accumLat = lat;
                                accumLng = lng;
                                lastLat = lat;
                                lastLng = lng;
                            }
                        }
                    }
                }
            }
            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override public void onProviderEnabled(String provider) {}
            @Override public void onProviderDisabled(String provider) {}
        };

        try {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 200, 0.01f, tempListenerHolder[0], Looper.getMainLooper());
            }
        } catch (Exception e) {}

        timeoutHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isLocked[0]) {
                    isLocked[0] = true;
                    try { locationManager.removeUpdates(tempListenerHolder[0]); } catch (Exception e) {}
                    // Pesan suara sesuai permintaan jika terhalang/timeout
                    suaraNavigasi("Lokasi akurat tidak ditemukan karena terhalang.");
                    menuUtama();
                }
            }
        }, 15000);
    }

    // ========================================================
    // PENCARIAN LOKASI DENGAN LOCATIONIQ
    // ========================================================
    private void cariLokasiAkurat() {
        String apiKey = getSavedApiKey();
        if (apiKey.isEmpty()) {
            suaraNavigasi("Access Token LocationIQ belum diatur.");
            inputApiKeyDialog(new Runnable() {
                @Override
                public void run() {
                    dialogCariTeks();
                }
            });
        } else {
            dialogCariTeks();
        }
    }

    private void dialogCariTeks() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Cari Lokasi di Sinjai");
        final EditText input = new EditText(this);
        input.setHint("Contoh: Jalan Anggrek / Masjid Agung");
        builder.setView(input);

        builder.setPositiveButton("Cari", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String query = input.getText().toString().trim();
                if (query.isEmpty()) {
                    suaraNavigasi("Input lokasi tidak boleh kosong");
                    menuUtama();
                    return;
                }
                if (!query.toLowerCase().contains("sinjai")) {
                    query += ", Sinjai";
                }
                suaraNavigasi("Mencari lokasi di Sinjai...");
                prosesGeocodingNative(query);
            }
        });
        builder.setNeutralButton("Ubah Token", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                inputApiKeyDialog(null);
            }
        });
        builder.setNegativeButton("Kembali", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                menuUtama();
            }
        });
        builder.show();
    }

    private void prosesGeocodingNative(final String lokasi) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String apiKey = getSavedApiKey();
                try {
                    String encodedQuery = URLEncoder.encode(lokasi, "UTF-8");
                    String urlString = "https://us1.locationiq.com/v1/search?key=" + apiKey + "&q=" + encodedQuery + "&format=json&accept-language=id";
                    URL url = new URL(urlString);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);

                    if (conn.getResponseCode() == 200) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) sb.append(line);
                        reader.close();
                        conn.disconnect();

                        final JSONArray jsonArray = new JSONArray(sb.toString());
                        if (jsonArray.length() > 0) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        if (jsonArray.length() == 1) {
                                            JSONObject item = jsonArray.getJSONObject(0);
                                            pilihAksiLokasi("Lokasi ditemukan: " + item.getString("display_name"), item.getDouble("lat"), item.getDouble("lon"), item.getString("display_name"));
                                        } else {
                                            pilihPilihanLokasiLocationIQ(jsonArray);
                                        }
                                    } catch (Exception e) {}
                                }
                            });
                        } else {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    suaraNavigasi("Lokasi tidak ditemukan di wilayah Sinjai.");
                                    menuUtama();
                                }
                            });
                        }
                    } else {
                        conn.disconnect();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                suaraNavigasi("Gagal terhubung ke server lokasi.");
                                menuUtama();
                            }
                        });
                    }
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            suaraNavigasi("Gagal memproses pencarian.");
                            menuUtama();
                        }
                    });
                }
            }
        }).start();
    }

    private void pilihPilihanLokasiLocationIQ(final JSONArray results) {
        try {
            final CharSequence[] addresses = new CharSequence[results.length()];
            for (int i = 0; i < results.length(); i++) {
                addresses[i] = results.getJSONObject(i).getString("display_name");
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Pilih Lokasi Sesuai");
            builder.setItems(addresses, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    try {
                        JSONObject selected = results.getJSONObject(which);
                        pilihAksiLokasi("Lokasi dipilih: " + selected.getString("display_name"), selected.getDouble("lat"), selected.getDouble("lon"), selected.getString("display_name"));
                    } catch (Exception e) {}
                }
            });
            builder.setNegativeButton("Kembali", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    menuUtama();
                }
            });
            builder.show();
        } catch (Exception e) {}
    }

    private void inputApiKeyDialog(final Runnable onSuccessCallback) {
        String currentKey = getSavedApiKey();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Access Token LocationIQ");
        builder.setMessage("Masukkan Access Token LocationIQ Anda:");

        final EditText input = new EditText(this);
        input.setHint("pk.xxxxxxxxxxxxxxxx");
        if (!currentKey.isEmpty()) input.setText(currentKey);
        builder.setView(input);

        builder.setPositiveButton("Simpan", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String newKey = input.getText().toString().trim();
                if (newKey.isEmpty()) {
                    suaraNavigasi("Access Token tidak boleh kosong");
                } else {
                    saveApiKey(newKey);
                    suaraNavigasi("Access Token berhasil disimpan");
                    if (onSuccessCallback != null) {
                        onSuccessCallback.run();
                    } else {
                        menuUtama();
                    }
                }
            }
        });
        builder.setNegativeButton("Kembali", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                menuUtama();
            }
        });
        builder.show();
    }

    private void pilihAksiLokasi(String pesanSuara, final double lat, final double lng, final String defaultNama) {
        suaraNavigasi(pesanSuara);
        CharSequence[] options = {
            "▶ Langsung Navigasi Otomatis",
            "🗺️ Buka Aplikasi Navigasi Lain (Google Maps/Lazarillo)",
            "💾 Simpan ke Bookmark Dulu"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Hasil Pencarian Lokasi");
        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    mulaiNavigasiOtomatis(new BookmarkItem(defaultNama, lat, lng, ""));
                } else if (which == 1) {
                    bukaAplikasiNavigasi(lat, lng);
                } else if (which == 2) {
                    dialogSimpanNamaDanCatatan(defaultNama, lat, lng);
                }
            }
        });
        builder.setNegativeButton("Kembali", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                menuUtama();
            }
        });
        builder.show();
    }

    private void dialogSimpanNamaDanCatatan(final String defaultNama, final double lat, final double lng) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Simpan Bookmark Lokasi");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        final EditText txtNama = new EditText(this);
        txtNama.setHint("Nama Penanda");
        txtNama.setText(defaultNama);
        layout.addView(txtNama);

        final EditText txtNote = new EditText(this);
        txtNote.setHint("Catatan Panduan (Misal: Pagar bambu)");
        layout.addView(txtNote);

        builder.setView(layout);

        builder.setPositiveButton("Simpan Saja", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String namaInput = txtNama.getText().toString().trim();
                String noteInput = txtNote.getText().toString().trim();
                if (namaInput.isEmpty()) namaInput = "Lokasi Tanpa Nama";
                simpanLokasiBaru(namaInput, lat, lng, noteInput);
                menuUtama();
            }
        });
        builder.setNeutralButton("Simpan & Navigasi", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String namaInput = txtNama.getText().toString().trim();
                String noteInput = txtNote.getText().toString().trim();
                if (namaInput.isEmpty()) namaInput = "Lokasi Tanpa Nama";
                BookmarkItem savedItem = simpanLokasiBaru(namaInput, lat, lng, noteInput);
                mulaiNavigasiOtomatis(savedItem);
            }
        });
        builder.setNegativeButton("Kembali", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                menuUtama();
            }
        });
        builder.show();
    }

    private void kelolaLokasiTersimpan() {
        final List<BookmarkItem> list = getSavedBookmarks();
        if (list.isEmpty()) {
            suaraNavigasi("Belum ada lokasi yang tersimpan.");
            menuUtama();
            return;
        }

        CharSequence[] items = new CharSequence[list.size()];
        for (int i = 0; i < list.size(); i++) {
            items[i] = list.get(i).name;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Daftar Lokasi Tersimpan");
        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                BookmarkItem selectedItem = list.get(which);
                menuAksiLokasiTersimpan(selectedItem, which);
            }
        });
        builder.setNegativeButton("Kembali", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                menuUtama();
            }
        });
        builder.show();
    }

    private void menuAksiLokasiTersimpan(final BookmarkItem item, final int index) {
        CharSequence[] options = {
            "▶️ Mulai Navigasi Otomatis",
            "🗺️ Buka Aplikasi Navigasi Lain (Google Maps/Lazarillo)",
            "✏️ Edit Nama & Catatan Panduan",
            "🗑️ Hapus Lokasi Ini"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(item.name);
        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    mulaiNavigasiOtomatis(item);
                } else if (which == 1) {
                    bukaAplikasiNavigasi(item.lat, item.lng);
                } else if (which == 2) {
                    dialogEditNamaDanCatatan(item, index);
                } else if (which == 3) {
                    dialogKonfirmasiHapus(item, index);
                }
            }
        });
        builder.setNegativeButton("Kembali", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                kelolaLokasiTersimpan();
            }
        });
        builder.show();
    }

    private void dialogEditNamaDanCatatan(final BookmarkItem item, final int index) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Bookmark");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        final EditText txtNama = new EditText(this);
        txtNama.setText(item.name);
        layout.addView(txtNama);

        final EditText txtNote = new EditText(this);
        txtNote.setText(item.note);
        txtNote.setHint("Catatan Panduan (Misal: Pagar bambu)");
        layout.addView(txtNote);

        builder.setView(layout);
        builder.setPositiveButton("Simpan", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String newName = txtNama.getText().toString().trim();
                String newNote = txtNote.getText().toString().trim();
                if (!newName.isEmpty()) {
                    List<BookmarkItem> list = getSavedBookmarks();
                    if (index < list.size()) {
                        list.get(index).name = newName;
                        list.get(index).note = newNote;
                        saveBookmarksTable(list);
                        suaraNavigasi("Bookmark berhasil diperbarui.");
                    }
                }
                kelolaLokasiTersimpan();
            }
        });
        builder.setNegativeButton("Batal", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                kelolaLokasiTersimpan();
            }
        });
        builder.show();
    }

    private void dialogKonfirmasiHapus(final BookmarkItem item, final int index) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Hapus Lokasi");
        builder.setMessage("Apakah Anda yakin ingin menghapus " + item.name + "?");
        builder.setPositiveButton("Hapus", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                List<BookmarkItem> list = getSavedBookmarks();
                if (index < list.size()) {
                    list.remove(index);
                    saveBookmarksTable(list);
                    suaraNavigasi("Lokasi " + item.name + " berhasil dihapus.");
                }
                kelolaLokasiTersimpan();
            }
        });
        builder.setNegativeButton("Batal", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                kelolaLokasiTersimpan();
            }
        });
        builder.show();
    }

    private void menuUtama() {
        CharSequence[] menuOptions = {
            "📍 Kunci Lokasi & Mulai Navigasi Otomatis",
            "🔍 Cari Lokasi di Sinjai (LocationIQ)",
            "📏 Cek Sisa Jarak & Catatan Panduan",
            "💾 Kelola Lokasi Tersimpan",
            "🗣️ Pilih Mesin Suara (TTS) Navigasi",
            "🔑 Pengaturan Access Token LocationIQ",
            "⏱️ Hentikan Navigasi Otomatis"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Menu Navigasi Otomatis");
        builder.setItems(menuOptions, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0: kunciLokasiSaatIni(); break;
                    case 1: cariLokasiAkurat(); break;
                    case 2: cekSisaJarakDanCatatan(); break;
                    case 3: kelolaLokasiTersimpan(); break;
                    case 4: pilihEngineTtsDialog(); break;
                    case 5: inputApiKeyDialog(null); break;
                    case 6: hentikanNavigasiOtomatis(); break;
                }
            }
        });
        builder.setNegativeButton("Tutup", null);
        builder.show();
    }

    @Override
    protected void onDestroy() {
        stopLocationUpdates();
        stopSuaraTts();
        super.onDestroy();
    }
}