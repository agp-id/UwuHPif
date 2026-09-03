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
    private static final String TAG = "UwuhBoot";

    private static final String URL_KB = "https://raw.githubusercontent.com/user/repo/main/keybox.xml";
    private static final String URL_PIF = "https://raw.githubusercontent.com/user/repo/main/pif.prop";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)) {
            
            Context directBootContext = context.createDeviceProtectedStorageContext();
            SharedPreferences sp = directBootContext.getSharedPreferences("pif_prefs", Context.MODE_PRIVATE);
            boolean isAuto = !sp.getBoolean("manual", false);

            new Thread(() -> {
                // 1. Copy fallback dari raw jika file belum pernah ada di storage
                applyFallback(directBootContext);

                // 2. Lakukan sync ke framework (otomatis di-skip oleh Hash Checker jika isi file tidak berubah)
                UwuhManager.syncAllToFramework(directBootContext, !isAuto);

                // 3. Cek update online jika mode otomatis aktif
                if (isAuto) {
                    checkUpdateOnline(directBootContext, sp);
                }
            }).start();
        }
    }

    private void applyFallback(Context context) {
        // Auto copy dari res/raw ke /data/system/uwuh jika berkas belum ada
        UwuhManager.copyRawIfNotExist(context, R.raw.default_keybox, UwuhManager.KB_PATH, UwuhManager.MODULE_KEYBOX);
        UwuhManager.copyRawIfNotExist(context, R.raw.default_pif, UwuhManager.PIF_PATH, UwuhManager.MODULE_PIF);
        UwuhManager.copyRawIfNotExist(context, R.raw.default_gameprops, UwuhManager.GAMEPROPS_PATH, UwuhManager.MODULE_GAMEPROPS);
        UwuhManager.copyRawIfNotExist(context, R.raw.default_thermals, UwuhManager.THERMALS_PATH, UwuhManager.MODULE_THERMALS);
    }

    private void checkUpdateOnline(Context context, SharedPreferences sp) {
    
        try {
            String newKb = fetch(URL_KB);
            String newPif = fetch(URL_PIF);

            // Cek dan update Keybox jika konten online berbeda
            if (newKb != null && !newKb.isEmpty()) {
                String currentKb = UwuhManager.readFile(UwuhManager.KB_PATH).replaceAll("\r\n", "\n").trim();
                String fetchedKb = newKb.replaceAll("\r\n", "\n").trim();
                if (!fetchedKb.equals(currentKb)) {
                    UwuhManager.writeAndSync(context, UwuhManager.MODULE_KEYBOX, UwuhManager.KB_PATH, fetchedKb);
                }
            }

            // Cek dan update PIF jika konten online berbeda
            if (newPif != null && !newPif.isEmpty()) {
                String currentPif = UwuhManager.readFile(UwuhManager.PIF_PATH).replaceAll("\r\n", "\n").trim();
                String fetchedPif = newPif.replaceAll("\r\n", "\n").trim();
                if (!fetchedPif.equals(currentPif)) {
                    UwuhManager.writeAndSync(context, UwuhManager.MODULE_PIF, UwuhManager.PIF_PATH, fetchedPif);
                }
            }

            String date = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new Date());
            sp.edit().putString("last_update", date).apply();
            Log.d(TAG, "Boot update completed & chunked!");
        } catch (Exception e) {
            Log.e(TAG, "Boot update failed", e);
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
