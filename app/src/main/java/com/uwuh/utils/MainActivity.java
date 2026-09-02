package com.uwuh.utils;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private Switch switchBootloader, switchPIF, switchFinsky, switchManual, switchGameProps, switchThermals;
    private LinearLayout panelManual, panelAutoUpdate, panelGamePropsBtn, panelThermalsBtn;
    private Button btnPickKeybox, btnPickPIF, btnApplyManual, btnUpdate, btnCopyLog;
    private EditText etPifEditor;
    private TextView tvLog, tvLastUpdate;

    private SharedPreferences sp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sp = getSharedPreferences("pif_prefs", MODE_PRIVATE);

        initViews();
        loadStateAndProps();
        setupListeners();

        // 1. Sync awal file disk ke RAM
        new Thread(UwuhManager::syncAllToFramework).start();

        // 2. Auto Cek Update saat app jalan (HANYA jika Manual Mode OFF)
        if (!switchManual.isChecked()) {
            checkUpdateOnlineAuto();
        }
    }

    private void initViews() {
        switchBootloader = findViewById(R.id.switchBootloader);
        switchPIF = findViewById(R.id.switchPIF);
        switchFinsky = findViewById(R.id.switchFinsky);
        switchManual = findViewById(R.id.switchManual);
        switchGameProps = findViewById(R.id.switchGameProps);
        switchThermals = findViewById(R.id.switchThermals);

        panelManual = findViewById(R.id.panelManual);
        panelAutoUpdate = findViewById(R.id.panelAutoUpdate);
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

        // Mengaktifkan Scroll pada TextView Log
        tvLog.setMovementMethod(new ScrollingMovementMethod());
    }

    private void loadStateAndProps() {
        // Read Properties via Reflection
        boolean bootloaderOn = UwuhManager.getProp(UwuhManager.PROP_BOOTLOADER, true);
        boolean pifOn = UwuhManager.getProp(UwuhManager.PROP_FINGERPRINT, true);
        boolean finskyOn = UwuhManager.getProp(UwuhManager.PROP_FINSKY, true);
        boolean manualOn = UwuhManager.getProp(UwuhManager.PROP_USE_CUSTOM, false);
        boolean gamePropsOn = UwuhManager.getProp(UwuhManager.PROP_GAMEPROPS, false);
        boolean thermalsOn = UwuhManager.getProp(UwuhManager.PROP_THERMALS, false);

        switchBootloader.setChecked(bootloaderOn);
        switchPIF.setChecked(pifOn);
        switchFinsky.setChecked(finskyOn);
        switchManual.setChecked(manualOn);
        switchGameProps.setChecked(gamePropsOn);
        switchThermals.setChecked(thermalsOn);

        // Logical Visibility
        switchFinsky.setVisibility(pifOn ? View.VISIBLE : View.GONE);
        panelManual.setVisibility(manualOn ? View.VISIBLE : View.GONE);
        panelAutoUpdate.setVisibility(manualOn ? View.GONE : View.VISIBLE);
        
        panelGamePropsBtn.setVisibility(gamePropsOn ? View.VISIBLE : View.GONE);
        panelThermalsBtn.setVisibility(thermalsOn ? View.VISIBLE : View.GONE);

        String lastUpdate = sp.getString("last_update", "-");
        tvLastUpdate.setText("Last update: " + lastUpdate);

        String pifContent = UwuhManager.readFile(UwuhManager.PIF_PATH);
        if (!pifContent.isEmpty()) {
            etPifEditor.setText(pifContent);
        }
    }

    private void setupListeners() {
        switchBootloader.setOnCheckedChangeListener((v, isChecked) -> {
            UwuhManager.setProp(UwuhManager.PROP_BOOTLOADER, isChecked);
            appendLog("Bootloader spoof: " + isChecked);
        });

        // Toggle PIF: finsky switch HANYA muncul jika PIF Active
        switchPIF.setOnCheckedChangeListener((v, isChecked) -> {
            UwuhManager.setProp(UwuhManager.PROP_FINGERPRINT, isChecked);
            switchFinsky.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            appendLog("PIF spoof: " + isChecked);
        });

        switchFinsky.setOnCheckedChangeListener((v, isChecked) -> {
            UwuhManager.setProp(UwuhManager.PROP_FINSKY, isChecked);
            appendLog("Play Store Fingerprint spoof: " + isChecked);
        });

        // Toggle Manual Mode
        switchManual.setOnCheckedChangeListener((v, isChecked) -> {
            UwuhManager.setProp(UwuhManager.PROP_USE_CUSTOM, isChecked);
            panelManual.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            panelAutoUpdate.setVisibility(isChecked ? View.GONE : View.VISIBLE);
            
            appendLog("Manual Mode: " + isChecked);
            if (!isChecked) {
                checkUpdateOnlineAuto();
            }
        });

        switchGameProps.setOnCheckedChangeListener((v, isChecked) -> {
            UwuhManager.setProp(UwuhManager.PROP_GAMEPROPS, isChecked);
            panelGamePropsBtn.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            appendLog("GameProps: " + isChecked);
        });

        switchThermals.setOnCheckedChangeListener((v, isChecked) -> {
            UwuhManager.setProp(UwuhManager.PROP_THERMALS, isChecked);
            panelThermalsBtn.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            appendLog("Thermals: " + isChecked);
        });

        btnApplyManual.setOnClickListener(v -> {
            String pifContent = etPifEditor.getText().toString();
            if (!pifContent.trim().isEmpty()) {
                boolean success = UwuhManager.writeAndSync(UwuhManager.MODULE_PIF, UwuhManager.PIF_PATH, pifContent);
                if (success) {
                    appendLog("Manual PIF applied!");
                } else {
                    appendLog("Error applying manual PIF.");
                }
            }
        });

        btnUpdate.setOnClickListener(v -> checkUpdateOnlineAuto());

        btnCopyLog.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("UwuhLog", tvLog.getText().toString());
            cm.setPrimaryClip(clip);
            Toast.makeText(this, "Log copied!", Toast.LENGTH_SHORT).show();
        });
    }

    private void checkUpdateOnlineAuto() {
        appendLog("Checking update online...");
        // Logika fetch update online kamu...
    }

    private void appendLog(String msg) {
        runOnUiThread(() -> {
            tvLog.append("\n" + msg);
            // Auto scroll ke paling bawah log
            final int scrollAmount = tvLog.getLayout().getLineTop(tvLog.getLineCount()) - tvLog.getHeight();
            if (scrollAmount > 0) {
                tvLog.scrollTo(0, scrollAmount);
            } else {
                tvLog.scrollTo(0, 0);
            }
        });
    }
}
