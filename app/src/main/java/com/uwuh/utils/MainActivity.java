package com.uwuh.utils;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private Switch switchBootloader, switchPIF, switchManual, switchGameProps, switchThermals;
    private LinearLayout panelManual, panelGamePropsBtn, panelThermalsBtn;
    private Button btnPickKeybox, btnPickPIF, btnApplyManual, btnUpdate, btnCopyLog;
    private EditText etPifEditor, tvLog;
    private TextView tvLastUpdate;

    private SharedPreferences sp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sp = getSharedPreferences("pif_prefs", MODE_PRIVATE);

        initViews();
        loadStateAndProps();
        setupListeners();

        // Pastikan sync awal data jika ada perubahan file di disk
        new Thread(UwuhManager::syncAllToFramework).start();
    }

    private void initViews() {
        switchBootloader = findViewById(R.id.switchBootloader);
        switchPIF = findViewById(R.id.switchPIF);
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

        etPifEditor = findViewById(R.id.etPifEditor);
        tvLog = findViewById(R.id.tvLog);
        tvLastUpdate = findViewById(R.id.tvLastUpdate);
    }

    private void loadStateAndProps() {
        // Read Properties via Reflection Helper
        switchBootloader.setChecked(UwuhManager.getProp(UwuhManager.PROP_BOOTLOADER, true));
        switchPIF.setChecked(UwuhManager.getProp(UwuhManager.PROP_FINGERPRINT, true));
        switchManual.setChecked(UwuhManager.getProp(UwuhManager.PROP_USE_CUSTOM, false));
        switchGameProps.setChecked(UwuhManager.getProp(UwuhManager.PROP_GAMEPROPS, false));
        switchThermals.setChecked(UwuhManager.getProp(UwuhManager.PROP_THERMALS, false));

        panelManual.setVisibility(switchManual.isChecked() ? View.VISIBLE : View.GONE);
        panelGamePropsBtn.setVisibility(switchGameProps.isChecked() ? View.VISIBLE : View.GONE);
        panelThermalsBtn.setVisibility(switchThermals.isChecked() ? View.VISIBLE : View.GONE);

        String lastUpdate = sp.getString("last_update", "-");
        tvLastUpdate.setText("Last update: " + lastUpdate);

        // Load editor content dari disk
        String pifContent = UwuhManager.readFile(UwuhManager.PIF_PATH);
        if (!pifContent.isEmpty()) {
            etPifEditor.setText(pifContent);
        }
    }

    private void setupListeners() {
        switchBootloader.setOnCheckedChangeListener((v, isChecked) -> {
            UwuhManager.setProp(UwuhManager.PROP_BOOTLOADER, isChecked);
            appendLog("Bootloader spoof set to: " + isChecked);
        });

        switchPIF.setOnCheckedChangeListener((v, isChecked) -> {
            UwuhManager.setProp(UwuhManager.PROP_FINGERPRINT, isChecked);
            appendLog("PIF spoof set to: " + isChecked);
        });

        switchManual.setOnCheckedChangeListener((v, isChecked) -> {
            UwuhManager.setProp(UwuhManager.PROP_USE_CUSTOM, isChecked);
            panelManual.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            appendLog("Manual mode set to: " + isChecked);
        });

        switchGameProps.setOnCheckedChangeListener((v, isChecked) -> {
            UwuhManager.setProp(UwuhManager.PROP_GAMEPROPS, isChecked);
            panelGamePropsBtn.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            appendLog("GameProps set to: " + isChecked);
        });

        switchThermals.setOnCheckedChangeListener((v, isChecked) -> {
            UwuhManager.setProp(UwuhManager.PROP_THERMALS, isChecked);
            panelThermalsBtn.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            appendLog("Thermals set to: " + isChecked);
        });

        btnApplyManual.setOnClickListener(v -> {
            String pifContent = etPifEditor.getText().toString();
            if (!pifContent.trim().isEmpty()) {
                // Tulis ke /data/system/uwuh/pif.prop dan sync chunk via Reflection
                boolean success = UwuhManager.writeAndSync(UwuhManager.MODULE_PIF, UwuhManager.PIF_PATH, pifContent);
                if (success) {
                    appendLog("Manual PIF applied and synced to RAM via Reflection!");
                } else {
                    appendLog("Error applying manual PIF.");
                }
            }
        });

        btnCopyLog.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("UwuhLog", tvLog.getText().toString());
            cm.setPrimaryClip(clip);
            Toast.makeText(this, "Log copied!", Toast.LENGTH_SHORT).show();
        });
    }

    private void appendLog(String msg) {
        tvLog.append("\n" + msg);
    }
}
