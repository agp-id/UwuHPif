package com.uwuh.utils;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.HashMap;
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

    private static final Map<String, Long> sLastModifiedMap = new HashMap<>();

    // SystemProperties Helper via Reflection
    public static String getProp(String key, String def) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("get", String.class, String.class);
            return (String) get.invoke(null, key, def);
        } catch (Exception e) {
            return def;
        }
    }

    public static boolean getPropBoolean(String key, boolean def) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method getBoolean = c.getMethod("getBoolean", String.class, boolean.class);
            return (boolean) getBoolean.invoke(null, key, def);
        } catch (Exception e) {
            return def;
        }
    }

    public static void setProp(String key, String value) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method set = c.getMethod("set", String.class, String.class);
            set.invoke(null, key, value);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set SystemProperty: " + key);
        }
    }

    // Chunk Writer Helper
    public static synchronized void syncToFramework(String moduleKey, String filePath) {
        File file = new File(filePath);

        if (!file.exists() || file.length() == 0) {
            if (sLastModifiedMap.containsKey(filePath)) {
                invokeFrameworkClearConfig(moduleKey);
                sLastModifiedMap.remove(filePath);
            }
            return;
        }

        long currentLastModified = file.lastModified();
        Long previousLastModified = sLastModifiedMap.get(filePath);

        if (previousLastModified != null && previousLastModified == currentLastModified) {
            return;
        }

        String rawContent = readFile(filePath);
        if (!rawContent.isEmpty()) {
            invokeFrameworkWriteConfig(moduleKey, rawContent);
            sLastModifiedMap.put(filePath, currentLastModified);
            Log.d(TAG, "Chunk synced to RAM via Reflection for: " + moduleKey);
        }
    }

    public static void syncAllToFramework(boolean useCustom) {
        syncToFramework(MODULE_KEYBOX, useCustom && new File(CUST_KB_PATH).exists() ? CUST_KB_PATH : KB_PATH);
        syncToFramework(MODULE_PIF, useCustom && new File(CUST_PIF_PATH).exists() ? CUST_PIF_PATH : PIF_PATH);
        syncToFramework(MODULE_GAMEPROPS, GAMEPROPS_PATH);
        syncToFramework(MODULE_THERMALS, THERMALS_PATH);
    }

    public static boolean writeAndSync(String moduleKey, String path, String content) {
        if (writeFile(path, content)) {
            syncToFramework(moduleKey, path);
            return true;
        }
        return false;
    }

    private static void invokeFrameworkWriteConfig(String moduleKey, String rawContent) {
        try {
            Class<?> clazz = Class.forName("com.android.internal.util.danda.SpoofUtils");
            Method method = clazz.getMethod("writeModuleConfig", String.class, String.class);
            method.invoke(null, moduleKey, rawContent);
        } catch (Throwable t) {
            Log.e(TAG, "Reflection SpoofUtils write error", t);
        }
    }

    private static void invokeFrameworkClearConfig(String moduleKey) {
        try {
            Class<?> clazz = Class.forName("com.android.internal.util.danda.SpoofUtils");
            Method method = clazz.getMethod("clearModuleConfig", String.class);
            method.invoke(null, moduleKey);
        } catch (Throwable t) {
            Log.e(TAG, "Reflection SpoofUtils clear error", t);
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
