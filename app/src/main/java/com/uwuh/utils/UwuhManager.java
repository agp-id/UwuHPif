package com.uwuh.utils;

import android.content.Context;
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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
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
    public static final String LOG_PATH = DIR_PATH + "uwuh.log";

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
    public static final String PROP_AUTO_UPDATE = "persist.sys.uwuh.utils.auto_update";
    public static final String PROP_GPHOTOS    = "persist.sys.uwuh.utils.gphotos";
    public static final String PROP_NETFLIX    = "persist.sys.uwuh.utils.netflix";

    private static final String FRAMEWORK_ENTRY_CLASS = "com.android.internal.util.danda.OemPortsUtils";

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

    public static String getVersionProp(String moduleKey) {
        return "persist.sys." + moduleKey + "v";
    }

    public static boolean isAutoUpdateEnabled() {
        return getPropBoolean(PROP_AUTO_UPDATE, true);
    }

    public static boolean isCustomMode() {
        return getPropBoolean(PROP_USE_CUSTOM, false);
    }

    public static boolean isGPhotosEnabled() {
        return getPropBoolean(PROP_GPHOTOS, false);
    }

    public static boolean isNetflixEnabled() {
        return getPropBoolean(PROP_NETFLIX, false);
    }

    // ========================================================================
    // VALIDASI
    // ========================================================================

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

    public static boolean isValidKeybox(String content) {
        if (content == null || content.trim().isEmpty()) return false;
        return content.contains("<Key") && content.contains("</Key>") 
                && (content.contains("<PrivateKey") || content.contains("</PrivateKey>"));
    }

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
    // LOGGING
    // ========================================================================

    public static void appendLog(String message) {
        try {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            String logEntry = "[" + timestamp + "] " + message + "\n";
            writeFile(LOG_PATH, logEntry, true);
        } catch (Exception e) {
            Log.e(TAG, "Failed to append log", e);
        }
    }

    public static String getLogContent() {
        return readFile(LOG_PATH);
    }

    public static void clearLog() {
        writeFile(LOG_PATH, "", false);
    }

    // ========================================================================
    // SYNC KE FRAMEWORK
    // ========================================================================

    public static synchronized void syncToFramework(Context context, String moduleKey, String filePath) {
        boolean useCustom = isCustomMode();
        String actualPath = filePath;
        
        if (moduleKey.equals(MODULE_KEYBOX) || moduleKey.equals(MODULE_PIF)) {
            if (useCustom) {
                String customPath = moduleKey.equals(MODULE_KEYBOX) ? CUST_KB_PATH : CUST_PIF_PATH;
                if (new File(customPath).exists() && new File(customPath).length() > 0) {
                    actualPath = customPath;
                    appendLog("Using custom file for " + moduleKey + ": " + actualPath);
                }
            }
        }
        
        File file = new File(actualPath);
        
        if (!file.exists() || file.length() == 0) {
            invokeFrameworkClearConfig(moduleKey);
            appendLog("Cleared config for: " + moduleKey + " (file not found: " + actualPath + ")");
            return;
        }
        
        long fileTime = file.lastModified();
        String currentVersion = getProp(getVersionProp(moduleKey), "0");
        
        if (String.valueOf(fileTime).equals(currentVersion)) {
            appendLog("Skip " + moduleKey + ": Version match (" + fileTime + ")");
            return;
        }
        
        appendLog("Version mismatch for " + moduleKey + ": current=" + currentVersion + ", file=" + fileTime);
        
        String rawContent = readFile(actualPath);
        if (rawContent.isEmpty()) {
            appendLog("File empty for " + moduleKey + ", skipping");
            return;
        }
        
        boolean isValid = false;
        switch (moduleKey) {
            case MODULE_KEYBOX:
                isValid = isValidKeybox(rawContent);
                break;
            case MODULE_PIF:
                isValid = isValidPif(rawContent);
                break;
            case MODULE_GAMEPROPS:
                isValid = isValidGameProps(rawContent);
                break;
            case MODULE_THERMALS:
                isValid = isValidThermals(rawContent);
                break;
        }
        
        if (!isValid) {
            invokeFrameworkClearConfig(moduleKey);
            appendLog("Cleared invalid " + moduleKey + " chunk");
            return;
        }
        
        boolean success = invokeFrameworkWriteConfig(moduleKey, rawContent, fileTime);
        
        if (success) {
            appendLog("Chunk updated for: " + moduleKey + " (version: " + fileTime + ")");
        } else {
            appendLog("Failed to update chunk for: " + moduleKey);
        }
    }

    public static void syncAllToFramework(Context context, boolean useCustom) {
        appendLog("syncAllToFramework: useCustom=" + useCustom);
        
        if (useCustom) {
            boolean hasCustomKb = new File(CUST_KB_PATH).exists() && new File(CUST_KB_PATH).length() > 0;
            boolean hasCustomPif = new File(CUST_PIF_PATH).exists() && new File(CUST_PIF_PATH).length() > 0;
            
            if (hasCustomKb) {
                syncToFramework(context, MODULE_KEYBOX, CUST_KB_PATH);
            } else {
                appendLog("Custom keybox not found, using default");
                syncToFramework(context, MODULE_KEYBOX, KB_PATH);
            }
            
            if (hasCustomPif) {
                syncToFramework(context, MODULE_PIF, CUST_PIF_PATH);
            } else {
                appendLog("Custom PIF not found, using default");
                syncToFramework(context, MODULE_PIF, PIF_PATH);
            }
        } else {
            syncToFramework(context, MODULE_KEYBOX, KB_PATH);
            syncToFramework(context, MODULE_PIF, PIF_PATH);
        }
        
        syncToFramework(context, MODULE_GAMEPROPS, GAMEPROPS_PATH);
        syncToFramework(context, MODULE_THERMALS, THERMALS_PATH);
    }

    // ========================================================================
    // WRITE & SYNC
    // ========================================================================

    public static boolean writeAndSync(Context context, String moduleKey, String path, String content) {
        if (!writeFile(path, content, false)) {
            appendLog("Failed to write file: " + path);
            return false;
        }
        
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
        
        if (moduleKey.equals(MODULE_GAMEPROPS)) {
            setProp(PROP_GAMEPROPS, String.valueOf(isValid));
            appendLog("GameProps " + (isValid ? "ENABLED" : "DISABLED"));
        } else if (moduleKey.equals(MODULE_THERMALS)) {
            setProp(PROP_THERMALS, String.valueOf(isValid));
            appendLog("Thermals " + (isValid ? "ENABLED" : "DISABLED"));
        }
        
        if (!isValid) {
            invokeFrameworkClearConfig(moduleKey);
            appendLog("Cleared chunk for invalid " + moduleKey);
            return false;
        }
        
        syncToFramework(context, moduleKey, path);
        return true;
    }

    public static void copyRawIfNotExist(Context ctx, int rawResId, String targetPath, String moduleKey) {
        File file = new File(targetPath);
        if (!file.exists() || file.length() == 0) {
            String content = readRawRes(ctx, rawResId);
            if (!content.isEmpty()) {
                writeAndSync(ctx, moduleKey, targetPath, content);
                appendLog("Copied default raw resource to: " + targetPath);
            }
        }
    }

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
        
        long fileTime = file.lastModified();
        boolean success = invokeFrameworkWriteConfig(moduleKey, rawContent, fileTime);
        
        if (success) {
            if (moduleKey.equals(MODULE_GAMEPROPS)) {
                setProp(PROP_GAMEPROPS, "true");
            } else if (moduleKey.equals(MODULE_THERMALS)) {
                setProp(PROP_THERMALS, "true");
            }
            appendLog("FORCE synced for: " + moduleKey + " (version: " + fileTime + ")");
        }
        return success;
    }

    // ========================================================================
    // FRAMEWORK REFLECTION
    // ========================================================================

    private static boolean invokeFrameworkWriteConfig(String moduleKey, String rawContent, long fileTime) {
        try {
            Class<?> clazz = Class.forName(FRAMEWORK_ENTRY_CLASS, true, getFrameworkClassLoader());
            Method method = clazz.getMethod("writeModuleConfig", String.class, String.class, long.class);
            method.invoke(null, moduleKey, rawContent, fileTime);
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Reflection write error for module " + moduleKey, t);
            return false;
        }
    }

    private static boolean invokeFrameworkClearConfig(String moduleKey) {
        try {
            Class<?> clazz = Class.forName(FRAMEWORK_ENTRY_CLASS, true, getFrameworkClassLoader());
            Method method = clazz.getMethod("clearModuleConfig", String.class);
            method.invoke(null, moduleKey);
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Reflection clear error for module " + moduleKey, t);
            return false;
        }
    }

    // ========================================================================
    // FILE OPERATIONS
    // ========================================================================

    public static boolean writeFile(String path, String content, boolean append) {
        try {
            File file = new File(path);
            File dir = file.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
                dir.setReadable(true, false);
                dir.setWritable(true, false);
                dir.setExecutable(true, false);
            }
            FileOutputStream fos = new FileOutputStream(file, append);
            fos.write(content.getBytes());
            fos.close();

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
}
