package com.uwuh.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "PifManagerBoot";

    private static final String URL_KB = "https://raw.githubusercontent.com/user/repo/main/keybox.xml";
    private static final String URL_PIF = "https://raw.githubusercontent.com/user/repo/main/pif.prop";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)) {
            SharedPreferences sp = context.getSharedPreferences("pif_prefs", Context.MODE_PRIVATE);
            boolean isAuto = sp.getBoolean("auto", true);

            new Thread(() -> {
                applyFallback(context);
                
                // Lakukan sync awal seluruh file /data/system/uwuh/ ke RAM Chunk
                UwuhManager.syncAllToFramework();

                if (isAuto) {
                    checkUpdateOnline(sp);
                }
            }).start();
        }
    }

    private void applyFallback(Context context) {
        if (!new java.io.File(UwuhManager.KB_PATH).exists()) {
            String data = UwuhManager.readRawRes(context, R.raw.default_keybox);
            if (!data.isEmpty()) UwuhManager.writeAndSync(UwuhManager.MODULE_KEYBOX, UwuhManager.KB_PATH, data);
        }

        if (!new java.io.File(UwuhManager.PIF_PATH).exists()) {
            String data = UwuhManager.readRawRes(context, R.raw.default_pif);
            if (!data.isEmpty()) UwuhManager.writeAndSync(UwuhManager.MODULE_PIF, UwuhManager.PIF_PATH, data);
        }

        if (!new java.io.File(UwuhManager.GAMEPROPS_PATH).exists()) {
            String data = UwuhManager.readRawRes(context, R.raw.default_gameprops);
            if (!data.isEmpty()) UwuhManager.writeAndSync(UwuhManager.MODULE_GAMEPROPS, UwuhManager.GAMEPROPS_PATH, data);
        }

        if (!new java.io.File(UwuhManager.THERMALS_PATH).exists()) {
            String data = UwuhManager.readRawRes(context, R.raw.default_thermals);
            if (!data.isEmpty()) UwuhManager.writeAndSync(UwuhManager.MODULE_THERMALS, UwuhManager.THERMALS_PATH, data);
        }
    }

    private void checkUpdateOnline(SharedPreferences sp) {
        try {
            String newKb = fetch(URL_KB);
            String newPif = fetch(URL_PIF);

            boolean updated = false;

            if (newKb != null && !newKb.isEmpty() && !newKb.equals(UwuhManager.readFile(UwuhManager.KB_PATH))) {
                if (UwuhManager.writeAndSync(UwuhManager.MODULE_KEYBOX, UwuhManager.KB_PATH, newKb)) updated = true;
            }

            if (newPif != null && !newPif.isEmpty() && !newPif.equals(UwuhManager.readFile(UwuhManager.PIF_PATH))) {
                if (UwuhManager.writeAndSync(UwuhManager.MODULE_PIF, UwuhManager.PIF_PATH, newPif)) updated = true;
            }

            if (updated) {
                String date = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new Date());
                sp.edit().putString("last_update", date).apply();
                Log.d(TAG, "Auto update completed at boot and synced to RAM via Reflection!");
            }

        } catch (Exception e) {
            Log.e(TAG, "Auto update failed: " + e.getMessage());
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
}
