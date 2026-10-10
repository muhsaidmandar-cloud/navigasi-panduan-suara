package com.navigasi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
    
    private TextView tvAlamatUtama, tvJarakMataAngin;
    private LocationManager locationManager;
    private SensorManager sensorManager;
    private Sensor rotationSensor;
    
    private float currentAzimuth = 0.0f; 
    private TextToSpeech tts;
    private boolean isTtsReady = false;
    private float kecepatanBicara = 1.0f;     
    private int selectedAudioStream = AudioManager.STREAM_MUSIC;

    private List<LokasiTersimpan> daftarLokasiTersimpan = new ArrayList<>();
    private LokasiTersimpan lokasiNavigasiAktif = null;
    
    private boolean isEksplorasiFiturAktif = false;
    private List<String> riwayatTempatDiumumkan = new ArrayList<>();

    private Handler explorationHandler = new Handler();
    private Runnable explorationRunnable;

    private static final int PERMISSION_REQUEST_CODE = 100;

    public static class LokasiTersimpan {
        String nama;
        double lat, lon;
        public LokasiTersimpan(String nama, double lat, double lon) {
            this.nama = nama; this.lat = lat; this.lon = lon;
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
        tampilkanHalamanExploration();
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
            currentAzimuth = (float) (Math.toDegrees(orientation[0]) + 360) % 360; 
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

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
        if (tts != null) { try { tts.stop(); tts.shutdown(); } catch (Exception e) {} }
        tts = new TextToSpeech(this, this);
    }

    // ==========================================
    // TAMPILAN UTAMA 100% PERSIS LAZARILLO
    // ==========================================
    private void tampilkanHalamanExploration() {
        LinearLayout mainRoot = new LinearLayout(this);
        mainRoot.setOrientation(LinearLayout.VERTICAL);
        mainRoot.setBackgroundColor(Color.parseColor("#EFEFEF"));

        // 1. TOP BAR (Merah Pekat khas Lazarillo)[span_37](start_span)[span_37](end_span)[span_38](start_span)[span_38](end_span)
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setBackgroundColor(Color.parseColor("#D32F2F"));
        topBar.setPadding(16, 16, 16, 16);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvTitleApp = new TextView(this);
        tvTitleApp.setText("Exploration");[span_39](start_span)[span_39](end_span)[span_40](start_span)[span_40](end_span)
        tvTitleApp.setTextColor(Color.WHITE);
        tvTitleApp.setTextSize(18);
        tvTitleApp.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitleApp.setSingleLine(true);
        tvTitleApp.setEllipsize(TextUtils.TruncateAt.END);
        
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        tvTitleApp.setLayoutParams(titleParams);
        topBar.addView(tvTitleApp);

        // Tombol Bilah Atas dengan penamaan pembaca layar identik Lazarillo[span_41](start_span)[span_41](end_span)[span_42](start_span)[span_42](end_span)
        Button btnPlayPause = createTopBarButton("▶", "Pause");
        btnPlayPause.setOnClickListener(v -> {
            isEksplorasiFiturAktif = !isEksplorasiFiturAktif;
            if (isEksplorasiFiturAktif) {
                btnPlayPause.setText("⏸");
                btnPlayPause.setContentDescription("Pause");
                mulaiEksplorasiRealTime();
                ucapkanSuara("Exploration resumed.");
            } else {
                btnPlayPause.setText("▶");
                btnPlayPause.setContentDescription("Play");
                hentikanEksplorasiRealTime();
                ucapkanSuara("Exploration paused.");
            }
        });
        topBar.addView(btnPlayPause);

        Button btnTarget = createTopBarButton("◎", "Current Location");[span_43](start_span)[span_43](end_span)[span_44](start_span)[span_44](end_span)
        btnTarget.setOnClickListener(v -> perbaruiPosisiGPSManual());
        topBar.addView(btnTarget);

        Button btnSearch = createTopBarButton("🔍", "Search");[span_45](start_span)[span_45](end_span)[span_46](start_span)[span_46](end_span)
        btnSearch.setOnClickListener(v -> tampilkanDialogPencarianLokasi());
        topBar.addView(btnSearch);

        Button btnMenu = createTopBarButton("≡", "Menu");[span_47](start_span)[span_47](end_span)[span_48](start_span)[span_48](end_span)
        btnMenu.setOnClickListener(v -> tampilkanHalamanFavourites());
        topBar.addView(btnMenu);

        mainRoot.addView(topBar);

        // Scrollable Content
        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));
        
        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(20, 20, 20, 20);

        // 2. KARTU BANNER ALAMAT DI ATAS[span_49](start_span)[span_49](end_span)
        LinearLayout bannerCard = new LinearLayout(this);
        bannerCard.setOrientation(LinearLayout.HORIZONTAL);
        bannerCard.setGravity(Gravity.CENTER_VERTICAL);
        
        GradientDrawable bannerBg = new GradientDrawable();
        bannerBg.setColor(Color.parseColor("#9E9E9E"));
        bannerBg.setCornerRadius(24);
        bannerCard.setBackground(bannerBg);
        bannerCard.setPadding(24, 24, 24, 24);
        bannerCard.setContentDescription("Location Banner");

        TextView iconPin = new TextView(this);
        iconPin.setText("📍");
        iconPin.setTextSize(32);
        iconPin.setPadding(0, 0, 20, 0);
        bannerCard.addView(iconPin);

        LinearLayout textContainer = new LinearLayout(this);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        textContainer.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        tvAlamatUtama = new TextView(this);
        tvAlamatUtama.setText("Acquiring GPS position...");
        tvAlamatUtama.setTextColor(Color.WHITE);
        tvAlamatUtama.setTextSize(16);
        tvAlamatUtama.setTypeface(null, android.graphics.Typeface.BOLD);
        textContainer.addView(tvAlamatUtama);

        bannerCard.addView(textContainer);
        contentLayout.addView(bannerCard);

        tvJarakMataAngin = new TextView(this);
        tvJarakMataAngin.setText("Waiting for signal...");
        tvJarakMataAngin.setTextColor(Color.parseColor("#212121"));
        tvJarakMataAngin.setTextSize(15);
        tvJarakMataAngin.setTypeface(null, android.graphics.Typeface.BOLD);
        tvJarakMataAngin.setPadding(8, 12, 0, 24);
        contentLayout.addView(tvJarakMataAngin);

        // 3. GRID 11 MENU KATEGORI LINGKARAN MERAH (Persis Lazarillo)[span_50](start_span)[span_50](end_span)[span_51](start_span)[span_51](end_span)
        GridLayout gridMenu = new GridLayout(this);
        gridMenu.setColumnCount(3);
        gridMenu.setAlignmentMode(GridLayout.ALIGN_BOUNDS);

        addMenuCard(gridMenu, "Recently announced", "⏱", "recently");[span_52](start_span)[span_52](end_span)[span_53](start_span)[span_53](end_span)
        addMenuCard(gridMenu, "Transport", "🚌", "transport");[span_54](start_span)[span_54](end_span)[span_55](start_span)[span_55](end_span)
        addMenuCard(gridMenu, "Banks and ATMs", "🏧", "banks");[span_56](start_span)[span_56](end_span)[span_57](start_span)[span_57](end_span)
        addMenuCard(gridMenu, "Health", "➕", "health");[span_58](start_span)[span_58](end_span)[span_59](start_span)[span_59](end_span)
        addMenuCard(gridMenu, "Food", "🍽", "food");[span_60](start_span)[span_60](end_span)[span_61](start_span)[span_61](end_span)
        addMenuCard(gridMenu, "Stores", "🛍", "stores");[span_62](start_span)[span_62](end_span)[span_63](start_span)[span_63](end_span)
        addMenuCard(gridMenu, "Arts and entertainment", "🎭", "arts");[span_64](start_span)[span_64](end_span)[span_65](start_span)[span_65](end_span)
        addMenuCard(gridMenu, "Public buildings", "🏢", "public");[span_66](start_span)[span_66](end_span)[span_67](start_span)[span_67](end_span)
        addMenuCard(gridMenu, "Education facilities", "🛠", "education");[span_68](start_span)[span_68](end_span)[span_69](start_span)[span_69](end_span)
        addMenuCard(gridMenu, "Pubs and clubs", "🍸", "pubs");[span_70](start_span)[span_70](end_span)[span_71](start_span)[span_71](end_span)
        addMenuCard(gridMenu, "Lodging", "🏨", "lodging");[span_72](start_span)[span_72](end_span)[span_73](start_span)[span_73](end_span)

        contentLayout.addView(gridMenu);
        scrollView.addView(contentLayout);
        mainRoot.addView(scrollView);

        // 4. BOTTOM NAVIGATION BAR (Persis Lazarillo)[span_74](start_span)[span_74](end_span)[span_75](start_span)[span_75](end_span)
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setBackgroundColor(Color.parseColor("#FAFAFA"));
        bottomBar.setPadding(8, 12, 8, 12);

        bottomBar.addView(createBottomNavItem("🧭\nExploration", "Exploration", true, v -> tampilkanHalamanExploration()));[span_76](start_span)[span_76](end_span)[span_77](start_span)[span_77](end_span)
        bottomBar.addView(createBottomNavItem("⭐\nFavourites", "Favourites", false, v -> tampilkanHalamanFavourites()));[span_78](start_span)[span_78](end_span)[span_79](start_span)[span_79](end_span)
        bottomBar.addView(createBottomNavItem("🔔\nNews", "News", false, v -> tampilkanHalamanNews()));[span_80](start_span)[span_80](end_span)[span_81](start_span)[span_81](end_span)
        bottomBar.addView(createBottomNavItem("⚙\nSettings", "Settings", false, v -> tampilkanHalamanSettings()));[span_82](start_span)[span_82](end_span)[span_83](start_span)[span_83](end_span)

        mainRoot.addView(bottomBar);
        setContentView(mainRoot);

        isEksplorasiFiturAktif = true;
        mulaiSensorKompas();
        mulaiMendengarkanGPS();
        mulaiEksplorasiRealTime();
    }

    private Button createTopBarButton(String symbol, String accessibilityDesc) {
        Button btn = new Button(this);
        btn.setText(symbol);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(18);
        btn.setBackgroundColor(Color.TRANSPARENT);
        btn.setPadding(8, 0, 8, 0);
        btn.setContentDescription(accessibilityDesc);
        return btn;
    }

    private void addMenuCard(GridLayout grid, String title, String emoji, String categoryKey) {
        LinearLayout itemBox = new LinearLayout(this);
        itemBox.setOrientation(LinearLayout.VERTICAL);
        itemBox.setGravity(Gravity.CENTER);
        int width = getResources().getDisplayMetrics().widthPixels / 3 - 24;
        itemBox.setLayoutParams(new GridLayout.LayoutParams(
            GridLayout.spec(GridLayout.UNDEFINED, 1f),
            GridLayout.spec(GridLayout.UNDEFINED, 1f)
        ));
        itemBox.getLayoutParams().width = width;
        itemBox.setPadding(4, 12, 4, 12);
        itemBox.setContentDescription(title);

        TextView circleIcon = new TextView(this);
        circleIcon.setText(emoji);
        circleIcon.setTextSize(24);
        circleIcon.setGravity(Gravity.CENTER);
        circleIcon.setTextColor(Color.WHITE);
        
        GradientDrawable circleBg = new GradientDrawable();
        circleBg.setColor(Color.parseColor("#D32F2F"));
        circleBg.setShape(GradientDrawable.OVAL);
        circleIcon.setBackground(circleBg);
        
        int size = 125;
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.gravity = Gravity.CENTER_HORIZONTAL;
        circleIcon.setLayoutParams(params);
        itemBox.addView(circleIcon);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(title);
        tvLabel.setTextSize(12);
        tvLabel.setTextColor(Color.parseColor("#333333"));
        tvLabel.setGravity(Gravity.CENTER);
        tvLabel.setPadding(0, 10, 0, 0);
        itemBox.addView(tvLabel);

        itemBox.setOnClickListener(v -> {
            ucapkanSuara("Searching for " + title + "...");
            if (categoryKey.equals("recently")) {
                tampilkanRiwayatPengumuman();
            } else {
                jalankanPencarianKategori(categoryKey, title);
            }
        });

        grid.addView(itemBox);
    }

    private TextView createBottomNavItem(String label, String accessibilityDesc, boolean isActive, View.OnClickListener listener) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(11);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(isActive ? Color.parseColor("#D32F2F") : Color.parseColor("#666666"));
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        tv.setContentDescription(accessibilityDesc);
        tv.setOnClickListener(listener);
        return tv;
    }

    private void tampilkanRiwayatPengumuman() {
        if (riwayatTempatDiumumkan.isEmpty()) {
            ucapkanSuara("No recently announced places.");
            tvAlamatUtama.setText("No recent announcements.");
            return;
        }
        StringBuilder sb = new StringBuilder("Recently announced: ");
        for (String tempat : riwayatTempatDiumumkan) {
            sb.append(tempat).append(", ");
        }
        tvAlamatUtama.setText(sb.toString());
        ucapkanSuara("Showing recent places.");
    }

    private void jalankanPencarianKategori(String key, String namaKategori) {
        Location loc = dapatkanLokasiTerakhir();
        double cLat = (loc != null) ? loc.getLatitude() : 0.0;
        double cLon = (loc != null) ? loc.getLongitude() : 0.0;
        new CariKategoriTask(cLat, cLon, namaKategori).execute(key);
    }

    private class CariKategoriTask extends AsyncTask<String, Void, List<TempatPro>> {
        double cLat, cLon;
        String namaKat;

        public CariKategoriTask(double lat, double lon, String kat) {
            this.cLat = lat; this.cLon = lon; this.namaKat = kat;
        }

        @Override
        protected List<TempatPro> doInBackground(String... params) {
            String tagQuery = "";
            String k = params[0];
            if (k.equals("transport")) tagQuery = "public_transport|amenity=bus_station";
            else if (k.equals("banks")) tagQuery = "amenity=bank|amenity=atm";
            else if (k.equals("health")) tagQuery = "amenity=hospital|amenity=pharmacy";
            else if (k.equals("food")) tagQuery = "amenity=restaurant|amenity=cafe|amenity=fast_food";
            else if (k.equals("stores")) tagQuery = "shop=supermarket|shop=convenience|shop=mall";
            else if (k.equals("arts")) tagQuery = "amenity=arts_centre|tourism=attraction";
            else if (k.equals("public")) tagQuery = "amenity=townhall|office=government";
            else if (k.equals("education")) tagQuery = "amenity=school|amenity=university";
            else if (k.equals("pubs")) tagQuery = "amenity=pub|amenity=bar";
            else if (k.equals("lodging")) tagQuery = "tourism=hotel|tourism=guest_house";
            else tagQuery = "name";

            List<TempatPro> hasil = new ArrayList<>();
            try {
                String query = "[out:json][timeout:5];(node(around:1000," + cLat + "," + cLon + ")[" + tagQuery.split("=")[0] + "];);out body 5;";
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
                        String nama = tags.optString("name", namaKat);
                        double lat = el.optDouble("lat", cLat);
                        double lon = el.optDouble("lon", cLon);
                        hasil.add(new TempatPro(nama, lat, lon));
                    }
                }
            } catch (Exception e) {}
            return hasil;
        }

        @Override
        protected void onPostExecute(List<TempatPro> result) {
            if (result != null && !result.isEmpty()) {
                TempatPro t = result.get(0);
                float[] dist = new float[1];
                Location.distanceBetween(cLat, cLon, t.lat, t.lon, dist);
                String pesan = t.nama + ", " + (int)dist[0] + " meters away.";
                ucapkanSuara(pesan);
                tvAlamatUtama.setText(t.nama);
                tvJarakMataAngin.setText((int)dist[0] + " m nearby");
                lokasiNavigasiAktif = new LokasiTersimpan(t.nama, t.lat, t.lon);
            } else {
                ucapkanSuara("No " + namaKat + " found nearby.");
                tvAlamatUtama.setText("Not found");
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

    private void tampilkanHalamanFavourites() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(32, 32, 32, 32);
        box.setBackgroundColor(Color.parseColor("#F5F5F5"));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Favourites");[span_84](start_span)[span_84](end_span)[span_85](start_span)[span_85](end_span)
        tvTitle.setTextSize(20);
        tvTitle.setTextColor(Color.parseColor("#D32F2F"));
        box.addView(tvTitle);

        TextView tvContent = new TextView(this);
        if (daftarLokasiTersimpan.isEmpty()) {
            tvContent.setText("\nNo saved locations.");
        } else {
            StringBuilder sb = new StringBuilder("\nSaved Locations:\n");
            for (int i = 0; i < daftarLokasiTersimpan.size(); i++) {
                sb.append((i + 1)).append(". ").append(daftarLokasiTersimpan.get(i).nama).append("\n");
            }
            tvContent.setText(sb.toString());
        }
        tvContent.setTextSize(16);
        box.addView(tvContent);

        Button btnKembali = new Button(this);
        btnKembali.setText("BACK TO EXPLORATION");
        btnKembali.setOnClickListener(v -> tampilkanHalamanExploration());
        box.addView(btnKembali);

        setContentView(box);
        ucapkanSuara("Favourites opened.");
    }

    private void tampilkanHalamanNews() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(32, 32, 32, 32);
        box.setBackgroundColor(Color.parseColor("#F5F5F5"));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("News");[span_86](start_span)[span_86](end_span)[span_87](start_span)[span_87](end_span)
        tvTitle.setTextSize(20);
        tvTitle.setTextColor(Color.parseColor("#D32F2F"));
        box.addView(tvTitle);

        TextView tvContent = new TextView(this);
        tvContent.setText("\nNo new updates available.");
        tvContent.setTextSize(16);
        box.addView(tvContent);

        Button btnKembali = new Button(this);
        btnKembali.setText("BACK TO EXPLORATION");
        btnKembali.setOnClickListener(v -> tampilkanHalamanExploration());
        box.addView(btnKembali);

        setContentView(box);
        ucapkanSuara("News opened.");
    }

    private void tampilkanHalamanSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(32, 32, 32, 32);
        box.setBackgroundColor(Color.parseColor("#F5F5F5"));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Settings");[span_88](start_span)[span_88](end_span)[span_89](start_span)[span_89](end_span)
        tvTitle.setTextSize(20);
        tvTitle.setTextColor(Color.parseColor("#D32F2F"));
        box.addView(tvTitle);

        TextView tvSpeed = new TextView(this);
        tvSpeed.setText("\nSpeech Speed: " + kecepatanBicara + "x");
        tvSpeed.setTextSize(16);
        box.addView(tvSpeed);

        Button btnCepat = new Button(this);
        btnCepat.setText("CHANGE SPEED");
        btnCepat.setOnClickListener(v -> {
            kecepatanBicara = (kecepatanBicara < 2.0f) ? (kecepatanBicara + 0.25f) : 1.0f;
            if (tts != null) tts.setSpeechRate(kecepatanBicara);
            tvSpeed.setText("\nSpeech Speed: " + kecepatanBicara + "x");
            ucapkanSuara("Speed " + kecepatanBicara);
        });
        box.addView(btnCepat);

        Button btnKembali = new Button(this);
        btnKembali.setText("BACK TO EXPLORATION");
        btnKembali.setOnClickListener(v -> tampilkanHalamanExploration());
        box.addView(btnKembali);

        setContentView(box);
        ucapkanSuara("Settings opened.");
    }

    private void mulaiEksplorasiRealTime() {
        if (explorationRunnable == null) {
            explorationRunnable = new Runnable() {
                @Override
                public void run() {
                    if (isEksplorasiFiturAktif) {
                        Location loc = dapatkanLokasiTerakhir();
                        if (loc != null) {
                            new EksplorasiKompasProTask().execute(loc.getLatitude(), loc.getLongitude(), 40.0, (double) currentAzimuth);
                        }
                    }
                    explorationHandler.postDelayed(this, 7000);
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
                    if (l != null && (bestLoc == null || l.getTime() > bestLoc.getTime())) {
                        bestLoc = l;
                    }
                }
            }
        } catch (Exception e) {}
        return bestLoc;
    }

    private void perbaruiPosisiGPSManual() {
        Location loc = dapatkanLokasiTerakhir();
        if (loc != null) {
            new CekAlamatTask().execute(loc.getLatitude(), loc.getLongitude());
        } else {
            ucapkanSuara("GPS signal not ready.");
        }
    }

    private class CekAlamatTask extends AsyncTask<Double, Void, String> {
        @Override
        protected String doInBackground(Double... params) {
            try {
                String urlStr = "https://nominatim.openstreetmap.org/reverse?lat=" + params[0] + "&lon=" + params[1] + "&format=json";
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
                tvAlamatUtama.setText(alamat.split(",")[0]);
                ucapkanSuara("Current location updated.");
            }
        }
    }

    private void tampilkanDialogPencarianLokasi() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Search Location");
        final EditText input = new EditText(this);
        input.setHint("Enter place name...");
        input.setPadding(40, 30, 40, 30);
        builder.setView(input);

        builder.setPositiveButton("Search", (dialog, which) -> {
            String query = input.getText().toString().trim();
            if (!query.isEmpty()) {
                Location loc = dapatkanLokasiTerakhir();
                double cLat = (loc != null) ? loc.getLatitude() : 0.0;
                double cLon = (loc != null) ? loc.getLongitude() : 0.0;
                new CariLokasiTask(cLat, cLon).execute(query);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private class CariLokasiTask extends AsyncTask<String, Void, String> {
        double uLat, uLon;
        public CariLokasiTask(double lat, double lon) { this.uLat = lat; this.uLon = lon; }

        @Override
        protected String doInBackground(String... params) {
            try {
                String urlStr = "https://nominatim.openstreetmap.org/search?q=" + URLEncoder.encode(params[0], "UTF-8") + "&format=json&limit=1";
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "NavigasiAplikasiAndroid");
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                
                JSONArray arr = new JSONArray(sb.toString());
                if (arr.length() > 0) {
                    JSONObject obj = arr.getJSONObject(0);
                    return obj.getString("display_name");
                }
            } catch (Exception e) {}
            return null;
        }

        @Override
        protected void onPostExecute(String hasil) {
            if (hasil != null) {
                String namaPendek = hasil.split(",")[0].trim();
                ucapkanSuara(namaPendek + " found.");
                tvAlamatUtama.setText(namaPendek);
            } else {
                ucapkanSuara("Location not found.");
            }
        }
    }

    private class EksplorasiKompasProTask extends AsyncTask<Double, Void, List<TempatPro>> {
        double cLat, cLon;
        int radius = 40;

        @Override
        protected List<TempatPro> doInBackground(Double... coords) {
            cLat = coords[0]; cLon = coords[1];
            if (coords.length > 2) radius = coords[2].intValue();

            List<TempatPro> hasil = new ArrayList<>();
            try {
                String query = "[out:json][timeout:3];(node(around:" + radius + "," + cLat + "," + cLon + ")[name];);out body 3;";
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
                                hasil.add(new TempatPro(nama, el.optDouble("lat", cLat), el.optDouble("lon", cLon)));
                            }
                        }
                    }
                }
            } catch (Exception e) {}
            return hasil;
        }

        @Override
        protected void onPostExecute(List<TempatPro> result) {
            if (result != null && !result.isEmpty()) {
                TempatPro t = result.get(0);
                float[] dist = new float[1];
                Location.distanceBetween(cLat, cLon, t.lat, t.lon, dist);
                
                String arahMataAngin = "to the East";
                if (currentAzimuth >= 45 && currentAzimuth < 135) arahMataAngin = "to the East";
                else if (currentAzimuth >= 135 && currentAzimuth < 225) arahMataAngin = "to the South";
                else if (currentAzimuth >= 225 && currentAzimuth < 315) arahMataAngin = "to the West";
                else arahMataAngin = "to the North";

                String teksJarak = (int)dist[0] + " m " + arahMataAngin;

                ucapkanSuara(t.nama + ", " + (int)dist[0] + " meters.");
                tvAlamatUtama.setText(t.nama);
                tvJarakMataAngin.setText(teksJarak);
                
                if (!riwayatTempatDiumumkan.contains(t.nama)) {
                    riwayatTempatDiumumkan.add(t.nama);
                    if (riwayatTempatDiumumkan.size() > 20) riwayatTempatDiumumkan.remove(0);
                }
            }
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            try {
                if (tts.setLanguage(new Locale("en", "US")) >= 0) {
                    isTtsReady = true;
                    tts.setSpeechRate(kecepatanBicara);
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

    @Override public void onLocationChanged(Location location) {}
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    @Override public void onProviderEnabled(String provider) {}
    @Override public void onProviderDisabled(String provider) { ucapkanSuara("GPS disabled."); }

    @Override
    protected void onDestroy() {
        hentikanEksplorasiRealTime();
        if (tts != null) { try { tts.stop(); tts.shutdown(); } catch (Exception e) {} }
        if (locationManager != null && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.removeUpdates(this);
        }
        hentikanSensorKompas();
        super.onDestroy();
    }
}