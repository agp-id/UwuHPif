package com.uwuh.utils;

import android.app.Activity;
import android.app.AlertDialog;
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

public class MainActivity extends Activity {

    private static final int REQ_PICK_KEYBOX = 1001;
    private static final int REQ_PICK_PIF = 1002;

    // View berdasarkan ID di activity_main.xml
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

        // Tombol GameProps & Thermals
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
        // 1. Switch Bootloader
        switchBootloader.setOnCheckedChangeListener((buttonView, isChecked) -> {
            UwuhManager.setProp(UwuhManager.PROP_BOOTLOADER, String.valueOf(isChecked));
        });

        // 2. Switch PIF
        switchPIF.setOnCheckedChangeListener((buttonView, isChecked) -> {
            UwuhManager.setProp(UwuhManager.PROP_PIF, String.valueOf(isChecked));
            switchFinsky.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // 2b. Switch Play Store Fingerprint (Finsky)
        switchFinsky.setOnCheckedChangeListener((buttonView, isChecked) -> {
            UwuhManager.setProp(UwuhManager.PROP_FINSKY, String.valueOf(isChecked));
        });

        // 3. Switch Manual Mode
        switchManual.setOnCheckedChangeListener((buttonView, isChecked) -> {
            UwuhManager.setProp(UwuhManager.PROP_USE_CUSTOM, String.valueOf(isChecked));
            updateUiState();

            runAsyncWithLock(() -> {
                UwuhManager.syncAllToFramework(isChecked);
            }, null);
        });

        // 4. Switch GameProps
        switchGameProps.setOnCheckedChangeListener((buttonView, isChecked) -> {
            UwuhManager.setProp(UwuhManager.PROP_GAMEPROPS, String.valueOf(isChecked));
            panelGamePropsBtn.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // 5. Switch Thermals
        switchThermals.setOnCheckedChangeListener((buttonView, isChecked) -> {
            UwuhManager.setProp(UwuhManager.PROP_THERMALS, String.valueOf(isChecked));
            panelThermalsBtn.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // Tombol Pilih File Keybox & PIF
        btnPickKeybox.setOnClickListener(v -> openFilePicker("text/xml", REQ_PICK_KEYBOX));
        btnPickPIF.setOnClickListener(v -> openFilePicker("*/*", REQ_PICK_PIF));

        // Tombol Apply Manual PIF
        btnApplyManual.setOnClickListener(v -> {
            String content = etPifEditor.getText().toString().trim();
            if (content.isEmpty()) {
                Toast.makeText(MainActivity.this, "Konten PIF tidak boleh kosong!", Toast.LENGTH_SHORT).show();
                return;
            }

            runAsyncWithLock(() -> {
                boolean useCustom = switchManual.isChecked();
                String targetPath = useCustom ? UwuhManager.CUST_PIF_PATH : UwuhManager.PIF_PATH;
                UwuhManager.writeAndSync(UwuhManager.MODULE_PIF, targetPath, content);
            }, () -> {
                Toast.makeText(MainActivity.this, "PIF berhasil diterapkan!", Toast.LENGTH_SHORT).show();
                loadPifContentToEditText();
            });
        });

        // Tombol Cek Update Online / Sync All
        btnUpdate.setOnClickListener(v -> {
            runAsyncWithLock(() -> {
                boolean useCustom = switchManual.isChecked();
                UwuhManager.syncAllToFramework(useCustom);
            }, () -> {
                Toast.makeText(MainActivity.this, "Sync & Update Selesai!", Toast.LENGTH_SHORT).show();
                refreshFileMetadataUI();
            });
        });

        // === LISTENER GAMEPROPS & THERMALS ===

        // Popup Edit App Config GameProps
        btnGameProps.setOnClickListener(v -> showJsonEditorDialog("Edit GameProps Config", UwuhManager.GAMEPROPS_PATH, UwuhManager.MODULE_GAMEPROPS));

        // Popup Pilihan Devices Preset GameProps
        btnDevices.setOnClickListener(v -> showDevicesPresetDialog());

        // Reset GameProps ke Default
        btnResetGameProps.setOnClickListener(v -> showResetConfirmDialog("GameProps", UwuhManager.GAMEPROPS_PATH, UwuhManager.MODULE_GAMEPROPS));

        // Popup Edit Thermals
        btnThermals.setOnClickListener(v -> showJsonEditorDialog("Edit Thermals Config", UwuhManager.THERMALS_PATH, UwuhManager.MODULE_THERMALS));

        // Reset Thermals ke Default
        btnResetThermals.setOnClickListener(v -> showResetConfirmDialog("Thermals", UwuhManager.THERMALS_PATH, UwuhManager.MODULE_THERMALS));

        // Copy Log Button
        btnCopyLog.setOnClickListener(v -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("UwuhLog", tvLog.getText().toString());
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(MainActivity.this, "Log disalin ke clipboard!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Membuka Popup Editor untuk Mengubah Berkas JSON (GameProps / Thermals)
     */
    private void showJsonEditorDialog(String title, String filePath, String moduleKey) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);

        final EditText input = new EditText(this);
        input.setText(UwuhManager.readFile(filePath));
        input.setMinLines(8);
        input.setGravity(android.view.Gravity.TOP);
        builder.setView(input);

        builder.setPositiveButton("Simpan", (dialog, which) -> {
            String newContent = input.getText().toString().trim();
            runAsyncWithLock(() -> {
                UwuhManager.writeAndSync(moduleKey, filePath, newContent);
            }, () -> {
                Toast.makeText(MainActivity.this, title + " berhasil disimpan!", Toast.LENGTH_SHORT).show();
            });
        });

        builder.setNegativeButton("Batal", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    /**
     * Membuka Popup Pemilihan Preset Device GameProps
     */
    private void showDevicesPresetDialog() {
        final String[] devices = {"ROG Phone 8 Pro", "Xiaomi 14 Pro", "Galaxy S24 Ultra", "iPad Pro M4"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Pilih Preset Device GameProps");
        builder.setItems(devices, (dialog, which) -> {
            String selectedDevice = devices[which];
            Toast.makeText(MainActivity.this, "Device dipilih: " + selectedDevice, Toast.LENGTH_SHORT).show();
            // Nanti dapat disesuaikan logika penulisan preset JSON berdasarkan device terpilih
        });
        builder.show();
    }

    /**
     * Popup Konfirmasi Reset Config
     */
    private void showResetConfirmDialog(String name, String filePath, String moduleKey) {
        new AlertDialog.Builder(this)
                .setTitle("Reset " + name)
                .setMessage("Apakah Anda yakin ingin menghapus konfigurasi " + name + "?")
                .setPositiveButton("Ya", (dialog, which) -> {
                    runAsyncWithLock(() -> {
                        File f = new File(filePath);
                        if (f.exists()) f.delete();
                        UwuhManager.syncToFramework(moduleKey, filePath);
                    }, () -> {
                        Toast.makeText(MainActivity.this, name + " telah di-reset!", Toast.LENGTH_SHORT).show();
                    });
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    /**
     * Membaca seluruh SystemProperties dan memperbarui status visual UI
     */
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

    /**
     * Membaca timestamp Last Modified dari file Keybox fisik
     */
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

    /**
     * Memuat isi file PIF langsung ke EditText
     */
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
                            UwuhManager.writeAndSync(UwuhManager.MODULE_KEYBOX, targetPath, fileContent);
                        } else if (requestCode == REQ_PICK_PIF) {
                            String targetPath = switchManual.isChecked() ? UwuhManager.CUST_PIF_PATH : UwuhManager.PIF_PATH;
                            UwuhManager.writeAndSync(UwuhManager.MODULE_PIF, targetPath, fileContent);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, () -> {
                Toast.makeText(MainActivity.this, "File berhasil ditulis dan disinkronkan!", Toast.LENGTH_SHORT).show();
                refreshFileMetadataUI();
                loadPifContentToEditText();
            });
        }
    }

    /**
     * Mengunci seluruh tombol & switch saat proses async berlangsung
     */
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

    /**
     * Helper Runnable Async dengan proteksi penguncian UI
     */
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
