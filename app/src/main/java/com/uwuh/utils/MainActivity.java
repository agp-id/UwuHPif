package com.uwuh.utils;

import android.app.Activity;
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

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_PICK_KEYBOX = 1001;
    private static final int REQ_PICK_PIF = 1002;

    private Switch switchUseCustom;
    private LinearLayout layoutUpdateSection;
    private LinearLayout layoutCustomFileSection;

    private Button btnSelectKeybox, btnSelectPif, btnApplyManualPif, btnCheckUpdate;
    private TextView tvKeyboxLastEdit, tvPifStatus;
    private EditText etPifContent;

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
        switchUseCustom = findViewById(R.id.switch_use_custom);
        layoutUpdateSection = findViewById(R.id.layout_update_section);
        layoutCustomFileSection = findViewById(R.id.layout_custom_file_section);

        btnSelectKeybox = findViewById(R.id.btn_select_keybox);
        btnSelectPif = findViewById(R.id.btn_select_pif);
        btnApplyManualPif = findViewById(R.id.btn_apply_manual_pif);
        btnCheckUpdate = findViewById(R.id.btn_check_update);

        tvKeyboxLastEdit = findViewById(R.id.tv_keybox_last_edit);
        tvPifStatus = findViewById(R.id.tv_pif_status);
        etPifContent = findViewById(R.id.et_pif_content);
    }

    private void setupListeners() {
        // Toggle Custom Switch
        switchUseCustom.setOnCheckedChangeListener((buttonView, isChecked) -> {
            UwuhManager.setProp(UwuhManager.PROP_USE_CUSTOM, String.valueOf(isChecked));
            updateUiState();
            
            // Sinkronkan ulang pilihan ke framework secara async
            runAsyncWithLock(() -> {
                UwuhManager.syncAllToFramework(isChecked);
            }, null);
        });

        // Tombol Select File
        btnSelectKeybox.setOnClickListener(v -> openFilePicker("text/xml", REQ_PICK_KEYBOX));
        btnSelectPif.setOnClickListener(v -> openFilePicker("*/*", REQ_PICK_PIF));

        // Tombol Manual Edit PIF
        btnApplyManualPif.setOnClickListener(v -> {
            String content = etPifContent.getText().toString().trim();
            if (content.isEmpty()) {
                Toast.makeText(this, "Konten PIF tidak boleh kosong!", Toast.LENGTH_SHORT).show();
                return;
            }

            runAsyncWithLock(() -> {
                boolean useCustom = switchUseCustom.isChecked();
                String targetPath = useCustom ? UwuhManager.CUST_PIF_PATH : UwuhManager.PIF_PATH;
                UwuhManager.writeAndSync(UwuhManager.MODULE_PIF, targetPath, content);
            }, () -> {
                Toast.makeText(this, "PIF berhasil diterapkan!", Toast.LENGTH_SHORT).show();
                loadPifContentToEditText();
            });
        });

        // Tombol Cek Update
        btnCheckUpdate.setOnClickListener(v -> {
            runAsyncWithLock(() -> {
                // Simulasi / Proses Sync All Data
                boolean useCustom = switchUseCustom.isChecked();
                UwuhManager.syncAllToFramework(useCustom);
            }, () -> {
                Toast.makeText(this, "Sync & Update Selesai!", Toast.LENGTH_SHORT).show();
                refreshFileMetadataUI();
            });
        });
    }

    /**
     * Memperbarui visibilitas UI berdasarkan status Switch "Use Custom"
     */
    private void updateUiState() {
        boolean useCustom = UwuhManager.getPropBoolean(UwuhManager.PROP_USE_CUSTOM, false);
        switchUseCustom.setChecked(useCustom);

        if (useCustom) {
            // Ketika Switch Manual ON: Tampilkan tempat select file, sembunyikan cek update
            layoutCustomFileSection.setVisibility(View.VISIBLE);
            layoutUpdateSection.setVisibility(View.GONE);
        } else {
            // Ketika Switch Manual OFF: Tampilkan cek update, sembunyikan tempat select file
            layoutCustomFileSection.setVisibility(View.GONE);
            layoutUpdateSection.setVisibility(View.VISIBLE);
        }

        refreshFileMetadataUI();
        loadPifContentToEditText();
    }

    /**
     * Membaca last modified Keybox dari file fisik
     */
    private void refreshFileMetadataUI() {
        boolean useCustom = switchUseCustom.isChecked();
        String kbPath = useCustom && new File(UwuhManager.CUST_KB_PATH).exists() 
                ? UwuhManager.CUST_KB_PATH 
                : UwuhManager.KB_PATH;

        File kbFile = new File(kbPath);
        if (kbFile.exists() && kbFile.length() > 0) {
            long lastModified = kbFile.lastModified();
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault());
            tvKeyboxLastEdit.setText("Last Edit: " + sdf.format(new Date(lastModified)));
        } else {
            tvKeyboxLastEdit.setText("Last Edit: File tidak ditemukan");
        }
    }

    /**
     * Mengisi EditText PIF langsung dari file tanpa menampilkan label status apply
     */
    private void loadPifContentToEditText() {
        boolean useCustom = switchUseCustom.isChecked();
        String pifPath = useCustom && new File(UwuhManager.CUST_PIF_PATH).exists() 
                ? UwuhManager.CUST_PIF_PATH 
                : UwuhManager.PIF_PATH;

        String content = UwuhManager.readFile(pifPath);
        etPifContent.setText(content);
    }

    private void openFilePicker(String mimeType, int requestCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType(mimeType);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Pilih File"), requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
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
                            String targetPath = switchUseCustom.isChecked() ? UwuhManager.CUST_KB_PATH : UwuhManager.KB_PATH;
                            UwuhManager.writeAndSync(UwuhManager.MODULE_KEYBOX, targetPath, fileContent);
                        } else if (requestCode == REQ_PICK_PIF) {
                            String targetPath = switchUseCustom.isChecked() ? UwuhManager.CUST_PIF_PATH : UwuhManager.PIF_PATH;
                            UwuhManager.writeAndSync(UwuhManager.MODULE_PIF, targetPath, fileContent);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, () -> {
                Toast.makeText(this, "File berhasil diimpor dan disinkronkan!", Toast.LENGTH_SHORT).show();
                refreshFileMetadataUI();
                loadPifContentToEditText();
            });
        }
    }

    /**
     * Mengunci seluruh UI selama proses async berjalan agar aman dari spam click
     */
    private void setUiEnabled(boolean enabled) {
        switchUseCustom.setEnabled(enabled);
        btnSelectKeybox.setEnabled(enabled);
        btnSelectPif.setEnabled(enabled);
        btnApplyManualPif.setEnabled(enabled);
        btnCheckUpdate.setEnabled(enabled);
        etPifContent.setEnabled(enabled);
    }

    /**
     * Helper Runnable Async dengan proteksi penguncian UI otomatis
     */
    private void runAsyncWithLock(Runnable backgroundTask, @Nullable Runnable onComplete) {
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
