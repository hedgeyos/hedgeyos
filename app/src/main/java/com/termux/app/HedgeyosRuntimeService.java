package com.termux.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import com.termux.BuildConfig;
import com.termux.R;

public final class HedgeyosRuntimeService extends Service {

    static final String ACTION_START = "org.hedgeyos.runtime.START";
    static final String ACTION_RESTART_DESKTOP = "org.hedgeyos.runtime.RESTART_DESKTOP";
    static final String ACTION_STOP_DESKTOP = "org.hedgeyos.runtime.STOP_DESKTOP";
    static final String ACTION_RESET_DEBIAN = "org.hedgeyos.runtime.RESET_DEBIAN";

    private static final String CHANNEL_ID = "hedgeyos_runtime";
    private static final String CHANNEL_NAME = "hedgeyos Runtime";
    private static final int NOTIFICATION_ID = 4242;
    private static final String PREFS_NAME = "hedgeyos_runtime_service";
    private static final String KEY_DESIRED_RUNNING = "desired_running";

    private static volatile boolean sRuntimeWakeLockHeld;
    private PowerManager.WakeLock runtimeWakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        setupNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null
            ? (isDesiredRunning(this) ? ACTION_START : ACTION_STOP_DESKTOP)
            : intent.getAction();
        if (ACTION_STOP_DESKTOP.equals(action)) {
            setDesiredRunning(this, false);
            releaseRuntimeWakeLock();
            HedgeyosRuntimeManager.stopDesktop(this);
            stopForeground(true);
            stopSelf(startId);
            return START_NOT_STICKY;
        } else if (ACTION_RESET_DEBIAN.equals(action)) {
            setDesiredRunning(this, false);
            releaseRuntimeWakeLock();
            HedgeyosRuntimeManager.resetDebianAsync(this);
        } else if (ACTION_RESTART_DESKTOP.equals(action)) {
            setDesiredRunning(this, true);
            acquireRuntimeWakeLock();
            HedgeyosRuntimeManager.restartDesktopAsync(this);
        } else {
            setDesiredRunning(this, true);
            acquireRuntimeWakeLock();
            HedgeyosRuntimeManager.startAsync(this);
        }

        Notification notification = buildNotification();
        startForeground(NOTIFICATION_ID, notification);
        NotificationManager notificationManager =
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, notification);
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        releaseRuntimeWakeLock();
        super.onDestroy();
    }

    public static void requestStart(Context context) {
        startWithAction(context, ACTION_START);
    }

    public static void requestRestartDesktop(Context context) {
        startWithAction(context, ACTION_RESTART_DESKTOP);
    }

    public static void requestStopDesktop(Context context) {
        startWithAction(context, ACTION_STOP_DESKTOP);
    }

    public static void requestResetDebian(Context context) {
        startWithAction(context, ACTION_RESET_DEBIAN);
    }

    public static void restoreAfterBoot(Context context) {
        if (isDesiredRunning(context)) {
            requestStart(context);
        }
    }

    public static boolean isDesiredRunning(Context context) {
        return preferences(context).getBoolean(KEY_DESIRED_RUNNING, false);
    }

    public static boolean isRuntimeWakeLockHeld() {
        return sRuntimeWakeLockHeld;
    }

    private static void startWithAction(Context context, String action) {
        Intent intent = new Intent(context, HedgeyosRuntimeService.class).setAction(action);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    private Notification buildNotification() {
        Intent contentIntent = new Intent();
        if (BuildConfig.HEDGEYOS_INCLUDE_X11_MODULE) {
            contentIntent.setClassName(this, "com.termux.x11.HedgeyosHomeActivity");
        } else {
            contentIntent.setClass(this, HedgeyosHomeActivity.class);
        }
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, contentIntent, flags);
        HedgeyosRuntimeManager.RuntimeStatus status = HedgeyosRuntimeManager.getStatus(this);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? new Notification.Builder(this, CHANNEL_ID)
            : new Notification.Builder(this);
        boolean protectedRuntime = isDesiredRunning(this) && isRuntimeWakeLockHeld();
        return builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("hedgeyos desktop runtime")
            .setContentText(status.state + (protectedRuntime
                ? ": background protection active"
                : ": desktop is not being kept awake"))
            .setSubText(status.detail)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
    }

    private void setupNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private void acquireRuntimeWakeLock() {
        if (runtimeWakeLock != null && runtimeWakeLock.isHeld()) {
            return;
        }
        PowerManager manager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (manager == null) {
            return;
        }
        runtimeWakeLock = manager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            getPackageName() + ":desktop-runtime");
        runtimeWakeLock.setReferenceCounted(false);
        runtimeWakeLock.acquire();
        sRuntimeWakeLockHeld = runtimeWakeLock.isHeld();
    }

    private void releaseRuntimeWakeLock() {
        if (runtimeWakeLock != null && runtimeWakeLock.isHeld()) {
            runtimeWakeLock.release();
        }
        runtimeWakeLock = null;
        sRuntimeWakeLockHeld = false;
    }

    private static void setDesiredRunning(Context context, boolean desiredRunning) {
        preferences(context).edit().putBoolean(KEY_DESIRED_RUNNING, desiredRunning).apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
