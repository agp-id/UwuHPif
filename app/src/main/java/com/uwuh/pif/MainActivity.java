package com.uwuh.pif;

import android.app.Activity;
import android.content.*;
import android.net.*;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.switchmaterial.SwitchMaterial;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends AppCompatActivity {
    private static final String DIR = "/data/system/pif";
    private static final String KB_PATH = DIR + "/custom_keybox.xml";
    private static final String PIF_PATH = DIR + "/custom_pif.json";
    private static final String URL_KB = "https://raw.githubusercontent.com/user/repo/main/keybox.xml";
    private static final String URL_PIF = "https://raw.githubusercontent.com/user/repo/main/pif.json";

    private SwitchMaterial sw;
    private TextView tvMode, tvKB, tvPIF, tvLast;
    private SharedPreferences sp;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        sp = getSharedPreferences("pif_prefs", MODE_PRIVATE);
        initUI();
        applyFallback();
        if (!sp.getBoolean("manual", false)) checkUpdate(false);
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
        findViewById(R.id.btnCheck).setOnClickListener(v -> checkUpdate(true));
        tvLast.setText("Terakhir cek: " + sp.getString("last", "-"));
    }

    private void containerVisible(boolean vis) {
        findViewById(R.id.containerManual).setVisibility(vis ? View.VISIBLE : View.GONE);
        tvMode.setText(vis ? "Mode: Manual" : "Mode: Auto");
    }

    private void applyFallback() {
        if (!new File(KB_PATH).exists()) write(KB_PATH, readRaw(R.raw.default_keybox));
        if (!new File(PIF_PATH).exists()) write(PIF_PATH, readRaw(R.raw.default_pif));
    }

    private void checkUpdate(boolean toast) {
        new Thread(() -> {
            try {
                String k = fetch(URL_KB); String p = fetch(URL_PIF);
                if (k != null) write(KB_PATH, k);
                if (p != null) write(PIF_PATH, p);
                String d = new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(new Date());
                sp.edit().putString("last", d).apply();
                runOnUiThread(() -> {
                    tvLast.setText("Terakhir cek: " + d);
                    if(toast) Toast.makeText(this, "Update berhasil!", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) { runOnUiThread(() -> { if(toast) Toast.makeText(this, "Gagal", Toast.LENGTH_SHORT).show(); }); }
        }).start();
    }

    private void pickFile(int code) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        startActivityForResult(i, code);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res == Activity.RESULT_OK && data != null) {
            String c = readUri(data.getData());
            if (req == 101) { write(KB_PATH, c); tvKB.setText("Keybox: Terpasang"); }
            else { write(PIF_PATH, c); tvPIF.setText("Props: Terpasang"); }
        }
    }

    private String fetch(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        if (c.getResponseCode() != 200) return null;
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
        StringBuilder sb = new StringBuilder(); String l;
        while ((l = r.readLine()) != null) sb.append(l).append("\n");
        return sb.toString();
    }

    private void write(String p, String c) {
        try { new File(DIR).mkdirs(); FileOutputStream f = new FileOutputStream(p); f.write(c.getBytes()); f.close(); } catch (Exception e) {}
    }

    private String readRaw(int id) {
        try { InputStream is = getResources().openRawResource(id); BufferedReader r = new BufferedReader(new InputStreamReader(is)); StringBuilder sb = new StringBuilder(); String l; while ((l = r.readLine()) != null) sb.append(l).append("\n"); return sb.toString(); } catch (Exception e) { return ""; }
    }

    private String readUri(Uri u) {
        try { InputStream is = getContentResolver().openInputStream(u); BufferedReader r = new BufferedReader(new InputStreamReader(is)); StringBuilder sb = new StringBuilder(); String l; while ((l = r.readLine()) != null) sb.append(l).append("\n"); return sb.toString(); } catch (Exception e) { return ""; }
    }
}
