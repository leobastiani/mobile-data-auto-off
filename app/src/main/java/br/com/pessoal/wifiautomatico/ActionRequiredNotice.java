// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Leonardo Guarnieri de Bastiani

package br.com.pessoal.wifiautomatico;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/** Single notification telling the user which manual action the app is waiting for. */
final class ActionRequiredNotice {
    private static final String CHANNEL_ID = "acao-necessaria";
    private static final int NOTIFICATION_ID = 102;
    private static final String SHIZUKU_PACKAGE = "moe.shizuku.privileged.api";

    static void show(Context context, String text, boolean openShizuku) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, "Ação necessária", NotificationManager.IMPORTANCE_HIGH));
        Intent target = openShizuku
                ? context.getPackageManager().getLaunchIntentForPackage(SHIZUKU_PACKAGE) : null;
        if (target == null) target = new Intent(context, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(context, 3, target,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        manager.notify(NOTIFICATION_ID, new Notification.Builder(context, CHANNEL_ID)
                .setContentTitle("Wi-Fi Automático precisa de você")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentIntent(content)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build());
        DiagnosticLog.add(context, "Notificação de ação necessária: " + text);
    }

    static void clear(Context context) {
        context.getSystemService(NotificationManager.class).cancel(NOTIFICATION_ID);
    }
}
