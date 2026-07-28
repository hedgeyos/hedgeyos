package com.termux.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

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

    @Override
    public void onCreate() {
        super.onCreate();
        setupNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP_DESKTOP.equals(action)) {
            HedgeyosRuntimeManager.stopDesktop(this);
            stopForeground(true);
            stopSelf(startId);
            return START_NOT_STICKY;
        } else if (ACTION_RESET_DEBIAN.equals(action)) {
            HedgeyosRuntimeManager.resetDebianAsync(this);
        } else if (ACTION_RESTART_DESKTOP.equals(action)) {
            HedgeyosRuntimeManager.restartDesktopAsync(this);
        } else {
            HedgeyosRuntimeManager.startAsync(this);
        }

        startForeground(NOTIFICATION_ID, buildNotification());
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
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
        return builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("hedgeyos desktop runtime")
            .setContentText(status.state + ": " + status.detail)
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
}
