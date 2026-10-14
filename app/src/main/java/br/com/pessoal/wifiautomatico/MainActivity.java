// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Leonardo Guarnieri de Bastiani

package br.com.pessoal.wifiautomatico;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import rikka.shizuku.Shizuku;

public final class MainActivity extends Activity {
    private static final int SHIZUKU_REQUEST = 9;
    private TextView state;
    private TextView logView;
    private TextView backgroundState;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService tests = java.util.concurrent.Executors.newSingleThreadExecutor();
    private boolean testBusy;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener logListener =
            (prefs, key) -> runOnUiThread(this::refresh);
    private final Shizuku.OnBinderReceivedListener binderReceived = () -> runOnUiThread(() -> {
        DiagnosticLog.add(this, "Shizuku disponível");
        refresh();
    });
    private final Shizuku.OnBinderDeadListener binderDead = () -> runOnUiThread(() -> {
        DiagnosticLog.add(this, "Shizuku parou");
        refresh();
    });
    private final Shizuku.OnRequestPermissionResultListener permissionResult = (requestCode, grantResult) -> {
        if (requestCode != SHIZUKU_REQUEST) return;
        runOnUiThread(() -> {
            DiagnosticLog.add(this, "Permissão Shizuku: " + grantResult);
            refresh();
            if (grantResult == PackageManager.PERMISSION_GRANTED) Toast.makeText(this, "Shizuku autorizado", Toast.LENGTH_SHORT).show();
            else Toast.makeText(this, "Permissão Shizuku recusada", Toast.LENGTH_LONG).show();
        });
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSharedPreferences("diagnostics", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(logListener);
        DiagnosticLog.add(this, "App aberto; Shizuku disponível=" + Shizuku.pingBinder());
        Shizuku.addBinderReceivedListenerSticky(binderReceived);
        Shizuku.addBinderDeadListener(binderDead);
        Shizuku.addRequestPermissionResultListener(permissionResult);

        int pad = (int) (getResources().getDisplayMetrics().density * 22);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, pad * 2, pad, pad);
        content.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("Wi-Fi Automático");
        title.setTextSize(26);
        title.setPadding(0, 0, 0, pad);
        content.addView(title);

        TextView details = new TextView(this);
        details.setText("• Ao conectar ao Wi-Fi: desliga os dados móveis.\n\n"
                + "• Ao ficar 2 minutos sem Wi-Fi: religa os dados móveis.\n\n"
                + "• Se reconectar antes dos 2 minutos: cancela a religação.\n");
        details.setTextSize(16);
        content.addView(details);

        state = new TextView(this);
        state.setTextSize(15);
        state.setPadding(0, pad, 0, pad);
        content.addView(state);

        backgroundState = new TextView(this);
        backgroundState.setTextSize(14);
        content.addView(backgroundState);
        makeButton("Permitir alarmes durante o repouso", content).setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 31) openSettings(new Intent(
                    android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    android.net.Uri.parse("package:" + getPackageName())));
            else Toast.makeText(this, "Alarmes já disponíveis neste Android", Toast.LENGTH_SHORT).show();
        });
        makeButton("Configurar bateria em segundo plano", content).setOnClickListener(v ->
                openSettings(new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));
        TextView backgroundHelp = new TextView(this);
        backgroundHelp.setText("Para funcionar com a tela apagada, permita alarmes e libere as restrições "
                + "de bateria deste app e do Shizuku. No HyperOS, habilite também a inicialização automática "
                + "dos dois nas configurações do aparelho. Sem alarmes exatos, a religação pode atrasar.");
        content.addView(backgroundHelp);

        Button permission = makeButton("1. Autorizar Shizuku", content);
        permission.setOnClickListener(v -> requestShizuku());

        Button enable = makeButton("2. Ativar automação", content);
        enable.setOnClickListener(v -> {
            if (!Shizuku.pingBinder()) {
                Toast.makeText(this, "Abra Shizuku e inicie o serviço primeiro", Toast.LENGTH_LONG).show();
            } else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                requestShizuku();
            } else {
                startMonitoring();
            }
        });

        Button disable = makeButton("Parar automação (tentar religar dados)", content);
        disable.setOnClickListener(v -> {
            getSharedPreferences("automation", MODE_PRIVATE).edit().putBoolean("enabled", false).apply();
            startService(new Intent(this, WifiMonitorService.class)
                    .setAction(WifiMonitorService.ACTION_STOP));
            refresh();
        });

        TextView testTitle = new TextView(this);
        testTitle.setText("Testes manuais (pare a automação primeiro)");
        content.addView(testTitle);
        makeButton("Ativar dados móveis", content).setOnClickListener(v -> manualTest("enable"));
        makeButton("Desligar dados móveis", content).setOnClickListener(v -> manualTest("disable"));
        makeButton("Toggle dos dados móveis", content).setOnClickListener(v -> manualTest("toggle"));
        makeButton("Copiar log de diagnóstico", content).setOnClickListener(v -> {
            DiagnosticLog.add(this, "Estado ao copiar: " + state.getText() + "; dados=" + mobileState());
            android.content.ClipboardManager clipboard = getSystemService(android.content.ClipboardManager.class);
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Diagnóstico Wi-Fi Automático", DiagnosticLog.report(this)));
            Toast.makeText(this, "Log copiado. Revise antes de compartilhar", Toast.LENGTH_SHORT).show();
        });
        makeButton("Limpar log", content).setOnClickListener(v ->
                getSharedPreferences("diagnostics", MODE_PRIVATE).edit().remove("log").apply());
        logView = new TextView(this);
        logView.setTextSize(12);
        logView.setTextIsSelectable(true);
        content.addView(logView);

        TextView alert = new TextView(this);
        alert.setPadding(0, pad, 0, 0);
        alert.setText("ATENÇÃO: se o Shizuku parar enquanto os dados móveis estiverem desligados, "
                + "o app poderá ficar impossibilitado de religá-los. Nesse caso, ligue-os "
                + "manualmente pelo painel de controle. Após reiniciar o celular, inicie o Shizuku novamente.");
        content.addView(alert);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        setContentView(scroll);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }
        refresh();
    }

    private void openSettings(Intent intent) {
        try { startActivity(intent); }
        catch (android.content.ActivityNotFoundException error) {
            Toast.makeText(this, "Abra as configurações do app no Android", Toast.LENGTH_LONG).show();
        }
    }

    private String mobileState() {
        return MobileDataCommand.observedState(this);
    }

    private void manualTest(String action) {
        DiagnosticLog.add(this, "Teste solicitado: " + action + "; dados=" + mobileState());
        if (getSharedPreferences("automation", MODE_PRIVATE).getBoolean("enabled", false)
                || (getSharedPreferences("automation", MODE_PRIVATE).getLong("stopping_until", 0) > android.os.SystemClock.elapsedRealtime())) {
            DiagnosticLog.add(this, "Teste bloqueado: pare a automação primeiro");
            Toast.makeText(this, "Pare a automação antes de testar", Toast.LENGTH_LONG).show();
            return;
        }
        if (testBusy) {
            Toast.makeText(this, "Aguarde o teste anterior", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Shizuku.pingBinder()) {
            DiagnosticLog.add(this, "ERRO: Shizuku desligado; abra o Shizuku e inicie-o");
            refresh();
            return;
        }
        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                DiagnosticLog.add(this, "ERRO: falta autorizar Shizuku");
                requestShizuku();
                return;
            }
            if ("toggle".equals(action)) {
                String current = mobileState();
                if (!"ligados".equals(current) && !"desligados".equals(current)) {
                    DiagnosticLog.add(this, "Toggle cancelado: não foi possível ler os dados: " + current);
                    if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
                        requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE}, 101);
                    return;
                }
                action = "ligados".equals(current) ? "disable" : "enable";
            }
            testBusy = true;
            DiagnosticLog.add(this, "Executando pelo servidor Shizuku; UID=" + Shizuku.getUid());
            executeTest(action);
        } catch (Exception e) {
            testBusy = false;
            DiagnosticLog.add(this, "ERRO no teste: " + e);
        }
        refresh();
    }

    private void executeTest(String action) {
        tests.execute(() -> {
            String result;
            try { result = MobileDataCommand.setEnabled("enable".equals(action)); }
            catch (Exception e) { result = "ERRO: " + e; }
            DiagnosticLog.add(this, "Teste " + action + ": " + result);
            handler.post(() -> {
                testBusy = false;
                DiagnosticLog.add(this, "Estado após comando: " + mobileState());
                refresh();
            });
        });
    }

    private Button makeButton(String label, LinearLayout content) {
        Button button = new Button(this);
        button.setText(label);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
        content.addView(button, params);
        return button;
    }

    private void requestShizuku() {
        if (!Shizuku.pingBinder()) {
            Toast.makeText(this, "Instale, abra e inicie o Shizuku primeiro", Toast.LENGTH_LONG).show();
            return;
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Shizuku já autorizado", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Shizuku.requestPermission(SHIZUKU_REQUEST);
        } catch (Exception e) {
            DiagnosticLog.add(this, "Falha na permissão: " + e);
            Toast.makeText(this, "Não foi possível solicitar permissão: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void startMonitoring() {
        if (testBusy || (getSharedPreferences("automation", MODE_PRIVATE).getLong("stopping_until", 0) > android.os.SystemClock.elapsedRealtime())) {
            Toast.makeText(this, "Aguarde a operação terminar", Toast.LENGTH_SHORT).show();
            return;
        }
        DiagnosticLog.add(this, "Ativar automação solicitado");
        getSharedPreferences("automation", MODE_PRIVATE).edit().putBoolean("enabled", true).apply();
        Intent intent = new Intent(this, WifiMonitorService.class).setAction(WifiMonitorService.ACTION_START);
        try {
            startForegroundService(intent);
            Toast.makeText(this, "Monitor iniciado", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            DiagnosticLog.add(this, "Falha ao iniciar: " + e);
            getSharedPreferences("automation", MODE_PRIVATE).edit().putBoolean("enabled", false).apply();
            Toast.makeText(this, "Não foi possível iniciar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        refresh();
    }

    private void refresh() {
        if (state == null) return;
        boolean enabled = getSharedPreferences("automation", MODE_PRIVATE).getBoolean("enabled", false);
        if (backgroundState != null) {
            boolean unrestricted = getSystemService(android.os.PowerManager.class)
                    .isIgnoringBatteryOptimizations(getPackageName());
            backgroundState.setText("Alarmes exatos: " + (RestoreAlarm.canScheduleExact(this) ? "permitidos" : "não permitidos")
                    + "\nOtimização de bateria: " + (unrestricted ? "isento" : "ativa"));
        }
        String shizukuState = !Shizuku.pingBinder() ? "Shizuku desligado"
                : Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                ? "Shizuku autorizado" : "Shizuku sem autorização";
        if (logView != null) logView.setText(DiagnosticLog.report(this));
        String last = getSharedPreferences("automation", MODE_PRIVATE)
                .getString("last_status", "Nenhum evento registrado ainda");
        state.setText("Automação configurada: " + (enabled ? "ATIVA" : "DESATIVADA") + "\n"
                + shizukuState + "\nDados móveis: " + mobileState() + "\n" + last);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        DiagnosticLog.add(this, "Permissão Android " + requestCode + ": " + java.util.Arrays.toString(grantResults));
        if (requestCode == 101) Toast.makeText(this, "Toque no toggle novamente para testar", Toast.LENGTH_LONG).show();
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (RestoreAlarm.prefs(this).getBoolean("enabled", false)) {
            try {
                startForegroundService(new Intent(this, WifiMonitorService.class)
                        .setAction(WifiMonitorService.ACTION_START));
            } catch (RuntimeException error) {
                DiagnosticLog.add(this, "Falha ao reconciliar monitor ao abrir: " + error);
            }
        }
        refresh();
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceived);
        Shizuku.removeBinderDeadListener(binderDead);
        Shizuku.removeRequestPermissionResultListener(permissionResult);
        getSharedPreferences("diagnostics", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(logListener);
        tests.shutdown();
        super.onDestroy();
    }
}
