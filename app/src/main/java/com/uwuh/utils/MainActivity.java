package com.uwuh.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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

    private Switch switchBootloader, switchPIF, switchFinsky, switchCustom;
    private Switch switchAutoUpdate, switchGPhotos, switchNetflix;
    private Switch switchGameProps, switchThermals;
    private LinearLayout panelCustom, panelGamePropsBtn, panelThermalsBtn, panelAutoUpdate;
    private Button btnPickKeybox, btnPickPIF, btnApplyCustom, btnCheckUpdate, btnCopyLog;
    private Button btnGameProps, btnDevices, btnResetGameProps, btnThermals, btnResetThermals;
    private TextView tvKeyboxLastApply, tvPifLastApply, tvAutoUpdateInfo, tvLastUpdate;
    private EditText tvLog;
    private EditText etPifEditor;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupListeners();
        updateUiState();
        loadLogContent();
        
        // Auto-check update saat app dibuka
        if (UwuhManager.isAutoUpdateEnabled() && !UwuhManager.isCustomMode()) {
            runAsyncWithLock(() -> {
                checkAndApplyUpdate();
            }, () -> {
                refreshLog();
                updateLastUpdate();
                UwuhManager.appendLog("Auto-check update completed on app open");
            });
        }
    }

    private void initViews() {
        switchBootloader = findViewById(R.id.switchBootloader);
        switchPIF = findViewById(R.id.switchPIF);
        switchFinsky = findViewById(R.id.switchFinsky);
        switchCustom = findViewById(R.id.switchCustom);
        switchAutoUpdate = findViewById(R.id.switchAutoUpdate);
        switchGPhotos = findViewById(R.id.switchGPhotos);
        switchNetflix = findViewById(R.id.switchNetflix);
        switchGameProps = findViewById(R.id.switchGameProps);
        switchThermals = findViewById(R.id.switchThermals);

        panelCustom = findViewById(R.id.panelCustom);
        panelGamePropsBtn = findViewById(R.id.panelGamePropsBtn);
        panelThermalsBtn = findViewById(R.id.panelThermalsBtn);
        panelAutoUpdate = findViewById(R.id.panelAutoUpdate);

        btnPickKeybox = findViewById(R.id.btnPickKeybox);
        btnPickPIF = findViewById(R.id.btnPickPIF);
        btnApplyCustom = findViewById(R.id.btnApplyCustom);
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate);
        btnCopyLog = findViewById(R.id.btnCopyLog);

        btnGameProps = findViewById(R.id.btnGameProps);
        btnDevices = findViewById(R.id.btnDevices);
        btnResetGameProps = findViewById(R.id.btnResetGameProps);
        btnThermals = findViewById(R.id.btnThermals);
        btnResetThermals = findViewById(R.id.btnResetThermals);

        tvKeyboxLastApply = findViewById(R.id.tvKeyboxLastApply);
        tvPifLastApply = findViewById(R.id.tvPifLastApply);
        tvLog = findViewById(R.id.tvLog);
        tvAutoUpdateInfo = findViewById(R.id.tvAutoUpdateInfo);
        tvLastUpdate = findViewById(R.id.tvLastUpdate);

        etPifEditor = findViewById(R.id.etPifEditor);

        // EditText settings untuk log
        tvLog.setHorizontallyScrolling(false);
        tvLog.setVerticalScrollBarEnabled(true);
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

        switchCustom.setOnCheckedChangeListener((buttonView, isChecked) -> {
            UwuhManager.setProp(UwuhManager.PROP_USE_CUSTOM, String.valueOf(isChecked));
            updateUiState();

            runAsyncWithLock(() -> {
                UwuhManager.syncAllToFramework(MainActivity.this, isChecked);
            }, () -> {
                UwuhManager.appendLog("Custom Mode switched to: " + isChecked + " (synced)");
                refreshLog();
                refreshFileMetadataUI();
                loadPifContentToEditText();
                updateLastUpdate();
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

        switchGPhotos.setOnCheckedChangeListener((buttonView, isChecked) -> {
            UwuhManager.setProp(UwuhManager.PROP_GPHOTOS, String.valueOf(isChecked));
            UwuhManager.appendLog("Google Photos unlimited set to: " + isChecked);
            refreshLog();
        });

        switchNetflix.setOnCheckedChangeListener((buttonView, isChecked) -> {
            UwuhManager.setProp(UwuhManager.PROP_NETFLIX, String.valueOf(isChecked));
            UwuhManager.appendLog("Netflix spoof set to: " + isChecked);
            refreshLog();
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

        btnApplyCustom.setOnClickListener(v -> {
            String content = etPifEditor.getText().toString().trim();
            if (content.isEmpty()) {
                Toast.makeText(MainActivity.this, "PIF content cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            runAsyncWithLock(() -> {
                boolean useCustom = switchCustom.isChecked();
                String targetPath = useCustom ? UwuhManager.CUST_PIF_PATH : UwuhManager.PIF_PATH;
                UwuhManager.writeAndSync(MainActivity.this, UwuhManager.MODULE_PIF, targetPath, content);
            }, () -> {
                Toast.makeText(MainActivity.this, "PIF applied successfully", Toast.LENGTH_SHORT).show();
                loadPifContentToEditText();
                refreshLog();
                UwuhManager.appendLog("Custom PIF content applied & synced");
                updateLastUpdate();
            });
        });

        btnCheckUpdate.setOnClickListener(v -> {
            boolean useCustom = switchCustom.isChecked();
            if (useCustom) {
                Toast.makeText(MainActivity.this, "Custom mode: update not available", Toast.LENGTH_SHORT).show();
                UwuhManager.appendLog("Update skipped: Custom mode enabled");
                refreshLog();
                return;
            }
            
            runAsyncWithLock(() -> {
                checkAndApplyUpdate();
            }, () -> {
                refreshLog();
                updateLastUpdate();
                UwuhManager.appendLog("Update check completed");
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

    private void checkAndApplyUpdate() {
        try {
            UwuhManager.appendLog("Checking and applying updates...");
            
            String newKb = fetchUrl(BootReceiver.URL_KB);
            String newPif = fetchUrl(BootReceiver.URL_PIF);

            if (newKb != null && !newKb.isEmpty() && UwuhManager.isValidKeybox(newKb)) {
                String currentKb = UwuhManager.readFile(UwuhManager.KB_PATH)
                        .replaceAll("\r\n", "\n").trim();
                if (!newKb.equals(currentKb)) {
                    UwuhManager.writeAndSync(this, UwuhManager.MODULE_KEYBOX, UwuhManager.KB_PATH, newKb);
                    UwuhManager.appendLog("Keybox updated from server");
                    Toast.makeText(this, "Keybox updated", Toast.LENGTH_SHORT).show();
                } else {
                    UwuhManager.appendLog("Keybox already up to date");
                }
            } else {
                if (newKb == null) {
                    UwuhManager.appendLog("Keybox: Download failed");
                    Toast.makeText(this, "Keybox: Download failed", Toast.LENGTH_SHORT).show();
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
                    UwuhManager.writeAndSync(this, UwuhManager.MODULE_PIF, UwuhManager.PIF_PATH, newPif);
                    UwuhManager.appendLog("PIF updated from server");
                    Toast.makeText(this, "PIF updated", Toast.LENGTH_SHORT).show();
                } else {
                    UwuhManager.appendLog("PIF already up to date");
                }
            } else {
                if (newPif == null) {
                    UwuhManager.appendLog("PIF: Download failed");
                    Toast.makeText(this, "PIF: Download failed", Toast.LENGTH_SHORT).show();
                } else if (newPif.isEmpty()) {
                    UwuhManager.appendLog("PIF: Empty content from server");
                } else {
                    UwuhManager.appendLog("PIF: Invalid format");
                }
            }
            
        } catch (Exception e) {
            UwuhManager.appendLog("Update check failed: " + e.getMessage());
            Log.e(TAG, "Update check failed", e);
            Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String fetchUrl(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestMethod("GET");
            c.setRequestProperty("User-Agent", "Uwuh-Android/1.0");
            
            if (c.getResponseCode() != 200) return null;
            
            int contentLength = c.getContentLength();
            if (contentLength == 0) return "";
            
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append("\n");
            }
            r.close();
            return sb.toString().trim();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void log(String msg) {
        runOnUiThread(() -> {
            UwuhManager.appendLog(msg);
            refreshLog();
        });
    }

    /**
     * Refresh log dan auto-scroll ke bawah
     */
    private void refreshLog() {
        runOnUiThread(() -> {
            String content = UwuhManager.getLogContent();
            tvLog.setText(content.isEmpty() ? "[Log ready]" : content);
            scrollToBottom();
        });
    }

    /**
     * Load log dan auto-scroll ke bawah
     */
    private void loadLogContent() {
        String content = UwuhManager.getLogContent();
        tvLog.setText(content.isEmpty() ? "[Log ready]" : content);
        scrollToBottom();
    }

    /**
     * Scroll EditText ke posisi paling bawah (log terbaru)
     */
    private void scrollToBottom() {
        tvLog.post(() -> {
            int lineCount = tvLog.getLineCount();
            if (lineCount > 0 && tvLog.getLayout() != null) {
                int scrollY = tvLog.getLayout().getLineTop(lineCount - 1);
                int maxScrollY = Math.max(0, scrollY - tvLog.getHeight() + tvLog.getPaddingBottom());
                tvLog.scrollTo(0, maxScrollY);
            }
        });
    }

    private void updateLastUpdate() {
        String kbPath = UwuhManager.KB_PATH;
        File file = new File(kbPath);
        if (file.exists() && file.length() > 0) {
            long lastModified = file.lastModified();
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault());
            tvLastUpdate.setText("Last update: " + sdf.format(new Date(lastModified)));
        } else {
            tvLastUpdate.setText("Last update: -");
        }
    }

    private void updateUiState() {
        boolean useBootloader = UwuhManager.getPropBoolean(UwuhManager.PROP_BOOTLOADER, true);
        boolean usePif = UwuhManager.getPropBoolean(UwuhManager.PROP_PIF, true);
        boolean useFinsky = UwuhManager.getPropBoolean(UwuhManager.PROP_FINSKY, false);
        boolean useCustom = UwuhManager.getPropBoolean(UwuhManager.PROP_USE_CUSTOM, false);
        boolean autoUpdate = UwuhManager.getPropBoolean(UwuhManager.PROP_AUTO_UPDATE, true);
        boolean gPhotos = UwuhManager.getPropBoolean(UwuhManager.PROP_GPHOTOS, false);
        boolean netflix = UwuhManager.getPropBoolean(UwuhManager.PROP_NETFLIX, false);
        boolean useGameProps = UwuhManager.getPropBoolean(UwuhManager.PROP_GAMEPROPS, false);
        boolean useThermals = UwuhManager.getPropBoolean(UwuhManager.PROP_THERMALS, false);

        switchBootloader.setChecked(useBootloader);
        switchPIF.setChecked(usePif);
        switchFinsky.setChecked(useFinsky);
        switchCustom.setChecked(useCustom);
        switchAutoUpdate.setChecked(autoUpdate);
        switchGPhotos.setChecked(gPhotos);
        switchNetflix.setChecked(netflix);
        switchGameProps.setChecked(useGameProps);
        switchThermals.setChecked(useThermals);

        switchFinsky.setVisibility(usePif ? View.VISIBLE : View.GONE);
        panelGamePropsBtn.setVisibility(useGameProps ? View.VISIBLE : View.GONE);
        panelThermalsBtn.setVisibility(useThermals ? View.VISIBLE : View.GONE);

        if (useCustom) {
            panelAutoUpdate.setVisibility(View.GONE);
            panelCustom.setVisibility(View.VISIBLE);
            btnCheckUpdate.setVisibility(View.GONE);
            tvLastUpdate.setVisibility(View.GONE);
        } else {
            panelAutoUpdate.setVisibility(View.VISIBLE);
            panelCustom.setVisibility(View.GONE);
            btnCheckUpdate.setVisibility(View.VISIBLE);
            tvLastUpdate.setVisibility(View.VISIBLE);
            
            if (autoUpdate) {
                tvAutoUpdateInfo.setText("Auto-update enabled - will check at boot");
                tvAutoUpdateInfo.setTextColor(Color.GREEN);
            } else {
                tvAutoUpdateInfo.setText("Auto-update disabled - manual check only");
                tvAutoUpdateInfo.setTextColor(Color.RED);
            }
            
            updateLastUpdate();
        }

        refreshFileMetadataUI();
        loadPifContentToEditText();
    }

    private void refreshFileMetadataUI() {
        boolean useCustom = switchCustom.isChecked();
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
        boolean useCustom = switchCustom.isChecked();
        String pifPath = useCustom && new File(UwuhManager.CUST_PIF_PATH).exists()
                ? UwuhManager.CUST_PIF_PATH
                : UwuhManager.PIF_PATH;

        String content = UwuhManager.readFile(pifPath);
        etPifEditor.setText(content);
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
                            String targetPath = switchCustom.isChecked() ? UwuhManager.CUST_KB_PATH : UwuhManager.KB_PATH;
                            UwuhManager.writeAndSync(MainActivity.this, UwuhManager.MODULE_KEYBOX, targetPath, fileContent);
                        } else if (requestCode == REQ_PICK_PIF) {
                            String targetPath = switchCustom.isChecked() ? UwuhManager.CUST_PIF_PATH : UwuhManager.PIF_PATH;
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
                refreshLog();
                updateLastUpdate();
                UwuhManager.appendLog("Custom file imported & synced to framework");
            });
        }
    }

    private void setUiEnabled(boolean enabled) {
        switchBootloader.setEnabled(enabled);
        switchPIF.setEnabled(enabled);
        switchFinsky.setEnabled(enabled);
        switchCustom.setEnabled(enabled);
        switchAutoUpdate.setEnabled(enabled);
        switchGPhotos.setEnabled(enabled);
        switchNetflix.setEnabled(enabled);
        switchGameProps.setEnabled(enabled);
        switchThermals.setEnabled(enabled);

        btnPickKeybox.setEnabled(enabled);
        btnPickPIF.setEnabled(enabled);
        btnApplyCustom.setEnabled(enabled);
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
