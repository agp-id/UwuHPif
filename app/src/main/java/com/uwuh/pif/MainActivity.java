package com.uwuh.pif;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

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

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "PifManager";
    private static final String DIR = "/data/system/pif";
    private static final String KB_PATH = DIR + "/custom_keybox.xml";
    private static final String PIF_PATH = DIR + "/custom_pif.json";
    private static final String URL_KB = "https://raw.githubusercontent.com/user/repo/main/keybox.xml";
    private static final String URL_PIF = "https://raw.githubusercontent.com/user/repo/main/pif.json";

    private Switch sw;
    private TextView tvMode, tvKB, tvPIF, tvLast;
    private SharedPreferences sp;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        sp = getSharedPreferences("pif_prefs", MODE_PRIVATE);
        
        initUI();

        new Thread(() -> {
            applyFallback();
            if (!sp.getBoolean("manual", false)) {
                checkUpdateInternal(false);
            }
        }).start();
    }

    private void initUI() {
        sw = findViewById(R.id.switchMode);
        tvMode = findViewById(R.id.tvModeStatus);
        tvKB = findViewById(R.id.tvKeyboxStatus);
        tvPIF = findViewById(R.id.tvPropsStatus);
        tvLast = findViewById(R.id.tvLastCheck);

        sw.setChecked(sp.getBoolean("manual", false));
        sw.setOnCheckedChangeListener((v, isManual) -> {
            sp.edit().putBoolean("manual", isManual).apply();
            containerVisible(isManual);
        });
        containerVisible(sw.isChecked());

        findViewById(R.id.btnKeybox).setOnClickListener(v -> pickFile(101));
        findViewById(R.id.btnProps).setOnClickListener(v -> pickFile(102));
        findViewById(R.id.btnCheck).setOnClickListener(v -> new Thread(() -> checkUpdateInternal(true)).start());
        tvLast.setText("Terakhir cek: " + sp.getString("last", "-"));
    }

    private void containerVisible(boolean vis) {
        findViewById(R.id.containerManual).setVisibility(vis ? View.VISIBLE : View.GONE);
        tvMode.setText(vis ? "Mode: Manual" : "Mode: Auto");
    }

    private void applyFallback() {
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
    }

    private void checkUpdateInternal(boolean toast) {
        try {
            String k = fetch(URL_KB); 
            String p = fetch(URL_PIF);
            
            if (k != null && !k.isEmpty()) write(KB_PATH, k);
            if (p != null && !p.isEmpty()) write(PIF_PATH, p);

            String d = new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(new Date());
            sp.edit().putString("last", d).apply();

            runOnUiThread(() -> {
                tvLast.setText("Terakhir cek: " + d);
                if (toast) Toast.makeText(this, "Update berhasil!", Toast.LENGTH_SHORT).show();
            });
        } catch (Exception e) {
            Log.e(TAG, "Gagal update dari GitHub: " + e.getMessage());
            if (toast) {
                runOnUiThread(() -> Toast.makeText(this, "Gagal update", Toast.LENGTH_SHORT).show());
            }
        }
    }

    private void pickFile(int code) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        startActivityForResult(i, code);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res == Activity.RESULT_OK && data != null && data.getData() != null) {
            new Thread(() -> {
                String c = readUri(data.getData());
                if (!c.isEmpty()) {
                    if (req == 101) { 
                        write(KB_PATH, c); 
                        runOnUiThread(() -> tvKB.setText("Keybox: Terpasang")); 
                    } else { 
                        write(PIF_PATH, c); 
                        runOnUiThread(() -> tvPIF.setText("Props: Terpasang")); 
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
        String l;
        while ((l = r.readLine()) != null) sb.append(l).append("\n");
        r.close();
        return sb.toString();
    }

    private void write(String path, String content) {
        try {
            File dir = new File(DIR);
            if (!dir.exists()) dir.mkdirs();

            FileOutputStream f = new FileOutputStream(path);
            f.write(content.getBytes());
            f.close();
            Log.d(TAG, "BERHASIL menulis file ke: " + path);
        } catch (Exception e) {
            Log.e(TAG, "GAGAL menulis file ke " + path + " -> Error: " + e.getMessage(), e);
        }
    }

    private String readRaw(int id) {
        try {
            InputStream is = getResources().openRawResource(id);
            BufferedReader r = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder(); 
            String l;
            while ((l = r.readLine()) != null) sb.append(l).append("\n");
            r.close();
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String readUri(Uri u) {
        try {
            InputStream is = getContentResolver().openInputStream(u);
            BufferedReader r = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder(); 
            String l;
            while ((l = r.readLine()) != null) sb.append(l).append("\n");
            r.close();
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
