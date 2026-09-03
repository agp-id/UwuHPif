package com.uwuh.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
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
    private static boolean sBootProcessed = false;

    public static final String URL_KB = "https://raw.githubusercontent.com/user/repo/main/keybox.xml";
    public static final String URL_PIF = "https://raw.githubusercontent.com/user/repo/main/pif.prop";
    
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 2000;
    private static final int CONNECT_TIMEOUT = 8000;
    private static final int READ_TIMEOUT = 8000;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) && 
            !Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)) {
            return;
        }
        
        if (sBootProcessed) {
            Log.d(TAG, "[BOOT] Already processed, skipping");
            return;
        }
        
        boolean autoUpdateEnabled = UwuhManager.isAutoUpdateEnabled();
        boolean useCustom = UwuhManager.isCustomMode();
        
        if (useCustom) {
            String msg = "[BOOT] Custom mode enabled, skipping auto-update";
            Log.d(TAG, msg);
            UwuhManager.appendLog(msg);
            sBootProcessed = true;
            return;
        }
        
        if (!autoUpdateEnabled) {
            String msg = "[BOOT] Auto-update disabled, skipping";
            Log.d(TAG, msg);
            UwuhManager.appendLog(msg);
            sBootProcessed = true;
            return;
        }

        new Thread(() -> {
            try {
                UwuhManager.appendLog("[BOOT] Boot processing started (auto-update enabled)");
                
                applyFallback(context);
                UwuhManager.syncAllToFramework(context, false);
                checkUpdateOnline(context);
                
                UwuhManager.appendLog("[BOOT] Boot processing completed");
                sBootProcessed = true;
                
            } catch (Exception e) {
                Log.e(TAG, "[BOOT] Boot processing failed", e);
                UwuhManager.appendLog("[BOOT] Boot processing failed: " + e.getMessage());
                sBootProcessed = true;
            }
        }).start();
    }

    private void applyFallback(Context context) {
        try {
            UwuhManager.copyRawIfNotExist(context, R.raw.default_keybox, UwuhManager.KB_PATH, UwuhManager.MODULE_KEYBOX);
            UwuhManager.copyRawIfNotExist(context, R.raw.default_pif, UwuhManager.PIF_PATH, UwuhManager.MODULE_PIF);
            UwuhManager.copyRawIfNotExist(context, R.raw.default_gameprops, UwuhManager.GAMEPROPS_PATH, UwuhManager.MODULE_GAMEPROPS);
            UwuhManager.copyRawIfNotExist(context, R.raw.default_thermals, UwuhManager.THERMALS_PATH, UwuhManager.MODULE_THERMALS);
            UwuhManager.appendLog("[BOOT] Fallback files applied (if needed)");
        } catch (Exception e) {
            Log.e(TAG, "[BOOT] Failed to apply fallback", e);
            UwuhManager.appendLog("[BOOT] Failed to apply fallback: " + e.getMessage());
        }
    }

    private void checkUpdateOnline(Context context) {
        try {
            UwuhManager.appendLog("[BOOT] Checking online updates...");
            
            String newKb = fetchWithRetry(URL_KB, MAX_RETRIES);
            String newPif = fetchWithRetry(URL_PIF, MAX_RETRIES);

            if (newKb != null && !newKb.isEmpty() && UwuhManager.isValidKeybox(newKb)) {
                String currentKb = UwuhManager.readFile(UwuhManager.KB_PATH)
                        .replaceAll("\r\n", "\n").trim();
                if (!newKb.equals(currentKb)) {
                    UwuhManager.writeAndSync(context, UwuhManager.MODULE_KEYBOX, UwuhManager.KB_PATH, newKb);
                    UwuhManager.appendLog("[BOOT] Keybox updated from server");
                }
            } else {
                if (newKb == null) {
                    UwuhManager.appendLog("[BOOT] Keybox: Download failed");
                } else if (newKb.isEmpty()) {
                    UwuhManager.appendLog("[BOOT] Keybox: Empty content from server");
                } else {
                    UwuhManager.appendLog("[BOOT] Keybox: Invalid format");
                }
            }

            if (newPif != null && !newPif.isEmpty() && UwuhManager.isValidPif(newPif)) {
                String currentPif = UwuhManager.readFile(UwuhManager.PIF_PATH)
                        .replaceAll("\r\n", "\n").trim();
                if (!newPif.equals(currentPif)) {
                    UwuhManager.writeAndSync(context, UwuhManager.MODULE_PIF, UwuhManager.PIF_PATH, newPif);
                    UwuhManager.appendLog("[BOOT] PIF updated from server");
                }
            } else {
                if (newPif == null) {
                    UwuhManager.appendLog("[BOOT] PIF: Download failed");
                } else if (newPif.isEmpty()) {
                    UwuhManager.appendLog("[BOOT] PIF: Empty content from server");
                } else {
                    UwuhManager.appendLog("[BOOT] PIF: Invalid format");
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "[BOOT] Online update check failed", e);
            UwuhManager.appendLog("[BOOT] Update check failed: " + e.getMessage());
        }
    }

    private String fetchWithRetry(String urlStr, int maxRetries) {
        int attempt = 0;
        Exception lastException = null;
        
        while (attempt < maxRetries) {
            try {
                String result = fetch(urlStr);
                if (result != null) {
                    return result;
                }
            } catch (Exception e) {
                lastException = e;
                Log.w(TAG, "[BOOT] Fetch attempt " + (attempt + 1) + " failed", e);
            }
            
            attempt++;
            if (attempt < maxRetries) {
                try {
                    long delay = RETRY_DELAY_MS * attempt;
                    Thread.sleep(delay);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        Log.e(TAG, "[BOOT] All " + maxRetries + " attempts failed", lastException);
        return null;
    }

    private String fetch(String urlStr) throws Exception {
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        
        try {
            URL url = new URL(urlStr);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Uwuh-Android/1.0");
            
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "[BOOT] Non-200 response: " + responseCode);
                return null;
            }
            
            int contentLength = connection.getContentLength();
            if (contentLength == 0) {
                Log.w(TAG, "[BOOT] Content length is 0 (empty file)");
                return "";
            }
            
            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString().trim();
            
        } finally {
            if (reader != null) try { reader.close(); } catch (Exception ignored) {}
            if (connection != null) try { connection.disconnect(); } catch (Exception ignored) {}
        }
    }
}
