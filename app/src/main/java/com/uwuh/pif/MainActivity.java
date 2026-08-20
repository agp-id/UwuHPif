package com.uwuh.pif;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;  // ← Log dengan L BESAR
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String TAG = "PifManager";
    private static final String DIR = "/data/system/uwuh";
    private static final String LOCAL_DIR = "/data/local/uwuh";
    
    private static final String KB_PATH = DIR + "/keybox.xml";
    private static final String PIF_PATH = DIR + "/pif.prop";
    private static final String CUST_KB_PATH = DIR + "/cust_keybox.xml";
    private static final String CUST_PIF_PATH = DIR + "/cust_pif.prop";
    private static final String GAMEPROPS_PATH = LOCAL_DIR + "/gameprops.json";
    private static final String THERMALS_PATH = LOCAL_DIR + "/per_app_thermals.json";
    
    private static final String URL_KB = "https://raw.githubusercontent.com/user/repo/main/keybox.xml";
    private static final String URL_PIF = "https://raw.githubusercontent.com/user/repo/main/pif.prop";
    private static final String URL_GAMEPROPS = "https://raw.githubusercontent.com/user/repo/main/gameprops.json";
    private static final String URL_THERMALS = "https://raw.githubusercontent.com/user/repo/main/per_app_thermals.json";
    
    private static final String PROP_BOOTLOADER = "persist.sys.oemports10t.utils.bootloader";
    private static final String PROP_PIF = "persist.sys.oemports10t.utils.fingerprint";
    private static final String PROP_USE_CUSTOM = "persist.sys.oemports10t.utils.use_custom";
    private static final String PROP_DEBUG = "persist.sys.oemports10t.utils-debug";
    
    private SharedPreferences sp;
    private Switch switchAuto, switchBootloader, switchPIF, switchProvider, switchDebug;
    private TextView tvStatus, tvLastUpdate;
    private LinearLayout panelManual;
    private EditText etPifEditor;
    private Button btnUpdate, btnApplyManual;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sp = getSharedPreferences("pif_prefs", MODE_PRIVATE);

        // Init views
        switchAuto = findViewById(R.id.switchAuto);
        switchBootloader = findViewById(R.id.switchBootloader);
        switchPIF = findViewById(R.id.switchPIF);
        switchProvider = findViewById(R.id.switchProvider);
        switchDebug = findViewById(R.id.switchDebug);
        tvStatus = findViewById(R.id.tvStatus);
        tvLastUpdate = findViewById(R.id.tvLastUpdate);
        panelManual = findViewById(R.id.panelManual);
        etPifEditor = findViewById(R.id.etPifEditor);
        btnUpdate = findViewById(R.id.btnUpdate);
        btnApplyManual = findViewById(R.id.btnApplyManual);

        boolean isAuto = sp.getBoolean("auto", true);
        switchAuto.setChecked(isAuto);
        updateUIState(isAuto);

        // Load persist states
        switchBootloader.setChecked(SystemPropertiesHelper.getBoolean(PROP_BOOTLOADER, true));
        switchPIF.setChecked(SystemPropertiesHelper.getBoolean(PROP_PIF, true));
        switchProvider.setChecked(SystemPropertiesHelper.getBoolean(PROP_USE_CUSTOM, false));
        switchDebug.setChecked(SystemPropertiesHelper.getBoolean(PROP_DEBUG, false));

        // Switch Listeners
        switchAuto.setOnCheckedChangeListener((v, checked) -> {
            sp.edit().putBoolean("auto", checked).apply();
            updateUIState(checked);
        });

        switchBootloader.setOnCheckedChangeListener((v, checked) -> {
            SystemPropertiesHelper.set(PROP_BOOTLOADER, checked ? "true" : "false");
            setStatus("Bootloader: " + (checked ? "ON" : "OFF"));
        });

        switchPIF.setOnCheckedChangeListener((v, checked) -> {
            SystemPropertiesHelper.set(PROP_PIF, checked ? "true" : "false");
            setStatus("PIF: " + (checked ? "ON" : "OFF"));
        });

        switchProvider.setOnCheckedChangeListener((v, checked) -> {
            SystemPropertiesHelper.set(PROP_USE_CUSTOM, checked ? "true" : "false");
            setStatus("Custom: " + (checked ? "ON" : "OFF"));
            if (checked) forceReloadPIF();
        });

        switchDebug.setOnCheckedChangeListener((v, checked) -> {
            SystemPropertiesHelper.set(PROP_DEBUG, checked ? "true" : "false");
            setStatus("Debug: " + (checked ? "ON" : "OFF"));
        });

        // Buttons
        btnUpdate.setOnClickListener(v -> new Thread(() -> checkUpdateOnline(true)).start());

        findViewById(R.id.btnLoadGameProps).setOnClickListener(v -> pickFile(201));
        findViewById(R.id.btnLoadThermals).setOnClickListener(v -> pickFile(202));
        findViewById(R.id.btnPickKeybox).setOnClickListener(v -> pickFile(101));
        findViewById(R.id.btnPickPIF).setOnClickListener(v -> pickFile(102));
        btnApplyManual.setOnClickListener(v -> applyManualPIF());
        findViewById(R.id.btnResetPersist).setOnClickListener(v -> resetPersistProps());

        loadCustomPIF();

        new Thread(() -> {
            createDirectories();
            applyFallback();
            if (sp.getBoolean("auto", true)) {
                checkUpdateOnline(false);
            }
        }).start();
    }

    private void updateUIState(boolean isAuto) {
        panelManual.setVisibility(isAuto ? View.GONE : View.VISIBLE);
        btnUpdate.setVisibility(isAuto ? View.VISIBLE : View.GONE);
        String lastUpdate = sp.getString("last_update", "-");
        tvLastUpdate.setText("Last update: " + lastUpdate);
        setStatus(isAuto ? "Mode: Auto" : "Mode: Manual");
    }

    private void setStatus(String msg) {
        runOnUiThread(() -> tvStatus.setText("Status: " + msg));
    }

    private void loadCustomPIF() {
        String content = readFile(CUST_PIF_PATH);
        if (content != null && !content.isEmpty()) {
            etPifEditor.setText(content);
        } else {
            etPifEditor.setText("# No custom PIF loaded");
        }
    }

    private void createDirectories() {
        new File(DIR).mkdirs();
        new File(LOCAL_DIR).mkdirs();
    }

    private void applyFallback() {
        try {
            if (!new File(KB_PATH).exists()) {
                String data = readRaw(R.raw.default_keybox);
                if (!data.isEmpty()) write(KB_PATH, data);
            }
            if (!new File(PIF_PATH).exists()) {
                String data = readRaw(R.raw.default_pif);
                if (!data.isEmpty()) write(PIF_PATH, data);
            }
            if (!new File(GAMEPROPS_PATH).exists()) {
                String data = readRaw(R.raw.default_gameprops);
                if (!data.isEmpty()) write(GAMEPROPS_PATH, data);
            }
            if (!new File(THERMALS_PATH).exists()) {
                String data = readRaw(R.raw.default_thermals);
                if (!data.isEmpty()) write(THERMALS_PATH, data);
            }
        } catch (Exception e) {
            Log.e(TAG, "Fallback error: " + e.getMessage());
        }
    }

    private void checkUpdateOnline(boolean showToast) {
        try {
            String newKb = fetch(URL_KB);
            String newPif = fetch(URL_PIF);
            String newGame = fetch(URL_GAMEPROPS);
            String newTherm = fetch(URL_THERMALS);

            boolean updated = false;

            if (newKb != null && !newKb.isEmpty() && !newKb.equals(readFile(KB_PATH))) {
                write(KB_PATH, newKb); updated = true;
            }
            if (newPif != null && !newPif.isEmpty() && !newPif.equals(readFile(PIF_PATH))) {
                write(PIF_PATH, newPif); updated = true;
            }
            if (newGame != null && !newGame.isEmpty() && !newGame.equals(readFile(GAMEPROPS_PATH))) {
                write(GAMEPROPS_PATH, newGame); updated = true;
            }
            if (newTherm != null && !newTherm.isEmpty() && !newTherm.equals(readFile(THERMALS_PATH))) {
                write(THERMALS_PATH, newTherm); updated = true;
            }

            String date = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new Date());
            sp.edit().putString("last_update", date).apply();

            runOnUiThread(() -> {
                tvLastUpdate.setText("Last update: " + date);
                if (showToast) {
                    Toast.makeText(this, updated ? "Files updated!" : "Already latest.", Toast.LENGTH_SHORT).show();
                }
                setStatus("Update completed");
            });

            if (updated) killGMSAndVending();

        } catch (Exception e) {
            Log.e(TAG, "Update error: " + e.getMessage());
            runOnUiThread(() -> {
                if (showToast) Toast.makeText(this, "Update failed", Toast.LENGTH_SHORT).show();
                setStatus("Update failed");
            });
        }
    }

    private void applyManualPIF() {
        String content = etPifEditor.getText().toString().trim();
        if (content.isEmpty() || content.startsWith("# No custom")) {
            Toast.makeText(this, "No content to apply", Toast.LENGTH_SHORT).show();
            return;
        }

        if (content.trim().startsWith("{")) {
            content = convertJsonToProp(content);
            etPifEditor.setText(content);
        }

        write(CUST_PIF_PATH, content);
        Toast.makeText(this, "Custom PIF applied!", Toast.LENGTH_SHORT).show();
        setStatus("Manual PIF applied");
        if (switchProvider.isChecked()) forceReloadPIF();
        killGMSAndVending();
    }

    private String convertJsonToProp(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            String[] keys = {"MANUFACTURER", "MODEL", "FINGERPRINT", "BRAND", "PRODUCT", "DEVICE", "SECURITY_PATCH", "DEVICE_INITIAL_SDK_INT"};
            StringBuilder result = new StringBuilder();
            for (String key : keys) {
                if (obj.has(key)) {
                    result.append(key).append("=").append(obj.getString(key)).append("\n");
                }
            }
            if (obj.has("spoofVendingSdk")) {
                result.append("spoofVendingSdk=").append(obj.getString("spoofVendingSdk")).append("\n");
            }
            return result.toString();
        } catch (Exception e) {
            return json;
        }
    }

    private void forceReloadPIF() {
        File f = new File(CUST_PIF_PATH);
        if (f.exists()) f.setLastModified(System.currentTimeMillis());
        setStatus("Custom PIF reloaded");
    }

    private void pickFile(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res == Activity.RESULT_OK && data != null && data.getData() != null) {
            new Thread(() -> {
                String content = readUri(data.getData());
                if (content.isEmpty()) return;

                if (req == 101) {
                    write(CUST_KB_PATH, content);
                    runOnUiThread(() -> Toast.makeText(this, "Custom Keybox saved", Toast.LENGTH_SHORT).show());
                } else if (req == 102) {
                    if (content.trim().startsWith("{")) content = convertJsonToProp(content);
                    write(CUST_PIF_PATH, content);
                    runOnUiThread(() -> {
                        etPifEditor.setText(content);
                        Toast.makeText(this, "Custom PIF saved", Toast.LENGTH_SHORT).show();
                        if (switchProvider.isChecked()) forceReloadPIF();
                    });
                    killGMSAndVending();
                } else if (req == 201) {
                    write(GAMEPROPS_PATH, content);
                    runOnUiThread(() -> Toast.makeText(this, "GameProps saved", Toast.LENGTH_SHORT).show());
                } else if (req == 202) {
                    write(THERMALS_PATH, content);
                    runOnUiThread(() -> Toast.makeText(this, "Thermals saved", Toast.LENGTH_SHORT).show());
                }
            }).start();
        }
    }

    private void resetPersistProps() {
        SystemPropertiesHelper.set(PROP_BOOTLOADER, "true");
        SystemPropertiesHelper.set(PROP_PIF, "true");
        SystemPropertiesHelper.set(PROP_USE_CUSTOM, "false");
        SystemPropertiesHelper.set(PROP_DEBUG, "false");

        runOnUiThread(() -> {
            switchBootloader.setChecked(true);
            switchPIF.setChecked(true);
            switchProvider.setChecked(false);
            switchDebug.setChecked(false);
            Toast.makeText(this, "Persist props reset", Toast.LENGTH_SHORT).show();
            setStatus("Persist reset");
        });
    }

    private void killGMSAndVending() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            try {
                Method forceStop = ActivityManager.class.getDeclaredMethod("forceStopPackage", String.class);
                forceStop.setAccessible(true);
                forceStop.invoke(am, "com.google.android.gms");
                forceStop.invoke(am, "com.android.vending");
                Log.d(TAG, "GMS & Vending killed");
            } catch (Exception e) {
                am.killBackgroundProcesses("com.google.android.gms");
                am.killBackgroundProcesses("com.android.vending");
            }
        } catch (Exception e) {
            Log.e(TAG, "Kill error: " + e.getMessage());
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
            Log.d(TAG, "Written: " + path);
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

    private String readRaw(int rawResId) {
        try {
            InputStream is = getResources().openRawResource(rawResId);
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

    private String readUri(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
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
