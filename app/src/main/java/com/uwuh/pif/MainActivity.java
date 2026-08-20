package com.uwuh.pif;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
import java.util.LinkedList;
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
    
    // ==================== LOG VIEWER ====================
    private static final int MAX_LOG_LINES = 10;
    private LinkedList<String> logLines = new LinkedList<>();
    private TextView tvLog;
    private ScrollView logScrollView;
    private TextView tvLogTitle;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isDebugMode = false;

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
        switchDebug = findViewById(R.id.switchDebug);  // ← SEKARANG DI BAWAH
        tvStatus = findViewById(R.id.tvStatus);
        tvLastUpdate = findViewById(R.id.tvLastUpdate);
        panelManual = findViewById(R.id.panelManual);
        etPifEditor = findViewById(R.id.etPifEditor);
        btnUpdate = findViewById(R.id.btnUpdate);
        btnApplyManual = findViewById(R.id.btnApplyManual);

        // Log viewer
        tvLog = findViewById(R.id.tvLog);
        tvLogTitle = findViewById(R.id.tvLogTitle);
        logScrollView = findViewById(R.id.logScrollView);

        boolean isAuto = sp.getBoolean("auto", true);
        switchAuto.setChecked(isAuto);
        updateUIState(isAuto);

        // Load persist states
        switchBootloader.setChecked(SystemPropertiesHelper.getBoolean(PROP_BOOTLOADER, true));
        switchPIF.setChecked(SystemPropertiesHelper.getBoolean(PROP_PIF, true));
        switchProvider.setChecked(SystemPropertiesHelper.getBoolean(PROP_USE_CUSTOM, false));
        
        // Debug mode (sekarang di bawah)
        isDebugMode = SystemPropertiesHelper.getBoolean(PROP_DEBUG, false);
        switchDebug.setChecked(isDebugMode);
        updateDebugUI(isDebugMode);

        // ==================== SWITCH LISTENERS ====================
        
        switchAuto.setOnCheckedChangeListener((v, checked) -> {
            sp.edit().putBoolean("auto", checked).apply();
            updateUIState(checked);
            addLog("Auto mode: " + (checked ? "ON" : "OFF"));
        });

        switchBootloader.setOnCheckedChangeListener((v, checked) -> {
            SystemPropertiesHelper.set(PROP_BOOTLOADER, checked ? "true" : "false");
            setStatus("Bootloader: " + (checked ? "ON" : "OFF"));
            addLog("Bootloader spoof: " + (checked ? "ON" : "OFF"));
        });

        switchPIF.setOnCheckedChangeListener((v, checked) -> {
            SystemPropertiesHelper.set(PROP_PIF, checked ? "true" : "false");
            setStatus("PIF: " + (checked ? "ON" : "OFF"));
            addLog("PIF spoof: " + (checked ? "ON" : "OFF"));
        });

        switchProvider.setOnCheckedChangeListener((v, checked) -> {
            SystemPropertiesHelper.set(PROP_USE_CUSTOM, checked ? "true" : "false");
            setStatus("Custom: " + (checked ? "ON" : "OFF"));
            addLog("Custom provider: " + (checked ? "ON" : "OFF"));
            if (checked) forceReloadPIF();
        });

        // ==================== SWITCH DEBUG (PALING BAWAH) ====================
        switchDebug.setOnCheckedChangeListener((v, checked) -> {
            SystemPropertiesHelper.set(PROP_DEBUG, checked ? "true" : "false");
            isDebugMode = checked;
            updateDebugUI(checked);
            addLog("Debug mode: " + (checked ? "ON" : "OFF"));
            setStatus("Debug: " + (checked ? "ON" : "OFF"));
        });

        // ==================== BUTTON LISTENERS ====================

        btnUpdate.setOnClickListener(v -> new Thread(() -> {
            addLog("Checking updates...");
            checkUpdateOnline(true);
        }).start());

        findViewById(R.id.btnLoadGameProps).setOnClickListener(v -> {
            addLog("Loading GameProps...");
            pickFile(201);
        });
        
        findViewById(R.id.btnLoadThermals).setOnClickListener(v -> {
            addLog("Loading Thermals...");
            pickFile(202);
        });
        
        findViewById(R.id.btnPickKeybox).setOnClickListener(v -> {
            addLog("Picking Keybox...");
            pickFile(101);
        });
        
        findViewById(R.id.btnPickPIF).setOnClickListener(v -> {
            addLog("Picking PIF...");
            pickFile(102);
        });
        
        btnApplyManual.setOnClickListener(v -> {
            addLog("Applying manual PIF...");
            applyManualPIF();
        });
        
        findViewById(R.id.btnResetPersist).setOnClickListener(v -> {
            addLog("Resetting persist props...");
            resetPersistProps();
        });

        loadCustomPIF();

        new Thread(() -> {
            createDirectories();
            applyFallback();
            addLog("Fallback applied");
            if (sp.getBoolean("auto", true)) {
                addLog("Auto update checking...");
                checkUpdateOnline(false);
            }
        }).start();
    }

    // ==================== LOG VIEWER METHODS ====================

    private void addLog(String msg) {
        if (!isDebugMode) return;
        
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        String logMsg = "[" + timestamp + "] " + msg;
        
        if (logLines.size() >= MAX_LOG_LINES) {
            logLines.removeFirst();
        }
        logLines.add(logMsg);
        
        mainHandler.post(() -> {
            StringBuilder sb = new StringBuilder();
            for (String line : logLines) {
                sb.append(line).append("\n");
            }
            tvLog.setText(sb.toString());
            logScrollView.post(() -> logScrollView.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }

    private void updateDebugUI(boolean enabled) {
        int visibility = enabled ? View.VISIBLE : View.GONE;
        tvLogTitle.setVisibility(visibility);
        logScrollView.setVisibility(visibility);
        if (enabled) {
            addLog("Debug mode enabled");
        } else {
            logLines.clear();
            tvLog.setText("[Log disabled]");
        }
    }

    // ==================== UI HELPERS ====================

    private void updateUIState(boolean isAuto) {
        panelManual.setVisibility(isAuto ? View.GONE : View.VISIBLE);
        btnUpdate.setVisibility(isAuto ? View.VISIBLE : View.GONE);
        String lastUpdate = sp.getString("last_update", "-");
        tvLastUpdate.setText("Last update: " + lastUpdate);
        setStatus(isAuto ? "Mode: Auto" : "Mode: Manual");
        addLog("UI mode: " + (isAuto ? "Auto" : "Manual"));
    }

    private void setStatus(String msg) {
        runOnUiThread(() -> tvStatus.setText("Status: " + msg));
    }

    private void loadCustomPIF() {
        String content = readFile(CUST_PIF_PATH);
        if (content != null && !content.isEmpty()) {
            etPifEditor.setText(content);
            addLog("Custom PIF loaded from file");
        } else {
            etPifEditor.setText("# No custom PIF loaded");
        }
    }

    private void createDirectories() {
        new File(DIR).mkdirs();
        new File(LOCAL_DIR).mkdirs();
        addLog("Directories created");
    }

    private void applyFallback() {
        try {
            if (!new File(KB_PATH).exists()) {
                String data = readRaw(R.raw.default_keybox);
                if (!data.isEmpty()) {
                    write(KB_PATH, data);
                    addLog("Keybox fallback applied");
                }
            }
            if (!new File(PIF_PATH).exists()) {
                String data = readRaw(R.raw.default_pif);
                if (!data.isEmpty()) {
                    write(PIF_PATH, data);
                    addLog("PIF fallback applied");
                }
            }
            if (!new File(GAMEPROPS_PATH).exists()) {
                String data = readRaw(R.raw.default_gameprops);
                if (!data.isEmpty()) {
                    write(GAMEPROPS_PATH, data);
                    addLog("GameProps fallback applied");
                }
            }
            if (!new File(THERMALS_PATH).exists()) {
                String data = readRaw(R.raw.default_thermals);
                if (!data.isEmpty()) {
                    write(THERMALS_PATH, data);
                    addLog("Thermals fallback applied");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Fallback error: " + e.getMessage());
            addLog("Fallback error: " + e.getMessage());
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
                write(KB_PATH, newKb);
                updated = true;
                addLog("Keybox updated from GitHub");
            }
            if (newPif != null && !newPif.isEmpty() && !newPif.equals(readFile(PIF_PATH))) {
                write(PIF_PATH, newPif);
                updated = true;
                addLog("PIF updated from GitHub");
            }
            if (newGame != null && !newGame.isEmpty() && !newGame.equals(readFile(GAMEPROPS_PATH))) {
                write(GAMEPROPS_PATH, newGame);
                updated = true;
                addLog("GameProps updated from GitHub");
            }
            if (newTherm != null && !newTherm.isEmpty() && !newTherm.equals(readFile(THERMALS_PATH))) {
                write(THERMALS_PATH, newTherm);
                updated = true;
                addLog("Thermals updated from GitHub");
            }

            String date = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new Date());
            sp.edit().putString("last_update", date).apply();

            final boolean isUpdated = updated;
            runOnUiThread(() -> {
                tvLastUpdate.setText("Last update: " + date);
                if (showToast) {
                    Toast.makeText(MainActivity.this, isUpdated ? "Files updated!" : "Already latest.", Toast.LENGTH_SHORT).show();
                }
                setStatus("Update completed");
            });

            if (updated) {
                addLog("Updates applied, killing GMS & Vending");
                killGMSAndVending();
            } else {
                addLog("No updates available");
            }

        } catch (Exception e) {
            Log.e(TAG, "Update error: " + e.getMessage());
            addLog("Update error: " + e.getMessage());
            runOnUiThread(() -> {
                if (showToast) Toast.makeText(MainActivity.this, "Update failed", Toast.LENGTH_SHORT).show();
                setStatus("Update failed");
            });
        }
    }

    private void applyManualPIF() {
        String content = etPifEditor.getText().toString().trim();
        if (content.isEmpty() || content.startsWith("# No custom")) {
            Toast.makeText(this, "No content to apply", Toast.LENGTH_SHORT).show();
            addLog("Manual PIF: No content");
            return;
        }

        if (content.trim().startsWith("{")) {
            content = convertJsonToProp(content);
            etPifEditor.setText(content);
            addLog("Manual PIF: JSON converted to PROP");
        }

        write(CUST_PIF_PATH, content);
        addLog("Manual PIF saved to " + CUST_PIF_PATH);
        Toast.makeText(this, "Custom PIF applied!", Toast.LENGTH_SHORT).show();
        setStatus("Manual PIF applied");
        if (switchProvider.isChecked()) forceReloadPIF();
        killGMSAndVending();
        addLog("Manual PIF applied, GMS & Vending killed");
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
        if (f.exists()) {
            f.setLastModified(System.currentTimeMillis());
            addLog("Custom PIF force reloaded");
        }
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
                if (content.isEmpty()) {
                    addLog("File pick: Empty content");
                    return;
                }

                if (req == 101) {
                    write(CUST_KB_PATH, content);
                    addLog("Custom Keybox saved");
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Custom Keybox saved", Toast.LENGTH_SHORT).show());
                } else if (req == 102) {
                    if (content.trim().startsWith("{")) content = convertJsonToProp(content);
                    final String finalContent = content;
                    write(CUST_PIF_PATH, finalContent);
                    addLog("Custom PIF saved");
                    runOnUiThread(() -> {
                        etPifEditor.setText(finalContent);
                        Toast.makeText(MainActivity.this, "Custom PIF saved", Toast.LENGTH_SHORT).show();
                        if (switchProvider.isChecked()) forceReloadPIF();
                    });
                    killGMSAndVending();
                    addLog("GMS & Vending killed after PIF save");
                } else if (req == 201) {
                    write(GAMEPROPS_PATH, content);
                    addLog("GameProps saved");
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "GameProps saved", Toast.LENGTH_SHORT).show());
                } else if (req == 202) {
                    write(THERMALS_PATH, content);
                    addLog("Thermals saved");
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Thermals saved", Toast.LENGTH_SHORT).show());
                }
            }).start();
        }
    }

    private void resetPersistProps() {
        SystemPropertiesHelper.set(PROP_BOOTLOADER, "true");
        SystemPropertiesHelper.set(PROP_PIF, "true");
        SystemPropertiesHelper.set(PROP_USE_CUSTOM, "false");
        SystemPropertiesHelper.set(PROP_DEBUG, "false");

        addLog("Persist props reset to default");
        runOnUiThread(() -> {
            switchBootloader.setChecked(true);
            switchPIF.setChecked(true);
            switchProvider.setChecked(false);
            switchDebug.setChecked(false);
            Toast.makeText(MainActivity.this, "Persist props reset", Toast.LENGTH_SHORT).show();
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
                addLog("GMS & Vending killed via forceStop");
            } catch (Exception e) {
                am.killBackgroundProcesses("com.google.android.gms");
                am.killBackgroundProcesses("com.android.vending");
                addLog("GMS & Vending killed via killBackgroundProcesses");
            }
        } catch (Exception e) {
            Log.e(TAG, "Kill error: " + e.getMessage());
            addLog("Kill error: " + e.getMessage());
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
            addLog("Write error: " + e.getMessage());
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
