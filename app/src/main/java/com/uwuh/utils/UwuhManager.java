package com.uwuh.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class UwuhManager {
    private static final String TAG = "UwuhManager";
    public static final String DIR_PATH = "/data/system/uwuh/";

    public static final String KB_PATH = DIR_PATH + "keybox.xml";
    public static final String PIF_PATH = DIR_PATH + "pif.prop";
    public static final String CUST_KB_PATH = DIR_PATH + "cust_keybox.xml";
    public static final String CUST_PIF_PATH = DIR_PATH + "cust_pif.prop";
    public static final String GAMEPROPS_PATH = DIR_PATH + "gameprops.json";
    public static final String THERMALS_PATH = DIR_PATH + "per_app_thermals.json";

    public static final String MODULE_KEYBOX = "k";
    public static final String MODULE_PIF = "p";
    public static final String MODULE_GAMEPROPS = "g";
    public static final String MODULE_THERMALS = "t";

    public static final String PROP_BOOTLOADER = "persist.sys.uwuh.utils.bootloader";
    public static final String PROP_PIF        = "persist.sys.uwuh.utils.fingerprint";
    public static final String PROP_FINSKY     = "persist.sys.uwuh.utils.finsky";
    public static final String PROP_USE_CUSTOM = "persist.sys.uwuh.utils.use_custom";
    public static final String PROP_GAMEPROPS  = "persist.sys.uwuh.utils.gameprops";
    public static final String PROP_THERMALS   = "persist.sys.uwuh.utils.perapp_thermals";

    private static final String FRAMEWORK_ENTRY_CLASS = "com.android.internal.util.danda.OemPortsUtils";
    private static final String PREF_HASHES = "uwuh_file_hashes";

    private static ClassLoader getFrameworkClassLoader() {
        try {
            ClassLoader cl = Class.forName("android.os.SystemProperties").getClassLoader();
            return (cl != null) ? cl : ClassLoader.getSystemClassLoader();
        } catch (Throwable t) {
            return ClassLoader.getSystemClassLoader();
        }
    }

    public static String getProp(String key, String def) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties", true, getFrameworkClassLoader());
            Method get = c.getMethod("get", String.class, String.class);
            return (String) get.invoke(null, key, def);
        } catch (Exception e) {
            return def;
        }
    }

    public static boolean getPropBoolean(String key, boolean def) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties", true, getFrameworkClassLoader());
            Method getBoolean = c.getMethod("getBoolean", String.class, boolean.class);
            return (boolean) getBoolean.invoke(null, key, def);
        } catch (Exception e) {
            return def;
        }
    }

    public static void setProp(String key, String value) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties", true, getFrameworkClassLoader());
            Method set = c.getMethod("set", String.class, String.class);
            set.invoke(null, key, value);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set SystemProperty: " + key, e);
        }
    }

    // ========================================================================
    // VALIDASI (DI APLIKASI, BUKAN FRAMEWORK)
    // ========================================================================

    /**
     * Validasi format GameProps JSON
     */
    public static boolean isValidGameProps(String content) {
        if (content == null || content.trim().isEmpty()) return false;
        try {
            JSONObject root = new JSONObject(content);
            Iterator<String> keys = root.keys();
            boolean hasValidEntry = false;
            
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject obj = root.optJSONObject(key);
                if (obj == null) continue;
                
                JSONArray pkgs = obj.optJSONArray("PKGNAMES");
                if (pkgs == null || pkgs.length() == 0) continue;
                
                if (!obj.has("BRAND") || !obj.has("MANUFACTURER") || !obj.has("MODEL")) {
                    continue;
                }
                
                hasValidEntry = true;
                break;
            }
            return hasValidEntry;
        } catch (JSONException e) {
            return false;
        }
    }

    /**
     * Validasi format Thermals JSON
     */
    public static boolean isValidThermals(String content) {
        if (content == null || content.trim().isEmpty()) return false;
        try {
            JSONObject root = new JSONObject(content);
            Iterator<String> keys = root.keys();
            boolean hasValidEntry = false;
            
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject obj = root.optJSONObject(key);
                if (obj == null) continue;
                
                JSONArray pkgs = obj.optJSONArray("PKGNAMES");
                if (pkgs == null || pkgs.length() == 0) continue;
                
                hasValidEntry = true;
                break;
            }
            return hasValidEntry;
        } catch (JSONException e) {
            return false;
        }
    }

    /**
     * Validasi format Keybox XML
     */
    public static boolean isValidKeybox(String content) {
        if (content == null || content.trim().isEmpty()) return false;
        return content.contains("<Key") && content.contains("</Key>") 
                && (content.contains("<PrivateKey") || content.contains("</PrivateKey>"));
    }

    /**
     * Validasi format PIF Prop
     */
    public static boolean isValidPif(String content) {
        if (content == null || content.trim().isEmpty()) return false;
        String[] lines = content.split("\n");
        int validLines = 0;
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.contains("=") && line.split("=").length >= 2) {
                validLines++;
            }
        }
        return validLines >= 1;
    }

    // ========================================================================
    // HASH CHECKING
    // ========================================================================

    private static String calculateHash(String content) {
        if (content == null || content.isEmpty()) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean needsSync(Context context, String moduleKey, String filePath) {
        File file = new File(filePath);
        if (!file.exists() || file.length() == 0) {
            return false;
        }
        
        String rawContent = readFile(filePath);
        if (rawContent.isEmpty()) return false;
        
        String currentHash = calculateHash(rawContent);
        SharedPreferences sp = context.createDeviceProtectedStorageContext()
                                      .getSharedPreferences(PREF_HASHES, Context.MODE_PRIVATE);
        String lastSyncedHash = sp.getString(moduleKey, "");
        
        return !currentHash.equals(lastSyncedHash);
    }

    public static boolean isValidFile(String filePath) {
        File file = new File(filePath);
        return file.exists() && file.length() > 0;
    }

    // ========================================================================
    // SYNC KE FRAMEWORK (DENGAN VALIDASI)
    // ========================================================================

    /**
     * Sync ke framework dengan hash checking + validasi
     * ✅ Jika file valid → enable feature + write chunk
     * ❌ Jika file invalid → disable feature + clear chunk
     */
    public static synchronized void syncToFramework(Context context, String moduleKey, String filePath) {
        File file = new File(filePath);
        Context dpContext = context.createDeviceProtectedStorageContext();
        SharedPreferences sp = dpContext.getSharedPreferences(PREF_HASHES, Context.MODE_PRIVATE);

        // Jika file tidak ada → clear config + disable
        if (!file.exists() || file.length() == 0) {
            if (sp.contains(moduleKey)) {
                invokeFrameworkClearConfig(moduleKey);
                sp.edit().remove(moduleKey).apply();
                Log.d(TAG, "Cleared config for: " + moduleKey);
            }
            // ✅ Disable feature di aplikasi
            if (moduleKey.equals(MODULE_GAMEPROPS)) {
                setProp(PROP_GAMEPROPS, "false");
            } else if (moduleKey.equals(MODULE_THERMALS)) {
                setProp(PROP_THERMALS, "false");
            }
            return;
        }

        String rawContent = readFile(filePath);
        if (rawContent.isEmpty()) {
            if (moduleKey.equals(MODULE_GAMEPROPS)) {
                setProp(PROP_GAMEPROPS, "false");
            } else if (moduleKey.equals(MODULE_THERMALS)) {
                setProp(PROP_THERMALS, "false");
            }
            return;
        }

        // ✅ Validasi konten di APLIKASI
        boolean isValid = false;
        switch (moduleKey) {
            case MODULE_GAMEPROPS:
                isValid = isValidGameProps(rawContent);
                break;
            case MODULE_THERMALS:
                isValid = isValidThermals(rawContent);
                break;
            case MODULE_KEYBOX:
                isValid = isValidKeybox(rawContent);
                break;
            case MODULE_PIF:
                isValid = isValidPif(rawContent);
                break;
        }

        // ❌ Jika tidak valid → clear chunk + disable
        if (!isValid) {
            invokeFrameworkClearConfig(moduleKey);
            sp.edit().remove(moduleKey).apply();
            if (moduleKey.equals(MODULE_GAMEPROPS)) {
                setProp(PROP_GAMEPROPS, "false");
            } else if (moduleKey.equals(MODULE_THERMALS)) {
                setProp(PROP_THERMALS, "false");
            }
            Log.d(TAG, "Cleared invalid " + moduleKey + " chunk");
            return;
        }

        // ✅ Jika valid → enable feature
        if (moduleKey.equals(MODULE_GAMEPROPS)) {
            setProp(PROP_GAMEPROPS, "true");
        } else if (moduleKey.equals(MODULE_THERMALS)) {
            setProp(PROP_THERMALS, "true");
        }

        // ✅ Hash checking
        String currentHash = calculateHash(rawContent);
        String lastSyncedHash = sp.getString(moduleKey, "");

        if (!currentHash.equals(lastSyncedHash)) {
            boolean success = invokeFrameworkWriteConfig(moduleKey, rawContent);
            if (success) {
                sp.edit().putString(moduleKey, currentHash).apply();
                Log.d(TAG, "Chunk synced for: " + moduleKey + " (Hash Updated)");
            } else {
                Log.e(TAG, "Failed to sync: " + moduleKey);
            }
        } else {
            Log.d(TAG, "Skip syncing " + moduleKey + ": Content has not changed.");
        }
    }

    /**
     * FORCE sync (paksa update) - KHUSUS untuk GameProps & Thermals reset!
     */
    public static boolean forceSyncToFramework(Context context, String moduleKey, String filePath) {
        File file = new File(filePath);
        if (!file.exists() || file.length() == 0) {
            invokeFrameworkClearConfig(moduleKey);
            if (moduleKey.equals(MODULE_GAMEPROPS)) {
                setProp(PROP_GAMEPROPS, "false");
            } else if (moduleKey.equals(MODULE_THERMALS)) {
                setProp(PROP_THERMALS, "false");
            }
            return true;
        }
        
        String rawContent = readFile(filePath);
        if (rawContent.isEmpty()) return false;
        
        // ✅ Validasi
        boolean isValid = false;
        switch (moduleKey) {
            case MODULE_GAMEPROPS:
                isValid = isValidGameProps(rawContent);
                break;
            case MODULE_THERMALS:
                isValid = isValidThermals(rawContent);
                break;
            case MODULE_KEYBOX:
                isValid = isValidKeybox(rawContent);
                break;
            case MODULE_PIF:
                isValid = isValidPif(rawContent);
                break;
        }
        
        if (!isValid) {
            invokeFrameworkClearConfig(moduleKey);
            if (moduleKey.equals(MODULE_GAMEPROPS)) {
                setProp(PROP_GAMEPROPS, "false");
            } else if (moduleKey.equals(MODULE_THERMALS)) {
                setProp(PROP_THERMALS, "false");
            }
            return false;
        }
        
        // ✅ FORCE: langsung panggil writeModuleConfig()
        boolean success = invokeFrameworkWriteConfig(moduleKey, rawContent);
        if (success) {
            String currentHash = calculateHash(rawContent);
            SharedPreferences sp = context.createDeviceProtectedStorageContext()
                                          .getSharedPreferences(PREF_HASHES, Context.MODE_PRIVATE);
            sp.edit().putString(moduleKey, currentHash).apply();
            
            if (moduleKey.equals(MODULE_GAMEPROPS)) {
                setProp(PROP_GAMEPROPS, "true");
            } else if (moduleKey.equals(MODULE_THERMALS)) {
                setProp(PROP_THERMALS, "true");
            }
            Log.d(TAG, "FORCE synced for: " + moduleKey);
        }
        return success;
    }

    public static void syncAllToFramework(Context context, boolean useCustom) {
        syncToFramework(context, MODULE_KEYBOX, useCustom && new File(CUST_KB_PATH).exists() ? CUST_KB_PATH : KB_PATH);
        syncToFramework(context, MODULE_PIF, useCustom && new File(CUST_PIF_PATH).exists() ? CUST_PIF_PATH : PIF_PATH);
        syncToFramework(context, MODULE_GAMEPROPS, GAMEPROPS_PATH);
        syncToFramework(context, MODULE_THERMALS, THERMALS_PATH);
    }

    // ========================================================================
    // WRITE & SYNC (DENGAN VALIDASI)
    // ========================================================================

    /**
     * Write file, validasi, dan sync ke framework
     * ✅ Jika file valid → enable feature + write chunk
     * ❌ Jika file invalid → disable feature + clear chunk
     */
    public static boolean writeAndSync(Context context, String moduleKey, String path, String content) {
        // 1. Tulis file ke disk
        if (!writeFile(path, content)) {
            Log.e(TAG, "Failed to write file: " + path);
            return false;
        }
        
        // 2. Validasi konten berdasarkan module
        boolean isValid = false;
        switch (moduleKey) {
            case MODULE_GAMEPROPS:
                isValid = isValidGameProps(content);
                break;
            case MODULE_THERMALS:
                isValid = isValidThermals(content);
                break;
            case MODULE_KEYBOX:
                isValid = isValidKeybox(content);
                break;
            case MODULE_PIF:
                isValid = isValidPif(content);
                break;
        }
        
        // 3. Set enable/disable prop di aplikasi
        if (moduleKey.equals(MODULE_GAMEPROPS)) {
            setProp(PROP_GAMEPROPS, String.valueOf(isValid));
            Log.d(TAG, "GameProps " + (isValid ? "ENABLED" : "DISABLED"));
        } else if (moduleKey.equals(MODULE_THERMALS)) {
            setProp(PROP_THERMALS, String.valueOf(isValid));
            Log.d(TAG, "Thermals " + (isValid ? "ENABLED" : "DISABLED"));
        }
        
        // 4. Jika tidak valid, clear chunk
        if (!isValid) {
            invokeFrameworkClearConfig(moduleKey);
            SharedPreferences sp = context.createDeviceProtectedStorageContext()
                                          .getSharedPreferences(PREF_HASHES, Context.MODE_PRIVATE);
            sp.edit().remove(moduleKey).apply();
            Log.d(TAG, "Cleared chunk for invalid " + moduleKey);
            return false;
        }
        
        // 5. Hanya sync jika valid
        syncToFramework(context, moduleKey, path);
        
        return true;
    }

    public static void copyRawIfNotExist(Context ctx, int rawResId, String targetPath, String moduleKey) {
        File file = new File(targetPath);
        if (!file.exists() || file.length() == 0) {
            String content = readRawRes(ctx, rawResId);
            if (!content.isEmpty()) {
                writeAndSync(ctx, moduleKey, targetPath, content);
                Log.d(TAG, "Copied default raw resource to: " + targetPath);
            }
        }
    }

    // ========================================================================
    // FRAMEWORK REFLECTION
    // ========================================================================

    private static boolean invokeFrameworkWriteConfig(String moduleKey, String rawContent) {
        try {
            Class<?> clazz = Class.forName(FRAMEWORK_ENTRY_CLASS, true, getFrameworkClassLoader());
            Method method = clazz.getMethod("writeModuleConfig", String.class, String.class);
            method.invoke(null, moduleKey, rawContent);
            Log.d(TAG, "Successfully invoked OemPortsUtils.writeModuleConfig for: " + moduleKey);
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Reflection OemPortsUtils write error for module " + moduleKey, t);
            return false;
        }
    }

    private static boolean invokeFrameworkClearConfig(String moduleKey) {
        try {
            Class<?> clazz = Class.forName(FRAMEWORK_ENTRY_CLASS, true, getFrameworkClassLoader());
            Method method = clazz.getMethod("clearModuleConfig", String.class);
            method.invoke(null, moduleKey);
            Log.d(TAG, "Successfully invoked OemPortsUtils.clearModuleConfig for: " + moduleKey);
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Reflection OemPortsUtils clear error for module " + moduleKey, t);
            return false;
        }
    }

    // ========================================================================
    // FILE OPERATIONS
    // ========================================================================

    public static boolean writeFile(String path, String content) {
        try {
            File file = new File(path);
            File dir = file.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
                dir.setReadable(true, false);
                dir.setWritable(true, false);
                dir.setExecutable(true, false);
            }
            FileOutputStream f = new FileOutputStream(file);
            f.write(content.getBytes());
            f.close();

            file.setReadable(true, false);
            file.setWritable(true, false);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error writing file: " + path, e);
            return false;
        }
    }

    public static String readFile(String path) {
        try {
            File f = new File(path);
            if (!f.exists()) return "";
            BufferedReader r = new BufferedReader(new FileReader(f));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append("\n");
            r.close();
            return sb.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    public static String readRawRes(Context ctx, int rawResId) {
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

    public static Map<String, Boolean> getSyncStatus(Context context) {
        Map<String, Boolean> status = new HashMap<>();
        status.put(MODULE_KEYBOX, needsSync(context, MODULE_KEYBOX, KB_PATH));
        status.put(MODULE_PIF, needsSync(context, MODULE_PIF, PIF_PATH));
        status.put(MODULE_GAMEPROPS, needsSync(context, MODULE_GAMEPROPS, GAMEPROPS_PATH));
        status.put(MODULE_THERMALS, needsSync(context, MODULE_THERMALS, THERMALS_PATH));
        return status;
    }
}
