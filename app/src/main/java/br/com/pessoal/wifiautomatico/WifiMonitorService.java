// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Leonardo Guarnieri de Bastiani

package br.com.pessoal.wifiautomatico;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

/** Monitor de eventos de conexão Wi-Fi. Não depende de GPS, polling ou acesso à internet. */
public final class WifiMonitorService extends Service {
    public static final String ACTION_START = "br.com.pessoal.wifiautomatico.START";
    public static final String ACTION_STOP = "br.com.pessoal.wifiautomatico.STOP";
    private static final String CHANNEL_ID = "wifi-automatico";
    private static final int NOTIFICATION_ID = 101;
    private static final long DELAY_MS = 120_000L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService shellQueue = Executors.newSingleThreadExecutor();
    private final Set<Network> wifiNetworks = new HashSet<>();
    private ConnectivityManager connectivity;
    private ConnectivityManager.NetworkCallback wifiCallback;
    private boolean isWifiConnected;
    private boolean monitoring;
    private boolean waitingForMobile;
    private int operationId;
    private Runnable restoreMobile;

    private final Shizuku.OnBinderReceivedListener receivedListener = () ->
            main.post(this::connectShizuku);

    private final Shizuku.OnBinderDeadListener deadListener = () ->
            main.post(() -> {
                if (monitoring) status("Shizuku parou: controle automático indisponível");
            });

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, "Automação de rede", NotificationManager.IMPORTANCE_LOW));
        startForeground(NOTIFICATION_ID, notification("Iniciando monitor de Wi-Fi"));
        connectivity = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        Shizuku.addBinderReceivedListenerSticky(receivedListener);
        Shizuku.addBinderDeadListener(deadListener);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopAutomation();
            return START_NOT_STICKY;
        }
        if (!monitoring) startAutomation();
        return START_STICKY;
    }

    private void startAutomation() {
        monitoring = true;
        getSharedPreferences("automation", MODE_PRIVATE).edit().putBoolean("enabled", true).apply();

        // Faz a leitura inicial antes de registrar os eventos.
        for (Network network : connectivity.getAllNetworks()) {
            NetworkCapabilities capabilities = connectivity.getNetworkCapabilities(network);
            if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                wifiNetworks.add(network);
            }
        }
        isWifiConnected = !wifiNetworks.isEmpty();

        wifiCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                main.post(() -> {
                    if (!monitoring) return;
                    wifiNetworks.add(network);
                    updateWifiState();
                });
            }

            @Override
            public void onLost(Network network) {
                main.post(() -> {
                    if (!monitoring) return;
                    wifiNetworks.remove(network);
                    updateWifiState();
                });
            }
        };
        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build();
        connectivity.registerNetworkCallback(request, wifiCallback);
        if (isWifiConnected) {
            status("Wi-Fi conectado: desligando dados móveis");
            setMobileData(false, "Wi-Fi conectado");
        } else {
            beginTwoMinuteTimer();
        }
    }

    private void updateWifiState() {
        boolean nowWifi = !wifiNetworks.isEmpty();
        if (nowWifi == isWifiConnected) return;
        isWifiConnected = nowWifi;
        if (nowWifi) {
            cancelTimer();
            setMobileData(false, "Wi-Fi conectado");
        } else {
            beginTwoMinuteTimer();
        }
    }

    private void beginTwoMinuteTimer() {
        cancelTimer();
        waitingForMobile = true;
        long disconnectTime = SystemClock.elapsedRealtime();
        status("Wi-Fi desconectado: aguardando 2 minutos");
        restoreMobile = () -> {
            if (!monitoring || isWifiConnected || !waitingForMobile) return;
            long elapsed = SystemClock.elapsedRealtime() - disconnectTime;
            if (elapsed < DELAY_MS) {
                main.postDelayed(restoreMobile, DELAY_MS - elapsed);
                return;
            }
            waitingForMobile = false;
            setMobileData(true, "Wi-Fi ausente por 2 minutos");
        };
        main.postDelayed(restoreMobile, DELAY_MS);
    }

    private void cancelTimer() {
        waitingForMobile = false;
        if (restoreMobile != null) {
            main.removeCallbacks(restoreMobile);
            restoreMobile = null;
        }
    }

    private void connectShizuku() {
        if (!monitoring) return;
        if (!Shizuku.pingBinder()) {
            status("Shizuku indisponível: inicie-o manualmente");
            return;
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            status("Autorize o aplicativo no Shizuku");
            return;
        }
        if (isWifiConnected) setMobileData(false, "Shizuku disponível e Wi-Fi conectado");
        else if (!waitingForMobile) setMobileData(true, "Shizuku disponível e Wi-Fi ausente");
    }

    private void setMobileData(boolean enabled, String reason) {
        int thisOperation = ++operationId;
        status(reason + (enabled ? ": religando dados móveis" : ": desligando dados móveis"));
        shellQueue.execute(() -> {
            String result;
            try {
                result = MobileDataCommand.setEnabled(enabled);
            } catch (Exception error) {
                result = "ERRO: " + error.getMessage();
            }
            DiagnosticLog.add(this, "Comando automático: " + result);
            final String finalResult = result;
            main.post(() -> {
                if (monitoring && thisOperation == operationId) {
                    status(finalResult.startsWith("OK:")
                            ? "Dados móveis " + (enabled ? "ligados" : "desligados")
                            : "Falha ao alterar dados móveis: " + finalResult);
                }
            });
        });
    }

    private void stopAutomation() {
        getSharedPreferences("automation", MODE_PRIVATE).edit().putLong("stopping_until", SystemClock.elapsedRealtime() + 20000).apply();
        monitoring = false;
        getSharedPreferences("automation", MODE_PRIVATE).edit().putBoolean("enabled", false).apply();
        cancelTimer();
        status("Parando e tentando religar os dados móveis");
        shellQueue.execute(() -> {
            String result;
            try {
                result = MobileDataCommand.setEnabled(true);
            } catch (Exception e) {
                result = "ERRO: " + e.getMessage();
            }
            String finalResult = result;
            main.post(() -> {
                status("Automação parada. Tentativa de religar dados: " + finalResult);
                stopSelf();
            });
        });
    }

    private void status(String message) {
        DiagnosticLog.add(this, message);
        getSharedPreferences("automation", MODE_PRIVATE).edit()
                .putString("last_status", message).apply();
        getSystemService(NotificationManager.class).notify(
                NOTIFICATION_ID, notification(message));
    }

    private Notification notification(String detail) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stop = new Intent(this, WifiMonitorService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Wi-Fi Automático")
                .setContentText(detail)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentIntent(content)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_media_pause, "Parar", stopPending)
                .build();
    }

    @Override
    public void onDestroy() {
        getSharedPreferences("automation", MODE_PRIVATE).edit().remove("stopping_until").apply();
        monitoring = false;
        cancelTimer();
        if (wifiCallback != null) {
            try { connectivity.unregisterNetworkCallback(wifiCallback); }
            catch (Exception ignored) { }
        }
        Shizuku.removeBinderReceivedListener(receivedListener);
        Shizuku.removeBinderDeadListener(deadListener);
        shellQueue.shutdown();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
