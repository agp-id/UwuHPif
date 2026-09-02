package com.uwuh.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Selection;
import android.text.method.ScrollingMovementMethod;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String URL_KB = "https://raw.githubusercontent.com/user/repo/main/keybox.xml";
    private static final String URL_PIF = "https://raw.githubusercontent.com/user/repo/main/pif.prop";

    private LinkedList<String> logLines = new LinkedList<>();
    private TextView tvLog;
    private EditText etPifEditor;
    private Button btnCopyLog, btnUpdate, btnApplyManual;
    private TextView tvLastUpdate, tvAutoStatus, tvKeyboxLastApply, tvPifLastApply;
    private LinearLayout panelManual, panelGamePropsBtn, panelThermalsBtn;
    private Switch switchManual, switchBootloader, switchPIF, switchFinsky, switchGameProps, switchThermals;

    private SharedPreferences sp;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sp = getSharedPreferences("pif_prefs", MODE_PRIVATE);

        initViews();
        loadSystemPropertiesState();
        setupListeners();

        boolean isManual = sp.getBoolean("manual", false);
        updateUIState(isManual);

        new Thread(() -> {
            UwuhManager.syncAllToFramework(isManual);
            if (!isManual) checkUpdateOnline(false);
        }).start();
    }

    private void initViews() {
        switchManual = findViewById(R.id.switchManual);
        switchBootloader = findViewById(R.id.switchBootloader);
        switchPIF = findViewById(R.id.switchPIF);
        switchFinsky = findViewById(R.id.switchFinsky);
        switchGameProps = findViewById(R.id.switchGameProps);
        switchThermals = findViewById(R.id.switchThermals);

        tvLastUpdate = findViewById(R.id.tvLastUpdate);
        tvAutoStatus = findViewById(R.id.tvAutoStatus);
        tvKeyboxLastApply = findViewById(R.id.tvKeyboxLastApply);
        tvPifLastApply = findViewById(R.id.tvPifLastApply);

        panelManual = findViewById(R.id.panelManual);
        panelGamePropsBtn = findViewById(R.id.panelGamePropsBtn);
        panelThermalsBtn = findViewById(R.id.panelThermalsBtn);

        etPifEditor = findViewById(R.id.etPifEditor);
        btnUpdate = findViewById(R.id.btnUpdate);
        btnApplyManual = findViewById(R.id.btnApplyManual);
        tvLog = findViewById(R.id.tvLog);
        btnCopyLog = findViewById(R.id.btnCopyLog);

        tvLog.setMovementMethod(new ScrollingMovementMethod());

        enableInnerScroll(etPifEditor);
    }

    private void loadSystemPropertiesState() {
        boolean pifState = UwuhManager.getPropBoolean(UwuhManager.PROP_PIF, true);
        boolean finskyState = UwuhManager.getPropBoolean(UwuhManager.PROP_FINSKY, true);

        switchBootloader.setChecked(UwuhManager.getPropBoolean(UwuhManager.PROP_BOOTLOADER, true));
        switchPIF.setChecked(pifState);
        switchFinsky.setChecked(finskyState);
        switchFinsky.setVisibility(pifState ? View.VISIBLE : View.GONE);

        switchGameProps.setChecked(UwuhManager.getPropBoolean(UwuhManager.PROP_GAMEPROPS, false));
        switchThermals.setChecked(UwuhManager.getPropBoolean(UwuhManager.PROP_THERMALS, false));

        panelGamePropsBtn.setVisibility(switchGameProps.isChecked() ? View.VISIBLE : View.GONE);
        panelThermalsBtn.setVisibility(switchThermals.isChecked() ? View.VISIBLE : View.GONE);

        tvKeyboxLastApply.setText("Last apply: " + sp.getString("keybox_last_apply", "-"));
        tvPifLastApply.setText("Last apply: " + sp.getString("pif_last_apply", "-"));

        loadCustomPIF();
    }

    private void setupListeners() {
        switchManual.setOnCheckedChangeListener((v, checked) -> {
            sp.edit().putBoolean("manual", checked).apply();
            UwuhManager.setProp(UwuhManager.PROP_USE_CUSTOM, checked ? "true" : "false");
            updateUIState(checked);
            addLog("Mode: " + (checked ? "Manual" : "Auto"));

            new Thread(() -> UwuhManager.syncAllToFramework(checked)).start();
        });

        switchBootloader.setOnCheckedChangeListener((v, checked) -> {
            UwuhManager.setProp(UwuhManager.PROP_BOOTLOADER, checked ? "true" : "false");
            addLog("Bootloader spoof: " + (checked ? "ON" : "OFF"));
        });

        switchPIF.setOnCheckedChangeListener((v, checked) -> {
            UwuhManager.setProp(UwuhManager.PROP_PIF, checked ? "true" : "false");
            switchFinsky.setVisibility(checked ? View.VISIBLE : View.GONE);
            addLog("PIF spoof: " + (checked ? "ON" : "OFF"));
        });

        switchFinsky.setOnCheckedChangeListener((v, checked) -> {
            UwuhManager.setProp(UwuhManager.PROP_FINSKY, checked ? "true" : "false");
            addLog("Play Store Fingerprint spoof: " + (checked ? "ON" : "OFF"));
        });

        switchGameProps.setOnCheckedChangeListener((v, checked) -> {
            UwuhManager.setProp(UwuhManager.PROP_GAMEPROPS, checked ? "true" : "false");
            panelGamePropsBtn.setVisibility(checked ? View.VISIBLE : View.GONE);
            addLog("GameProps " + (checked ? "enabled" : "disabled"));
        });

        switchThermals.setOnCheckedChangeListener((v, checked) -> {
            UwuhManager.setProp(UwuhManager.PROP_THERMALS, checked ? "true" : "false");
            panelThermalsBtn.setVisibility(checked ? View.VISIBLE : View.GONE);
            addLog("Thermals " + (checked ? "enabled" : "disabled"));
        });

        btnUpdate.setOnClickListener(v -> new Thread(() -> checkUpdateOnline(true)).start());
        btnCopyLog.setOnClickListener(v -> copyLog());

        findViewById(R.id.btnGameProps).setOnClickListener(v -> GamePropsThermalController.showAppConfigDialog(this, true, this::addLog));
        findViewById(R.id.btnDevices).setOnClickListener(v -> GamePropsThermalController.showDevicesManagerDialog(this, this::addLog));
        findViewById(R.id.btnThermals).setOnClickListener(v -> GamePropsThermalController.showAppConfigDialog(this, false, this::addLog));
        findViewById(R.id.btnResetGameProps).setOnClickListener(v -> GamePropsThermalController.resetGameProps(this, this::addLog));
        findViewById(R.id.btnResetThermals).setOnClickListener(v -> GamePropsThermalController.resetThermals(this, this::addLog));

        findViewById(R.id.btnPickKeybox).setOnClickListener(v -> pickFile(101));
        findViewById(R.id.btnPickPIF).setOnClickListener(v -> pickFile(102));
        btnApplyManual.setOnClickListener(v -> applyManualPIF());
    }

    private void applyManualPIF() {
        String content = etPifEditor.getText().toString().trim();
        if (content.isEmpty() || content.startsWith("# No custom")) return;

        if (UwuhManager.writeAndSync(UwuhManager.MODULE_PIF, UwuhManager.CUST_PIF_PATH, content)) {
            String time = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new Date());
            sp.edit().putString("pif_last_apply", time).apply();
            tvPifLastApply.setText("Last apply: " + time);

            addLog("Custom PIF applied & chunked!");
            Toast.makeText(this, "Custom PIF Applied!", Toast.LENGTH_SHORT).show();
            killGMSAndVending();
        }
    }

    private void checkUpdateOnline(boolean showToast) {
        addLog("Checking update...");
        try {
            String newKb = fetch(URL_KB);
            String newPif = fetch(URL_PIF);

            boolean kbUpdated = false;
            boolean pifUpdated = false;

            if (newKb != null && !newKb.isEmpty() && !newKb.equals(UwuhManager.readFile(UwuhManager.KB_PATH))) {
                kbUpdated = UwuhManager.writeAndSync(UwuhManager.MODULE_KEYBOX, UwuhManager.KB_PATH, newKb);
            }

            if (newPif != null && !newPif.isEmpty() && !newPif.equals(UwuhManager.readFile(UwuhManager.PIF_PATH))) {
                pifUpdated = UwuhManager.writeAndSync(UwuhManager.MODULE_PIF, UwuhManager.PIF_PATH, newPif);
            }

            final boolean isUpdated = kbUpdated || pifUpdated;
            String date = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new Date());
            sp.edit().putString("last_update", date).apply();

            runOnUiThread(() -> {
                tvLastUpdate.setText("Last update: " + date);
                if (showToast) {
                    Toast.makeText(this, isUpdated ? "Files updated!" : "Already latest.", Toast.LENGTH_SHORT).show();
                }
            });

            if (isUpdated) {
                addLog("Online update applied & chunked");
                killGMSAndVending();
            } else {
                addLog("Already latest");
            }
        } catch (Exception e) {
            addLog("Update failed");
        }
    }

    private void updateUIState(boolean isManual) {
        panelManual.setVisibility(isManual ? View.VISIBLE : View.GONE);
        btnUpdate.setVisibility(isManual ? View.GONE : View.VISIBLE);
        tvAutoStatus.setVisibility(isManual ? View.GONE : View.VISIBLE);
        tvAutoStatus.setText(isManual ? "Auto update: OFF" : "Auto update: ON");
        tvLastUpdate.setVisibility(isManual ? View.GONE : View.VISIBLE);
    }

    private void loadCustomPIF() {
        String content = UwuhManager.readFile(UwuhManager.CUST_PIF_PATH);
        if (!content.isEmpty()) {
            etPifEditor.setText(content);
        } else {
            etPifEditor.setText("# No custom PIF loaded");
        }
    }

    private void pickFile(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res == Activity.RESULT_OK && data != null && data.getData() != null) {
            new Thread(() -> {
                String content = readUri(data.getData());
                if (content.isEmpty()) return;

                if (req == 101) {
                    if (UwuhManager.writeAndSync(UwuhManager.MODULE_KEYBOX, UwuhManager.CUST_KB_PATH, content)) {
                        String time = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new Date());
                        sp.edit().putString("keybox_last_apply", time).apply();
                        runOnUiThread(() -> tvKeyboxLastApply.setText("Last apply: " + time));
                        addLog("Custom Keybox saved & chunked");
                    }
                } else if (req == 102) {
                    if (UwuhManager.writeAndSync(UwuhManager.MODULE_PIF, UwuhManager.CUST_PIF_PATH, content)) {
                        String time = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new Date());
                        sp.edit().putString("pif_last_apply", time).apply();
                        runOnUiThread(() -> {
                            tvPifLastApply.setText("Last apply: " + time);
                            etPifEditor.setText(content);
                        });
                        addLog("Custom PIF saved & chunked");
                        killGMSAndVending();
                    }
                }
            }).start();
        }
    }

    private void killGMSAndVending() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            Method forceStop = ActivityManager.class.getDeclaredMethod("forceStopPackage", String.class);
            forceStop.setAccessible(true);
            forceStop.invoke(am, "com.google.android.gms");
            forceStop.invoke(am, "com.android.vending");
        } catch (Exception e) {}
    }

    private void addLog(String msg) {
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        String logMsg = "[" + timestamp + "] " + msg;
        if (logLines.size() >= 20) logLines.removeFirst();
        logLines.add(logMsg);

        mainHandler.post(() -> {
            StringBuilder sb = new StringBuilder();
            for (String line : logLines) sb.append(line).append("\n");
            tvLog.setText(sb.toString().trim());
        });
    }

    private void copyLog() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Debug Log", tvLog.getText().toString()));
        Toast.makeText(this, "Log copied", Toast.LENGTH_SHORT).show();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void enableInnerScroll(EditText editText) {
        editText.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            if ((event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP ||
                (event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_CANCEL) {
                v.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });
    }

    private String fetch(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(8000);
        if (c.getResponseCode() != 200) return null;
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
        StringBuilder sb = new StringBuilder(); String line;
        while ((line = r.readLine()) != null) sb.append(line).append("\n");
        r.close();
        return sb.toString().trim();
    }

    private String readUri(Uri uri) {
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri)));
            StringBuilder sb = new StringBuilder(); String line;
            while ((line = r.readLine()) != null) sb.append(line).append("\n");
            r.close();
            return sb.toString().trim();
        } catch (Exception e) { return ""; }
    }
}
