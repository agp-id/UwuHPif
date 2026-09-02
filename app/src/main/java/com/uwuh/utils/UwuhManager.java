package com.uwuh.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.security.MessageDigest;

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

    /**
     * Hitung SHA-256 Hash dari string isi file
     */
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

    public static synchronized void syncToFramework(Context context, String moduleKey, String filePath) {
        File file = new File(filePath);
        Context dpContext = context.createDeviceProtectedStorageContext();
        SharedPreferences sp = dpContext.getSharedPreferences(PREF_HASHES, Context.MODE_PRIVATE);

        if (!file.exists() || file.length() == 0) {
            if (sp.contains(moduleKey)) {
                invokeFrameworkClearConfig(moduleKey);
                sp.edit().remove(moduleKey).apply();
            }
            return;
        }

        String rawContent = readFile(filePath);
        if (rawContent.isEmpty()) return;

        String currentHash = calculateHash(rawContent);
        String lastSyncedHash = sp.getString(moduleKey, "");

        // HANYA DIPANGGIL JIKA ISI FILE/HASH BERBEDA!
        if (!currentHash.equals(lastSyncedHash)) {
            boolean success = invokeFrameworkWriteConfig(moduleKey, rawContent);
            if (success) {
                sp.edit().putString(moduleKey, currentHash).apply();
                Log.d(TAG, "Chunk synced via OemPortsUtils for: " + moduleKey + " (Hash Updated)");
            }
        } else {
            Log.d(TAG, "Skip syncing " + moduleKey + ": Content has not changed.");
        }
    }

    public static void syncAllToFramework(Context context, boolean useCustom) {
        syncToFramework(context, MODULE_KEYBOX, useCustom && new File(CUST_KB_PATH).exists() ? CUST_KB_PATH : KB_PATH);
        syncToFramework(context, MODULE_PIF, useCustom && new File(CUST_PIF_PATH).exists() ? CUST_PIF_PATH : PIF_PATH);
        syncToFramework(context, MODULE_GAMEPROPS, GAMEPROPS_PATH);
        syncToFramework(context, MODULE_THERMALS, THERMALS_PATH);
    }

    public static boolean writeAndSync(Context context, String moduleKey, String path, String content) {
        if (writeFile(path, content)) {
            syncToFramework(context, moduleKey, path);
            return true;
        }
        return false;
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
}
