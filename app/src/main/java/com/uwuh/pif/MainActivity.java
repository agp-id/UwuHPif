package com.uwuh.pif;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String TAG = "PifManager";
    private static final String DIR = "/data/system/pif";
    private static final String KB_PATH = DIR + "/custom_keybox.xml";
    private static final String PIF_PATH = DIR + "/custom_pif.json";
    
    private static final String URL_KB = "https://raw.githubusercontent.com/user/repo/main/keybox.xml";
    private static final String URL_PIF = "https://raw.githubusercontent.com/user/repo/main/pif.json";

    private Switch switchAuto;
    private TextView tvStatus, tvLastCheck;
    private LinearLayout panelManual;
    private SharedPreferences sp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sp = getSharedPreferences("pif_prefs", MODE_PRIVATE);

        switchAuto = findViewById(R.id.switchAuto);
        tvStatus = findViewById(R.id.tvStatus);
        tvLastCheck = findViewById(R.id.tvLastCheck);
        panelManual = findViewById(R.id.panelManual);

        boolean isAuto = sp.getBoolean("auto", true);
        switchAuto.setChecked(isAuto);
        updateUIState(isAuto);

        switchAuto.setOnCheckedChangeListener((v, autoChecked) -> {
            sp.edit().putBoolean("auto", autoChecked).apply();
            updateUIState(autoChecked);
        });

        findViewById(R.id.btnPickKeybox).setOnClickListener(v -> pickFile(101));
        findViewById(R.id.btnPickProps).setOnClickListener(v -> pickFile(102));
        findViewById(R.id.btnCheckUpdate).setOnClickListener(v -> {
            new Thread(() -> checkUpdateOnline(true)).start();
        });

        // Jalankan pemasangan fallback di Thread terpisah agar UI tidak lag
        new Thread(() -> {
            applyFallback();
            if (sp.getBoolean("auto", true)) {
                checkUpdateOnline(false);
            }
        }).start();
    }

    private void updateUIState(boolean isAuto) {
        tvStatus.setText(isAuto ? "Status: Mode Auto Aktif" : "Status: Mode Manual Aktif");
        panelManual.setVisibility(isAuto ? View.GONE : View.VISIBLE);
        tvLastCheck.setText("Terakhir update: " + sp.getString("last_update", "-"));
    }

    private void applyFallback() {
        try {
            File dir = new File(DIR);
            if (!dir.exists()) dir.mkdirs();

            File kbFile = new File(KB_PATH);
            File pifFile = new File(PIF_PATH);

            if (!kbFile.exists()) {
                String kbData = readRaw(R.raw.default_keybox);
                if (!kbData.isEmpty()) write(KB_PATH, kbData);
            }

            if (!pifFile.exists()) {
                String pifData = readRaw(R.raw.default_pif);
                if (!pifData.isEmpty()) write(PIF_PATH, pifData);
            }
        } catch (Exception e) {
            Log.e(TAG, "Gagal terapkan fallback: " + e.getMessage());
        }
    }

    private void checkUpdateOnline(boolean showToast) {
        try {
            String newKb = fetch(URL_KB);
            String newPif = fetch(URL_PIF);

            boolean updated = false;

            if (newKb != null && !newKb.isEmpty() && !newKb.equals(readFile(KB_PATH))) {
                write(KB_PATH, newKb);
                updated = true;
            }

            if (newPif != null && !newPif.isEmpty() && !newPif.equals(readFile(PIF_PATH))) {
                write(PIF_PATH, newPif);
                updated = true;
            }

            String date = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new Date());
            sp.edit().putString("last_update", date).apply();

            final boolean isUpdated = updated;
            runOnUiThread(() -> {
                tvLastCheck.setText("Terakhir update: " + date);
                if (showToast) {
                    Toast.makeText(this, isUpdated ? "File berhasil diperbarui!" : "File sudah versi terbaru.", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Gagal update dari GitHub: " + e.getMessage());
            if (showToast) {
                runOnUiThread(() -> Toast.makeText(this, "Gagal terhubung ke GitHub", Toast.LENGTH_SHORT).show());
            }
        }
    }

    private void pickFile(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res == Activity.RESULT_OK && data != null && data.getData() != null) {
            new Thread(() -> {
                String content = readUri(data.getData());
                if (!content.isEmpty()) {
                    if (req == 101) {
                        write(KB_PATH, content);
                        runOnUiThread(() -> Toast.makeText(this, "Keybox XML terpasang", Toast.LENGTH_SHORT).show());
                    } else if (req == 102) {
                        write(PIF_PATH, content);
                        runOnUiThread(() -> Toast.makeText(this, "Props JSON terpasang", Toast.LENGTH_SHORT).show());
                    }
                }
            }).start();
        }
    }

    private String fetch(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(5000);
        if (c.getResponseCode() != 200) return null;

        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append("\n");
        r.close();
        return sb.toString().trim();
    }

    private void write(String path, String content) {
        try {
            File dir = new File(DIR);
            if (!dir.exists()) dir.mkdirs();

            FileOutputStream f = new FileOutputStream(path);
            f.write(content.getBytes());
            f.close();
            Log.d(TAG, "Sukses menulis ke: " + path);
        } catch (Exception e) {
            Log.e(TAG, "Gagal menulis file ke " + path + ": " + e.getMessage());
        }
    }

    private String readFile(String path) {
        try {
            File f = new File(path);
            if (!f.exists()) return "";
            BufferedReader r = new BufferedReader(new java.io.FileReader(f));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append("\n");
            r.close();
            return sb.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    private String readRaw(int rawResId) {
        try {
            InputStream is = getResources().openRawResource(rawResId);
            BufferedReader r = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append("\n");
            r.close();
            return sb.toString().trim();
        } catch (Exception e) {
            Log.e(TAG, "Resource raw ID " + rawResId + " tidak ditemukan: " + e.getMessage());
            return "";
        }
    }

    private String readUri(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            BufferedReader r = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append("\n");
            r.close();
            return sb.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }
}
