package com.uwuh.pif;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
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
    private EditText etPifEditor;
    private Button btnUpdate, btnApplyManual, btnGameProps, btnThermals;
    private Button btnResetGameProps, btnResetThermals;

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
        btnUpdate = findViewById(R.id.btnUpdate);
        btnApplyManual = findViewById(R.id.btnApplyManual);
        btnGameProps = findViewById(R.id.btnGameProps);
        btnThermals = findViewById(R.id.btnThermals);
        btnResetGameProps = findViewById(R.id.btnResetGameProps);
        btnResetThermals = findViewById(R.id.btnResetThermals);

        // Log viewer - EditText
        tvLog = findViewById(R.id.tvLog);
        btnCopyLog = findViewById(R.id.btnCopyLog);

        tvLog.setFocusable(false);
        tvLog.setFocusableInTouchMode(false);
        tvLog.setLongClickable(false);
        tvLog.setKeyListener(null);
        tvLog.setMovementMethod(new ScrollingMovementMethod());

        enableInnerScroll(etPifEditor);
        enableInnerScroll(tvLog);

        // Load states
        boolean isManual = sp.getBoolean("manual", false);
        switchManual.setChecked(isManual);
        updateUIState(isManual);

        switchBootloader.setChecked(SystemPropertiesHelper.getBoolean(PROP_BOOTLOADER, true));
        switchPIF.setChecked(SystemPropertiesHelper.getBoolean(PROP_PIF, true));
        switchGameProps.setChecked(SystemPropertiesHelper.getBoolean(PROP_GAMEPROPS, false));
        switchThermals.setChecked(SystemPropertiesHelper.getBoolean(PROP_THERMALS, false));

        tvKeyboxLastApply.setText("Last apply: " + sp.getString("keybox_last_apply", "-"));
        tvPifLastApply.setText("Last apply: " + sp.getString("pif_last_apply", "-"));

        loadCustomPIF();

        // LISTENERS
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
            panelGamePropsBtn.setVisibility(checked ? View.VISIBLE : View.GONE);
            addLog("GameProps " + (checked ? "enabled" : "disabled"));
        });

        switchThermals.setOnCheckedChangeListener((v, checked) -> {
            SystemPropertiesHelper.set(PROP_THERMALS, checked ? "true" : "false");
            panelThermalsBtn.setVisibility(checked ? View.VISIBLE : View.GONE);
            addLog("Thermals " + (checked ? "enabled" : "disabled"));
        });

        btnUpdate.setOnClickListener(v -> new Thread(() -> {
            addLog("Checking updates...");
            checkUpdateOnline(true);
        }).start());

        btnCopyLog.setOnClickListener(v -> copyLog());

        // BUKA GUI POPUP
        btnGameProps.setOnClickListener(v -> showAppConfigDialog(true));
        btnThermals.setOnClickListener(v -> showAppConfigDialog(false));
        
        btnResetGameProps.setOnClickListener(v -> resetGameProps());
        btnResetThermals.setOnClickListener(v -> resetThermals());
        
        findViewById(R.id.btnPickKeybox).setOnClickListener(v -> pickFile(101));
        findViewById(R.id.btnPickPIF).setOnClickListener(v -> pickFile(102));
        btnApplyManual.setOnClickListener(v -> applyManualPIF());
        findViewById(R.id.btnResetPersist).setOnClickListener(v -> resetPersistProps());

        new Thread(() -> {
            createDirectories();
            applyFallback();
            addLog("Fallback applied");
            if (!sp.getBoolean("manual", false)) {
                checkUpdateOnline(false);
            }
        }).start();
    }

    // ==================== GUI POPUP DIALOG LOGIC ====================

    private void showAppConfigDialog(boolean isGameProps) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_app_config, null);
        builder.setView(view);

        TextView tvTitle = view.findViewById(R.id.tvDialogTitle);
        Switch switchSystem = view.findViewById(R.id.switchSystemApps);
        ListView lvList = view.findViewById(R.id.lvAppList);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnSave = view.findViewById(R.id.btnSaveConfig);

        tvTitle.setText(isGameProps ? "GameProps App Config" : "Thermals App Config");

        List<String> options = new ArrayList<>();
        options.add("None");
        if (isGameProps) {
            options.add("device1");
            options.add("device2");
            options.add("device3");
        } else {
            options.add("1");
            options.add("2");
            options.add("3");
        }

        String jsonString = readFile(isGameProps ? GAMEPROPS_PATH : THERMALS_PATH);
        HashMap<String, String> currentMap = parseJsonToMap(jsonString, isGameProps);

        PackageManager pm = getPackageManager();
        List<PackageInfo> installedPackages = pm.getInstalledPackages(0);

        List<AppModel> allApps = new ArrayList<>();
        for (PackageInfo pkg : installedPackages) {
            boolean isSys = (pkg.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            String appName = pkg.applicationInfo.loadLabel(pm).toString();
            String pkgName = pkg.packageName;
            String config = currentMap.containsKey(pkgName) ? currentMap.get(pkgName) : "None";

            allApps.add(new AppModel(appName, pkgName, pkg.applicationInfo.loadIcon(pm), isSys, config));
        }

        AppConfigAdapter adapter = new AppConfigAdapter(this, filterApps(allApps, false), options);
        lvList.setAdapter(adapter);

        switchSystem.setOnCheckedChangeListener((v, isChecked) -> {
            adapter.updateList(filterApps(allApps, isChecked));
        });

        AlertDialog dialog = builder.create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            saveMapToJson(allApps, isGameProps);
            addLog((isGameProps ? "GameProps" : "Thermals") + " config saved via GUI");
            Toast.makeText(MainActivity.this, "Configuration saved!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    private List<AppModel> filterApps(List<AppModel> list, boolean showSystem) {
        List<AppModel> filtered = new ArrayList<>();
        for (AppModel app : list) {
            if (showSystem || !app.isSystem()) {
                filtered.add(app);
            }
        }
        return filtered;
    }

    private HashMap<String, String> parseJsonToMap(String jsonString, boolean isGameProps) {
        HashMap<String, String> map = new HashMap<>();
        if (jsonString == null || jsonString.isEmpty()) return map;

        try {
            JSONObject root = new JSONObject(jsonString);
            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String configKey = keys.next();
                if (configKey.equals("DEFAULT")) continue;

                JSONObject obj = root.optJSONObject(configKey);
                if (obj != null && obj.has("PKGNAMES")) {
                    JSONArray pkgs = obj.getJSONArray("PKGNAMES");
                    for (int i = 0; i < pkgs.length(); i++) {
                        map.put(pkgs.getString(i), configKey);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return map;
    }

    private void saveMapToJson(List<AppModel> allApps, boolean isGameProps) {
        try {
            JSONObject root = new JSONObject();

            if (!isGameProps) {
                root.put("DEFAULT", "0");
            }

            HashMap<String, JSONArray> configGroups = new HashMap<>();

            for (AppModel app : allApps) {
                String config = app.getSelectedConfig();
                if (!config.equals("None")) {
                    if (!configGroups.containsKey(config)) {
                        configGroups.put(config, new JSONArray());
                    }
                    configGroups.get(config).put(app.getPackageName());
                }
            }

            for (String configKey : configGroups.keySet()) {
                JSONObject configObj = new JSONObject();
                configObj.put("PKGNAMES", configGroups.get(configKey));

                if (isGameProps) {
                    configObj.put("MANUFACTURER", "Xiaomi");
                    configObj.put("MODEL", "2201122C");
                } else {
                    configObj.put("THERMAL_PROFILE", configKey);
                }

                root.put(configKey, configObj);
            }

            writeFile(isGameProps ? GAMEPROPS_PATH : THERMALS_PATH, root.toString(4));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== UTILITIES & HELPERS ====================

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

    private boolean validateProp(String content) {
        if (content == null || content.trim().isEmpty()) return false;
        String[] lines = content.split("\n");
        boolean hasManufacturer = false, hasModel = false, hasFingerprint = false, hasProduct = false, hasDevice = false;

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
                        case "MANUFACTURER": hasManufacturer = true; break;
                        case "MODEL": hasModel = true; break;
                        case "FINGERPRINT": hasFingerprint = true; break;
                        case "PRODUCT": hasProduct = true; break;
                        case "DEVICE": hasDevice = true; break;
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
        boolean hasBrand = false, hasSecurityPatch = false, hasInitialSdk = false, hasSpoofVendingSdk = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            String cleanLine = trimmed.startsWith("#") ? trimmed.substring(1).trim() : trimmed;

            if (cleanLine.contains("=") || cleanLine.contains(":")) {
                String delimiter = cleanLine.contains("=") ? "=" : ":";
                int idx = cleanLine.indexOf(delimiter);
                String key = cleanLine.substring(0, idx).trim();
                String val = cleanLine.substring(idx + 1).trim();

                if (key.equalsIgnoreCase("MANUFACTURER") && !val.isEmpty()) manufacturer = val;
                else if (key.equalsIgnoreCase("BRAND")) hasBrand = true;
                else if (key.equalsIgnoreCase("SECURITY_PATCH")) hasSecurityPatch = true;
                else if (key.equalsIgnoreCase("DEVICE_INITIAL_SDK_INT")) hasInitialSdk = true;
                else if (key.equalsIgnoreCase("spoofVendingSdk")) hasSpoofVendingSdk = true;
            }
        }

        StringBuilder sb = new StringBuilder();
        for (String line : lines) sb.append(line).append("\n");

        if (!hasBrand && !manufacturer.isEmpty()) {
            sb.append("BRAND=").append(manufacturer).append("\n");
            addLog("BRAND auto-set to " + manufacturer);
        }
        if (!hasSecurityPatch) sb.append("#SECURITY_PATCH=\n");
        if (!hasInitialSdk) sb.append("#DEVICE_INITIAL_SDK_INT=\n");
        if (!hasSpoofVendingSdk) sb.append("#spoofVendingSdk=true\n");

        return sb.toString().trim();
    }

    private void updateApplyButton(Button btn, EditText editor) {
        String content = editor.getText().toString().trim();
        btn.setEnabled(validateProp(content));
    }

    private void resetGameProps() {
        String data = readRaw(R.raw.default_gameprops);
        if (!data.isEmpty() && writeFile(GAMEPROPS_PATH, data)) {
            addLog("GameProps reset");
            Toast.makeText(this, "GameProps reset", Toast.LENGTH_SHORT).show();
        }
    }

    private void resetThermals() {
        String data = readRaw(R.raw.default_thermals);
        if (!data.isEmpty() && writeFile(THERMALS_PATH, data)) {
            addLog("Thermals reset");
            Toast.makeText(this, "Thermals reset", Toast.LENGTH_SHORT).show();
        }
    }

    private void addLog(String msg) {
        msg = msg.replaceAll("/data/[^\\s]+", "").replaceAll("/odm/[^\\s]+", "")
                 .replaceAll("/vendor/[^\\s]+", "").replaceAll("/system/[^\\s]+", "");
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        String logMsg = "[" + timestamp + "] " + msg;
        
        if (logLines.size() >= MAX_LOG_LINES) logLines.removeFirst();
        logLines.add(logMsg);
        
        mainHandler.post(() -> {
            StringBuilder sb = new StringBuilder();
            for (String line : logLines) sb.append(line).append("\n");
            tvLog.setText(sb.toString());
        });
    }

    private void copyLog() {
        String logText = tvLog.getText().toString();
        if (logText != null && !logText.isEmpty() && !logText.equals("[Log ready]")) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Debug Log", logText));
            Toast.makeText(this, "Log copied", Toast.LENGTH_SHORT).show();
            addLog("Log copied");
        }
    }

    private void updateUIState(boolean isManual) {
        panelManual.setVisibility(isManual ? View.VISIBLE : View.GONE);
        btnUpdate.setVisibility(isManual ? View.GONE : View.VISIBLE);
        tvAutoStatus.setVisibility(isManual ? View.GONE : View.VISIBLE);
        tvAutoStatus.setText(isManual ? "Auto update: OFF" : "Auto update: ON");
        tvLastUpdate.setVisibility(isManual ? View.GONE : View.VISIBLE);
        String lastUpdate = sp.getString("last_update", "-");
        tvLastUpdate.setText("Last update: " + lastUpdate);
    }

    private void loadCustomPIF() {
        String content = readFile(CUST_PIF_PATH);
        if (content != null && !content.isEmpty()) {
            etPifEditor.setText(content);
            updateApplyButton(btnApplyManual, etPifEditor);
        } else {
            etPifEditor.setText("# No custom PIF loaded");
            btnApplyManual.setEnabled(false);
        }
    }

    private void createDirectories() {
        new File(DIR).mkdirs();
        new File(LOCAL_DIR).mkdirs();
    }

    private void applyFallback() {
        try {
            if (!new File(KB_PATH).exists()) writeFile(KB_PATH, readRaw(R.raw.default_keybox));
            if (!new File(PIF_PATH).exists()) writeFile(PIF_PATH, readRaw(R.raw.default_pif));
            if (!new File(GAMEPROPS_PATH).exists()) writeFile(GAMEPROPS_PATH, readRaw(R.raw.default_gameprops));
            if (!new File(THERMALS_PATH).exists()) writeFile(THERMALS_PATH, readRaw(R.raw.default_thermals));
        } catch (Exception e) {}
    }

    private void checkUpdateOnline(boolean showToast) {
        try {
            String newKb = fetch(URL_KB), newPif = fetch(URL_PIF);
            String newGame = fetch(URL_GAMEPROPS), newTherm = fetch(URL_THERMALS);

            boolean updated = false;
            if (newKb != null && !newKb.equals(readFile(KB_PATH))) { writeFile(KB_PATH, newKb); updated = true; }
            if (newPif != null && !newPif.equals(readFile(PIF_PATH))) { writeFile(PIF_PATH, newPif); updated = true; }
            if (newGame != null && !newGame.equals(readFile(GAMEPROPS_PATH))) { writeFile(GAMEPROPS_PATH, newGame); updated = true; }
            if (newTherm != null && !newTherm.equals(readFile(THERMALS_PATH))) { writeFile(THERMALS_PATH, newTherm); updated = true; }

            String date = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new Date());
            sp.edit().putString("last_update", date).apply();

            final boolean isUpdated = updated;
            runOnUiThread(() -> {
                tvLastUpdate.setText("Last update: " + date);
                if (showToast) Toast.makeText(MainActivity.this, isUpdated ? "Files updated!" : "Already latest.", Toast.LENGTH_SHORT).show();
            });

            if (updated) killGMSAndVending();
        } catch (Exception e) {}
    }

    private void applyManualPIF() {
        String content = etPifEditor.getText().toString().trim();
        if (content.isEmpty() || content.startsWith("# No custom")) return;

        if (!validateProp(content)) {
            Toast.makeText(this, "Invalid PROP format", Toast.LENGTH_SHORT).show();
            return;
        }

        content = processOptionalProps(content);
        etPifEditor.setText(content);

        if (writeFile(CUST_PIF_PATH, content)) {
            String time = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new Date());
            sp.edit().putString("pif_last_apply", time).apply();
            tvPifLastApply.setText("Last apply: " + time);
            Toast.makeText(this, "Custom PIF applied!", Toast.LENGTH_SHORT).show();
            if (switchManual.isChecked()) forceReloadPIF();
            killGMSAndVending();
        }
    }

    private void forceReloadPIF() {
        File f = new File(CUST_PIF_PATH);
        if (f.exists()) f.setLastModified(System.currentTimeMillis());
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
                    if (writeFile(CUST_KB_PATH, content)) {
                        String time = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new Date());
                        sp.edit().putString("keybox_last_apply", time).apply();
                        runOnUiThread(() -> tvKeyboxLastApply.setText("Last apply: " + time));
                    }
                } else if (req == 102) {
                    content = processOptionalProps(content);
                    final String finalContent = content;
                    if (validateProp(finalContent) && writeFile(CUST_PIF_PATH, finalContent)) {
                        String time = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new Date());
                        sp.edit().putString("pif_last_apply", time).apply();
                        runOnUiThread(() -> {
                            tvPifLastApply.setText("Last apply: " + time);
                            etPifEditor.setText(finalContent);
                            updateApplyButton(btnApplyManual, etPifEditor);
                        });
                        killGMSAndVending();
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
            } catch (Exception e) {
                am.killBackgroundProcesses("com.google.android.gms");
                am.killBackgroundProcesses("com.android.vending");
            }
        } catch (Exception e) {}
    }

    private boolean writeFile(String path, String content) {
        try {
            File dir = new File(path).getParentFile();
            if (!dir.exists()) dir.mkdirs();
            FileOutputStream f = new FileOutputStream(path);
            f.write(content.getBytes());
            f.close();
            return true;
        } catch (Exception e) { return false; }
    }

    private String fetch(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(8000);
        if (c.getResponseCode() != 200) return null;
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
        StringBuilder sb = new StringBuilder(); String line;
        while ((line = r.readLine()) != null) sb.append(line).append("\n");
        r.close();
        return sb.toString().trim();
    }

    private String readFile(String path) {
        try {
            File f = new File(path);
            if (!f.exists()) return "";
            BufferedReader r = new BufferedReader(new java.io.FileReader(f));
            StringBuilder sb = new StringBuilder(); String line;
            while ((line = r.readLine()) != null) sb.append(line).append("\n");
            r.close();
            return sb.toString().trim();
        } catch (Exception e) { return ""; }
    }

    private String readRaw(int rawResId) {
        try {
            InputStream is = getResources().openRawResource(rawResId);
            BufferedReader r = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder(); String line;
            while ((line = r.readLine()) != null) sb.append(line).append("\n");
            r.close();
            return sb.toString().trim();
        } catch (Exception e) { return ""; }
    }

    private String readUri(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            BufferedReader r = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder(); String line;
            while ((line = r.readLine()) != null) sb.append(line).append("\n");
            r.close();
            return sb.toString().trim();
        } catch (Exception e) { return ""; }
    }
}
