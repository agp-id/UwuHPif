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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
        List<String> dynamicOptions = new ArrayList<>();
        HashMap<String, String> labelToKeyMap = new HashMap<>();

        if (file.exists() && file.length() > 0) {
            try {
                JSONObject root = new JSONObject(UwuhManager.readFile(filePath));
                Iterator<String> keys = root.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    JSONObject obj = root.optJSONObject(key);

                    String customLabel = (obj != null) ? obj.optString("LABEL", "").trim() : "";
                    String label;
                    if (!customLabel.isEmpty()) {
                        label = customLabel;
                    } else {
                        label = isGameProps ? key : "profile " + key;
                    }

                    dynamicOptions.add(label);
                    labelToKeyMap.put(label, key);
                }
            } catch (Exception e) { 
                e.printStackTrace(); 
            }
        }

        Collections.sort(dynamicOptions, String.CASE_INSENSITIVE_ORDER);
        options.add(defaultOption);
        options.addAll(dynamicOptions);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_app_config, null);
        builder.setView(view);

        TextView tvTitle = view.findViewById(R.id.tvDialogTitle);
        Switch switchSystem = view.findViewById(R.id.switchSystemApps);
        ListView lvList = view.findViewById(R.id.lvAppList);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnSave = view.findViewById(R.id.btnSaveConfig);

        tvTitle.setText(isGameProps ? "GameProps App Config" : "Thermals App Config");

        HashMap<String, String> currentMap = parseJsonToMap(UwuhManager.readFile(filePath), isGameProps);

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

        Collections.sort(allApps, (a1, a2) -> a1.getAppName().compareToIgnoreCase(a2.getAppName()));

        AppConfigAdapter adapter = new AppConfigAdapter(context, filterApps(allApps, false), options);
        lvList.setAdapter(adapter);

        switchSystem.setOnCheckedChangeListener((v, isChecked) -> adapter.updateList(filterApps(allApps, isChecked)));

        AlertDialog dialog = builder.create();
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            boolean success = saveMapToJson(allApps, filePath, defaultOption, labelToKeyMap);
            if (success) {
                // ✅ syncToFramework akan validasi + set enable/disable
                UwuhManager.syncToFramework(context, moduleKey, filePath);
                callback.log((isGameProps ? "GameProps" : "Thermals") + " config saved & synced");
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

        Collections.sort(deviceList, String.CASE_INSENSITIVE_ORDER);

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

                // ✅ writeAndSync akan validasi + set enable/disable
                UwuhManager.writeAndSync(context, UwuhManager.MODULE_GAMEPROPS, UwuhManager.GAMEPROPS_PATH, root.toString(4));
                callback.log("Device " + name + " saved & synced");
            } catch (Exception e) { e.printStackTrace(); }
            dialog.dismiss();
            if (onComplete != null) onComplete.run();
        });

        btnDelete.setOnClickListener(v -> {
            try {
                JSONObject root = new JSONObject(UwuhManager.readFile(UwuhManager.GAMEPROPS_PATH));
                if (root.has(devName)) {
                    root.remove(devName);
                    // ✅ writeAndSync akan validasi + set enable/disable
                    UwuhManager.writeAndSync(context, UwuhManager.MODULE_GAMEPROPS, UwuhManager.GAMEPROPS_PATH, root.toString(4));
                    callback.log("Device " + devName + " deleted");
                }
            } catch (Exception e) { e.printStackTrace(); }
            dialog.dismiss();
            if (onComplete != null) onComplete.run();
        });

        dialog.show();
    }

    /**
     * Reset GameProps ke default
     * ✅ FORCE sync - chunk & version PASTI berubah
     */
    public static void resetGameProps(Context context, LogCallback callback) {
        String data = UwuhManager.readRawRes(context, R.raw.default_gameprops);
        if (!data.isEmpty()) {
            if (UwuhManager.writeFile(UwuhManager.GAMEPROPS_PATH, data)) {
                // ✅ forceSyncToFramework akan validasi + set enable/disable
                boolean success = UwuhManager.forceSyncToFramework(
                    context, 
                    UwuhManager.MODULE_GAMEPROPS, 
                    UwuhManager.GAMEPROPS_PATH
                );
                if (success) {
                    callback.log("GameProps reset to default & forced sync");
                    Toast.makeText(context, "GameProps Reset & Synced!", Toast.LENGTH_SHORT).show();
                } else {
                    callback.log("GameProps reset failed to sync!");
                }
            }
        }
    }

    /**
     * Reset Thermals ke default
     * ✅ FORCE sync - chunk & version PASTI berubah
     */
    public static void resetThermals(Context context, LogCallback callback) {
        String data = UwuhManager.readRawRes(context, R.raw.default_thermals);
        if (!data.isEmpty()) {
            if (UwuhManager.writeFile(UwuhManager.THERMALS_PATH, data)) {
                // ✅ forceSyncToFramework akan validasi + set enable/disable
                boolean success = UwuhManager.forceSyncToFramework(
                    context, 
                    UwuhManager.MODULE_THERMALS, 
                    UwuhManager.THERMALS_PATH
                );
                if (success) {
                    callback.log("Thermals reset to default & forced sync");
                    Toast.makeText(context, "Thermals Reset & Synced!", Toast.LENGTH_SHORT).show();
                } else {
                    callback.log("Thermals reset failed to sync!");
                }
            }
        }
    }

    private static List<AppModel> filterApps(List<AppModel> list, boolean showSystem) {
        List<AppModel> filtered = new ArrayList<>();
        for (AppModel app : list) {
            if (showSystem || !app.isSystem()) filtered.add(app);
        }
        return filtered;
    }

    private static HashMap<String, String> parseJsonToMap(String jsonString, boolean isGameProps) {
        HashMap<String, String> map = new HashMap<>();
        if (jsonString.isEmpty()) return map;
        try {
            JSONObject root = new JSONObject(jsonString);
            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject obj = root.optJSONObject(key);
                if (obj == null) continue;

                String customLabel = obj.optString("LABEL", "").trim();
                String label;
                if (!customLabel.isEmpty()) {
                    label = customLabel;
                } else {
                    label = isGameProps ? key : "profile " + key;
                }

                JSONArray pkgs = obj.optJSONArray("PKGNAMES");
                if (pkgs != null) {
                    for (int i = 0; i < pkgs.length(); i++) {
                        map.put(pkgs.getString(i), label);
                    }
                }
            }
        } catch (Exception e) { 
            e.printStackTrace(); 
        }
        return map;
    }

    private static boolean saveMapToJson(List<AppModel> allApps, String path, String defaultOption, HashMap<String, String> labelToKeyMap) {
        try {
            File file = new File(path);
            JSONObject root = file.exists() ? new JSONObject(UwuhManager.readFile(path)) : new JSONObject();

            HashSet<String> installedPkgSet = new HashSet<>();
            Map<String, String> currentSelectionMap = new HashMap<>();

            for (AppModel app : allApps) {
                installedPkgSet.add(app.getPackageName());
                currentSelectionMap.put(app.getPackageName(), app.getSelectedConfig());
            }

            Map<String, HashSet<String>> finalPkgGroups = new HashMap<>();
            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                finalPkgGroups.put(key, new HashSet<>());

                JSONObject obj = root.optJSONObject(key);
                if (obj != null) {
                    JSONArray oldPkgs = obj.optJSONArray("PKGNAMES");
                    if (oldPkgs != null) {
                        for (int i = 0; i < oldPkgs.length(); i++) {
                            String oldPkg = oldPkgs.getString(i);
                            if (!installedPkgSet.contains(oldPkg)) {
                                finalPkgGroups.get(key).add(oldPkg);
                            }
                        }
                    }
                }
            }

            for (AppModel app : allApps) {
                String selectedLabel = app.getSelectedConfig();
                if (!selectedLabel.equals(defaultOption)) {
                    String actualKey = labelToKeyMap.get(selectedLabel);
                    if (actualKey != null) {
                        if (!finalPkgGroups.containsKey(actualKey)) {
                            finalPkgGroups.put(actualKey, new HashSet<>());
                        }
                        finalPkgGroups.get(actualKey).add(app.getPackageName());
                    }
                }
            }

            for (Map.Entry<String, HashSet<String>> entry : finalPkgGroups.entrySet()) {
                String key = entry.getKey();
                JSONArray newArray = new JSONArray();
                for (String pkg : entry.getValue()) {
                    newArray.put(pkg);
                }

                if (root.has(key)) {
                    JSONObject obj = root.getJSONObject(key);
                    obj.put("PKGNAMES", newArray);
                }
            }

            return UwuhManager.writeFile(path, root.toString(4));
        } catch (Exception e) { 
            return false; 
        }
    }
}
