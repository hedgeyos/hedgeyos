package com.termux.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class LinuxRuntimeCapabilities {

    public final boolean reportAvailable;
    public final boolean passed;
    public final boolean writableTmp;
    public final boolean writableSharedMemory;
    public final boolean writableRun;
    public final boolean xdgRuntimeDir;
    public final boolean posixSharedMemory;
    public final boolean sysvIpc;
    public final boolean memfd;
    public final boolean sessionDbus;
    public final boolean sessionDbusProcess;
    public final boolean xfceSessionProcess;
    public final boolean systemDbus;
    public final boolean procSysctlVisibility;
    public final boolean x11Socket;
    public final boolean gtkSvgLoader;
    public final boolean gtkSvgLoaderCache;
    public final boolean gtkSvgDecode;
    public final boolean appSupervision;
    public final boolean managedLogBudget;
    public final boolean prootRecvmsg;
    public final String x11SessionMode;
    public final List<CompatibilityWarning> warnings;

    private LinuxRuntimeCapabilities(Builder builder) {
        reportAvailable = builder.reportAvailable;
        passed = builder.passed;
        writableTmp = builder.writableTmp;
        writableSharedMemory = builder.writableSharedMemory;
        writableRun = builder.writableRun;
        xdgRuntimeDir = builder.xdgRuntimeDir;
        posixSharedMemory = builder.posixSharedMemory;
        sysvIpc = builder.sysvIpc;
        memfd = builder.memfd;
        sessionDbus = builder.sessionDbus;
        sessionDbusProcess = builder.sessionDbusProcess;
        xfceSessionProcess = builder.xfceSessionProcess;
        systemDbus = builder.systemDbus;
        procSysctlVisibility = builder.procSysctlVisibility;
        x11Socket = builder.x11Socket;
        gtkSvgLoader = builder.gtkSvgLoader;
        gtkSvgLoaderCache = builder.gtkSvgLoaderCache;
        gtkSvgDecode = builder.gtkSvgDecode;
        appSupervision = builder.appSupervision;
        managedLogBudget = builder.managedLogBudget;
        prootRecvmsg = builder.prootRecvmsgChecks == 3 && builder.prootRecvmsg;
        x11SessionMode = builder.x11SessionMode;
        warnings = Collections.unmodifiableList(new ArrayList<>(builder.warnings));
    }

    public static LinuxRuntimeCapabilities parse(String report) {
        Builder builder = new Builder();
        if (report == null || report.trim().isEmpty()) {
            return new LinuxRuntimeCapabilities(builder);
        }
        builder.reportAvailable = true;

        for (String line : report.split("\\r?\\n")) {
            if ("summary=PASS fatal=0".equals(line)) {
                builder.passed = true;
                continue;
            }
            String[] fields = line.split("\\|", 3);
            if (fields.length != 3) {
                continue;
            }
            String severity = fields[0];
            String name = fields[1];
            boolean available = "PASS".equals(severity);
            builder.setCapability(name, available, fields[2]);
            if (!available) {
                builder.warnings.add(new CompatibilityWarning(severity, name, fields[2]));
            }
        }
        return new LinuxRuntimeCapabilities(builder);
    }

    public String toDisplayText() {
        if (!reportAvailable) {
            return "No Linux runtime report has been generated yet. Start or restart the desktop to run the preflight checks.";
        }
        StringBuilder text = new StringBuilder();
        text.append("Overall: ").append(passed ? "PASS" : "FAILED").append('\n');
        appendCapability(text, "Writable /tmp", writableTmp);
        appendCapability(text, "Writable /dev/shm", writableSharedMemory);
        appendCapability(text, "Writable /run", writableRun);
        appendCapability(text, "XDG runtime directory", xdgRuntimeDir);
        appendCapability(text, "POSIX shared memory", posixSharedMemory);
        appendCapability(text, "System V IPC", sysvIpc);
        appendCapability(text, "memfd", memfd);
        appendCapability(text, "Session D-Bus", sessionDbus);
        appendResult(text, "Owned session D-Bus process", sessionDbusProcess);
        appendResult(text, "Owned XFCE session process", xfceSessionProcess);
        appendCapability(text, "System D-Bus", systemDbus);
        appendCapability(text, "Android procfs sysctls", procSysctlVisibility);
        appendCapability(text, "X11 socket", x11Socket);
        appendResult(text, "GTK SVG loader", gtkSvgLoader);
        appendResult(text, "GTK SVG loader cache", gtkSvgLoaderCache);
        appendResult(text, "GTK SVG decode", gtkSvgDecode);
        appendResult(text, "GUI app supervision", appSupervision);
        appendResult(text, "Managed log budget", managedLogBudget);
        appendResult(text, "PRoot recvmsg lifecycle", prootRecvmsg);
        text.append("X11 diagnostic mode: ")
            .append("DIAGNOSTIC".equals(x11SessionMode)
                ? "ON"
                : ("NORMAL".equals(x11SessionMode) ? "OFF" : "UNKNOWN"))
            .append(" (")
            .append(x11SessionMode)
            .append(" session)\n");

        if (!warnings.isEmpty()) {
            text.append("\nWarnings\n");
            for (CompatibilityWarning warning : warnings) {
                text.append(warning.severity)
                    .append(" | ")
                    .append(warning.capability)
                    .append(" | ")
                    .append(warning.message)
                    .append('\n');
            }
        }
        return text.toString().trim();
    }

    private static void appendCapability(StringBuilder text, String label, boolean available) {
        text.append(label).append(": ").append(available ? "available" : "unavailable").append('\n');
    }

    private static void appendResult(StringBuilder text, String label, boolean passed) {
        text.append(label).append(": ").append(passed ? "PASS" : "FAILED").append('\n');
    }

    public static final class CompatibilityWarning {
        public final String severity;
        public final String capability;
        public final String message;

        CompatibilityWarning(String severity, String capability, String message) {
            this.severity = severity.toUpperCase(Locale.ROOT);
            this.capability = capability;
            this.message = message;
        }
    }

    private static final class Builder {
        boolean reportAvailable;
        boolean passed;
        boolean writableTmp;
        boolean writableSharedMemory;
        boolean writableRun;
        boolean xdgRuntimeDir;
        boolean posixSharedMemory;
        boolean sysvIpc;
        boolean memfd;
        boolean sessionDbus;
        boolean sessionDbusProcess;
        boolean xfceSessionProcess;
        boolean systemDbus;
        boolean procSysctlVisibility;
        boolean x11Socket;
        boolean gtkSvgLoader;
        boolean gtkSvgLoaderCache;
        boolean gtkSvgDecode;
        boolean appSupervision;
        boolean managedLogBudget;
        boolean prootRecvmsg = true;
        int prootRecvmsgChecks;
        String x11SessionMode = "UNKNOWN";
        final List<CompatibilityWarning> warnings = new ArrayList<>();

        void setCapability(String name, boolean available, String message) {
            if ("tmp".equals(name)) {
                writableTmp = available;
            } else if ("shm".equals(name)) {
                writableSharedMemory = available;
            } else if ("run".equals(name)) {
                writableRun = available;
            } else if ("xdg-runtime".equals(name)) {
                xdgRuntimeDir = available;
            } else if ("posix-shm".equals(name)) {
                posixSharedMemory = available;
            } else if ("sysv-ipc".equals(name)) {
                sysvIpc = available;
            } else if ("memfd".equals(name)) {
                memfd = available;
            } else if ("session-dbus".equals(name)) {
                sessionDbus = available;
            } else if ("session-dbus-process".equals(name)) {
                sessionDbusProcess = available;
            } else if ("xfce-session-process".equals(name)) {
                xfceSessionProcess = available;
            } else if ("system-dbus".equals(name)) {
                systemDbus = available;
            } else if ("inotify".equals(name)) {
                procSysctlVisibility = available;
            } else if ("x11".equals(name)) {
                x11Socket = available;
            } else if ("gtk-svg-loader".equals(name)) {
                gtkSvgLoader = available;
            } else if ("gtk-svg-cache".equals(name)) {
                gtkSvgLoaderCache = available;
            } else if ("gtk-svg-decode".equals(name)) {
                gtkSvgDecode = available;
            } else if ("app-supervision".equals(name)) {
                appSupervision = available;
            } else if ("managed-log-budget".equals(name)) {
                managedLogBudget = available;
            } else if (name.startsWith("proot-recvmsg-")) {
                prootRecvmsg &= available;
                prootRecvmsgChecks++;
            } else if ("x11-diagnostic".equals(name)) {
                if (message.startsWith("OFF")) {
                    x11SessionMode = "NORMAL";
                } else if (message.startsWith("ON")) {
                    x11SessionMode = "DIAGNOSTIC";
                }
            }
        }
    }
}
