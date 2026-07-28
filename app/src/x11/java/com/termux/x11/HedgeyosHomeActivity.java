package com.termux.x11;

import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.termux.app.HedgeyosRuntimeManager;
import com.termux.app.HedgeyosRuntimeService;

import java.util.Collections;
import java.util.List;

public final class HedgeyosHomeActivity extends MainActivity {

    private static final int HEDGEYOS_DISPLAY_DEFAULTS_VERSION = 2;

    private final Handler hedgeyosStatusHandler = new Handler(Looper.getMainLooper());
    private final Runnable hedgeyosStatusPoller = new Runnable() {
        @Override
        public void run() {
            updateStartupStatus();
            hedgeyosStatusHandler.postDelayed(this, 1000);
        }
    };

    private TextView startupStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyHedgeyosDisplayDefaults();
        startupStatus = findViewById(R.id.textView);
        rebrandStartupScreen();
        addHedgeyosMenuButton();
        HedgeyosRuntimeService.requestStart(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        hedgeyosStatusHandler.post(hedgeyosStatusPoller);
    }

    @Override
    public void onPause() {
        hedgeyosStatusHandler.removeCallbacks(hedgeyosStatusPoller);
        super.onPause();
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
            button.setOnClickListener(v -> showHedgeyosMenu());
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

        prefs.displayResolutionMode.put("scaled");
        prefs.get().edit()
            .putString("displayResolutionMode", "scaled")
            .putInt("displayScale", 240)
            .putBoolean("displayStretch", false)
            .putBoolean("fullscreen", true)
            .putBoolean("showAdditionalKbd", true)
            .putBoolean("additionalKbdVisible", true)
            .putBoolean("hedgeyosDisplayDefaultsApplied", true)
            .putInt("hedgeyosDisplayDefaultsVersion", HEDGEYOS_DISPLAY_DEFAULTS_VERSION)
            .commit();
        onPreferencesChanged("hedgeyosDisplayDefaultsApplied");
    }

    private void addHedgeyosMenuButton() {
        FrameLayout content = findViewById(android.R.id.content);
        ImageButton button = new ImageButton(this);
        button.setImageResource(com.termux.R.drawable.hedgeyos_icon);
        button.setScaleType(ImageView.ScaleType.CENTER_CROP);
        button.setPadding(dp(2), dp(2), dp(2), dp(2));
        button.setContentDescription("hedgeyos menu");
        button.setBackground(menuButtonBackground());
        button.setOnClickListener(v -> showHedgeyosMenu());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            button.setTooltipText("hedgeyos menu");
        }

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.END);
        params.setMargins(0, dp(12), dp(12), 0);
        content.addView(button, params);
    }

    private void showHedgeyosMenu() {
        String[] items = new String[] {
            "Open Debian Terminal",
            "Run Debian APT Check",
            "Open hedgeyos logs",
            "Restart Desktop",
            "Stop Desktop",
            "Reset Debian",
            "Toggle Soft Keyboard",
            "Android Apps",
            "Android Settings",
            "Display Settings",
            "Choose Home App"
        };

        new AlertDialog.Builder(this)
            .setTitle("hedgeyos")
            .setItems(items, (dialog, which) -> {
                switch (which) {
                    case 0:
                        openHedgeyosTerminal();
                        break;
                    case 1:
                        HedgeyosRuntimeManager.runDebianAcceptanceChecksAsync(this);
                        break;
                    case 2:
                        showHedgeyosLogs();
                        break;
                    case 3:
                        HedgeyosRuntimeService.requestRestartDesktop(this);
                        break;
                    case 4:
                        HedgeyosRuntimeService.requestStopDesktop(this);
                        break;
                    case 5:
                        confirmResetDebian();
                        break;
                    case 6:
                        MainActivity.toggleKeyboardVisibility(this);
                        break;
                    case 7:
                        showAndroidApps();
                        break;
                    case 8:
                        startActivity(new Intent(Settings.ACTION_SETTINGS));
                        break;
                    case 9:
                        openDisplaySettings();
                        break;
                    case 10:
                        requestHomeRole();
                        break;
                    default:
                        break;
                }
            })
            .show();
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

    private GradientDrawable menuButtonBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.argb(238, 55, 42, 32));
        drawable.setCornerRadius(dp(22));
        drawable.setStroke(dp(1), Color.rgb(238, 184, 91));
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
