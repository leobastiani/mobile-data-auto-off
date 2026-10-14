// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Leonardo Guarnieri de Bastiani

package br.com.pessoal.wifiautomatico;

import android.app.Instrumentation;
import android.os.Bundle;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import rikka.shizuku.Shizuku;

/** Teste real via Shizuku: altera e verifica os dados, restaurando o estado inicial. */
public final class DataControlProbe extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        TelephonyManager phone = null;
        Boolean initial = null;
        boolean success = false;
        StringBuilder report = new StringBuilder();
        try {
            CountDownLatch connected = new CountDownLatch(1);
            Shizuku.OnBinderReceivedListener listener = connected::countDown;
            Shizuku.addBinderReceivedListenerSticky(listener);
            try {
                if (!connected.await(10, TimeUnit.SECONDS)) throw new AssertionError("Shizuku indisponível");
            } finally { Shizuku.removeBinderReceivedListener(listener); }
            int sub = SubscriptionManager.getDefaultDataSubscriptionId();
            if (!SubscriptionManager.isValidSubscriptionId(sub)) throw new AssertionError("Sem SIM padrão");
            phone = getTargetContext().getSystemService(TelephonyManager.class).createForSubscriptionId(sub);
            initial = phone.isDataEnabled();
            report.append("Estado inicial: ").append(initial).append('\n');
            for (boolean desired : new boolean[]{false, true, false, true}) {
                String output = MobileDataCommand.setEnabled(desired);
                report.append(output).append('\n');
                if (!output.startsWith("OK:")) throw new AssertionError(output);
                long deadline = android.os.SystemClock.elapsedRealtime() + 3000;
                while (phone.isDataEnabled() != desired && android.os.SystemClock.elapsedRealtime() < deadline)
                    Thread.sleep(100);
                boolean actual = phone.isDataEnabled();
                report.append("Estado verificado: ").append(actual).append('\n');
                if (actual != desired) throw new AssertionError("Estado não mudou para " + desired);
            }
            success = true;
        } catch (Throwable error) { report.append("FALHA: ").append(error).append('\n'); }
        finally {
            if (initial != null) {
                String restored = MobileDataCommand.setEnabled(initial);
                report.append("Restauração: ").append(restored).append('\n');
                if (!restored.startsWith("OK:")) success = false;
            }
        }
        result.putString("stream", report.toString());
        DiagnosticLog.add(getTargetContext(), "Teste ADB: " + report);
        finish(success ? -1 : 0, result);
    }
}
