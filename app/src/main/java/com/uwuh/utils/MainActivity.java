package com.uwuh.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements GamePropsThermalController.LogCallback {

    private static final int REQ_PICK_KEYBOX = 1001;
    private static final int REQ_PICK_PIF = 1002;
    private static final String TAG = "UwuhMain";

    private Switch switchBootloader, switchPIF, switchFinsky, switchManual, switchGameProps, switchThermals, switchAutoUpdate;
    private LinearLayout panelManual, panelGamePropsBtn, panelThermalsBtn, panelAutoUpdate;
    private Button btnPickKeybox, btnPickPIF, btnApplyManual, btnCheckUpdate, btnCopyLog;
    private Button btnGameProps, btnDevices, btnResetGameProps, btnThermals, btnResetThermals;
    private TextView tvKeyboxLastApply, tvPifLastApply, tvLastUpdate, tvLog, tvUpdateStatus, tvUpdateDetails, tvUpdateAvailable, tvAutoUpdateInfo;
    private EditText etPifEditor;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupListeners();
        updateUiState();
        loadUpdateStatus();
        checkForUpdateNotification();
        loadLogContent();
    }

    private void initViews() {
        switchBootloader = findViewById(R.id.switchBootloader);
        switchPIF = findViewById(R.id.switchPIF);
        switchFinsky = findViewById(R.id.switchFinsky);
        switchManual = findViewById(R.id.switchManual);
        switchGameProps = findViewById(R.id.switchGameProps);
        switchThermals = findViewById(R.id.switchThermals);
        switchAutoUpdate = findViewById(R.id.switchAutoUpdate);

        panelManual = findViewById(R.id.panelManual);
        panelGamePropsBtn = findViewById(R.id.panelGamePropsBtn);
        panelThermalsBtn = findViewById(R.id.panelThermalsBtn);
        panelAutoUpdate = findViewById(R.id.panelAutoUpdate);

        btnPickKeybox = findViewById(R.id.btnPickKeybox);
        btnPickPIF = findViewById(R.id.btnPickPIF);
        btnApplyManual = findViewById(R.id.btnApplyManual);
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate);
        btnCopyLog = findViewById(R.id.btnCopyLog);

        btnGameProps = findViewById(R.id.btnGameProps);
        btnDevices = findViewById(R.id.btnDevices);
        btnResetGameProps = findViewById(R.id.btnResetGameProps);
        btnThermals = findViewById(R.id.btnThermals);
        btnResetThermals = findViewById(R.id.btnResetThermals);

        tvKeyboxLastApply = findViewById(R.id.tvKeyboxLastApply);
        tvPifLastApply = findViewById(R.id.tvPifLastApply);
        tvLastUpdate = findViewById(R.id.tvLastUpdate);
        tvLog = findViewById(R.id.tvLog);
        tvUpdateStatus = findViewById(R.id.tvUpdateStatus);
        tvUpdateDetails = findViewById(R.id.tvUpdateDetails);
        tvUpdateAvailable = findViewById(R.id.tvUpdateAvailable);
        tvAutoUpdateInfo = findViewById(R.id.tvAutoUpdateInfo);

        etPifEditor = findViewById(R.id.etPifEditor);
    }

    private void setupListeners() {
        switchBootloader.setOnCheckedChangeListener((buttonView, isChecked) -> {
            UwuhManager.setProp(UwuhManager.PROP_BOOTLOADER, String.valueOf(isChecked));
            UwuhManager.appendLog("Bootloader prop set to: " + isChecked);
            refreshLog();
        });

        switchPIF.setOnCheckedChangeListener((buttonView, isChecked) -> {
            UwuhManager.setProp(UwuhManager.PROP_PIF, String.valueOf(isChecked));
            switchFinsky.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            UwuhManager.appendLog("PIF prop set to: " + isChecked);
            refreshLog();
        });

        switchFinsky.setOnCheckedChangeListener((buttonView, isChecked) -> {
            UwuhManager.setProp(UwuhManager.PROP_FINSKY, String.valueOf(isChecked));
            UwuhManager.appendLog("Finsky prop set to: " + isChecked);
            refreshLog();
        });

        switchManual.setOnCheckedChangeListener((buttonView, isChecked) -> {
            UwuhManager.setProp(UwuhManager.PROP_USE_CUSTOM, String.valueOf(isChecked));
            updateUiState();

            runAsyncWithLock(() -> {
                UwuhManager.syncAllToFramework(MainActivity.this, isChecked);
            }, () -> {
                UwuhManager.appendLog("Custom Mode switched to: " + isChecked + " (synced)");
                refreshLog();
                refreshFileMetadataUI();
                loadPifContentToEditText();
            });
        });

        switchAutoUpdate.setOnCheckedChangeListener((buttonView, isChecked) -> {
            UwuhManager.setProp(UwuhManager.PROP_AUTO_UPDATE, String.valueOf(isChecked));
            UwuhManager.appendLog("Auto-Update set to: " + isChecked);
            refreshLog();
            
            if (isChecked) {
                tvAutoUpdateInfo.setText("Auto-update enabled - will check at boot");
                tvAutoUpdateInfo.setTextColor(Color.GREEN);
            } else {
                tvAutoUpdateInfo.setText("Auto-update disabled - manual check only");
                tvAutoUpdateInfo.setTextColor(Color.RED);
            }
        });

        switchGameProps.setOnCheckedChangeListener((buttonView, isChecked) -> {
            UwuhManager.setProp(UwuhManager.PROP_GAMEPROPS, String.valueOf(isChecked));
            panelGamePropsBtn.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            UwuhManager.appendLog("GameProps prop set to: " + isChecked);
            refreshLog();
        });

        switchThermals.setOnCheckedChangeListener((buttonView, isChecked) -> {
            UwuhManager.setProp(UwuhManager.PROP_THERMALS, String.valueOf(isChecked));
            panelThermalsBtn.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            UwuhManager.appendLog("Thermals prop set to: " + isChecked);
            refreshLog();
        });

        btnPickKeybox.setOnClickListener(v -> openFilePicker("text/xml", REQ_PICK_KEYBOX));
        btnPickPIF.setOnClickListener(v -> openFilePicker("*/*", REQ_PICK_PIF));

        btnApplyManual.setOnClickListener(v -> {
            String content = etPifEditor.getText().toString().trim();
            if (content.isEmpty()) {
                Toast.makeText(MainActivity.this, "PIF content cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            runAsyncWithLock(() -> {
                boolean useCustom = switchManual.isChecked();
                String targetPath = useCustom ? UwuhManager.CUST_PIF_PATH : UwuhManager.PIF_PATH;
                UwuhManager.writeAndSync(MainActivity.this, UwuhManager.MODULE_PIF, targetPath, content);
            }, () -> {
                Toast.makeText(MainActivity.this, "PIF applied successfully", Toast.LENGTH_SHORT).show();
                loadPifContentToEditText();
                refreshLog();
                UwuhManager.appendLog("Manual PIF content applied & synced");
            });
        });

        btnCheckUpdate.setOnClickListener(v -> {
            boolean useCustom = switchManual.isChecked();
            if (useCustom) {
                Toast.makeText(MainActivity.this, "Custom mode: update check not available", Toast.LENGTH_SHORT).show();
                UwuhManager.appendLog("Update check skipped: Custom mode enabled");
                refreshLog();
                return;
            }
            
            runAsyncWithLock(() -> {
                checkUpdateOnlineNotifyOnly();
            }, () -> {
                loadUpdateStatus();
                checkForUpdateNotification();
                refreshLog();
                UwuhManager.appendLog("Update check completed (notify only)");
                Toast.makeText(MainActivity.this, "Update check completed", Toast.LENGTH_SHORT).show();
            });
        });

        btnGameProps.setOnClickListener(v -> 
            GamePropsThermalController.showAppConfigDialog(MainActivity.this, true, MainActivity.this));

        btnDevices.setOnClickListener(v -> 
            GamePropsThermalController.showDevicesManagerDialog(MainActivity.this, MainActivity.this));

        btnResetGameProps.setOnClickListener(v -> 
            GamePropsThermalController.resetGameProps(MainActivity.this, MainActivity.this));

        btnThermals.setOnClickListener(v -> 
            GamePropsThermalController.showAppConfigDialog(MainActivity.this, false, MainActivity.this));

        btnResetThermals.setOnClickListener(v -> 
            GamePropsThermalController.resetThermals(MainActivity.this, MainActivity.this));

        btnCopyLog.setOnClickListener(v -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("UwuhLog", tvLog.getText().toString());
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(MainActivity.this, "Log copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkUpdateOnlineNotifyOnly() {
        try {
            UwuhManager.appendLog("Checking for updates (notify only)...");
            
            String newKb = fetchUrl(BootReceiver.URL_KB);
            String newPif = fetchUrl(BootReceiver.URL_PIF);
            
            Context dpContext = createDeviceProtectedStorageContext();
            SharedPreferences sp = dpContext.getSharedPreferences("uwuh_prefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sp.edit();
            
            boolean hasUpdate = false;
            StringBuilder details = new StringBuilder();
            String date = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new Date());
            
            if (newKb != null && !newKb.isEmpty() && UwuhManager.isValidKeybox(newKb)) {
                String currentKb = UwuhManager.readFile(UwuhManager.KB_PATH)
                        .replaceAll("\r\n", "\n").trim();
                if (!newKb.equals(currentKb)) {
                    hasUpdate = true;
                    details.append("Keybox: New version available");
                }
            } else {
                if (newKb == null) {
                    UwuhManager.appendLog("Keybox: Download failed");
                } else if (newKb.isEmpty()) {
                    UwuhManager.appendLog("Keybox: Empty content from server");
                } else {
                    UwuhManager.appendLog("Keybox: Invalid format");
                }
            }
            
            if (newPif != null && !newPif.isEmpty() && UwuhManager.isValidPif(newPif)) {
                String currentPif = UwuhManager.readFile(UwuhManager.PIF_PATH)
                        .replaceAll("\r\n", "\n").trim();
                if (!newPif.equals(currentPif)) {
                    hasUpdate = true;
                    details.append(details.length() > 0 ? "\n" : "")
                           .append("PIF: New version available");
                }
            } else {
                if (newPif == null) {
                    UwuhManager.appendLog("PIF: Download failed");
                } else if (newPif.isEmpty()) {
                    UwuhManager.appendLog("PIF: Empty content from server");
                } else {
                    UwuhManager.appendLog("PIF: Invalid format");
                }
            }
            
            if (hasUpdate) {
                editor.putString("last_update", date);
                editor.putString("update_status", "UPDATE_AVAILABLE");
                editor.putString("update_details", details.toString());
                editor.putString("update_available", "true");
                UwuhManager.appendLog("Updates available: " + details.toString());
            } else {
                editor.putString("last_update", date);
                editor.putString("update_status", "UP_TO_DATE");
                editor.putString("update_details", "All files are up to date");
                editor.putString("update_available", "false");
                UwuhManager.appendLog("No updates available");
            }
            editor.apply();
            
        } catch (Exception e) {
            UwuhManager.appendLog("Update check failed: " + e.getMessage());
            Log.e(TAG, "Update check failed", e);
        }
        
        refreshLog();
    }

    private String fetchUrl(String urlStr) {
        try {
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
        } catch (Exception e) {
            return null;
        }
    }

    private void checkForUpdateNotification() {
        Context dpContext = createDeviceProtectedStorageContext();
        SharedPreferences sp = dpContext.getSharedPreferences("uwuh_prefs", Context.MODE_PRIVATE);
        
        String updateAvailable = sp.getString("update_available", "false");
        String details = sp.getString("update_details", "");
        
        if ("true".equals(updateAvailable) && !details.isEmpty()) {
            tvUpdateAvailable.setVisibility(View.VISIBLE);
            tvUpdateAvailable.setText("UPDATE AVAILABLE\n" + details);
            tvUpdateAvailable.setTextColor(Color.parseColor("#FFA500"));
        } else {
            tvUpdateAvailable.setVisibility(View.GONE);
        }
    }

    @Override
    public void log(String msg) {
        runOnUiThread(() -> {
            UwuhManager.appendLog(msg);
            refreshLog();
        });
    }

    private void refreshLog() {
        runOnUiThread(() -> {
            String content = UwuhManager.getLogContent();
            tvLog.setText(content.isEmpty() ? "[Log ready]" : content);
        });
    }

    private void loadLogContent() {
        String content = UwuhManager.getLogContent();
        tvLog.setText(content.isEmpty() ? "[Log ready]" : content);
    }

    private void updateUiState() {
        boolean useBootloader = UwuhManager.getPropBoolean(UwuhManager.PROP_BOOTLOADER, true);
        boolean usePif = UwuhManager.getPropBoolean(UwuhManager.PROP_PIF, true);
        boolean useFinsky = UwuhManager.getPropBoolean(UwuhManager.PROP_FINSKY, false);
        boolean useCustom = UwuhManager.getPropBoolean(UwuhManager.PROP_USE_CUSTOM, false);
        boolean useGameProps = UwuhManager.getPropBoolean(UwuhManager.PROP_GAMEPROPS, false);
        boolean useThermals = UwuhManager.getPropBoolean(UwuhManager.PROP_THERMALS, false);
        boolean autoUpdate = UwuhManager.getPropBoolean(UwuhManager.PROP_AUTO_UPDATE, true);

        switchBootloader.setChecked(useBootloader);
        switchPIF.setChecked(usePif);
        switchFinsky.setChecked(useFinsky);
        switchManual.setChecked(useCustom);
        switchGameProps.setChecked(useGameProps);
        switchThermals.setChecked(useThermals);
        switchAutoUpdate.setChecked(autoUpdate);

        switchFinsky.setVisibility(usePif ? View.VISIBLE : View.GONE);
        panelGamePropsBtn.setVisibility(useGameProps ? View.VISIBLE : View.GONE);
        panelThermalsBtn.setVisibility(useThermals ? View.VISIBLE : View.GONE);

        if (useCustom) {
            panelAutoUpdate.setVisibility(View.GONE);
            panelManual.setVisibility(View.VISIBLE);
        } else {
            panelAutoUpdate.setVisibility(View.VISIBLE);
            panelManual.setVisibility(View.GONE);
            
            if (autoUpdate) {
                tvAutoUpdateInfo.setText("Auto-update enabled - will check at boot");
                tvAutoUpdateInfo.setTextColor(Color.GREEN);
            } else {
                tvAutoUpdateInfo.setText("Auto-update disabled - manual check only");
                tvAutoUpdateInfo.setTextColor(Color.RED);
            }
        }

        refreshFileMetadataUI();
        loadPifContentToEditText();
    }

    private void refreshFileMetadataUI() {
        boolean useCustom = switchManual.isChecked();
        String kbPath = useCustom && new File(UwuhManager.CUST_KB_PATH).exists()
                ? UwuhManager.CUST_KB_PATH
                : UwuhManager.KB_PATH;

        File kbFile = new File(kbPath);
        if (kbFile.exists() && kbFile.length() > 0) {
            long lastModified = kbFile.lastModified();
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault());
            tvKeyboxLastApply.setText("Last apply: " + sdf.format(new Date(lastModified)));
        } else {
            tvKeyboxLastApply.setText("Last apply: -");
        }

        tvPifLastApply.setVisibility(View.GONE);
    }

    private void loadPifContentToEditText() {
        boolean useCustom = switchManual.isChecked();
        String pifPath = useCustom && new File(UwuhManager.CUST_PIF_PATH).exists()
                ? UwuhManager.CUST_PIF_PATH
                : UwuhManager.PIF_PATH;

        String content = UwuhManager.readFile(pifPath);
        etPifEditor.setText(content);
    }

    private void loadUpdateStatus() {
        Context dpContext = createDeviceProtectedStorageContext();
        SharedPreferences sp = dpContext.getSharedPreferences("uwuh_prefs", Context.MODE_PRIVATE);
        
        String lastUpdate = sp.getString("last_update", "Never");
        String status = sp.getString("update_status", "UNKNOWN");
        String details = sp.getString("update_details", "");
        
        tvLastUpdate.setText("Last update: " + lastUpdate);
        tvUpdateStatus.setText("Status: " + status);
        tvUpdateDetails.setText(details);
        tvUpdateDetails.setVisibility(details.isEmpty() ? View.GONE : View.VISIBLE);
        
        if ("UPDATE_AVAILABLE".equals(status)) {
            tvUpdateStatus.setTextColor(Color.parseColor("#FFA500"));
        } else if ("SUCCESS".equals(status) || "UP_TO_DATE".equals(status)) {
            tvUpdateStatus.setTextColor(Color.GREEN);
        } else if ("FAILED".equals(status) || "ERROR".equals(status)) {
            tvUpdateStatus.setTextColor(Color.RED);
        } else {
            tvUpdateStatus.setTextColor(Color.GRAY);
        }
    }

    private void openFilePicker(String mimeType, int requestCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType(mimeType);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Select File"), requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();

            runAsyncWithLock(() -> {
                try {
                    InputStream inputStream = getContentResolver().openInputStream(uri);
                    if (inputStream == null) return;

                    byte[] buffer = new byte[inputStream.available()];
                    int bytesRead = inputStream.read(buffer);
                    inputStream.close();

                    if (bytesRead > 0) {
                        String fileContent = new String(buffer, 0, bytesRead).trim();

                        if (requestCode == REQ_PICK_KEYBOX) {
                            String targetPath = switchManual.isChecked() ? UwuhManager.CUST_KB_PATH : UwuhManager.KB_PATH;
                            UwuhManager.writeAndSync(MainActivity.this, UwuhManager.MODULE_KEYBOX, targetPath, fileContent);
                        } else if (requestCode == REQ_PICK_PIF) {
                            String targetPath = switchManual.isChecked() ? UwuhManager.CUST_PIF_PATH : UwuhManager.PIF_PATH;
                            UwuhManager.writeAndSync(MainActivity.this, UwuhManager.MODULE_PIF, targetPath, fileContent);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, () -> {
                Toast.makeText(MainActivity.this, "File written and synced successfully", Toast.LENGTH_SHORT).show();
                refreshFileMetadataUI();
                loadPifContentToEditText();
                loadUpdateStatus();
                refreshLog();
                UwuhManager.appendLog("Custom file imported & synced to framework");
            });
        }
    }

    private void setUiEnabled(boolean enabled) {
        switchBootloader.setEnabled(enabled);
        switchPIF.setEnabled(enabled);
        switchFinsky.setEnabled(enabled);
        switchManual.setEnabled(enabled);
        switchGameProps.setEnabled(enabled);
        switchThermals.setEnabled(enabled);
        switchAutoUpdate.setEnabled(enabled);

        btnPickKeybox.setEnabled(enabled);
        btnPickPIF.setEnabled(enabled);
        btnApplyManual.setEnabled(enabled);
        btnCheckUpdate.setEnabled(enabled);
        btnCopyLog.setEnabled(enabled);

        btnGameProps.setEnabled(enabled);
        btnDevices.setEnabled(enabled);
        btnResetGameProps.setEnabled(enabled);
        btnThermals.setEnabled(enabled);
        btnResetThermals.setEnabled(enabled);

        etPifEditor.setEnabled(enabled);
    }

    private void runAsyncWithLock(Runnable backgroundTask, Runnable onComplete) {
        setUiEnabled(false);
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                backgroundTask.run();
            } finally {
                mainHandler.post(() -> {
                    setUiEnabled(true);
                    if (onComplete != null) {
                        onComplete.run();
                    }
                });
            }
        });
    }
}
