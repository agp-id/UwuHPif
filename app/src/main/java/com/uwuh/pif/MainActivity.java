package com.uwuh.pif;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import java.io.*;
import java.net.*;

public class MainActivity extends Activity {
    private final String DIR = "/data/system/pif";
    private Switch sw;
    private SharedPreferences sp;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        sp = getSharedPreferences("prefs", MODE_PRIVATE);
        
        sw = findViewById(R.id.switchAuto);
        sw.setChecked(sp.getBoolean("auto", true));
        sw.setOnCheckedChangeListener((v, isAuto) -> sp.edit().putBoolean("auto", isAuto).apply());

        findViewById(R.id.btnUpdate).setOnClickListener(v -> {
            new Thread(() -> {
                String msg = updateFiles() ? "Update Berhasil" : "Update Gagal";
                runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
            }).start();
        });
    }

    private boolean updateFiles() {
        try {
            String newPif = fetch("https://raw.githubusercontent.com/user/repo/main/pif.json");
            if (newPif != null) write(DIR + "/custom_pif.json", newPif);
            return true;
        } catch (Exception e) { return false; }
    }

    private String fetch(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(5000);
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    private void write(String path, String data) throws Exception {
        File dir = new File(DIR);
        if (!dir.exists()) dir.mkdirs();
        FileOutputStream f = new FileOutputStream(path);
        f.write(data.getBytes());
        f.close();
    }
}
