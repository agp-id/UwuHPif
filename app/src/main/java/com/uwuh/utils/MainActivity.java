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
    private TextView tvKeyboxLastApply, tvPifLastApply, tvLog, tvAutoUpdateInfo, tvLastUpdate;
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
        
        // ✅ Auto-check update saat app dibuka (jika auto-update enabled)
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
            UwuhManager.setProp(UwuhManager.PROP
