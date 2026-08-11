package com.uwuh.pif;

import android.content.*;
import java.io.*;
import java.net.*;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        SharedPreferences sp = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE);
        if (sp.getBoolean("auto", true)) {
            new Thread(() -> {
                try {
                    // Cek update atau apply fallback disini
                    // Logika penulisan file sama seperti MainActivity
                } catch (Exception e) {}
            }).start();
        }
    }
}
