// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Leonardo Guarnieri de Bastiani

package br.com.pessoal.wifiautomatico;

import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import moe.shizuku.server.IRemoteProcess;
import moe.shizuku.server.IShizukuService;
import rikka.shizuku.Shizuku;

/** Contorna a falha do UserService no HyperOS usando o processo remoto do servidor. */
final class MobileDataCommand {
    static synchronized String setEnabled(boolean enabled) {
        String command = "cmd phone data " + (enabled ? "enable" : "disable");
        IRemoteProcess process = null;
        ExecutorService readers = Executors.newFixedThreadPool(2);
        try {
            if (!Shizuku.pingBinder()) return "ERRO: Shizuku desligado";
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED)
                return "ERRO: Shizuku sem autorização";
            process = IShizukuService.Stub.asInterface(Shizuku.getBinder()).newProcess(
                    new String[]{"cmd", "phone", "data", enabled ? "enable" : "disable"}, null, null);
            final IRemoteProcess running = process;
            Future<String> out = readers.submit(() -> read(running.getInputStream()));
            Future<String> err = readers.submit(() -> read(running.getErrorStream()));
            // Fecha stdin: estes comandos não recebem entrada.
            try (ParcelFileDescriptor stdin = process.getOutputStream()) { }
            if (!process.waitForTimeout(15, "SECONDS")) return "ERRO: " + command + "; timeout de 15 segundos";
            int exit = process.exitValue();
            String output = out.get(2, TimeUnit.SECONDS) + err.get(2, TimeUnit.SECONDS);
            String lower = output.toLowerCase(java.util.Locale.ROOT);
            boolean failed = exit != 0 || lower.contains("exception") || lower.contains("permission denial")
                    || lower.contains("error") || lower.contains("unknown");
            return (failed ? "ERRO: " : "OK: ") + command + "; via=Shizuku RemoteProcess; UID="
                    + Shizuku.getUid() + "; exit=" + exit + "; saída=" + (output.isEmpty() ? "(vazia)" : output);
        } catch (Exception error) {
            return "ERRO: " + command + "; " + error;
        } finally {
            if (process != null) try { process.destroy(); } catch (Exception ignored) { }
            readers.shutdownNow();
        }
    }
    private static String read(ParcelFileDescriptor descriptor) throws Exception {
        try (InputStream stream = new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                int remaining = 8000 - output.size();
                if (remaining > 0) output.write(buffer, 0, Math.min(count, remaining));
            }
            return output.toString(StandardCharsets.UTF_8.name()).trim();
        }
    }
}
