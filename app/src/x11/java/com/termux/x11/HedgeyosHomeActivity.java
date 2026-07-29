package com.termux.x11;

import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import com.termux.app.HedgeyosRuntimeManager;
import com.termux.app.HedgeyosRuntimeService;
import com.termux.x11.utils.TermuxX11ExtraKeys;

import java.util.Collections;
import java.util.List;

public final class HedgeyosHomeActivity extends MainActivity {

    private static final int HEDGEYOS_DISPLAY_DEFAULTS_VERSION = 3;
    private static final String HEDGEYOS_EXTRA_KEYS =
        "[['ESC','/',{key: '-', popup: '|'},'HOME','UP','END','KEYBOARD','PREFERENCES'], " +
        "['TAB','CTRL','ALT','LEFT','DOWN','RIGHT','PGDN','PGUP']]";

    private final Handler hedgeyosStatusHandler = new Handler(Looper.getMainLooper());
    private final Runnable hedgeyosStatusPoller = new Runnable() {
        @Override
        public void run() {
            updateStartupStatus();
            hedgeyosStatusHandler.postDelayed(this, 1000);
        }
    };

    private TextView startupStatus;
    private HedgeyosOverlayController overlayController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyHedgeyosDisplayDefaults();
        startupStatus = findViewById(R.id.textView);
        rebrandStartupScreen();
        overlayController = new HedgeyosOverlayController(this, new HedgeyosOverlayController.Actions() {
            @Override
            public void openTerminal() {
                openHedgeyosTerminal();
            }

            @Override
            public void runAptCheck() {
                HedgeyosRuntimeManager.runDebianAcceptanceChecksAsync(HedgeyosHomeActivity.this);
            }

            @Override
            public void showRuntimeReport() {
                showLinuxRuntimeReport();
            }

            @Override
            public void showLogs() {
                showHedgeyosLogs();
            }

            @Override
            public void restartDesktop() {
                HedgeyosRuntimeService.requestRestartDesktop(HedgeyosHomeActivity.this);
            }

            @Override
            public void stopDesktop() {
                HedgeyosRuntimeService.requestStopDesktop(HedgeyosHomeActivity.this);
            }

            @Override
            public void resetDebian() {
                confirmResetDebian();
            }

            @Override
            public void toggleKeyboard() {
                MainActivity.toggleKeyboardVisibility(HedgeyosHomeActivity.this);
            }

            @Override
            public void showAndroidApps() {
                HedgeyosHomeActivity.this.showAndroidApps();
            }

            @Override
            public void openAndroidSettings() {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }

            @Override
            public void openDisplaySettings() {
                HedgeyosHomeActivity.this.openDisplaySettings();
            }

            @Override
            public void chooseHomeApp() {
                requestHomeRole();
            }
        });
        overlayController.install();
        HedgeyosRuntimeService.requestStart(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        hedgeyosStatusHandler.post(hedgeyosStatusPoller);
        if (overlayController != null) {
            overlayController.onResume();
        }
    }

    @Override
    public void onPause() {
        hedgeyosStatusHandler.removeCallbacks(hedgeyosStatusPoller);
        if (overlayController != null) {
            overlayController.onPause();
        }
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (overlayController != null) {
            overlayController.onWindowFocusChanged(hasFocus);
        }
    }

    @Override
    protected void onDestroy() {
        if (overlayController != null) {
            overlayController.destroy();
            overlayController = null;
        }
        super.onDestroy();
    }

    @Override
    public void onUserLeaveHint() {
        // hedgeyos is the HOME activity; pressing Home should return here, not
        // move the desktop into Termux:X11 picture-in-picture mode.
    }

    private void rebrandStartupScreen() {
        View preferences = findViewById(R.id.preferences_button);
        if (preferences instanceof Button) {
            Button button = (Button) preferences;
            button.setText("Display Settings");
            button.setOnClickListener(v -> openDisplaySettings());
        }

        View help = findViewById(R.id.help_button);
        if (help instanceof Button) {
            Button button = (Button) help;
            button.setText("hedgeyos menu");
            button.setOnClickListener(v -> {
                if (overlayController != null) {
                    overlayController.showControlMenu();
                }
            });
        }

        View exit = findViewById(R.id.exit_button);
        if (exit instanceof Button) {
            Button button = (Button) exit;
            button.setText("Stop Desktop");
            button.setOnClickListener(v -> HedgeyosRuntimeService.requestStopDesktop(this));
        }

        updateStartupStatus();
    }

