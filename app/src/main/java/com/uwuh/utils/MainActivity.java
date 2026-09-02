package com.uwuh.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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

import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements GamePropsThermalController.LogCallback {

    private static final int REQ_PICK_KEYBOX = 1001;
    private static final int REQ_PICK_PIF = 1002;

    private Switch switchBootloader, switchPIF, switchFinsky, switchManual, switchGameProps, switchThermals;
    private LinearLayout panelManual, panelGamePropsBtn, panelThermalsBtn;
    private Button btnPickKeybox, btnPickPIF, btnApplyManual, btnUpdate, btnCopyLog;
    private Button btnGameProps, btnDevices, btnResetGameProps, btnThermals, btnResetThermals;
    private TextView tvKeyboxLastApply, tvPifLastApply, tvAutoStatus, tvLastUpdate, tvLog;
    private EditText etPifEditor;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupListeners();
        updateUiState();
    }

    private void initViews() {
        switchBootloader = findViewById(R.id.switchBootloader);
        switchPIF = findViewById(R.id.switchPIF);
        switchFinsky = findViewById(R.id.switchFinsky);
        switchManual = findViewById(R.id.switchManual);
        switchGameProps = findViewById(R.id.switchGameProps);
        switchThermals = findViewById(R.id.switchThermals);

        panelManual = findViewById(R.id.panelManual);
        panelGamePropsBtn = findViewById(R.id.panelGamePropsBtn);
        panelThermalsBtn = findViewById(R.id.panelThermalsBtn);

        btnPickKeybox = findViewById(R.id.btnPickKeybox);
        btnPickPIF = findViewById(R.id.btnPickPIF);
        btnApplyManual = findViewById(R.id.btnApplyManual);
        btnUpdate = findViewById(R.id.btnUpdate);
        btnCopyLog = findViewById(R.id.btnCopyLog);

        btnGameProps = findViewById(R.id.btnGameProps);
        btnDevices = findViewById(R.id.btnDevices);
        btnResetGameProps = findViewById(R.id.btnResetGameProps);
        btnThermals = findViewById(R.id.btnThermals);
        btnResetThermals = findViewById(R.id.btnResetThermals);

        tvKeyboxLastApply = findViewById(R.id.tvKeyboxLastApply);
        tvPifLastApply = findViewById(R.id.tvPifLastApply);
        tvAutoStatus = findViewById(R.id.tvAutoStatus);
        tvLastUpdate = findViewById(R.id.tvLastUpdate);
        tvLog = findViewById(R.id.tvLog);

        etPifEditor = findViewById(R.id.etPifEditor);
    }

    private void setupListeners() {
        switchBootloader.setOnCheckedChangeListener((buttonView, isChecked) -> {
            UwuhManager.setProp(UwuhManager.PROP_BOOTLOADER, String.valueOf(isChecked));
            appendLog("Bootloader prop set to: " + isChecked);
        });

        switchPIF.setOnCheckedChangeListener((buttonView, isChecked) -> {
            UwuhManager.setProp(UwuhManager.PROP_PIF, String.valueOf(isChecked));
            switchFinsky.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            appendLog("PIF prop set to: " + isChecked);
        });

        switchFinsky.setOnCheckedChangeListener((buttonView, isChecked) -> {
            UwuhManager.setProp(UwuhManager.PROP_FINSKY, String.valueOf(isChecked));
            appendLog("Finsky prop set to: " + isChecked);
        });

        switchManual.setOnCheckedChangeListener((buttonView, isChecked) -> {
            UwuhManager.setProp(UwuhManager.PROP_USE_CUSTOM, String.valueOf(isChecked));
            updateUiState();

            runAsyncWithLock(() -> {
                UwuhManager.syncAllToFramework(MainActivity.this, isChecked);
            }, () -> appendLog("Custom Mode switched to: " + isChecked));
        });

        switchGameProps.setOnCheckedChangeListener((buttonView, isChecked) -> {
            UwuhManager.setProp(UwuhManager.PROP_GAMEPROPS, String.valueOf(isChecked));
            panelGamePropsBtn.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            appendLog("GameProps prop set to: " + isChecked);
        });

        switchThermals.setOnCheckedChangeListener((buttonView, isChecked) -> {
            UwuhManager.setProp(UwuhManager.PROP_THERMALS, String.valueOf(isChecked));
            panelThermalsBtn.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            appendLog("Thermals prop set to: " + isChecked);
        });

        btnPickKeybox.setOnClickListener(v -> openFilePicker("text/xml", REQ_PICK_KEYBOX));
        btnPickPIF.setOnClickListener(v -> openFilePicker("*/*", REQ_PICK_PIF));

        btnApplyManual.setOnClickListener(v -> {
            String content = etPifEditor.getText().toString().trim();
            if (content.isEmpty()) {
                Toast.makeText(MainActivity.this, "Konten PIF tidak boleh kosong!", Toast.LENGTH_SHORT).show();
                return;
            }

            runAsyncWithLock(() -> {
                boolean useCustom = switchManual.isChecked();
                String targetPath = useCustom ? UwuhManager.CUST_PIF_PATH : UwuhManager.PIF_PATH;
                UwuhManager.writeAndSync(MainActivity.this, UwuhManager.MODULE_PIF, targetPath, content);
            }, () -> {
                Toast.makeText(MainActivity.this, "PIF berhasil diterapkan!", Toast.LENGTH_SHORT).show();
                loadPifContentToEditText();
                appendLog("Manual PIF content applied & chunked");
            });
        });

        btnUpdate.setOnClickListener(v -> {
            runAsyncWithLock(() -> {
                boolean useCustom = switchManual.isChecked();
                UwuhManager.syncAllToFramework(MainActivity.this, useCustom);
            }, () -> {
                Toast.makeText(MainActivity.this, "Sync & Update Selesai!", Toast.LENGTH_SHORT).show();
                refreshFileMetadataUI();
                appendLog("Manual trigger sync completed");
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
                Toast.makeText(MainActivity.this, "Log disalin ke clipboard!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void log(String msg) {
        runOnUiThread(() -> appendLog(msg));
    }

    private void appendLog(String msg) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String currentLog = tvLog.getText().toString();
        tvLog.setText("[" + time + "] " + msg + "\n" + currentLog);
    }

    private void updateUiState() {
        boolean useBootloader = UwuhManager.getPropBoolean(UwuhManager.PROP_BOOTLOADER, true);
        boolean usePif = UwuhManager.getPropBoolean(UwuhManager.PROP_PIF, true);
        boolean useFinsky = UwuhManager.getPropBoolean(UwuhManager.PROP_FINSKY, false);
        boolean useCustom = UwuhManager.getPropBoolean(UwuhManager.PROP_USE_CUSTOM, false);
        boolean useGameProps = UwuhManager.getPropBoolean(UwuhManager.PROP_GAMEPROPS, false);
        boolean useThermals = UwuhManager.getPropBoolean(UwuhManager.PROP_THERMALS, false);

        switchBootloader.setChecked(useBootloader);
        switchPIF.setChecked(usePif);
        switchFinsky.setChecked(useFinsky);
        switchManual.setChecked(useCustom);
        switchGameProps.setChecked(useGameProps);
        switchThermals.setChecked(useThermals);

        switchFinsky.setVisibility(usePif ? View.VISIBLE : View.GONE);
        panelGamePropsBtn.setVisibility(useGameProps ? View.VISIBLE : View.GONE);
        panelThermalsBtn.setVisibility(useThermals ? View.VISIBLE : View.GONE);

        if (useCustom) {
            panelManual.setVisibility(View.VISIBLE);
            tvAutoStatus.setVisibility(View.GONE);
            tvLastUpdate.setVisibility(View.GONE);
            btnUpdate.setVisibility(View.GONE);
        } else {
            panelManual.setVisibility(View.GONE);
            tvAutoStatus.setVisibility(View.VISIBLE);
            tvLastUpdate.setVisibility(View.VISIBLE);
            btnUpdate.setVisibility(View.VISIBLE);
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

    private void openFilePicker(String mimeType, int requestCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType(mimeType);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Pilih File"), requestCode);
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
                Toast.makeText(MainActivity.this, "File berhasil ditulis dan disinkronkan!", Toast.LENGTH_SHORT).show();
                refreshFileMetadataUI();
                loadPifContentToEditText();
                appendLog("Custom file imported & synced to framework");
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

        btnPickKeybox.setEnabled(enabled);
        btnPickPIF.setEnabled(enabled);
        btnApplyManual.setEnabled(enabled);
        btnUpdate.setEnabled(enabled);
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
