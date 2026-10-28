// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Leonardo Guarnieri de Bastiani

package br.com.pessoal.wifiautomatico;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        boolean enabled = context.getSharedPreferences("automation", Context.MODE_PRIVATE)
                .getBoolean("enabled", false);
        RestoreAlarm.cancel(context);
        DiagnosticLog.add(context, "Boot recebido; automação configurada=" + enabled);
        if (enabled) {
            Intent service = new Intent(context, WifiMonitorService.class)
                    .setAction(WifiMonitorService.ACTION_START);
            try {
                context.startForegroundService(service);
            } catch (Exception error) {
                DiagnosticLog.add(context, "Falha ao iniciar após boot: " + error);
                // Algumas versões do HyperOS bloqueiam o início automático.
                // Neste caso, abrir o app e tocar em Ativar automação.
                ActionRequiredNotice.show(context,
                        "O monitor não iniciou após reiniciar. Abra o app e toque em Ativar automação.", false);
            }
        }
    }
}