    private void applyHedgeyosDisplayDefaults() {
        if (prefs == null) {
            return;
        }
        int appliedVersion = prefs.get().getInt(
            "hedgeyosDisplayDefaultsVersion",
            prefs.get().getBoolean("hedgeyosDisplayDefaultsApplied", false) ? 1 : 0);
        if (appliedVersion >= HEDGEYOS_DISPLAY_DEFAULTS_VERSION) {
            return;
        }

        String extraKeys = prefs.extra_keys_config.get();
        boolean usesUpstreamExtraKeys =
            extraKeys == null || extraKeys.isEmpty() ||
                TermuxX11ExtraKeys.DEFAULT_IVALUE_EXTRA_KEYS.equals(extraKeys);

        if (appliedVersion < 2) {
            prefs.displayResolutionMode.put("scaled");
        }
        prefs.touchMode.put("3");
        if (usesUpstreamExtraKeys) {
            prefs.extra_keys_config.put(HEDGEYOS_EXTRA_KEYS);
        }

        android.content.SharedPreferences.Editor editor = prefs.get().edit();
        if (appliedVersion < 2) {
            editor
                .putString("displayResolutionMode", "scaled")
                .putInt("displayScale", 240)
                .putBoolean("displayStretch", false)
                .putBoolean("fullscreen", true)
                .putBoolean("showAdditionalKbd", true)
                .putBoolean("additionalKbdVisible", true);
        }
        editor
            .putString("touchMode", "3")
            .putBoolean("hedgeyosDisplayDefaultsApplied", true)
            .putInt("hedgeyosDisplayDefaultsVersion", HEDGEYOS_DISPLAY_DEFAULTS_VERSION);
        if (usesUpstreamExtraKeys) {
            editor.putString("extra_keys_config", HEDGEYOS_EXTRA_KEYS);
        }
        editor.commit();
        onPreferencesChanged("hedgeyosDisplayDefaultsApplied");
    }

    private void openHedgeyosTerminal() {
        HedgeyosRuntimeManager.openDebianTerminalAsync(this);
    }

    private void openDisplaySettings() {
        Intent intent = new Intent(this, LoriePreferences.class);
        intent.setAction(Intent.ACTION_MAIN);
        startActivity(intent);
    }

    private void confirmResetDebian() {
        new AlertDialog.Builder(this)
            .setTitle("Reset Debian")
            .setMessage("Delete the installed Debian rootfs and keep the hedgeyos export directory?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Reset", (dialog, which) -> HedgeyosRuntimeService.requestResetDebian(this))
            .show();
    }

    private void showHedgeyosLogs() {
        TextView logText = new TextView(this);
        logText.setText(HedgeyosRuntimeManager.readRecentLogs(this));
        logText.setTextIsSelectable(true);
        logText.setTextSize(12);
        int padding = dp(16);
        logText.setPadding(padding, padding, padding, padding);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(logText);

        new AlertDialog.Builder(this)
            .setTitle("hedgeyos logs")
            .setView(scrollView)
            .setPositiveButton("Close", null)
            .show();
    }

    private void showLinuxRuntimeReport() {
        TextView reportText = new TextView(this);
        reportText.setText(HedgeyosRuntimeManager.readLinuxRuntimeReport(this));
        reportText.setTextIsSelectable(true);
        reportText.setTextSize(12);
        int padding = dp(16);
        reportText.setPadding(padding, padding, padding, padding);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(reportText);

        new AlertDialog.Builder(this)
            .setTitle("Linux Runtime Report")
            .setView(scrollView)
            .setPositiveButton("Close", null)
            .show();
    }

    private void showAndroidApps() {
        PackageManager packageManager = getPackageManager();
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = packageManager.queryIntentActivities(launcherIntent, 0);
        Collections.sort(apps, new ResolveInfo.DisplayNameComparator(packageManager));

        String[] labels = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            ResolveInfo app = apps.get(i);
            labels[i] = app.loadLabel(packageManager).toString();
        }

        new AlertDialog.Builder(this)
            .setTitle("Android Apps")
            .setItems(labels, (dialog, which) -> launchAndroidApp(apps.get(which)))
            .show();
    }

    private void launchAndroidApp(ResolveInfo resolveInfo) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(new ComponentName(
            resolveInfo.activityInfo.packageName,
            resolveInfo.activityInfo.name));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void requestHomeRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = getSystemService(RoleManager.class);
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                startActivity(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME));
                return;
            }
        }

        startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
    }

    private void updateStartupStatus() {
        if (startupStatus == null) {
            return;
        }
        HedgeyosRuntimeManager.RuntimeStatus runtimeStatus = HedgeyosRuntimeManager.getStatus(this);
        String worker = runtimeStatus.workerRunning ? " working" : "";
        startupStatus.setText("hedgeyos: " + runtimeStatus.state + worker + "\n" + runtimeStatus.detail);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
