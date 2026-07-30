package com.termux.app;

import android.content.Context;
import android.content.Intent;

import com.termux.BuildConfig;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

final class HedgeyosX11Bridge {

    static final String DISPLAY = ":1";

    private static final long START_GRACE_MS = 1800L;
    private static final Object LOCK = new Object();

    private static Process sX11Process;

    private HedgeyosX11Bridge() {}

    static boolean isAvailable(Context context) {
        try {
            Class.forName("com.termux.x11.CmdEntryPoint", false, context.getClassLoader());
            Class.forName("com.termux.x11.MainActivity", false, context.getClassLoader());
            Class.forName("com.termux.x11.HedgeyosHomeActivity", false, context.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    static void startServer(Context context, File tmpDir, File logFile, File pidFile,
                            File diagnosticPidFile,
                            File activityDiagnosticPidFile,
                            HedgeyosX11DiagnosticState.SessionMode requestedMode) throws Exception {
        if (!isAvailable(context)) {
            throw new IOException("Embedded Termux:X11 module is not packaged in this build.");
        }

        synchronized (LOCK) {
            if (sX11Process != null) {
                try {
                    int exitCode = sX11Process.exitValue();
                    appendLog(logFile, "Previous X11 server exited with code " + exitCode);
                    sX11Process = null;
                } catch (IllegalThreadStateException stillRunning) {
                    return;
                }
            }
        }

        mkdirs(tmpDir);
        mkdirs(logFile.getParentFile());
        stopDiagnosticLogcat(
            context,
            diagnosticPidFile,
            activityDiagnosticPidFile,
            pidFile,
            logFile);
        List<Integer> stale = HedgeyosProcessOwner.stopRecordedAndMatching(pidFile, "hedgeyos-x11");
        if (!stale.isEmpty()) {
            appendLog(context, logFile, "Stopped owned stale X11 processes: " + stale);
        }
        File appProcess = new File("/system/bin/app_process");
        if (!appProcess.exists()) {
            throw new IOException("Android app_process is missing; embedded X11 cannot start.");
        }

        List<String> command = new ArrayList<>();
        command.add(appProcess.getAbsolutePath());
        command.add("-Xnoimage-dex2oat");
        command.add("/");
        command.add("--nice-name=hedgeyos-x11");
        command.add("com.termux.x11.CmdEntryPoint");
        command.add(DISPLAY);

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        builder.environment().put("CLASSPATH", context.getPackageCodePath());
        builder.environment().put("TMPDIR", tmpDir.getAbsolutePath());
        boolean diagnostic = configureDebugEnvironment(
            builder.environment(),
            BuildConfig.HEDGEYOS_X11_DEBUG,
            requestedMode == HedgeyosX11DiagnosticState.SessionMode.DIAGNOSTIC);
        if (diagnostic) {
            builder.environment().put(
                "HEDGEYOS_X11_DIAGNOSTIC_PID_FILE",
                diagnosticPidFile.getAbsolutePath());
        } else {
            builder.environment().remove("HEDGEYOS_X11_DIAGNOSTIC_PID_FILE");
            HedgeyosProcessOwner.clear(diagnosticPidFile);
            HedgeyosProcessOwner.clear(activityDiagnosticPidFile);
        }
        File xkbConfigRoot = new File(context.getFilesDir(), "debian/usr/share/X11/xkb");
        if (xkbConfigRoot.isDirectory()) {
            builder.environment().put("XKB_CONFIG_ROOT", xkbConfigRoot.getAbsolutePath());
        }
        builder.environment().remove("LD_LIBRARY_PATH");
        builder.environment().remove("LD_PRELOAD");

        appendLog(context, logFile, "Starting embedded Termux:X11 server on display " + DISPLAY);
        appendLog(context, logFile, "Command: " + command);
        appendLog(context, logFile, "TMPDIR=" + tmpDir.getAbsolutePath());
        appendLog(context, logFile, "XKB_CONFIG_ROOT=" + builder.environment().get("XKB_CONFIG_ROOT"));
        appendLog(context, logFile, "X11 session mode=" + (diagnostic ? "DIAGNOSTIC" : "NORMAL"));
        Process process = builder.start();
        HedgeyosBoundedLog.pump(
            process,
            logFile,
            HedgeyosRuntimeManager.managedPublicLogFile(context, logFile.getName()));
        synchronized (LOCK) {
            sX11Process = process;
        }
        try {
            HedgeyosProcessOwner.recordMatching(pidFile, "hedgeyos-x11");
        } catch (IOException e) {
            process.destroyForcibly();
            synchronized (LOCK) {
                if (sX11Process == process) {
                    sX11Process = null;
                }
            }
            throw e;
        }

        Thread.sleep(START_GRACE_MS);
        try {
            int exitCode = process.exitValue();
            synchronized (LOCK) {
                if (sX11Process == process) {
                    sX11Process = null;
                }
            }
            HedgeyosProcessOwner.clear(pidFile);
            throw new IOException(
                "Embedded X11 server exited during startup with code " + exitCode +
                    ".\n" + HedgeyosBoundedLog.readTail(logFile, 2400));
        } catch (IllegalThreadStateException stillRunning) {
            appendLog(context, logFile, "Embedded Termux:X11 server is running.");
        }
    }

    static void stopServer(Context context, File pidFile, File diagnosticPidFile,
                           File activityDiagnosticPidFile, File logFile) {
        stopDiagnosticLogcat(
            context,
            diagnosticPidFile,
            activityDiagnosticPidFile,
            pidFile,
            logFile);
        Process processToStop = null;
        synchronized (LOCK) {
            if (sX11Process != null) {
                processToStop = sX11Process;
                sX11Process = null;
            }
        }
        if (processToStop != null) {
            processToStop.destroy();
            try {
                if (!processToStop.waitFor(2, TimeUnit.SECONDS)) {
                    processToStop.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                processToStop.destroyForcibly();
            }
        }
        List<Integer> stopped = HedgeyosProcessOwner.stopRecordedAndMatching(pidFile, "hedgeyos-x11");
        if (!stopped.isEmpty()) {
            appendLog(context, logFile, "Stopped owned X11 processes: " + stopped);
        }
        HedgeyosProcessOwner.clear(diagnosticPidFile);
        HedgeyosProcessOwner.clear(activityDiagnosticPidFile);
    }

    static boolean configureDebugEnvironment(Map<String, String> environment,
                                             boolean diagnosticBuild,
                                             boolean oneShotDiagnostic) {
        boolean diagnostic = diagnosticBuild || oneShotDiagnostic;
        if (diagnostic) {
            environment.put("TERMUX_X11_DEBUG", "1");
        } else {
            environment.remove("TERMUX_X11_DEBUG");
        }
        return diagnostic;
    }

    static void openSurface(Context context) {
        Intent intent = new Intent();
        intent.setClassName(context.getPackageName(), "com.termux.x11.HedgeyosHomeActivity");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private static void stopDiagnosticLogcat(Context context, File diagnosticPidFile,
                                             File activityDiagnosticPidFile,
                                             File x11PidFile, File logFile) {
        if (HedgeyosProcessOwner.stopRecordedOwnedChild(diagnosticPidFile, x11PidFile)) {
            appendLog(context, logFile, "Stopped recorded owned X11 diagnostic logcat child.");
        }
        int appPid = android.os.Process.myPid();
        if (HedgeyosProcessOwner.stopRecordedOwnedChild(
            activityDiagnosticPidFile,
            appPid,
            "logcat",
            "--pid=" + appPid)) {
            appendLog(
                context,
                logFile,
                "Stopped recorded owned X11 renderer diagnostic logcat child.");
        }
        HedgeyosProcessOwner.clear(diagnosticPidFile);
        HedgeyosProcessOwner.clear(activityDiagnosticPidFile);
    }

    private static void appendLog(File file, String text) {
        HedgeyosBoundedLog.append(file, text + "\n");
    }

    private static void appendLog(Context context, File file, String text) {
        appendLog(file, text);
        HedgeyosRuntimeManager.appendPublicLog(context, file.getName(), text + "\n");
    }

    private static void mkdirs(File dir) throws IOException {
        if (dir == null) {
            return;
        }
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Failed to create directory: " + dir.getAbsolutePath());
        }
    }
}
