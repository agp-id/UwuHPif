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
    private static final String DIR = "/data/system/uwuh";
    private static final String LOCAL_DIR = "/data/local/uwuh";

    private static final String KB_PATH = DIR + "/keybox.xml";
    private static final String PIF_PATH = DIR + "/pif.prop";
    private static final String GAMEPROPS_PATH = LOCAL_DIR + "/gameprops.json";
    private static final String THERMALS_PATH = LOCAL_DIR + "/per_app_thermals.json";

    private static final String URL_KB = "https://raw.githubusercontent.com/user/repo/main/keybox.xml";
    private static final String URL_PIF = "https://raw.githubusercontent.com/user/repo/main/pif.prop";
    private static final String URL_GAMEPROPS = "https://raw.githubusercontent.com/user/repo/main/gameprops.json";
    private static final String URL_THERMALS = "https://raw.githubusercontent.com/user/repo/main/per_app_thermals.json";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SharedPreferences sp = context.getSharedPreferences("pif_prefs", Context.MODE_PRIVATE);
            boolean isAuto = sp.getBoolean("auto", true);

            new Thread(() -> {
                createDirectories();
                applyFallback(context);
                if (isAuto) {
                    checkUpdateOnline(context, sp);
                }
            }).start();
        }
    }

    private void createDirectories() {
        new File(DIR).mkdirs();
        new File(LOCAL_DIR).mkdirs();
    }

    private void applyFallback(Context context) {
        if (!new File(KB_PATH).exists()) {
            String data = readRaw(context, R.raw.default_keybox);
            if (!data.isEmpty()) write(KB_PATH, data);
        }
        if (!new File(PIF_PATH).exists()) {
            String data = readRaw(context, R.raw.default_pif);
            if (!data.isEmpty()) write(PIF_PATH, data);
        }
        if (!new File(GAMEPROPS_PATH).exists()) {
            String data = readRaw(context, R.raw.default_gameprops);
            if (!data.isEmpty()) write(GAMEPROPS_PATH, data);
        }
        if (!new File(THERMALS_PATH).exists()) {
            String data = readRaw(context, R.raw.default_thermals);
            if (!data.isEmpty()) write(THERMALS_PATH, data);
        }
    }

    private void checkUpdateOnline(Context context, SharedPreferences sp) {
        try {
            String newKb = fetch(URL_KB);
            String newPif = fetch(URL_PIF);
            String newGame = fetch(URL_GAMEPROPS);
            String newTherm = fetch(URL_THERMALS);

            boolean updated = false;

            if (newKb != null && !newKb.isEmpty() && !newKb.equals(readFile(KB_PATH))) {
                write(KB_PATH, newKb);
                updated = true;
            }
            if (newPif != null && !newPif.isEmpty() && !newPif.equals(readFile(PIF_PATH))) {
                write(PIF_PATH, newPif);
                updated = true;
            }
            if (newGame != null && !newGame.isEmpty() && !newGame.equals(readFile(GAMEPROPS_PATH))) {
                write(GAMEPROPS_PATH, newGame);
                updated = true;
            }
            if (newTherm != null && !newTherm.isEmpty() && !newTherm.equals(readFile(THERMALS_PATH))) {
                write(THERMALS_PATH, newTherm);
                updated = true;
            }

            if (updated) {
                String date = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new Date());
                sp.edit().putString("last_update", date).apply();
                Log.d(TAG, "Auto update completed at boot");
            }

        } catch (Exception e) {
            Log.e(TAG, "Auto update failed: " + e.getMessage());
        }
    }

    // ==================== UTILITY METHODS ====================
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
            File dir = new File(path).getParentFile();
            if (!dir.exists()) dir.mkdirs();
            FileOutputStream f = new FileOutputStream(path);
            f.write(content.getBytes());
            f.close();
        } catch (Exception e) {
            Log.e(TAG, "Write error: " + e.getMessage());
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
