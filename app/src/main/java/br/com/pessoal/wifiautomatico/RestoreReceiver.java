// SPDX-License-Identifier: GPL-3.0-only
package br.com.pessoal.wifiautomatico;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

public final class RestoreReceiver extends BroadcastReceiver {
    private static PowerManager.WakeLock deliveryLock;

    @Override public void onReceive(Context context, Intent intent) {
        boolean restore = RestoreAlarm.ACTION.equals(intent.getAction());
        boolean permission = AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
                .equals(intent.getAction());
        if (!restore && !permission) return;
        if (restore && !RestoreAlarm.isCurrent(context,
                intent.getLongExtra(RestoreAlarm.TOKEN, -1))) {
            DiagnosticLog.add(context, "Alarme antigo ignorado");
            return;
        }
        if (permission && (!RestoreAlarm.prefs(context).getBoolean("enabled", false)
                || !RestoreAlarm.canScheduleExact(context))) return;
        DiagnosticLog.add(context, restore ? "Alarme de religação recebido" : "Acesso a alarmes concedido");
        releaseDeliveryLock();
        deliveryLock = context.getSystemService(PowerManager.class)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "wifi-automatico:alarm-delivery");
        deliveryLock.acquire(30_000L);
        Intent service = new Intent(context, WifiMonitorService.class)
                .setAction(restore ? RestoreAlarm.ACTION : WifiMonitorService.ACTION_START);
        if (restore) service.putExtra(RestoreAlarm.TOKEN, intent.getLongExtra(RestoreAlarm.TOKEN, -1));
        try {
            context.startForegroundService(service);
        } catch (RuntimeException error) {
            releaseDeliveryLock();
            DiagnosticLog.add(context, "Falha ao entregar alarme ao serviço: " + error);
        }
    }

    static void releaseDeliveryLock() {
        if (deliveryLock != null && deliveryLock.isHeld()) deliveryLock.release();
        deliveryLock = null;
    }
}
