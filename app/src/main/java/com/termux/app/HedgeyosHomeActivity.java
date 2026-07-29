package com.termux.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.termux.R;

import java.util.Collections;
import java.util.List;

public final class HedgeyosHomeActivity extends Activity {

    private static final int PANEL_COLOR = Color.argb(228, 38, 33, 29);
    private static final int TEXT_COLOR = Color.rgb(255, 248, 230);
    private static final int MUTED_TEXT_COLOR = Color.rgb(222, 205, 174);
    private static final int ACCENT_COLOR = Color.rgb(238, 184, 91);

    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private final Runnable statusPoller = new Runnable() {
        @Override
        public void run() {
            updateRuntimeStatus();
            statusHandler.postDelayed(this, 1000);
        }
    };
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createContentView());
        HedgeyosRuntimeService.requestStart(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        statusHandler.post(statusPoller);
    }

    @Override
    protected void onPause() {
        statusHandler.removeCallbacks(statusPoller);
        super.onPause();
    }

    private View createContentView() {
        FrameLayout frame = new FrameLayout(this);

        ImageView wallpaper = new ImageView(this);
        wallpaper.setImageResource(R.drawable.hedgeyos_wallpaper);
        wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wallpaper.setAlpha(0.72f);
        frame.addView(wallpaper, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.argb(112, 8, 9, 12));
        frame.addView(scrollView, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(24), dp(32), dp(24), dp(32));
        scrollView.addView(root, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.hedgeyos_icon);
        icon.setContentDescription("hedgeyos");
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(96), dp(96));
        iconParams.setMargins(0, 0, 0, dp(14));
        root.addView(icon, iconParams);

        TextView title = new TextView(this);
        title.setText("hedgeyos");
        title.setTextColor(TEXT_COLOR);
        title.setTextSize(34);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        status = new TextView(this);
        status.setText("Runtime: checking...");
        status.setTextColor(MUTED_TEXT_COLOR);
        status.setTextSize(16);
        status.setLineSpacing(dp(2), 1.0f);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.setMargins(0, dp(10), 0, dp(24));
        root.addView(status, statusParams);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(16), dp(16), dp(16));
        panel.setBackground(panelBackground());
        root.addView(panel, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        panel.addView(button("Start Runtime", v -> HedgeyosRuntimeService.requestStart(this)));
        panel.addView(button("Restart Desktop", v -> HedgeyosRuntimeService.requestRestartDesktop(this)));
        panel.addView(button("Stop Desktop", v -> HedgeyosRuntimeService.requestStopDesktop(this)));
        panel.addView(button("Reset Debian", v -> confirmResetDebian()));
        panel.addView(button("Open X11 Surface", v -> openX11Surface()));
        panel.addView(button("Linux Runtime Report", v -> showLinuxRuntimeReport()));
        panel.addView(button("Open hedgeyos logs", v -> showHedgeyosLogs()));
        panel.addView(button("Open hedgeyos terminal", v -> openHedgeyosTerminal()));
        panel.addView(button("Run Debian APT Check", v -> HedgeyosRuntimeManager.runDebianAcceptanceChecksAsync(this)));
        panel.addView(button("Android Apps", v -> showAndroidApps()));
        panel.addView(button("Android Settings", v -> startActivity(new Intent(Settings.ACTION_SETTINGS))));
        panel.addView(button("Choose Home App", v -> requestHomeRole()));

        return frame;
    }

    private Button button(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(TEXT_COLOR);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setGravity(Gravity.CENTER);
        button.setBackground(buttonBackground());
        button.setOnClickListener(listener);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(48));
        params.setMargins(0, 0, 0, dp(10));
        button.setLayoutParams(params);

        return button;
    }

    private void updateRuntimeStatus() {
        if (status == null) {
            return;
        }
        HedgeyosRuntimeManager.RuntimeStatus runtimeStatus = HedgeyosRuntimeManager.getStatus(this);
        String worker = runtimeStatus.workerRunning ? " working" : "";
        status.setText("Runtime: " + runtimeStatus.state + worker + "\n" + runtimeStatus.detail);
    }

    private GradientDrawable panelBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(PANEL_COLOR);
        drawable.setCornerRadius(dp(8));
        drawable.setStroke(dp(1), Color.rgb(42, 50, 60));
        return drawable;
    }

    private GradientDrawable buttonBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.argb(238, 67, 51, 40));
        drawable.setCornerRadius(dp(6));
        drawable.setStroke(dp(1), ACCENT_COLOR);
        return drawable;
    }

    private void openHedgeyosTerminal() {
        HedgeyosRuntimeManager.openDebianTerminalAsync(this);
    }

    private void openX11Surface() {
        if (!HedgeyosX11Bridge.isAvailable(this)) {
            new AlertDialog.Builder(this)
                .setTitle("X11 Surface")
                .setMessage("This build does not include the embedded Termux:X11 module.")
                .setPositiveButton("Close", null)
                .show();
            return;
        }
        HedgeyosX11Bridge.openSurface(this);
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

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
