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
import android.os.PowerManager;

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

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService shellQueue = Executors.newSingleThreadExecutor();
    private final Set<Network> wifiNetworks = new HashSet<>();
    private ConnectivityManager connectivity;
    private ConnectivityManager.NetworkCallback wifiCallback;
    private volatile boolean isWifiConnected;
    private volatile boolean monitoring;
    private volatile int operationId;
    private boolean restoreInFlight;

    private final Shizuku.OnBinderReceivedListener receivedListener = () ->
            main.post(this::connectShizuku);

    private final Shizuku.OnBinderDeadListener deadListener = () ->
            main.post(() -> {
                if (monitoring) status("Shizuku parou: controle automático indisponível");
            });

    @Override
    public void onCreate() {
        super.onCreate();
        DiagnosticLog.add(this, "Serviço criado");
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, "Automação de rede", NotificationManager.IMPORTANCE_LOW));
        startForeground(NOTIFICATION_ID, notification());
        connectivity = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        Shizuku.addBinderReceivedListenerSticky(receivedListener);
        Shizuku.addBinderDeadListener(deadListener);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            String action = intent == null ? null : intent.getAction();
            DiagnosticLog.add(this, "Serviço iniciado; ação=" + action + "; reinício=" + (intent == null));
            if (ACTION_STOP.equals(action)) {
                stopAutomation();
                return START_NOT_STICKY;
            }
            if (!RestoreAlarm.prefs(this).getBoolean("enabled", false)) {
                stopSelf();
                return START_NOT_STICKY;
            }
            if (RestoreAlarm.ACTION.equals(action) && !RestoreAlarm.isCurrent(this,
                    intent.getLongExtra(RestoreAlarm.TOKEN, -1))) {
                DiagnosticLog.add(this, "Entrega antiga ignorada pelo serviço");
                if (!monitoring) stopSelf();
                return monitoring ? START_STICKY : START_NOT_STICKY;
            }
            if (!monitoring) startAutomation();
            else reconcileWifi();
            return START_STICKY;
        } finally {
            RestoreReceiver.releaseDeliveryLock();
        }
    }

    private void startAutomation() {
        monitoring = true;
        getSharedPreferences("automation", MODE_PRIVATE).edit().putBoolean("enabled", true).apply();

        wifiCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                main.post(() -> {
                    if (!monitoring) return;
                    DiagnosticLog.add(WifiMonitorService.this, "Evento Wi-Fi disponível");
                    readWifiNetworks();
                    updateWifiState();
                });
            }

            @Override
            public void onLost(Network network) {
                main.post(() -> {
                    if (!monitoring) return;
                    DiagnosticLog.add(WifiMonitorService.this, "Evento Wi-Fi perdido");
                    readWifiNetworks();
                    updateWifiState();
                });
            }
        };
        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build();
        connectivity.registerNetworkCallback(request, wifiCallback);
        readWifiNetworks();
        isWifiConnected = !wifiNetworks.isEmpty();
        if (isWifiConnected) {
            cancelTimer();
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

    private boolean hasWifi() {
        for (Network network : connectivity.getAllNetworks()) {
            NetworkCapabilities capabilities = connectivity.getNetworkCapabilities(network);
            if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return true;
        }
        return false;
    }

    private void readWifiNetworks() {
        wifiNetworks.clear();
        for (Network network : connectivity.getAllNetworks()) {
            NetworkCapabilities capabilities = connectivity.getNetworkCapabilities(network);
            if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                wifiNetworks.add(network);
        }
    }

    private void reconcileWifi() {
        readWifiNetworks();
        boolean changed = isWifiConnected != !wifiNetworks.isEmpty();
        updateWifiState();
        if (!changed && !isWifiConnected) beginTwoMinuteTimer();
    }

    private void beginTwoMinuteTimer() {
        if (RestorePolicy.isDue(monitoring, isWifiConnected, RestoreAlarm.deadline(this),
                SystemClock.elapsedRealtime())) {
            if (!restoreInFlight) setMobileData(true, "Wi-Fi ausente por 2 minutos");
        } else {
            status("Wi-Fi desconectado: aguardando 2 minutos");
            RestoreAlarm.schedule(this);
        }
    }

    private void cancelTimer() {
        ++operationId;
        RestoreAlarm.cancel(this);
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
        reconcileWifi();
        if (isWifiConnected) setMobileData(false, "Shizuku disponível e Wi-Fi conectado");
    }

    private void setMobileData(boolean enabled, String reason) {
        if (enabled && restoreInFlight) return;
        if (enabled) restoreInFlight = true;
        int thisOperation = ++operationId;
        long token = RestoreAlarm.prefs(this).getLong(RestoreAlarm.TOKEN, 0);
        PowerManager.WakeLock lock = getSystemService(PowerManager.class)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "wifi-automatico:data-command");
        lock.acquire(30_000L);
        status(reason + (enabled ? ": religando dados móveis" : ": desligando dados móveis"));
        shellQueue.execute(() -> {
            String result;
            try {
                if (!monitoring || thisOperation != operationId
                        || enabled == hasWifi()
                        || (enabled && !RestoreAlarm.isCurrent(this, token))) {
                    result = "IGNORADO: operação substituída ou rede alterada";
                } else {
                    result = MobileDataCommand.setEnabled(enabled);
                }
            } catch (Exception error) {
                result = "ERRO: " + error;
            }
            DiagnosticLog.add(this, "Comando automático: " + result);
            DiagnosticLog.add(this, "Dados observados após comando: " + MobileDataCommand.observedState(this));
            final String finalResult = result;
            main.post(() -> {
                try {
                    if (enabled) restoreInFlight = false;
                    if (monitoring && finalResult.startsWith("IGNORADO:")) {
                        reconcileWifi();
                        return;
                    }
                    if (enabled && monitoring && thisOperation != operationId && !isWifiConnected)
                        beginTwoMinuteTimer();
                    if (monitoring && thisOperation == operationId) {
                        if (enabled && finalResult.startsWith("OK:")) RestoreAlarm.cancel(this);
                        status(finalResult.startsWith("OK:")
                                ? "Dados móveis " + (enabled ? "ligados" : "desligados")
                                : "Falha ao alterar dados móveis: " + finalResult);
                    }
                } finally {
                    if (lock.isHeld()) lock.release();
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
        PowerManager.WakeLock stopLock = getSystemService(PowerManager.class)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "wifi-automatico:stop-restore");
        stopLock.acquire(30_000L);
        shellQueue.execute(() -> {
            String result;
            try {
                result = MobileDataCommand.setEnabled(true);
            } catch (Exception e) {
                result = "ERRO: " + e.getMessage();
            }
            String finalResult = result;
            main.post(() -> {
                try {
                    status("Automação parada. Tentativa de religar dados: " + finalResult);
                    stopSelf();
                } finally {
                    if (stopLock.isHeld()) stopLock.release();
                }
            });
        });
    }

    private void status(String message) {
        DiagnosticLog.add(this, message);
        getSharedPreferences("automation", MODE_PRIVATE).edit()
                .putString("last_status", message).apply();
        // Mudanças de estado ficam na interface e no log, sem reenviar notificações.
    }

    private Notification notification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stop = new Intent(this, WifiMonitorService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Wi-Fi Automático")
                .setContentText("Automação ativa • Monitorando o Wi-Fi")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentIntent(content)
                .setOngoing(true)
                .setGroup("wifi-automatico-monitor")
                .setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .addAction(android.R.drawable.ic_media_pause, "Parar", stopPending)
                .build();
    }

    @Override
    public void onDestroy() {
        getSharedPreferences("automation", MODE_PRIVATE).edit().remove("stopping_until").apply();
        DiagnosticLog.add(this, "Serviço destruído; prazo de religação preservado");
        monitoring = false;
        ++operationId;
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
