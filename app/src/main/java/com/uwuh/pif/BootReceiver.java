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
    private static final String DIR = "/data/system";
    private static final String KB_PATH = DIR + "/custom_keybox.xml";
    private static final String PIF_PATH = DIR + "/custom_pif.json";

    private static final String URL_KB = "https://raw.githubusercontent.com/user/repo/main/keybox.xml";
    private static final String URL_PIF = "https://raw.githubusercontent.com/user/repo/main/pif.json";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SharedPreferences sp = context.getSharedPreferences("pif_prefs", Context.MODE_PRIVATE);
            boolean isAuto = sp.getBoolean("auto", true);

            new Thread(() -> {
                // 1. Terapkan fallback jika file belum ada
                applyFallback(context);

                // 2. Jika Mode AUTO aktif, cek update online dari GitHub saat booting
                if (isAuto) {
                    checkUpdateOnline(context, sp);
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

    private void checkUpdateOnline(Context context, SharedPreferences sp) {
        try {
            String newKb = fetch(URL_KB);
            String newPif = fetch(URL_PIF);

            if (newKb != null && !newKb.isEmpty() && !newKb.equals(readFile(KB_PATH))) {
                write(KB_PATH, newKb);
            }

            if (newPif != null && !newPif.isEmpty() && !newPif.equals(readFile(PIF_PATH))) {
                write(PIF_PATH, newPif);
            }

            String date = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new Date());
            sp.edit().putString("last_update", date).apply();
            Log.d(TAG, "Auto update saat boot selesai.");
        } catch (Exception e) {
            Log.e(TAG, "Gagal auto update saat boot: " + e.getMessage());
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
        } catch (Exception e) {
            Log.e(TAG, "Gagal menulis file: " + e.getMessage());
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

    private String readRaw(Context ctx, int rawResId) {
        try {
            InputStream is = ctx.getResources().openRawResource(rawResId);
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
