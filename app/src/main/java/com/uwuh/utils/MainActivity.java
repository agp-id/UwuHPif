package com.uwuh.utils;

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
import android.os.SystemClock;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
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
    
    private static final String KB_PATH = DIR + "/keybox.xml";
    private static final String PIF_PATH = DIR + "/pif.prop";
    private static final String CUST_KB_PATH = DIR + "/cust_keybox.xml";
    private static final String CUST_PIF_PATH = DIR + "/cust_pif.prop";
    private static final String GAMEPROPS_PATH = DIR + "/gameprops.json";
    private static final String THERMALS_PATH = DIR + "/per_app_thermals.json";
    private static final String LOG_FILE_PATH = DIR + "/app_session.log";
    
    private static final String URL_KB = "https://raw.githubusercontent.com/user/repo/main/keybox.xml";
    private static final String URL_PIF = "https://raw.githubusercontent.com/user/repo/main/pif.prop";
    private static final String URL_GAMEPROPS_DEVICES = "https://raw.githubusercontent.com/user/repo/main/gameprops_devices.json";
    
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
    private Toast currentToast;

    private SharedPreferences sp;
    private Switch switchManual, switchBootloader, switchPIF;
    private Switch switchGameProps, switchThermals, switchAutoUpdateDevices;
    private TextView tvLastUpdate, tvAutoStatus;
    private TextView tvKeyboxLastApply, tvPifLastApply;
    private LinearLayout panelManual, panelGamePropsBtn, panelThermalsBtn;
    private EditText etPifEditor;
    private Button btnUpdate, btnApplyManual, btnGameProps, btnDevices, btnThermals;
    private Button btnResetGameProps, btnResetThermals;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sp = getSharedPreferences("pif_prefs", MODE_PRIVATE);

        switchManual = findViewById(R.id.switchManual);
        switchBootloader = findViewById(R.id.switchBootloader);
        switchPIF = findViewById(R.id.switchPIF);
        switchGameProps = findViewById(R.id.switchGameProps);
        switchThermals = findViewById(R.id.switchThermals);
        switchAutoUpdateDevices = findViewById(R.id.switchAutoUpdateDevices);
        
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
        btnDevices = findViewById(R.id.btnDevices);
        btnThermals = findViewById(R.id.btnThermals);
        btnResetGameProps = findViewById(R.id.btnResetGameProps);
        btnResetThermals = findViewById(R.id.btnResetThermals);

        tvLog = findViewById(R.id.tvLog);
        btnCopyLog = findViewById(R.id.btnCopyLog);

        tvLog.setFocusable(false);
        tvLog.setFocusableInTouchMode(false);
        tvLog.setLongClickable(false);
        tvLog.setKeyListener(null);
        tvLog.setMovementMethod(new ScrollingMovementMethod());

        enableInnerScroll(etPifEditor);
        enableInnerScroll(tvLog);

        boolean isManual = sp.getBoolean("manual", false);
        switchManual.setChecked(isManual);
        updateUIState(isManual);

        boolean bootloaderState = SystemPropertiesHelper.getBoolean(PROP_BOOTLOADER, true);
        boolean pifState = SystemPropertiesHelper.getBoolean(PROP_PIF, true);
        boolean gamePropsState = SystemPropertiesHelper.getBoolean(PROP_GAMEPROPS, false);
        boolean thermalsState = SystemPropertiesHelper.getBoolean(PROP_THERMALS, false);

        switchBootloader.setChecked(bootloaderState);
        switchPIF.setChecked(pifState);
        
        switchGameProps.setChecked(gamePropsState);
        panelGamePropsBtn.setVisibility(gamePropsState ? View.VISIBLE : View.GONE);

        switchThermals.setChecked(thermalsState);
        panelThermalsBtn.setVisibility(thermalsState ? View.VISIBLE : View.GONE);

        boolean autoUpdateDevices = sp.getBoolean("auto_update_devices", false);
        switchAutoUpdateDevices.setChecked(autoUpdateDevices);

        tvKeyboxLastApply.setText("Last apply: " + sp.getString("keybox_last_apply", "-"));
        tvPifLastApply.setText("Last apply: " + sp.getString("pif_last_apply", "-"));

        loadLogHistory();
        loadCustomPIF();

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

        switchAutoUpdateDevices.setOnCheckedChangeListener((v, checked) -> {
            sp.edit().putBoolean("auto_update_devices", checked).apply();
            addLog("Auto update devices: " + (checked ? "ON" : "OFF"));
            if (checked) {
                new Thread(this::updateGamePropsDevicesFromOnline).start();
            }
        });

        btnUpdate.setOnClickListener(v -> new Thread(() -> checkUpdateOnline(true)).start());
        btnCopyLog.setOnClickListener(v -> copyLog());

        btnGameProps.setOnClickListener(v -> showAppConfigDialog(true));
        btnDevices.setOnClickListener(v -> showDevicesManagerDialog());
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
            if (!sp.getBoolean("manual", false)) {
                checkUpdateOnline(false);
            }
            if (sp.getBoolean("auto_update_devices", false)) {
                updateGamePropsDevicesFromOnline();
            }
        }).start();
    }

    private void showToast(String message) {
        runOnUiThread(() -> {
            if (currentToast != null) currentToast.cancel();
            currentToast = Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT);
            currentToast.show();
        });
    }

    private void loadLogHistory() {
        File logFile = new File(LOG_FILE_PATH);
        long bootTime = System.currentTimeMillis() - SystemClock.elapsedRealtime();
        if (logFile.exists() && logFile.lastModified() < bootTime) {
            logFile.delete();
        }

        if (logFile.exists()) {
            String savedLogs = readFile(LOG_FILE_PATH);
            if (!savedLogs.isEmpty()) {
                String[] lines = savedLogs.split("\n");
                for (String line : lines) {
                    if (!line.trim().isEmpty()) logLines.add(line);
                }
                updateLogView();
            }
        }
    }

    private void addLog(String msg) {
        msg = msg.replaceAll("/data/[^\\s]+", "").replaceAll("/odm/[^\\s]+", "")
                 .replaceAll("/vendor/[^\\s]+", "").replaceAll("/system/[^\\s]+", "");
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        String logMsg = "[" + timestamp + "] " + msg;
        
        if (logLines.size() >= MAX_LOG_LINES) logLines.removeFirst();
        logLines.add(logMsg);
        
        saveLogToFile();
        updateLogView();
    }

    private void saveLogToFile() {
        StringBuilder sb = new StringBuilder();
        for (String line : logLines) sb.append(line).append("\n");
        writeFile(LOG_FILE_PATH, sb.toString().trim());
    }

    private void updateLogView() {
        mainHandler.post(() -> {
            StringBuilder sb = new StringBuilder();
            for (String line : logLines) sb.append(line).append("\n");
            tvLog.setText(sb.toString().trim());
        });
    }

    private void applyFallback() {
        try {
            checkAndCreateFallback(KB_PATH, R.raw.default_keybox, "Keybox");
            checkAndCreateFallback(PIF_PATH, R.raw.default_pif, "PIF");
            checkAndCreateFallback(GAMEPROPS_PATH, R.raw.default_gameprops, "GameProps");
            checkAndCreateFallback(THERMALS_PATH, R.raw.default_thermals, "Thermals");
        } catch (Exception e) {
            addLog("Fallback check error");
        }
    }

    private void checkAndCreateFallback(String path, int rawResId, String tag) {
        File file = new File(path);
        if (!file.exists()) {
            String data = readRaw(rawResId);
            if (!data.isEmpty() && writeFile(path, data)) {
                addLog(tag + " created from fallback");
            }
        }
    }

    private void checkUpdateOnline(boolean showToast) {
        addLog("Checking update...");

        try {
            String newKb = fetch(URL_KB);
            String newPif = fetch(URL_PIF);

            boolean kbUpdated = false;
            boolean pifUpdated = false;

            if (newKb != null && !newKb.isEmpty() && !newKb.equals(readFile(KB_PATH))) {
                if (writeFile(KB_PATH, newKb)) kbUpdated = true;
            }

            if (newPif != null && !newPif.isEmpty() && !newPif.equals(readFile(PIF_PATH))) {
                if (writeFile(PIF_PATH, newPif)) pifUpdated = true;
            }

            String date = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new Date());
            sp.edit().putString("last_update", date).apply();

            final boolean isAnyUpdated = kbUpdated || pifUpdated;

            runOnUiThread(() -> {
                tvLastUpdate.setText("Last update: " + date);
                if (showToast) showToast(isAnyUpdated ? "Files updated!" : "Already latest.");
            });

            if (kbUpdated || pifUpdated) {
                addLog("Online update applied");
                killGMSAndVending();
            } else {
                addLog("Already latest");
            }

        } catch (Exception e) {
            addLog("Update failed");
            if (showToast) showToast("Update failed");
        }
    }

    private void updateGamePropsDevicesFromOnline() {
        addLog("Syncing GameProps devices online...");
        try {
            String onlineJson = fetch(URL_GAMEPROPS_DEVICES);
            if (onlineJson == null || onlineJson.isEmpty()) {
                addLog("Sync devices failed: Empty response");
                return;
            }

            JSONObject onlineDevices = new JSONObject(onlineJson);
            File localFile = new File(GAMEPROPS_PATH);
            JSONObject root = localFile.exists() ? new JSONObject(readFile(GAMEPROPS_PATH)) : new JSONObject();

            boolean changed = false;
            Iterator<String> keys = onlineDevices.keys();

            while (keys.hasNext()) {
                String devName = keys.next();
                JSONObject onlineDevObj = onlineDevices.getJSONObject(devName);

                if (root.has(devName)) {
                    JSONObject localDevObj = root.getJSONObject(devName);
                    Iterator<String> attrKeys = onlineDevObj.keys();
                    while (attrKeys.hasNext()) {
                        String attr = attrKeys.next();
                        if (!attr.equals("PKGNAMES")) {
                            String newVal = onlineDevObj.getString(attr);
                            if (!localDevObj.optString(attr).equals(newVal)) {
                                localDevObj.put(attr, newVal);
                                changed = true;
                            }
                        }
                    }
                } else {
                    JSONObject newDevObj = new JSONObject(onlineDevObj.toString());
                    if (!newDevObj.has("PKGNAMES")) {
                        newDevObj.put("PKGNAMES", new JSONArray());
                    }
                    root.put(devName, newDevObj);
                    changed = true;
                }
            }

            if (changed) {
                if (writeFile(GAMEPROPS_PATH, root.toString(4))) {
                    addLog("GameProps devices updated from online");
                    showToast("Devices synced!");
                }
            } else {
                addLog("Devices already up to date");
            }
        } catch (Exception e) {
            addLog("Sync devices error");
        }
    }

    private void showAppConfigDialog(boolean isGameProps) {
        String tag = isGameProps ? "GameProps" : "Thermals";
        String filePath = isGameProps ? GAMEPROPS_PATH : THERMALS_PATH;
        File file = new File(filePath);

        List<String> options = new ArrayList<>();
        options.add("None");

        if (file.exists()) {
            try {
                JSONObject root = new JSONObject(readFile(filePath));
                Iterator<String> keys = root.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    if (!k.equals("DEFAULT")) options.add(k);
                }
            } catch (Exception e) { e.printStackTrace(); }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_app_config, null);
        builder.setView(view);

        TextView tvTitle = view.findViewById(R.id.tvDialogTitle);
        Switch switchSystem = view.findViewById(R.id.switchSystemApps);
        ListView lvList = view.findViewById(R.id.lvAppList);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnSave = view.findViewById(R.id.btnSaveConfig);

        tvTitle.setText(isGameProps ? "GameProps App Config" : "Thermals App Config");

        String jsonString = file.exists() ? readFile(filePath) : "";
        HashMap<String, String> currentMap = parseJsonToMap(jsonString);

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

        switchSystem.setOnCheckedChangeListener((v, isChecked) -> adapter.updateList(filterApps(allApps, isChecked)));

        AlertDialog dialog = builder.create();
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            boolean success = saveMapToJson(allApps, isGameProps, filePath);
            if (success) {
                addLog(tag + " config saved");
                showToast("Configuration saved!");
            } else {
                addLog(tag + " config save failed");
                showToast("Save failed!");
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showDevicesManagerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_device_manager, null);
        builder.setView(view);

        ListView lvDevices = view.findViewById(R.id.lvDevices);
        Button btnAddDevice = view.findViewById(R.id.btnAddDevice);
        Button btnClose = view.findViewById(R.id.btnCloseDeviceDialog);

        List<String> deviceList = new ArrayList<>();
        try {
            File f = new File(GAMEPROPS_PATH);
            if (f.exists()) {
                JSONObject root = new JSONObject(readFile(GAMEPROPS_PATH));
                Iterator<String> keys = root.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    if (!k.equals("DEFAULT")) deviceList.add(k);
                }
            }
        } catch (Exception e) { e.printStackTrace(); }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceList);
        lvDevices.setAdapter(adapter);

        AlertDialog dialog = builder.create();

        lvDevices.setOnItemClickListener((parent, v, position, id) -> {
            String selectedDevice = deviceList.get(position);
            showEditDeviceDialog(selectedDevice, () -> showDevicesManagerDialog());
            dialog.dismiss();
        });

        btnAddDevice.setOnClickListener(v -> {
            showEditDeviceDialog(null, () -> showDevicesManagerDialog());
            dialog.dismiss();
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showEditDeviceDialog(String devName, Runnable onComplete) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_device, null);
        builder.setView(view);

        EditText etName = view.findViewById(R.id.etDeviceName);
        EditText etBrand = view.findViewById(R.id.etBrand);
        EditText etManufacturer = view.findViewById(R.id.etManufacturer);
        EditText etModel = view.findViewById(R.id.etModel);
        Button btnSave = view.findViewById(R.id.btnSaveDevice);
        Button btnDelete = view.findViewById(R.id.btnDeleteDevice);

        boolean isEdit = (devName != null);
        btnDelete.setVisibility(isEdit ? View.VISIBLE : View.GONE);

        if (isEdit) {
            etName.setText(devName);
            try {
                JSONObject root = new JSONObject(readFile(GAMEPROPS_PATH));
                JSONObject devObj = root.optJSONObject(devName);
                if (devObj != null) {
                    etBrand.setText(devObj.optString("BRAND", ""));
                    etManufacturer.setText(devObj.optString("MANUFACTURER", ""));
                    etModel.setText(devObj.optString("MODEL", ""));
                }
            } catch (Exception e) { e.printStackTrace(); }
        }

        AlertDialog dialog = builder.create();

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String brand = etBrand.getText().toString().trim();
            String manuf = etManufacturer.getText().toString().trim();
            String model = etModel.getText().toString().trim();

            if (name.isEmpty() || manuf.isEmpty() || model.isEmpty()) {
                showToast("Fields required!");
                return;
            }

            try {
                JSONObject root = new File(GAMEPROPS_PATH).exists() ? new JSONObject(readFile(GAMEPROPS_PATH)) : new JSONObject();
                JSONObject devObj = isEdit && root.has(devName) ? root.getJSONObject(devName) : new JSONObject();

                if (!devObj.has("PKGNAMES")) devObj.put("PKGNAMES", new JSONArray());
                if (!brand.isEmpty()) devObj.put("BRAND", brand);
                devObj.put("MANUFACTURER", manuf);
                devObj.put("MODEL", model);

                if (isEdit && !devName.equals(name)) root.remove(devName);
                root.put(name, devObj);

                if (writeFile(GAMEPROPS_PATH, root.toString(4))) {
                    addLog("Device " + name + " saved");
                    showToast("Device saved");
                } else {
                    addLog("Device save failed");
                    showToast("Failed to save device");
                }
            } catch (Exception e) {
                addLog("Device save error");
                showToast("Error saving device");
            }
            dialog.dismiss();
            if (onComplete != null) onComplete.run();
        });

        btnDelete.setOnClickListener(v -> {
            try {
                JSONObject root = new JSONObject(readFile(GAMEPROPS_PATH));
                if (root.has(devName)) {
                    root.remove(devName);
                    if (writeFile(GAMEPROPS_PATH, root.toString(4))) {
                        addLog("Device " + devName + " deleted");
                        showToast("Device deleted");
                    } else {
                        addLog("Delete device failed");
                        showToast("Delete failed");
                    }
                }
            } catch (Exception e) {
                addLog("Delete device error");
            }
            dialog.dismiss();
            if (onComplete != null) onComplete.run();
        });

        dialog.show();
    }

    private List<AppModel> filterApps(List<AppModel> list, boolean showSystem) {
        List<AppModel> filtered = new ArrayList<>();
        for (AppModel app : list) {
            if (showSystem || !app.isSystem()) filtered.add(app);
        }
        return filtered;
    }

    private HashMap<String, String> parseJsonToMap(String jsonString) {
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
        } catch (Exception e) { e.printStackTrace(); }
        return map;
    }

    private boolean saveMapToJson(List<AppModel> allApps, boolean isGameProps, String path) {
        try {
            File file = new File(path);
            JSONObject root = file.exists() ? new JSONObject(readFile(path)) : new JSONObject();

            if (!isGameProps && !root.has("DEFAULT")) {
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

            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                if (k.equals("DEFAULT")) continue;
                JSONObject obj = root.getJSONObject(k);
                obj.put("PKGNAMES", configGroups.containsKey(k) ? configGroups.get(k) : new JSONArray());
            }

            return writeFile(path, root.toString(4));
        } catch (Exception e) { return false; }
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
            addLog("GameProps reset successfully");
            showToast("GameProps reset");
        } else {
            addLog("GameProps reset failed");
            showToast("Reset failed");
        }
    }

    private void resetThermals() {
        String data = readRaw(R.raw.default_thermals);
        if (!data.isEmpty() && writeFile(THERMALS_PATH, data)) {
            addLog("Thermals reset successfully");
            showToast("Thermals reset");
        } else {
            addLog("Thermals reset failed");
            showToast("Reset failed");
        }
    }

    private void copyLog() {
        String logText = tvLog.getText().toString();
        if (logText != null && !logText.isEmpty() && !logText.equals("[Log ready]")) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Debug Log", logText));
            showToast("Log copied");
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
    }

    private void applyManualPIF() {
        String content = etPifEditor.getText().toString().trim();
        if (content.isEmpty() || content.startsWith("# No custom")) {
            addLog("Manual PIF apply failed: Content empty");
            showToast("No content to apply");
            return;
        }

        if (!validateProp(content)) {
            addLog("Manual PIF apply failed: Invalid format");
            showToast("Invalid PROP format");
            return;
        }

        content = processOptionalProps(content);
        etPifEditor.setText(content);

        if (writeFile(CUST_PIF_PATH, content)) {
            String time = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new Date());
            sp.edit().putString("pif_last_apply", time).apply();
            tvPifLastApply.setText("Last apply: " + time);
            
            addLog("Custom PIF applied successfully");
            showToast("Custom PIF applied!");
            
            if (switchManual.isChecked()) forceReloadPIF();
            killGMSAndVending();
        } else {
            addLog("Manual PIF apply failed: File write error");
            showToast("Save failed");
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
                        addLog("Custom Keybox saved successfully");
                        showToast("Keybox saved!");
                    } else {
                        addLog("Custom Keybox save failed");
                        showToast("Keybox save failed");
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
                        addLog("Custom PIF saved successfully");
                        showToast("Custom PIF saved!");
                        killGMSAndVending();
                    } else {
                        addLog("Custom PIF save failed");
                        showToast("PIF save failed");
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

            panelGamePropsBtn.setVisibility(View.GONE);
            panelThermalsBtn.setVisibility(View.GONE);

            showToast("Configuration reset");
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
            File file = new File(path);
            File dir = file.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
                dir.setReadable(true, true);
                dir.setWritable(true, true);
                dir.setExecutable(true, true);
            }
            
            FileOutputStream f = new FileOutputStream(file);
            f.write(content.getBytes());
            f.close();

            file.setReadable(true, true);
            file.setWritable(true, true);
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
