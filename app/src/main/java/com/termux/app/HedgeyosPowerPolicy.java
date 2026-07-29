package com.termux.app;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HedgeyosPowerPolicy {

    private static final String PREFS_NAME = "hedgeyos_power_setup";
    private static final String KEY_ONBOARDING_VERSION = "onboarding_version";
    private static final String KEY_MANUAL_REVIEWED = "manual_background_settings_reviewed";
    private static final int ONBOARDING_VERSION = 1;

    private HedgeyosPowerPolicy() {}

    public static boolean isOnboardingComplete(Context context) {
        return preferences(context).getInt(KEY_ONBOARDING_VERSION, 0) >= ONBOARDING_VERSION;
    }

    public static void completeOnboarding(Context context, boolean manualReviewed) {
        preferences(context).edit()
            .putInt(KEY_ONBOARDING_VERSION, ONBOARDING_VERSION)
            .putBoolean(KEY_MANUAL_REVIEWED, manualReviewed)
            .apply();
    }

    public static boolean wasManualBackgroundSetupReviewed(Context context) {
        return preferences(context).getBoolean(KEY_MANUAL_REVIEWED, false);
    }

    public static boolean isIgnoringBatteryOptimizations(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        PowerManager manager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return manager != null && manager.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    public static boolean isBackgroundRestricted(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return false;
        }
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        return manager != null && manager.isBackgroundRestricted();
    }

    public static boolean areNotificationsEnabled(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return true;
        }
        NotificationManager manager =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        return manager == null || manager.areNotificationsEnabled();
    }

    public static boolean requestBatteryOptimizationExemption(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || isIgnoringBatteryOptimizations(activity)) {
            return true;
        }
        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:" + activity.getPackageName()));
        return startResolvedActivity(activity, intent);
    }

    public static boolean openBatterySettings(Activity activity) {
        List<Intent> candidates = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            candidates.add(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
        candidates.add(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:" + activity.getPackageName())));
        candidates.add(new Intent(Settings.ACTION_SETTINGS));
        return startFirstResolvedActivity(activity, candidates);
    }

    public static boolean openNotificationSettings(Activity activity) {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName());
        if (startResolvedActivity(activity, intent)) {
            return true;
        }
        return startResolvedActivity(activity,
            new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:" + activity.getPackageName())));
    }

    public static boolean openVendorBackgroundSettings(Activity activity) {
        List<Intent> candidates = new ArrayList<>();
        String manufacturer = manufacturer();

        if (containsAny(manufacturer, "oppo", "realme", "oneplus")) {
            candidates.add(componentIntent("com.oplus.battery",
                "com.oplus.powermanager.fuelgaue.PowerUsageModelActivity"));
            candidates.add(componentIntent("com.coloros.oppoguardelf",
                "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"));
            candidates.add(componentIntent("com.oplus.safecenter",
                "com.oplus.safecenter.permission.startupapp.StartupAppListActivity"));
            candidates.add(componentIntent("com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"));
        } else if (containsAny(manufacturer, "xiaomi", "redmi")) {
            candidates.add(componentIntent("com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"));
            candidates.add(componentIntent("com.miui.powerkeeper",
                "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"));
        } else if (containsAny(manufacturer, "samsung")) {
            candidates.add(componentIntent("com.samsung.android.lool",
                "com.samsung.android.sm.battery.ui.BatteryActivity"));
        } else if (containsAny(manufacturer, "vivo", "iqoo")) {
            candidates.add(componentIntent("com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"));
            candidates.add(componentIntent("com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"));
        } else if (containsAny(manufacturer, "huawei", "honor")) {
            candidates.add(componentIntent("com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"));
            candidates.add(componentIntent("com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"));
        }

        candidates.add(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:" + activity.getPackageName())));
        candidates.add(new Intent(Settings.ACTION_SETTINGS));
        return startFirstResolvedActivity(activity, candidates);
    }

    public static String vendorInstructions() {
        String manufacturer = manufacturer();
        if (containsAny(manufacturer, "oppo", "realme")) {
            return "ColorOS / Realme UI: set Battery usage to Unrestricted, enable Allow " +
                "background activity and Auto launch, and lock hedgeyos in Recents when that " +
                "option is available.";
        }
        if (containsAny(manufacturer, "oneplus")) {
            return "OxygenOS: set Battery usage to Unrestricted, enable Allow background " +
                "activity and Auto launch, and lock hedgeyos in Recents if available.";
        }
        if (containsAny(manufacturer, "xiaomi", "redmi")) {
            return "MIUI / HyperOS: enable Autostart, choose No restrictions for battery " +
                "saver, and lock hedgeyos in Recents.";
        }
        if (containsAny(manufacturer, "samsung")) {
            return "Samsung: set Battery to Unrestricted and add hedgeyos to Never sleeping " +
                "apps.";
        }
        if (containsAny(manufacturer, "vivo", "iqoo")) {
            return "Vivo / iQOO: enable Autostart and allow high background power " +
                "consumption for hedgeyos.";
        }
        if (containsAny(manufacturer, "huawei", "honor")) {
            return "Huawei / Honor: open App launch, disable automatic management for " +
                "hedgeyos, then allow auto-launch, secondary launch, and background running.";
        }
        return "Open hedgeyos App info and allow unrestricted battery use, background " +
            "activity, notifications, and autostart. Lock it in Recents if your phone offers " +
            "that control.";
    }

    public static String manufacturerLabel() {
        String value = Build.MANUFACTURER == null ? "Android" : Build.MANUFACTURER.trim();
        return value.isEmpty() ? "Android" : value;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static Intent componentIntent(String packageName, String className) {
        return new Intent().setComponent(new ComponentName(packageName, className));
    }

    private static boolean startFirstResolvedActivity(Activity activity, List<Intent> intents) {
        for (Intent intent : intents) {
            if (startResolvedActivity(activity, intent)) {
                return true;
            }
        }
        return false;
    }

    private static boolean startResolvedActivity(Activity activity, Intent intent) {
        try {
            if (intent.resolveActivity(activity.getPackageManager()) == null) {
                return false;
            }
            activity.startActivity(intent);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String manufacturer() {
        return Build.MANUFACTURER == null
            ? ""
            : Build.MANUFACTURER.toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
