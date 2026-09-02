package com.uwuh.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class GamePropsThermalController {

    public interface LogCallback {
        void log(String msg);
    }

    public static void showAppConfigDialog(Context context, boolean isGameProps, LogCallback callback) {
        String moduleKey = isGameProps ? UwuhManager.MODULE_GAMEPROPS : UwuhManager.MODULE_THERMALS;
        String filePath = isGameProps ? UwuhManager.GAMEPROPS_PATH : UwuhManager.THERMALS_PATH;
        File file = new File(filePath);
        String defaultOption = isGameProps ? "None" : "Default";

        List<String> options = new ArrayList<>();
        options.add(defaultOption);

        HashMap<String, String> labelToKeyMap = new HashMap<>();
        HashMap<String, String> thermalNameMap = getThermalNameMap(context);

        if (file.exists()) {
            try {
                JSONObject root = new JSONObject(UwuhManager.readFile(filePath));
                Iterator<String> keys = root.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    if (isGameProps) {
                        options.add(key);
                        labelToKeyMap.put(key, key);
                    } else {
                        String label = thermalNameMap.containsKey(key) ? thermalNameMap.get(key) : "Thermal Profile " + key;
                        options.add(label);
                        labelToKeyMap.put(label, key);
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_app_config, null);
        builder.setView(view);

        TextView tvTitle = view.findViewById(R.id.tvDialogTitle);
        Switch switchSystem = view.findViewById(R.id.switchSystemApps);
        ListView lvList = view.findViewById(R.id.lvAppList);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnSave = view.findViewById(R.id.btnSaveConfig);

        tvTitle.setText(isGameProps ? "GameProps App Config" : "Thermals App Config");

        HashMap<String, String> currentMap = parseJsonToMap(UwuhManager.readFile(filePath), isGameProps, thermalNameMap);

        PackageManager pm = context.getPackageManager();
        List<PackageInfo> installedPackages = pm.getInstalledPackages(0);

        List<AppModel> allApps = new ArrayList<>();
        for (PackageInfo pkg : installedPackages) {
            boolean isSys = (pkg.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            String appName = pkg.applicationInfo.loadLabel(pm).toString();
            String pkgName = pkg.packageName;
            String config = currentMap.containsKey(pkgName) ? currentMap.get(pkgName) : defaultOption;

            allApps.add(new AppModel(appName, pkgName, pkg.applicationInfo.loadIcon(pm), isSys, config));
        }

        AppConfigAdapter adapter = new AppConfigAdapter(context, filterApps(allApps, false), options);
        lvList.setAdapter(adapter);

        switchSystem.setOnCheckedChangeListener((v, isChecked) -> adapter.updateList(filterApps(allApps, isChecked)));

        AlertDialog dialog = builder.create();
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            boolean success = saveMapToJson(allApps, filePath, defaultOption, labelToKeyMap);
            if (success) {
                UwuhManager.syncToFramework(moduleKey, filePath);
                callback.log((isGameProps ? "GameProps" : "Thermals") + " config saved & chunked");
                Toast.makeText(context, "Saved & Synced!", Toast.LENGTH_SHORT).show();
            } else {
                callback.log("Save failed!");
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    public static void showDevicesManagerDialog(Context context, LogCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_device_manager, null);
        builder.setView(view);

        Switch switchAutoUpdateDevices = view.findViewById(R.id.switchAutoUpdateDevices);
        ListView lvDevices = view.findViewById(R.id.lvDevices);
        Button btnAddDevice = view.findViewById(R.id.btnAddDevice);
        Button btnClose = view.findViewById(R.id.btnCloseDeviceDialog);

        List<String> deviceList = new ArrayList<>();
        try {
            File f = new File(UwuhManager.GAMEPROPS_PATH);
            if (f.exists()) {
                JSONObject root = new JSONObject(UwuhManager.readFile(UwuhManager.GAMEPROPS_PATH));
                Iterator<String> keys = root.keys();
                while (keys.hasNext()) deviceList.add(keys.next());
            }
        } catch (Exception e) { e.printStackTrace(); }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, deviceList);
        lvDevices.setAdapter(adapter);

        AlertDialog dialog = builder.create();

        lvDevices.setOnItemClickListener((parent, v, position, id) -> {
            showEditDeviceDialog(context, deviceList.get(position), callback, () -> showDevicesManagerDialog(context, callback));
            dialog.dismiss();
        });

        btnAddDevice.setOnClickListener(v -> {
            showEditDeviceDialog(context, null, callback, () -> showDevicesManagerDialog(context, callback));
            dialog.dismiss();
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private static void showEditDeviceDialog(Context context, String devName, LogCallback callback, Runnable onComplete) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_edit_device, null);
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
                JSONObject root = new JSONObject(UwuhManager.readFile(UwuhManager.GAMEPROPS_PATH));
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
            String manuf = etManufacturer.getText().toString().trim();
            String model = etModel.getText().toString().trim();
            String brand = etBrand.getText().toString().trim();
            if (brand.isEmpty()) brand = manuf;

            if (name.isEmpty() || manuf.isEmpty() || model.isEmpty()) return;

            try {
                JSONObject root = new File(UwuhManager.GAMEPROPS_PATH).exists() ? new JSONObject(UwuhManager.readFile(UwuhManager.GAMEPROPS_PATH)) : new JSONObject();
                JSONObject devObj = isEdit && root.has(devName) ? root.getJSONObject(devName) : new JSONObject();

                if (!devObj.has("PKGNAMES")) devObj.put("PKGNAMES", new JSONArray());
                devObj.put("BRAND", brand);
                devObj.put("MANUFACTURER", manuf);
                devObj.put("MODEL", model);

                if (isEdit && !devName.equals(name)) root.remove(devName);
                root.put(name, devObj);

                if (UwuhManager.writeAndSync(UwuhManager.MODULE_GAMEPROPS, UwuhManager.GAMEPROPS_PATH, root.toString(4))) {
                    callback.log("Device " + name + " saved & chunked");
                }
            } catch (Exception e) { e.printStackTrace(); }
            dialog.dismiss();
            if (onComplete != null) onComplete.run();
        });

        btnDelete.setOnClickListener(v -> {
            try {
                JSONObject root = new JSONObject(UwuhManager.readFile(UwuhManager.GAMEPROPS_PATH));
                if (root.has(devName)) {
                    root.remove(devName);
                    UwuhManager.writeAndSync(UwuhManager.MODULE_GAMEPROPS, UwuhManager.GAMEPROPS_PATH, root.toString(4));
                    callback.log("Device " + devName + " deleted");
                }
            } catch (Exception e) { e.printStackTrace(); }
            dialog.dismiss();
            if (onComplete != null) onComplete.run();
        });

        dialog.show();
    }

    public static void resetGameProps(Context context, LogCallback callback) {
        String data = UwuhManager.readRawRes(context, R.raw.default_gameprops);
        if (!data.isEmpty() && UwuhManager.writeAndSync(UwuhManager.MODULE_GAMEPROPS, UwuhManager.GAMEPROPS_PATH, data)) {
            callback.log("GameProps reset & chunked");
        }
    }

    public static void resetThermals(Context context, LogCallback callback) {
        String data = UwuhManager.readRawRes(context, R.raw.default_thermals);
        if (!data.isEmpty() && UwuhManager.writeAndSync(UwuhManager.MODULE_THERMALS, UwuhManager.THERMALS_PATH, data)) {
            callback.log("Thermals reset & chunked");
        }
    }

    private static HashMap<String, String> getThermalNameMap(Context context) {
        HashMap<String, String> map = new HashMap<>();
        String[] thermalArray = context.getResources().getStringArray(R.array.thermal_options);
        for (String entry : thermalArray) {
            if (entry.contains(":")) {
                String[] parts = entry.split(":", 2);
                map.put(parts[0].trim(), parts[1].trim());
            }
        }
        return map;
    }

    private static List<AppModel> filterApps(List<AppModel> list, boolean showSystem) {
        List<AppModel> filtered = new ArrayList<>();
        for (AppModel app : list) {
            if (showSystem || !app.isSystem()) filtered.add(app);
        }
        return filtered;
    }

    private static HashMap<String, String> parseJsonToMap(String jsonString, boolean isGameProps, HashMap<String, String> thermalNameMap) {
        HashMap<String, String> map = new HashMap<>();
        if (jsonString.isEmpty()) return map;
        try {
            JSONObject root = new JSONObject(jsonString);
            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject obj = root.optJSONObject(key);
                if (obj == null) continue;
                JSONArray pkgs = obj.optJSONArray("PKGNAMES");
                if (pkgs != null) {
                    String label = isGameProps ? key : (thermalNameMap.containsKey(key) ? thermalNameMap.get(key) : "Thermal Profile " + key);
                    for (int i = 0; i < pkgs.length(); i++) map.put(pkgs.getString(i), label);
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return map;
    }

    private static boolean saveMapToJson(List<AppModel> allApps, String path, String defaultOption, HashMap<String, String> labelToKeyMap) {
        try {
            File file = new File(path);
            JSONObject root = file.exists() ? new JSONObject(UwuhManager.readFile(path)) : new JSONObject();
            HashMap<String, JSONArray> configGroups = new HashMap<>();

            for (AppModel app : allApps) {
                String selectedLabel = app.getSelectedConfig();
                if (!selectedLabel.equals(defaultOption)) {
                    String actualKey = labelToKeyMap.get(selectedLabel);
                    if (actualKey != null) {
                        if (!configGroups.containsKey(actualKey)) configGroups.put(actualKey, new JSONArray());
                        configGroups.get(actualKey).put(app.getPackageName());
                    }
                }
            }

            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                JSONObject obj = root.getJSONObject(k);
                obj.put("PKGNAMES", configGroups.containsKey(k) ? configGroups.get(k) : new JSONArray());
            }

            return UwuhManager.writeFile(path, root.toString(4));
        } catch (Exception e) { return false; }
    }
}
