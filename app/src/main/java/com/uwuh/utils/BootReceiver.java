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
    
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 2000;
    private static final int CONNECT_TIMEOUT = 8000;
    private static final int READ_TIMEOUT = 8000;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)) {
            
            Context directBootContext = context.createDeviceProtectedStorageContext();
            SharedPreferences sp = directBootContext.getSharedPreferences("uwuh_prefs", Context.MODE_PRIVATE);
            boolean isAuto = !sp.getBoolean("manual", false);

            new Thread(() -> {
                try {
                    // 1. Copy fallback dari raw jika file belum ada
                    applyFallback(directBootContext);

                    // 2. Sync ke framework (di-skip oleh Hash Checker jika file tidak berubah)
                    UwuhManager.syncAllToFramework(directBootContext, !isAuto);

                    // 3. Cek update online jika mode otomatis aktif
                    if (isAuto) {
                        checkUpdateOnline(directBootContext, sp);
                    }
                    
                    Log.d(TAG, "Boot processing completed successfully");
                } catch (Exception e) {
                    Log.e(TAG, "Boot processing failed", e);
                }
            }).start();
        }
    }

    private void applyFallback(Context context) {
        try {
            UwuhManager.copyRawIfNotExist(context, R.raw.default_keybox, UwuhManager.KB_PATH, UwuhManager.MODULE_KEYBOX);
            UwuhManager.copyRawIfNotExist(context, R.raw.default_pif, UwuhManager.PIF_PATH, UwuhManager.MODULE_PIF);
            UwuhManager.copyRawIfNotExist(context, R.raw.default_gameprops, UwuhManager.GAMEPROPS_PATH, UwuhManager.MODULE_GAMEPROPS);
            UwuhManager.copyRawIfNotExist(context, R.raw.default_thermals, UwuhManager.THERMALS_PATH, UwuhManager.MODULE_THERMALS);
            Log.d(TAG, "Fallback files applied (if needed)");
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply fallback", e);
        }
    }

    private void checkUpdateOnline(Context context, SharedPreferences sp) {
        boolean allSuccess = true;
        StringBuilder statusLog = new StringBuilder();
        StringBuilder errors = new StringBuilder();
        
        try {
            Log.d(TAG, "Starting online update check...");
            
            String newKb = fetchWithRetry(URL_KB, MAX_RETRIES);
            UpdateResult kbResult = processKeyboxUpdate(context, newKb);
            allSuccess = allSuccess && kbResult.success;
            statusLog.append("Keybox: ").append(kbResult.message).append("\n");
            if (!kbResult.success && kbResult.error != null) {
                errors.append("Keybox: ").append(kbResult.error).append("\n");
            }

            String newPif = fetchWithRetry(URL_PIF, MAX_RETRIES);
            UpdateResult pifResult = processPifUpdate(context, newPif);
            allSuccess = allSuccess && pifResult.success;
            statusLog.append("PIF: ").append(pifResult.message).append("\n");
            if (!pifResult.success && pifResult.error != null) {
                errors.append("PIF: ").append(pifResult.error).append("\n");
            }

            String date = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new Date());
            SharedPreferences.Editor editor = sp.edit();
            
            if (allSuccess) {
                editor.putString("last_update", date);
                editor.putString("update_status", "SUCCESS");
                editor.putString("update_details", statusLog.toString());
                Log.d(TAG, "Online update completed successfully:\n" + statusLog.toString());
            } else {
                editor.putString("last_update", "FAILED: " + date);
                editor.putString("update_status", "FAILED");
                editor.putString("update_details", errors.toString());
                Log.w(TAG, "Online update completed with errors:\n" + errors.toString());
            }
            editor.apply();

        } catch (Exception e) {
            Log.e(TAG, "Fatal error during online update", e);
            String date = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new Date());
            sp.edit()
                .putString("last_update", "ERROR: " + date)
                .putString("update_status", "ERROR")
                .putString("update_details", "Fatal error: " + e.getMessage())
                .apply();
        }
    }

    private UpdateResult processKeyboxUpdate(Context context, String newContent) {
        UpdateResult result = new UpdateResult();
        
        try {
            if (newContent == null) {
                result.success = false;
                result.message = "Download failed - SKIPPED";
                result.error = "Connection error";
                return result;
            }
            
            if (newContent.isEmpty()) {
                result.success = false;
                result.message = "Empty file - SKIPPED";
                result.error = "Empty content";
                return result;
            }
            
            // ✅ Validasi di aplikasi
            if (!UwuhManager.isValidKeybox(newContent)) {
                result.success = false;
                result.message = "Invalid XML format - SKIPPED";
                result.error = "Invalid XML";
                return result;
            }
            
            String currentKb = UwuhManager.readFile(UwuhManager.KB_PATH)
                    .replaceAll("\r\n", "\n")
                    .trim();
            
            if (newContent.equals(currentKb)) {
                result.success = true;
                result.message = "Already up-to-date";
                return result;
            }
            
            // ✅ writeAndSync akan validasi + set enable/disable
            boolean writeSuccess = UwuhManager.writeAndSync(
                context, 
                UwuhManager.MODULE_KEYBOX, 
                UwuhManager.KB_PATH, 
                newContent
            );
            
            if (writeSuccess) {
                result.success = true;
                result.message = "Updated successfully";
            } else {
                result.success = false;
                result.message = "Write failed";
                result.error = "Failed to write";
            }
            
        } catch (Exception e) {
            result.success = false;
            result.message = "Error - SKIPPED";
            result.error = e.getMessage();
        }
        
        return result;
    }

    private UpdateResult processPifUpdate(Context context, String newContent) {
        UpdateResult result = new UpdateResult();
        
        try {
            if (newContent == null) {
                result.success = false;
                result.message = "Download failed - SKIPPED";
                result.error = "Connection error";
                return result;
            }
            
            if (newContent.isEmpty()) {
                result.success = false;
                result.message = "Empty file - SKIPPED";
                result.error = "Empty content";
                return result;
            }
            
            // ✅ Validasi di aplikasi
            if (!UwuhManager.isValidPif(newContent)) {
                result.success = false;
                result.message = "Invalid PIF format - SKIPPED";
                result.error = "Invalid format";
                return result;
            }
            
            String currentPif = UwuhManager.readFile(UwuhManager.PIF_PATH)
                    .replaceAll("\r\n", "\n")
                    .trim();
            
            if (newContent.equals(currentPif)) {
                result.success = true;
                result.message = "Already up-to-date";
                return result;
            }
            
            // ✅ writeAndSync akan validasi + set enable/disable
            boolean writeSuccess = UwuhManager.writeAndSync(
                context, 
                UwuhManager.MODULE_PIF, 
                UwuhManager.PIF_PATH, 
                newContent
            );
            
            if (writeSuccess) {
                result.success = true;
                result.message = "Updated successfully";
            } else {
                result.success = false;
                result.message = "Write failed";
                result.error = "Failed to write";
            }
            
        } catch (Exception e) {
            result.success = false;
            result.message = "Error - SKIPPED";
            result.error = e.getMessage();
        }
        
        return result;
    }

    private String fetchWithRetry(String urlStr, int maxRetries) {
        int attempt = 0;
        Exception lastException = null;
        
        while (attempt < maxRetries) {
            try {
                Log.d(TAG, "Fetch attempt " + (attempt + 1) + "/" + maxRetries);
                String result = fetch(urlStr);
                if (result != null) {
                    Log.d(TAG, "Fetch successful (size: " + result.length() + " bytes)");
                    return result;
                }
            } catch (Exception e) {
                lastException = e;
                Log.w(TAG, "Fetch attempt " + (attempt + 1) + " failed", e);
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
        
        Log.e(TAG, "All " + maxRetries + " attempts failed", lastException);
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
                Log.w(TAG, "Non-200 response: " + responseCode);
                return null;
            }
            
            int contentLength = connection.getContentLength();
            if (contentLength == 0) {
                Log.w(TAG, "Content length is 0 (empty file)");
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

    private static class UpdateResult {
        boolean success = false;
        String message = "";
        String error = null;
    }
}
