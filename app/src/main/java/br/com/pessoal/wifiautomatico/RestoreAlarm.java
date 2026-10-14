// SPDX-License-Identifier: GPL-3.0-only
package br.com.pessoal.wifiautomatico;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;

final class RestoreAlarm {
    static final String ACTION = "br.com.pessoal.wifiautomatico.RESTORE";
    static final String TOKEN = "restore_token";
    static final long DELAY_MS = 120_000L;

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("automation", Context.MODE_PRIVATE);
    }

    static int boot(Context context) {
        return Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
    }

    static long deadline(Context context) {
        SharedPreferences p = prefs(context);
        return p.getInt("restore_boot", -2) == boot(context)
                ? p.getLong("restore_deadline", 0) : 0;
    }

    static boolean canScheduleExact(Context context) {
        return Build.VERSION.SDK_INT < 31
                || context.getSystemService(AlarmManager.class).canScheduleExactAlarms();
    }

    static void schedule(Context context) {
        SharedPreferences p = prefs(context);
        long deadline = deadline(context);
        if (deadline == 0) {
            deadline = SystemClock.elapsedRealtime() + DELAY_MS;
            p.edit().putLong("restore_deadline", deadline)
                    .putInt("restore_boot", boot(context))
                    .putLong(TOKEN, p.getLong(TOKEN, 0) + 1).commit();
        }
        PendingIntent pending = pending(context, p.getLong(TOKEN, 0));
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        boolean exact = canScheduleExact(context);
        try {
            if (exact) alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    deadline, pending);
            else alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, pending);
        } catch (SecurityException revoked) {
            exact = false;
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, pending);
        }
        DiagnosticLog.add(context, "Religação agendada; restante_ms="
                + Math.max(0, deadline - SystemClock.elapsedRealtime()) + "; exato=" + exact);
    }

    static boolean isCurrent(Context context, long token) {
        SharedPreferences p = prefs(context);
        return RestorePolicy.isCurrent(p.getBoolean("enabled", false), deadline(context),
                p.getLong(TOKEN, 0), token);
    }

    static void cancel(Context context) {
        SharedPreferences p = prefs(context);
        context.getSystemService(AlarmManager.class).cancel(pending(context, p.getLong(TOKEN, 0)));
        if (p.contains("restore_deadline")) DiagnosticLog.add(context, "Religação cancelada/concluída");
        p.edit().remove("restore_deadline").remove("restore_boot")
                .putLong(TOKEN, p.getLong(TOKEN, 0) + 1).commit();
    }

    private static PendingIntent pending(Context context, long token) {
        Intent intent = new Intent(context, RestoreReceiver.class).setAction(ACTION)
                .putExtra(TOKEN, token);
        return PendingIntent.getBroadcast(context, 2, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
