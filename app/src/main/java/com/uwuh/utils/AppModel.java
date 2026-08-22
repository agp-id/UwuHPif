package com.uwuh.utils;

import android.graphics.drawable.Drawable;

public class AppModel {
    private String appName;
    private String packageName;
    private Drawable icon;
    private boolean isSystem;
    private String selectedConfig;

    public AppModel(String appName, String packageName, Drawable icon, boolean isSystem, String selectedConfig) {
        this.appName = appName;
        this.packageName = packageName;
        this.icon = icon;
        this.isSystem = isSystem;
        this.selectedConfig = selectedConfig;
    }

    public String getAppName() { return appName; }
    public String getPackageName() { return packageName; }
    public Drawable getIcon() { return icon; }
    public boolean isSystem() { return isSystem; }
    public String getSelectedConfig() { return selectedConfig; }
    public void setSelectedConfig(String selectedConfig) { this.selectedConfig = selectedConfig; }
}
