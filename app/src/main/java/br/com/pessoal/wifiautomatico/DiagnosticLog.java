// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Leonardo Guarnieri de Bastiani

package br.com.pessoal.wifiautomatico;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class DiagnosticLog {
    static synchronized void add(Context context, String message) {
        Log.i("WifiAutomatico", message);
        android.content.SharedPreferences prefs = context.getSharedPreferences("diagnostics", Context.MODE_PRIVATE);
        String log = prefs.getString("log", "") + new SimpleDateFormat("MM-dd HH:mm:ss", Locale.ROOT)
                .format(new Date()) + " " + message + "\n";
        if (log.length() > 24000) {
            log = log.substring(log.length() - 24000);
            int newline = log.indexOf('\n');
            if (newline >= 0) log = log.substring(newline + 1);
        }
        prefs.edit().putString("log", log).apply();
    }
    static String report(Context context) {
        return "Wi-Fi Automático 0.3 — diagnóstico\nAparelho: " + Build.MANUFACTURER + " "
                + Build.MODEL + "\nAndroid: " + Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT
                + "\nBuild: " + Build.DISPLAY + "\n" + context.getSharedPreferences("diagnostics", Context.MODE_PRIVATE)
                .getString("log", "Nenhum evento registrado.");
    }
}
