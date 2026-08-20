package com.uwuh.pif;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipboardManager;
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

import org.json.JSONException;
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
    private TextView tvLog, tvLogTitle;
    private ScrollView logScrollView;
    private Button btnCopyLog;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isDebugMode = false;

    private SharedPreferences sp;
    private Switch switchManual, switchBootloader, switchPIF, switchProvider, switchDebug;
    private TextView tvLastUpdate, tvAutoStatus;
    private TextView tvKeyboxLastApply, tvPifLastApply;
    private LinearLayout panelManual;
    private EditText etPifEditor, etGameProps, etThermals;
    private ScrollView scrollGameProps, scrollThermals;
    private Button btnUpdate, btnApplyManual, btnGameProps, btnThermals;
    private Button btnResetGameProps, btnResetThermals;
    private boolean gamePropsVisible = false;
    private boolean thermalsVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sp = getSharedPreferences("pif_prefs", MODE_PRIVATE);

        // Init views
        switchManual = findViewById(R.id.switchManual);
        switchBootloader = findViewById(R.id.switchBootloader);
        switchPIF = findViewById(R.id.switchPIF);
        switchProvider = findViewById(R.id.switchProvider);
        switchDebug = findViewById(R.id.switchDebug);
        tvLastUpdate = findViewById(R.id.tvLastUpdate);
        tvAutoStatus = findViewById(R.id.tvAutoStatus);
        tvKeyboxLastApply = findViewById(R.id.tvKeyboxLastApply);
        tvPifLastApply = findViewById(R.id.tvPifLastApply);
        panelManual = findViewById(R.id.panelManual);
        etPifEditor = findViewById(R.id.etPifEditor);
        etGameProps = findViewById(R.id.etGameProps);
        etThermals = findViewById(R.id.etThermals);
        scrollGameProps = findViewById(R.id.scrollGameProps);
        scrollThermals = findViewById(R.id.scrollThermals);
        btnUpdate = findViewById(R.id.btnUpdate);
        btnApplyManual = findViewById(R.id.btnApplyManual);
        btnGameProps = findViewById(R.id.btnGameProps);
        btnThermals = findViewById(R.id.btnThermals);
        btnResetGameProps = findViewById(R.id.btnResetGameProps);
        btnResetThermals = findViewById(R.id.btnResetThermals);

        // Log viewer
        tvLog = findViewById(R.id.tvLog);
        tvLogTitle = findViewById(R.id.tvLogTitle);
        logScrollView = findViewById(R.id.logScrollView);
        btnCopyLog = findViewById(R.id.btnCopyLog);

        // Load states
        boolean isManual = sp.getBoolean("manual", false);
        switchManual.setChecked(isManual);
        updateUIState(isManual);

        // Load persist states
        switchBootloader.setChecked(SystemPropertiesHelper.getBoolean(PROP_BOOTLOADER, true));
        switchPIF.setChecked(SystemPropertiesHelper.getBoolean(PROP_PIF, true));
        switchProvider.setChecked(SystemPropertiesHelper.getBoolean(PROP_USE_CUSTOM, false));
        
        // Debug mode
        isDebugMode = SystemPropertiesHelper.getBoolean(PROP_DEBUG, false);
        switchDebug.setChecked(isDebugMode);
        updateDebugUI(isDebugMode);

        // Load last apply times
        tvKeyboxLastApply.setText("Last apply: " + sp.getString("keybox_last_apply", "-"));
        tvPifLastApply.setText("Last apply: " + sp.getString("pif_last_apply", "-"));

        // Load editors
        loadGameProps();
        loadThermals();
        loadCustomPIF();

        // ==================== SWITCH LISTENERS ====================
        
        switchManual.setOnCheckedChangeListener((v, checked) -> {
            sp.edit().putBoolean("manual", checked).apply();
            updateUIState(checked);
            addLog("Manual mode: " + (checked ? "ON" : "OFF"));
        });

        switchBootloader.setOnCheckedChangeListener((v, checked) -> {
            SystemPropertiesHelper.set(PROP_BOOTLOADER, checked ? "true" : "false");
            addLog("Bootloader spoof: " + (checked ? "ON" : "OFF"));
        });

        switchPIF.setOnCheckedChangeListener((v, checked) -> {
            SystemPropertiesHelper.set(PROP_PIF, checked ? "true" : "false");
            addLog("PIF spoof: " + (checked ? "ON" : "OFF"));
        });

        switchProvider.setOnCheckedChangeListener((v, checked) -> {
            SystemPropertiesHelper.set(PROP_USE_CUSTOM, checked ? "true" : "false");
            addLog("Custom provider: " + (checked ? "ON" : "OFF"));
            if (checked) forceReloadPIF();
        });

        switchDebug.setOnCheckedChangeListener((v, checked) -> {
            SystemPropertiesHelper.set(PROP_DEBUG, checked ? "true" : "false");
            isDebugMode = checked;
            updateDebugUI(checked);
            addLog("Debug mode: " + (checked ? "ON" : "OFF"));
        });

        // ==================== BUTTON LISTENERS ====================

        btnUpdate.setOnClickListener(v -> new Thread(() -> {
            addLog("Checking updates...");
            checkUpdateOnline(true);
        }).start());

        btnCopyLog.setOnClickListener(v -> copyLog());

        btnGameProps.setOnClickListener(v -> toggleGameProps());
        btnThermals.setOnClickListener(v -> toggleThermals());
        btnResetGameProps.setOnClickListener(v -> resetGameProps());
        btnResetThermals.setOnClickListener(v -> resetThermals());
        
        findViewById(R.id.btnPickKeybox).setOnClickListener(v -> {
            addLog("Picking Keybox...");
            pickFile(101);
        });
        
        findViewById(R.id.btnPickPIF).setOnClickListener(v -> {
            addLog("Picking PIF...");
            pickFile(102);
        });
        
        btnApplyManual.setOnClickListener(v -> applyManualPIF());
        
        findViewById(R.id.btnResetPersist).setOnClickListener(v -> {
            addLog("Resetting configuration...");
            resetPersistProps();
        });

        // ==================== INITIAL BACKGROUND ====================
        new Thread(() -> {
            createDirectories();
            applyFallback();
            addLog("Fallback applied");
            if (!sp.getBoolean("manual", false)) {
                addLog("Auto update checking...");
                checkUpdateOnline(false);
            }
        }).start();
    }

    // ==================== VALIDATION ====================

    private boolean validateProp(String content) {
        if (content == null || content.trim().isEmpty()) return false;
        String[] lines = content.split("\n");
        boolean hasManufacturer = false, hasModel = false, hasFingerprint = false;
        boolean hasBrand = false, hasProduct = false, hasDevice = false;
        boolean hasSecurityPatch = false, hasSdk = false;
        
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("#") || line.isEmpty()) continue;
            if (line.contains("=")) {
                String key = line.substring(0, line.indexOf('=')).trim();
                String value = line.substring(line.indexOf('=') + 1).trim();
                if (key.equals("MANUFACTURER") && !value.isEmpty()) hasManufacturer = true;
                else if (key.equals("MODEL") && !value.isEmpty()) hasModel = true;
                else if (key.equals("FINGERPRINT") && !value.isEmpty()) hasFingerprint = true;
                else if (key.equals("BRAND") && !value.isEmpty()) hasBrand = true;
                else if (key.equals("PRODUCT") && !value.isEmpty()) hasProduct = true;
                else if (key.equals("DEVICE") && !value.isEmpty()) hasDevice = true;
                else if (key.equals("SECURITY_PATCH") && !value.isEmpty()) hasSecurityPatch = true;
                else if (key.equals("DEVICE_INITIAL_SDK_INT") && !value.isEmpty()) hasSdk = true;
            }
        }
        return hasManufacturer && hasModel && hasFingerprint && hasBrand && 
               hasProduct && hasDevice && hasSecurityPatch && hasSdk;
    }

    private boolean validateJson(String content) {
        if (content == null || content.trim().isEmpty()) return false;
        try {
            new JSONObject(content);
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    private void updateApplyButton(Button btn, EditText editor, boolean isJson) {
        String content = editor.getText().toString().trim();
        boolean valid = isJson ? validateJson(content) : validateProp(content);
        btn.setEnabled(valid);
        if (!valid && !content.isEmpty() && !content.startsWith("#") && !content.startsWith("{")) {
            btn.setText("Error: Invalid");
        } else {
            btn.setText(isJson ? "Save" : "APPLY");
        }
    }

    // ==================== EDITOR TOGGLES ====================

    private void toggleGameProps() {
        gamePropsVisible = !gamePropsVisible;
        scrollGameProps.setVisibility(gamePropsVisible ? View.VISIBLE : View.GONE);
        btnGameProps.setText(gamePropsVisible ? "Save GameProps" : "Edit GameProps");
        if (gamePropsVisible) {
            loadGameProps();
        }
    }

    private void toggleThermals() {
        thermalsVisible = !thermalsVisible;
        scrollThermals.setVisibility(thermalsVisible ? View.VISIBLE : View.GONE);
        btnThermals.setText(thermalsVisible ? "Save Thermals" : "Edit Thermals");
        if (thermalsVisible) {
            loadThermals();
        }
    }

    private void loadGameProps() {
        String content = readFile(GAMEPROPS_PATH);
        if (content != null && !content.isEmpty()) {
            etGameProps.setText(content);
            updateApplyButton(btnGameProps, etGameProps, true);
        } else {
            etGameProps.setText("{\n    \"device1\": {\n        \"PKGNAMES\": [],\n        \"MANUFACTURER\": \"\",\n        \"MODEL\": \"\"\n    }\n}");
            btnGameProps.setEnabled(false);
        }
    }

    private void saveGameProps() {
        String content = etGameProps.getText().toString().trim();
        if (validateJson(content)) {
            if (writeFile(GAMEPROPS_PATH, content)) {
                addLog("GameProps saved");
                Toast.makeText(this, "GameProps saved", Toast.LENGTH_SHORT).show();
            } else {
                addLog("GameProps save failed");
                Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show();
                return;
            }
            btnGameProps.setText("Edit GameProps");
            gamePropsVisible = false;
            scrollGameProps.setVisibility(View.GONE);
        } else {
            Toast.makeText(this, "Invalid JSON format", Toast.LENGTH_SHORT).show();
            addLog("GameProps: Invalid JSON");
        }
    }

    private void resetGameProps() {
        String data = readRaw(R.raw.default_gameprops);
        if (!data.isEmpty()) {
            if (writeFile(GAMEPROPS_PATH, data)) {
                etGameProps.setText(data);
                addLog("GameProps reset");
                Toast.makeText(this, "GameProps reset", Toast.LENGTH_SHORT).show();
                updateApplyButton(btnGameProps, etGameProps, true);
            } else {
                addLog("GameProps reset failed");
                Toast.makeText(this, "Reset failed", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadThermals() {
        String content = readFile(THERMALS_PATH);
        if (content != null && !content.isEmpty()) {
            etThermals.setText(content);
            updateApplyButton(btnThermals, etThermals, true);
        } else {
            etThermals.setText("{\n    \"DEFAULT\": \"0\",\n    \"1\": {\n        \"PKGNAMES\": [],\n        \"THERMAL_PROFILE\": \"\"\n    }\n}");
            btnThermals.setEnabled(false);
        }
    }

    private void saveThermals() {
        String content = etThermals.getText().toString().trim();
        if (validateJson(content)) {
            if (writeFile(THERMALS_PATH, content)) {
                addLog("Thermals saved");
                Toast.makeText(this, "Thermals saved", Toast.LENGTH_SHORT).show();
            } else {
                addLog("Thermals save failed");
                Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show();
                return;
            }
            btnThermals.setText("Edit Thermals");
            thermalsVisible = false;
            scrollThermals.setVisibility(View.GONE);
        } else {
            Toast.makeText(this, "Invalid JSON format", Toast.LENGTH_SHORT).show();
            addLog("Thermals: Invalid JSON");
        }
    }

    private void resetThermals() {
        String data = readRaw(R.raw.default_thermals);
        if (!data.isEmpty()) {
            if (writeFile(THERMALS_PATH, data)) {
                etThermals.setText(data);
                addLog("Thermals reset");
                Toast.makeText(this, "Thermals reset", Toast.LENGTH_SHORT).show();
                updateApplyButton(btnThermals, etThermals, true);
            } else {
                addLog("Thermals reset failed");
                Toast.makeText(this, "Reset failed", Toast.LENGTH_SHORT).show();
            }
        }
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

    private void copyLog() {
        String logText = tvLog.getText().toString();
        if (logText != null && !logText.isEmpty() && !logText.equals("[Log ready]") && !logText.equals("[Log disabled]")) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Debug Log", logText);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Log copied", Toast.LENGTH_SHORT).show();
            addLog("Log copied");
        }
    }

    private void updateDebugUI(boolean enabled) {
        int visibility = enabled ? View.VISIBLE : View.GONE;
        tvLogTitle.setVisibility(visibility);
        logScrollView.setVisibility(visibility);
        btnCopyLog.setVisibility(visibility);
        if (enabled) {
            addLog("Debug mode enabled");
        } else {
            logLines.clear();
            tvLog.setText("[Log disabled]");
        }
    }

    // ==================== UI HELPERS ====================

    private void updateUIState(boolean isManual) {
        panelManual.setVisibility(isManual ? View.VISIBLE : View.GONE);
        btnUpdate.setVisibility(isManual ? View.GONE : View.VISIBLE);
        tvAutoStatus.setVisibility(isManual ? View.GONE : View.VISIBLE);
        tvAutoStatus.setText(isManual ? "Auto update: OFF" : "Auto update: ON");
        tvLastUpdate.setVisibility(isManual ? View.GONE : View.VISIBLE);
        String lastUpdate = sp.getString("last_update", "-");
        tvLastUpdate.setText("Last update: " + lastUpdate);
        addLog("Mode: " + (isManual ? "Manual" : "Auto"));
    }

    private void loadCustomPIF() {
        String content = readFile(CUST_PIF_PATH);
        if (content != null && !content.isEmpty()) {
            etPifEditor.setText(content);
            updateApplyButton(btnApplyManual, etPifEditor, false);
            addLog("Custom PIF loaded");
        } else {
            etPifEditor.setText("# No custom PIF loaded");
            btnApplyManual.setEnabled(false);
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
                    writeFile(KB_PATH, data);
                    addLog("Keybox fallback applied");
                }
            }
            if (!new File(PIF_PATH).exists()) {
                String data = readRaw(R.raw.default_pif);
                if (!data.isEmpty()) {
                    writeFile(PIF_PATH, data);
                    addLog("PIF fallback applied");
                }
            }
            if (!new File(GAMEPROPS_PATH).exists()) {
                String data = readRaw(R.raw.default_gameprops);
                if (!data.isEmpty()) {
                    writeFile(GAMEPROPS_PATH, data);
                    addLog("GameProps fallback applied");
                }
            }
            if (!new File(THERMALS_PATH).exists()) {
                String data = readRaw(R.raw.default_thermals);
                if (!data.isEmpty()) {
                    writeFile(THERMALS_PATH, data);
                    addLog("Thermals fallback applied");
                }
            }
        } catch (Exception e) {
            addLog("Fallback error");
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
                writeFile(KB_PATH, newKb);
                updated = true;
                addLog("Keybox updated");
            }
            if (newPif != null && !newPif.isEmpty() && !newPif.equals(readFile(PIF_PATH))) {
                writeFile(PIF_PATH, newPif);
                updated = true;
                addLog("PIF updated");
            }
            if (newGame != null && !newGame.isEmpty() && !newGame.equals(readFile(GAMEPROPS_PATH))) {
                writeFile(GAMEPROPS_PATH, newGame);
                updated = true;
                addLog("GameProps updated");
            }
            if (newTherm != null && !newTherm.isEmpty() && !newTherm.equals(readFile(THERMALS_PATH))) {
                writeFile(THERMALS_PATH, newTherm);
                updated = true;
                addLog("Thermals updated");
            }

            String date = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new Date());
            sp.edit().putString("last_update", date).apply();

            final boolean isUpdated = updated;
            runOnUiThread(() -> {
                tvLastUpdate.setText("Last update: " + date);
                if (showToast) {
                    Toast.makeText(MainActivity.this, isUpdated ? "Files updated!" : "Already latest.", Toast.LENGTH_SHORT).show();
                }
            });

            if (updated) {
                addLog("Updates applied");
                killGMSAndVending();
            } else {
                addLog("No updates");
            }

        } catch (Exception e) {
            addLog("Update error");
            runOnUiThread(() -> {
                if (showToast) Toast.makeText(MainActivity.this, "Update failed", Toast.LENGTH_SHORT).show();
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

        if (!validateProp(content)) {
            Toast.makeText(this, "Invalid PROP format", Toast.LENGTH_SHORT).show();
            addLog("Manual PIF: Invalid format");
            return;
        }

        if (content.trim().startsWith("{")) {
            content = convertJsonToProp(content);
            etPifEditor.setText(content);
            addLog("Manual PIF: JSON converted");
        }

        if (writeFile(CUST_PIF_PATH, content)) {
            String time = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new Date());
            sp.edit().putString("pif_last_apply", time).apply();
            tvPifLastApply.setText("Last apply: " + time);
            
            addLog("Manual PIF applied");
            Toast.makeText(this, "Custom PIF applied!", Toast.LENGTH_SHORT).show();
            if (switchProvider.isChecked()) forceReloadPIF();
            killGMSAndVending();
            addLog("GMS & Vending killed");
        } else {
            addLog("Manual PIF save failed");
            Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show();
        }
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
            addLog("Custom PIF reloaded");
        }
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
                    addLog("File pick: Empty");
                    return;
                }

                if (req == 101) {
                    if (writeFile(CUST_KB_PATH, content)) {
                        String time = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new Date());
                        sp.edit().putString("keybox_last_apply", time).apply();
                        runOnUiThread(() -> {
                            tvKeyboxLastApply.setText("Last apply: " + time);
                            Toast.makeText(MainActivity.this, "Custom Keybox saved", Toast.LENGTH_SHORT).show();
                        });
                        addLog("Custom Keybox saved");
                    } else {
                        addLog("Custom Keybox save failed");
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Save failed", Toast.LENGTH_SHORT).show());
                    }
                } else if (req == 102) {
                    if (content.trim().startsWith("{")) content = convertJsonToProp(content);
                    final String finalContent = content;
                    if (validateProp(finalContent)) {
                        if (writeFile(CUST_PIF_PATH, finalContent)) {
                            String time = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new Date());
                            sp.edit().putString("pif_last_apply", time).apply();
                            runOnUiThread(() -> {
                                tvPifLastApply.setText("Last apply: " + time);
                                etPifEditor.setText(finalContent);
                                updateApplyButton(btnApplyManual, etPifEditor, false);
                                Toast.makeText(MainActivity.this, "Custom PIF saved", Toast.LENGTH_SHORT).show();
                                if (switchProvider.isChecked()) forceReloadPIF();
                            });
                            killGMSAndVending();
                            addLog("Custom PIF saved");
                        } else {
                            addLog("Custom PIF save failed");
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Save failed", Toast.LENGTH_SHORT).show());
                        }
                    } else {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Invalid PIF format", Toast.LENGTH_SHORT).show());
                        addLog("Custom PIF: Invalid format");
                    }
                }
            }).start();
        }
    }

    private void resetPersistProps() {
        SystemPropertiesHelper.set(PROP_BOOTLOADER, "true");
        SystemPropertiesHelper.set(PROP_PIF, "true");
        SystemPropertiesHelper.set(PROP_USE_CUSTOM, "false");
        SystemPropertiesHelper.set(PROP_DEBUG, "false");

        addLog("Configuration reset");
        runOnUiThread(() -> {
            switchBootloader.setChecked(true);
            switchPIF.setChecked(true);
            switchProvider.setChecked(false);
            switchDebug.setChecked(false);
            Toast.makeText(MainActivity.this, "Configuration reset", Toast.LENGTH_SHORT).show();
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
                addLog("GMS & Vending killed");
            } catch (Exception e) {
                am.killBackgroundProcesses("com.google.android.gms");
                am.killBackgroundProcesses("com.android.vending");
                addLog("GMS & Vending killed (bg)");
            }
        } catch (Exception e) {
            addLog("Kill error");
        }
    }

    // ==================== UTILITY METHODS ====================

    // TRUE jika sukses, FALSE jika gagal
    private boolean writeFile(String path, String content) {
        try {
            File dir = new File(path).getParentFile();
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    addLog("Cannot create dir: " + path);
                    return false;
                }
            }
            FileOutputStream f = new FileOutputStream(path);
            f.write(content.getBytes());
            f.close();
            return true;
        } catch (Exception e) {
            addLog("Write error: " + e.getMessage());
            return false;
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
