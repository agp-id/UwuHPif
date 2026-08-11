package com.uwuh.pif;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

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

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "PifManagerBoot";
    private static final String DIR = "/data/system/pif";
    private static final String KB_PATH = DIR + "/custom_keybox.xml";
    private static final String PIF_PATH = DIR + "/custom_pif.json";
    private static final String URL_KB = "https://raw.githubusercontent.com/user/repo/main/keybox.xml";
    private static final String URL_PIF = "https://raw.githubusercontent.com/user/repo/main/pif.json";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || 
            "android.intent.action.LOCKED_BOOT_COMPLETED".equals(action)) {
            
            SharedPreferences sp = context.getSharedPreferences("pif_prefs", Context.MODE_PRIVATE);
            boolean isManual = sp.getBoolean("manual", false);

            new Thread(() -> {
                applyFallback(context);
                if (!isManual) {
                    checkUpdateInternal(context, sp);
                }
            }).start();
        }
    }

    private void applyFallback(Context context) {
        File kbFile = new File(KB_PATH);
        File pifFile = new File(PIF_PATH);

        if (!kbFile.exists()) {
            String kbData = readRaw(context, R.raw.default_keybox);
            if (!kbData.isEmpty()) write(KB_PATH, kbData);
        }

        if (!pifFile.exists()) {
            String pifData = readRaw(context, R.raw.default_pif);
            if (!pifData.isEmpty()) write(PIF_PATH, pifData);
        }
    }

    private void checkUpdateInternal(Context context, SharedPreferences sp) {
        try {
            String k = fetch(URL_KB);
            String p = fetch(URL_PIF);

            if (k != null && !k.isEmpty()) write(KB_PATH, k);
            if (p != null && !p.isEmpty()) write(PIF_PATH, p);

            String date = new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(new Date());
            sp.edit().putString("last", date).apply();
            Log.d(TAG, "Auto update saat boot BERHASIL dilakukan!");
        } catch (Exception e) {
            Log.e(TAG, "Auto update saat boot GAGAL: " + e.getMessage());
        }
    }

    private String fetch(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
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
            Log.d(TAG, "Berhasil menulis file: " + path);
        } catch (Exception e) {
            Log.e(TAG, "Gagal menulis file ke " + path + ": " + e.getMessage());
        }
    }

    private String readRaw(Context ctx, int id) {
        try {
            InputStream is = ctx.getResources().openRawResource(id);
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
