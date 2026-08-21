package com.uwuh.pif;

import android.annotation.SuppressLint;
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
import android.text.method.ScrollingMovementMethod;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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
import java.util.Iterator;
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
    private static final String PROP_THERMALS = "persist.sys.oemports10t.utils.perapp_thermals";
    private static final String PROP_GAMEPROPS = "persist.sys.oemports10t.utils.gameprops";
    
    // ==================== LOG VIEWER ====================
    private static final int MAX_LOG_LINES = 20;
    private LinkedList<String> logLines = new LinkedList<>();
    private EditText tvLog;
    private Button btnCopyLog;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private SharedPreferences sp;
    private Switch switchManual, switchBootloader, switchPIF;
    private Switch switchGameProps, switchThermals;
    private TextView tvLastUpdate, tvAutoStatus;
    private TextView tvKeyboxLastApply, tvPifLastApply;
    private LinearLayout panelManual, panelGamePropsBtn, panelThermalsBtn;
    private EditText etPifEditor, etGameProps, etThermals;
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
        switchGameProps = findViewById(R.id.switchGameProps);
        switchThermals = findViewById(R.id.switchThermals);
        tvLastUpdate = findViewById(R.id.tvLastUpdate);
        tvAutoStatus = findViewById(R.id.tvAutoStatus);
        tvKeyboxLastApply = findViewById(R.id.tvKeyboxLastApply);
        tvPifLastApply = findViewById(R.id.tvPifLastApply);
        panelManual = findViewById(R.id.panelManual);
        panelGamePropsBtn = findViewById(R.id.panelGamePropsBtn);
        panelThermalsBtn = findViewById(R.id.panelThermalsBtn);
        etPifEditor = findViewById(R.id.etPifEditor);
        etGameProps = findViewById(R.id.etGameProps);
        etThermals = findViewById(R.id.etThermals);
        btnUpdate = findViewById(R.id.btnUpdate);
        btnApplyManual = findViewById(R.id.btnApplyManual);
        btnGameProps = findViewById(R.id.btnGameProps);
        btnThermals = findViewById(R.id.btnThermals);
        btnResetGameProps = findViewById(R.id.btnResetGameProps);
        btnResetThermals = findViewById(R.id.btnResetThermals);

        // Log viewer - EditText (Read-Only)
        tvLog = findViewById(R.id.tvLog);
        btnCopyLog = findViewById(R.id.btnCopyLog);

        tvLog.setFocusable(false);
        tvLog.setFocusableInTouchMode(false);
        tvLog.setLongClickable(false);
        tvLog.setKeyListener(null);
        tvLog.setMovementMethod(new ScrollingMovementMethod());

        enableInnerScroll(etPifEditor);
        enableInnerScroll(etGameProps);
        enableInnerScroll(etThermals);
        enableInnerScroll(tvLog);

        // Load states
        boolean isManual = sp.getBoolean("manual", false);
        switchManual.setChecked(isManual);
        updateUIState(isManual);

        // Load persist states
        switchBootloader.setChecked(SystemPropertiesHelper.getBoolean(PROP_BOOTLOADER, true));
        switchPIF.setChecked(SystemPropertiesHelper.getBoolean(PROP_PIF, true));
        switchGameProps.setChecked(SystemPropertiesHelper.getBoolean(PROP_GAMEPROPS, false));
        switchThermals.setChecked(SystemPropertiesHelper.getBoolean(PROP_THERMALS, false));

        // Load last apply times
        tvKeyboxLastApply.setText("Last apply: " + sp.getString("keybox_last_apply", "-"));
        tvPifLastApply.setText("Last apply: " + sp.getString("pif_last_apply", "-"));

        // Load editors
        btnGameProps.setEnabled(false);
        btnThermals.setEnabled(false);
        loadGameProps();
        loadThermals();
        loadCustomPIF();

        // ==================== SWITCH LISTENERS ====================
        
        switchManual.setOnCheckedChangeListener((v, checked) -> {
            sp.edit().putBoolean("manual", checked).apply();
            SystemPropertiesHelper.set(PROP_USE_CUSTOM, checked ? "true" : "false");
            updateUIState(checked);
            addLog("Mode: " + (checked ? "Manual" : "Auto"));
            if (checked) forceReloadPIF();
        });

        switchBootloader.setOnCheckedChangeListener((v, checked) -> {
            SystemPropertiesHelper.set(PROP_BOOTLOADER, checked ? "true" : "false");
            addLog("Bootloader spoof: " + (checked ? "ON" : "OFF"));
        });

        switchPIF.setOnCheckedChangeListener((v, checked) -> {
            SystemPropertiesHelper.set(PROP_PIF, checked ? "true" : "false");
            addLog("PIF spoof: " + (checked ? "ON" : "OFF"));
        });

        switchGameProps.setOnCheckedChangeListener((v, checked) -> {
            SystemPropertiesHelper.set(PROP_GAMEPROPS, checked ? "true" : "false");
            int vis = checked ? View.VISIBLE : View.GONE;
            panelGamePropsBtn.setVisibility(vis);
            if (checked) {
                loadGameProps();
                btnGameProps.setText("Edit");
                gamePropsVisible = false;
                etGameProps.setVisibility(View.GONE);
                addLog("GameProps enabled");
            } else {
                etGameProps.setVisibility(View.GONE);
                gamePropsVisible = false;
                btnGameProps.setText("Edit");
                addLog("GameProps disabled");
            }
        });

        switchThermals.setOnCheckedChangeListener((v, checked) -> {
            SystemPropertiesHelper.set(PROP_THERMALS, checked ? "true" : "false");
            int vis = checked ? View.VISIBLE : View.GONE;
            panelThermalsBtn.setVisibility(vis);
            if (checked) {
                loadThermals();
                btnThermals.setText("Edit");
                thermalsVisible = false;
                etThermals.setVisibility(View.GONE);
                addLog("Thermals enabled");
            } else {
                etThermals.setVisibility(View.GONE);
                thermalsVisible = false;
                btnThermals.setText("Edit");
                addLog("Thermals disabled");
            }
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

    @SuppressLint("ClickableViewAccessibility")
    private void enableInnerScroll(EditText editText) {
        editText.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            if ((event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP ||
                (event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_CANCEL) {
                v.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });
    }

    // ==================== VALIDATION & CONVERSION ====================

    private boolean validateProp(String content) {
        if (content == null || content.trim().isEmpty()) return false;

        String[] lines = content.split("\n");

        boolean hasManufacturer = false;
        boolean hasModel = false;
        boolean hasFingerprint = false;
        boolean hasProduct = false;
        boolean hasDevice = false;

        for (String line : lines) {
            line = line.trim();

            if (line.startsWith("#") || line.isEmpty()) continue;

            if (line.contains("=") || line.contains(":")) {
                String delimiter = line.contains("=") ? "=" : ":";
                int index = line.indexOf(delimiter);

                String key = line.substring(0, index).trim();
                String value = line.substring(index + 1).trim();

                if (!value.isEmpty()) {
                    switch (key) {
                        case "MANUFACTURER":
                            hasManufacturer = true;
                            break;
                        case "MODEL":
                            hasModel = true;
                            break;
                        case "FINGERPRINT":
                            hasFingerprint = true;
                            break;
                        case "PRODUCT":
                            hasProduct = true;
                            break;
                        case "DEVICE":
                            hasDevice = true;
                            break;
                    }
                }
            }
        }

        return hasManufacturer && hasModel && hasFingerprint && hasProduct && hasDevice;
    }

    private String processOptionalProps(String content) {
        if (content == null || content.trim().isEmpty()) return content;

        String[] lines = content.split("\n");
        String manufacturer = "";
        boolean hasBrand = false;
        boolean hasSecurityPatch = false;
        boolean hasInitialSdk = false;
        boolean hasSpoofVendingSdk = false;

        // Iterasi awal untuk analisis keberadaan key
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            // Periksa key bahkan jika sedang di-comment (#)
            String cleanLine = trimmed.startsWith("#") ? trimmed.substring(1).trim() : trimmed;

            if (cleanLine.contains("=") || cleanLine.contains(":")) {
                String delimiter = cleanLine.contains("=") ? "=" : ":";
                int idx = cleanLine.indexOf(delimiter);
                String key = cleanLine.substring(0, idx).trim();
                String val = cleanLine.substring(idx + 1).trim();

                if (key.equalsIgnoreCase("MANUFACTURER") && !val.isEmpty()) {
                    manufacturer = val;
                } else if (key.equalsIgnoreCase("BRAND")) {
                    hasBrand = true;
                } else if (key.equalsIgnoreCase("SECURITY_PATCH")) {
                    hasSecurityPatch = true;
                } else if (key.equalsIgnoreCase("DEVICE_INITIAL_SDK_INT")) {
                    hasInitialSdk = true;
                } else if (key.equalsIgnoreCase("spoofVendingSdk")) {
                    hasSpoofVendingSdk = true;
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line).append("\n");
        }

        // 1. BRAND: Jika belum ada, samakan nilainya dengan MANUFACTURER
        if (!hasBrand && !manufacturer.isEmpty()) {
            sb.append("BRAND=").append(manufacturer).append("\n");
            addLog("BRAND auto-set to " + manufacturer);
        }

        // 2. Tambahkan key opsional lain dengan #(disable) jika belum ada di file
        if (!hasSecurityPatch) {
            sb.append("#SECURITY_PATCH=\n");
        }
        if (!hasInitialSdk) {
            sb.append("#DEVICE_INITIAL_SDK_INT=\n");
        }
        if (!hasSpoofVendingSdk) {
            sb.append("#spoofVendingSdk=true\n");
        }

        return sb.toString().trim();
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
    }

    // ==================== EDITOR TOGGLES ====================

    private void toggleGameProps() {
        if (!switchGameProps.isChecked()) return;
        
        gamePropsVisible = !gamePropsVisible;
        etGameProps.setVisibility(gamePropsVisible ? View.VISIBLE : View.GONE);
        btnGameProps.setText(gamePropsVisible ? "Apply" : "Edit");
        if (gamePropsVisible) {
            loadGameProps();
            updateApplyButton(btnGameProps, etGameProps, true);
        } else {
            saveGameProps();
        }
    }

    private void toggleThermals() {
        if (!switchThermals.isChecked()) return;
        
        thermalsVisible = !thermalsVisible;
        etThermals.setVisibility(thermalsVisible ? View.VISIBLE : View.GONE);
        btnThermals.setText(thermalsVisible ? "Apply" : "Edit");
        if (thermalsVisible) {
            loadThermals();
            updateApplyButton(btnThermals, etThermals, true);
        } else {
            saveThermals();
        }
    }

    // ==================== EDITOR METHODS ====================

    private void loadGameProps() {
        String content = readFile(GAMEPROPS_PATH);
        if (content != null && !content.isEmpty()) {
            etGameProps.setText(content);
        } else {
            etGameProps.setText("{\n    \"device1\": {\n        \"PKGNAMES\": [],\n        \"MANUFACTURER\": \"\",\n        \"MODEL\": \"\"\n    }\n}");
        }
        updateApplyButton(btnGameProps, etGameProps, true);
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
            }
        } else {
            Toast.makeText(this, "Invalid JSON format", Toast.LENGTH_SHORT).show();
            addLog("GameProps: Invalid JSON");
        }
        updateApplyButton(btnGameProps, etGameProps, true);
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
        } else {
            etThermals.setText("{\n    \"DEFAULT\": \"0\",\n    \"1\": {\n        \"PKGNAMES\": [],\n        \"THERMAL_PROFILE\": \"\"\n    }\n}");
        }
        updateApplyButton(btnThermals, etThermals, true);
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
            }
        } else {
            Toast.makeText(this, "Invalid JSON format", Toast.LENGTH_SHORT).show();
            addLog("Thermals: Invalid JSON");
        }
        updateApplyButton(btnThermals, etThermals, true);
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

    private String removePath(String msg) {
        msg = msg.replaceAll("/data/[^\\s]+", "");
        msg = msg.replaceAll("/odm/[^\\s]+", "");
        msg = msg.replaceAll("/vendor/[^\\s]+", "");
        msg = msg.replaceAll("/system/[^\\s]+", "");
        return msg;
    }

    private void addLog(String msg) {
        msg = removePath(msg);
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
        });
    }

    private void copyLog() {
        String logText = tvLog.getText().toString();
        if (logText != null && !logText.isEmpty() && !logText.equals("[Log ready]")) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Debug Log", logText);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Log copied", Toast.LENGTH_SHORT).show();
            addLog("Log copied");
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
        }

        content = processOptionalProps(content);
        etPifEditor.setText(content);

        if (writeFile(CUST_PIF_PATH, content)) {
            String time = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new Date());
            sp.edit().putString("pif_last_apply", time).apply();
            tvPifLastApply.setText("Last apply: " + time);
            
            addLog("Manual PIF applied");
            Toast.makeText(this, "Custom PIF applied!", Toast.LENGTH_SHORT).show();
            if (switchManual.isChecked()) forceReloadPIF();
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
            StringBuilder result = new StringBuilder();

            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String val = obj.optString(key, "").trim();
                if (!val.isEmpty()) {
                    result.append(key).append("=").append(val).append("\n");
                }
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
                    content = processOptionalProps(content);
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
                                if (switchManual.isChecked()) forceReloadPIF();
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
        SystemPropertiesHelper.set(PROP_THERMALS, "false");
        SystemPropertiesHelper.set(PROP_GAMEPROPS, "false");

        addLog("Configuration reset");
        runOnUiThread(() -> {
            switchBootloader.setChecked(true);
            switchPIF.setChecked(true);
            switchManual.setChecked(false);
            switchGameProps.setChecked(false);
            switchThermals.setChecked(false);
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

    private boolean writeFile(String path, String content) {
        try {
            File dir = new File(path).getParentFile();
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    addLog("Cannot create dir");
                    return false;
                }
            }
            FileOutputStream f = new FileOutputStream(path);
            f.write(content.getBytes());
            f.close();
            return true;
        } catch (Exception e) {
            addLog("Write error");
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
