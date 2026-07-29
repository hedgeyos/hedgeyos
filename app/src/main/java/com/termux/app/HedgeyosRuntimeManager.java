package com.termux.app;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Environment;
import android.os.StatFs;
import android.system.Os;

import com.termux.shared.termux.TermuxConstants;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class HedgeyosRuntimeManager {

    static final String STATE_NOT_INSTALLED = "NOT_INSTALLED";
    static final String STATE_VERIFYING_ASSET = "VERIFYING_ASSET";
    static final String STATE_INSTALLING_PROOT = "INSTALLING_PROOT";
    static final String STATE_EXTRACTING = "EXTRACTING";
    static final String STATE_CONFIGURING = "CONFIGURING";
    static final String STATE_READY = "READY";
    static final String STATE_STARTING_X11 = "STARTING_X11";
    static final String STATE_STARTING_DESKTOP = "STARTING_DESKTOP";
    static final String STATE_RUNNING = "RUNNING";
    static final String STATE_STOPPING = "STOPPING";
    static final String STATE_FAILED = "FAILED";

    private static final String ROOTFS_NAME = "debian-trixie-arm64-rootfs.tar.zst";
    private static final String ROOTFS_SHA_NAME = ROOTFS_NAME + ".sha256";
    private static final String PROOT_PAYLOAD_NAME = "termux-proot-aarch64.tar.zst";
    private static final String PROOT_PAYLOAD_SHA_NAME = PROOT_PAYLOAD_NAME + ".sha256";
    private static final String HEDGEYOS_ICON_ASSET = "hedgeyos-icon.png";
    private static final String HEDGEYOS_WALLPAPER_ASSET = "hedgeyos-wallpaper.png";
    private static final String LINUX_BRAND_DIR = "usr/share/hedgeyos";
    private static final String LINUX_BACKGROUND_DIR = "usr/share/backgrounds/hedgeyos";
    private static final String VERSION_MARKER = ".hedgeyos-rootfs";
    private static final String PROOT_MARKER = ".hedgeyos-proot";
    private static final long MIN_FREE_BYTES_BEFORE_EXTRACTION = 1536L * 1024L * 1024L;
    private static final long DESKTOP_START_GRACE_MS = 2500L;
    private static final Object LOCK = new Object();

    private static boolean sWorkerRunning;
    private static Process sDesktopProcess;

    private HedgeyosRuntimeManager() {}

    static void startAsync(Context context) {
        Context appContext = context.getApplicationContext();
        synchronized (LOCK) {
            if (sWorkerRunning) {
                return;
            }
            sWorkerRunning = true;
        }

        Thread worker = new Thread(() -> {
            try {
                ensureInstalled(appContext);
                startDesktop(appContext);
            } catch (Exception e) {
                setState(appContext, STATE_FAILED, e.getMessage() == null ? e.toString() : e.getMessage());
                appendLog(appContext, "runtime", stackTrace(e));
            } finally {
                synchronized (LOCK) {
                    sWorkerRunning = false;
                }
            }
        }, "hedgeyos-runtime-start");
        worker.start();
    }

    static void restartDesktopAsync(Context context) {
        Context appContext = context.getApplicationContext();
        synchronized (LOCK) {
            if (sWorkerRunning) {
                return;
            }
            sWorkerRunning = true;
        }

        Thread worker = new Thread(() -> {
            try {
                stopDesktop(appContext);
                startDesktop(appContext);
            } catch (Exception e) {
                setState(appContext, STATE_FAILED, e.getMessage() == null ? e.toString() : e.getMessage());
                appendLog(appContext, "runtime", stackTrace(e));
            } finally {
                synchronized (LOCK) {
                    sWorkerRunning = false;
                }
            }
        }, "hedgeyos-runtime-restart");
        worker.start();
    }

    public static void openDebianTerminalAsync(Context context) {
        Context appContext = context.getApplicationContext();
        Thread worker = new Thread(() -> {
            try {
                openDebianTerminal(appContext);
            } catch (Exception e) {
                appendLog(appContext, "desktop", "Failed to open Debian terminal: " + (e.getMessage() == null ? e.toString() : e.getMessage()));
                appendLog(appContext, "runtime", stackTrace(e));
            }
        }, "hedgeyos-open-debian-terminal");
        worker.start();
    }

    public static void runDebianAcceptanceChecksAsync(Context context) {
        Context appContext = context.getApplicationContext();
        Thread worker = new Thread(() -> {
            try {
                runDebianAcceptanceChecks(appContext);
            } catch (Exception e) {
                appendLog(appContext, "debian-acceptance", "FAILED: " + (e.getMessage() == null ? e.toString() : e.getMessage()));
                appendLog(appContext, "runtime", stackTrace(e));
            }
        }, "hedgeyos-debian-acceptance");
        worker.start();
    }

    static void stopDesktop(Context context) {
        Context appContext = context.getApplicationContext();
        setState(appContext, STATE_STOPPING, "Stopping hedgeyos desktop supervisor.");
        Process processToStop = null;
        synchronized (LOCK) {
            if (sDesktopProcess != null) {
                processToStop = sDesktopProcess;
                sDesktopProcess = null;
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
        File desktopLog = new File(logDir(appContext), "desktop.log");
        stopOwnedDesktopProcesses(appContext, desktopLog);
        HedgeyosX11Bridge.stopServer(appContext, x11PidFile(appContext), desktopLog);
        setState(appContext, isRootfsInstalled(appContext) ? STATE_READY : STATE_NOT_INSTALLED, "hedgeyos desktop is stopped.");
    }

    static void resetDebianAsync(Context context) {
        Context appContext = context.getApplicationContext();
        synchronized (LOCK) {
            if (sWorkerRunning) {
                return;
            }
            sWorkerRunning = true;
        }

        Thread worker = new Thread(() -> {
            try {
                stopDesktop(appContext);
                setState(appContext, STATE_CONFIGURING, "Resetting Debian rootfs.");
                deleteRecursively(stagingDir(appContext));
                deleteRecursively(rootfsDir(appContext));
                setState(appContext, STATE_NOT_INSTALLED, "Debian reset complete. The export directory was preserved.");
            } catch (Exception e) {
                setState(appContext, STATE_FAILED, e.getMessage() == null ? e.toString() : e.getMessage());
                appendLog(appContext, "runtime", stackTrace(e));
            } finally {
                synchronized (LOCK) {
                    sWorkerRunning = false;
                }
            }
        }, "hedgeyos-runtime-reset");
        worker.start();
    }

    public static RuntimeStatus getStatus(Context context) {
        String state = readFile(stateFile(context)).trim();
        String detail = readFile(detailFile(context)).trim();
        if (state.isEmpty()) {
            state = isRootfsInstalled(context) ? STATE_READY : STATE_NOT_INSTALLED;
        }
        if (detail.isEmpty()) {
            detail = defaultDetailForState(state);
        }
        return new RuntimeStatus(state, detail, sWorkerRunning);
    }

    public static String readRecentLogs(Context context) {
        StringBuilder result = new StringBuilder();
        appendFileTail(result, new File(logDir(context), "runtime.log"), 12000);
        appendFileTail(result, new File(logDir(context), "firstboot.log"), 20000);
        appendFileTail(result, new File(logDir(context), "desktop.log"), 12000);
        if (result.length() == 0) {
            return "No hedgeyos logs have been written yet.";
        }
        return result.toString();
    }

    public static String readLinuxRuntimeReport(Context context) {
        String report = readFile(new File(publicLogDir(context), "linux-runtime-report.txt")).trim();
        LinuxRuntimeCapabilities capabilities = LinuxRuntimeCapabilities.parse(report);
        if (!capabilities.reportAvailable) {
            return capabilities.toDisplayText();
        }
        return capabilities.toDisplayText() + "\n\nDetailed checks\n" + report;
    }

    public static LinuxRuntimeCapabilities getLinuxRuntimeCapabilities(Context context) {
        return LinuxRuntimeCapabilities.parse(
            readFile(new File(publicLogDir(context), "linux-runtime-report.txt")));
    }

    private static void ensureInstalled(Context context) throws Exception {
        ensureDirectories(context);
        TermuxInstaller.setupBootstrapForHedgeyosRuntime(context);
        installProotPayload(context);

        if (isRootfsInstalled(context)) {
            ensureLinuxBranding(context, rootfsDir(context));
            ensureLinuxWindowPolicy(context);
            setState(context, STATE_READY, "Debian rootfs is installed.");
            return;
        }

        setState(context, STATE_VERIFYING_ASSET, "Preparing bundled Debian rootfs asset.");
        File asset = copyRequiredAsset(context, ROOTFS_NAME);
        File checksumFile = copyRequiredAsset(context, ROOTFS_SHA_NAME);
        String expectedSha = readExpectedSha(checksumFile);
        String actualSha = sha256(asset);
        if (!expectedSha.equals(actualSha)) {
            throw new IOException("Bundled rootfs checksum mismatch: expected " + expectedSha + ", actual " + actualSha);
        }

        long availableBytes = new StatFs(filesDir(context).getAbsolutePath()).getAvailableBytes();
        long requiredBytes = Math.max(MIN_FREE_BYTES_BEFORE_EXTRACTION, asset.length() * 3L);
        if (availableBytes < requiredBytes) {
            throw new IOException("Not enough free storage for Debian extraction. Available=" +
                formatBytes(availableBytes) + ", required=" + formatBytes(requiredBytes));
        }

        setState(context, STATE_EXTRACTING, "Extracting Debian rootfs.");
        File staging = stagingDir(context);
        deleteRecursively(staging);
        mkdirs(staging);
        runShell(context,
            quote(new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "zstd").getAbsolutePath()) +
                " -dc " + quote(asset.getAbsolutePath()) + " | " +
                quote(new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "tar").getAbsolutePath()) +
                " --no-same-owner --no-same-permissions --delay-directory-restore" +
                " -C " + quote(staging.getAbsolutePath()) + " -xf -",
            new File(logDir(context), "firstboot.log"));

        setState(context, STATE_CONFIGURING, "Configuring Debian rootfs.");
        configureRootfs(context, staging, expectedSha);

        File rootfs = rootfsDir(context);
        if (rootfs.exists()) {
            throw new IOException("Refusing to replace existing rootfs: " + rootfs.getAbsolutePath());
        }
        if (!staging.renameTo(rootfs)) {
            throw new IOException("Failed to move staging rootfs into place.");
        }

        setState(context, STATE_READY, "Debian rootfs is installed.");
    }

    private static void startDesktop(Context context) throws Exception {
        if (!isRootfsInstalled(context)) {
            setState(context, STATE_NOT_INSTALLED, "Debian rootfs is not installed.");
            return;
        }
        if (!isProotInstalled(context)) {
            throw new IOException("Bundled PRoot payload is not installed.");
        }

        synchronized (LOCK) {
            if (sDesktopProcess != null) {
                try {
                    int exitCode = sDesktopProcess.exitValue();
                    appendLog(context, "desktop", "Previous desktop supervisor exited with code " + exitCode);
                    sDesktopProcess = null;
                } catch (IllegalThreadStateException stillRunning) {
                    setState(context, STATE_RUNNING, "hedgeyos desktop supervisor is already running.");
                    return;
                }
            }
        }

        File desktopLog = new File(logDir(context), "desktop.log");
        stopOwnedDesktopProcesses(context, desktopLog);
        HedgeyosX11Bridge.stopServer(context, x11PidFile(context), desktopLog);
        HedgeyosGuestRuntime.Layout runtime = guestRuntime(context);
        HedgeyosGuestRuntime.prepare(runtime, rootfsDir(context), true);
        setState(context, STATE_STARTING_X11, "Starting embedded Termux:X11 server.");
        HedgeyosX11Bridge.startServer(context, runtime.tmp, desktopLog, x11PidFile(context));

        try {
            runLinuxRuntimePreflight(context, desktopLog);
            setState(context, STATE_STARTING_DESKTOP, "Starting Debian XFCE through bundled PRoot.");
            appendLog(desktopLog, "Starting hedgeyos desktop supervisor.");

            List<String> command = buildDebianCommand(context, desktopStartupCommand(),
                "0:0", "/home/hedgeyos", "root", true);
            ProcessBuilder builder = createDebianProcessBuilder(context, command, desktopLog);
            Process process = builder.start();
            synchronized (LOCK) {
                sDesktopProcess = process;
            }
            HedgeyosProcessOwner.recordMatching(
                desktopPidFile(context),
                prootBinary(context).getAbsolutePath(),
                "--rootfs=" + rootfsDir(context).getAbsolutePath(),
                "--kill-on-exit");

            Thread.sleep(DESKTOP_START_GRACE_MS);
            try {
                int exitCode = process.exitValue();
                synchronized (LOCK) {
                    if (sDesktopProcess == process) {
                        sDesktopProcess = null;
                    }
                }
                HedgeyosProcessOwner.clear(desktopPidFile(context));
                throw new IOException("Desktop supervisor exited during startup with code " + exitCode + ". Open hedgeyos logs for details.");
            } catch (IllegalThreadStateException stillRunning) {
                setState(context, STATE_RUNNING, "hedgeyos desktop supervisor is running.");
            }
        } catch (Exception e) {
            synchronized (LOCK) {
                if (sDesktopProcess != null) {
                    sDesktopProcess.destroyForcibly();
                    sDesktopProcess = null;
                }
            }
            stopOwnedDesktopProcesses(context, desktopLog);
            HedgeyosX11Bridge.stopServer(context, x11PidFile(context), desktopLog);
            throw e;
        }
    }

    private static String desktopStartupCommand() {
        return "exec /usr/local/libexec/hedgeyos-start-desktop";
    }

    private static void openDebianTerminal(Context context) throws Exception {
        if (!STATE_RUNNING.equals(getStatus(context).state)) {
            startDesktop(context);
        }
        if (!isRootfsInstalled(context)) {
            throw new IOException("Debian rootfs is not installed.");
        }
        if (!isProotInstalled(context)) {
            throw new IOException("Bundled PRoot payload is not installed.");
        }

        File desktopLog = new File(logDir(context), "desktop.log");
        HedgeyosGuestRuntime.prepare(guestRuntime(context), rootfsDir(context), false);
        mkdirs(exportDir(context));
        mkdirs(new File(rootfsDir(context), "home/hedgeyos/Downloads"));

        List<String> command = buildDebianCommand(context,
            "mkdir -p /home/hedgeyos/Downloads && " +
                "exec xfce4-terminal --title 'hedgeyos Debian Terminal' --command " +
                "\"/bin/bash -lc 'cat /etc/os-release; exec /bin/bash -l'\"");

        ProcessBuilder builder = createDebianProcessBuilder(context, command, desktopLog);

        appendLog(desktopLog, "Opening Debian XFCE terminal.");
        builder.start();
    }

    private static void runDebianAcceptanceChecks(Context context) throws Exception {
        if (!STATE_RUNNING.equals(getStatus(context).state)) {
            startDesktop(context);
        }
        if (!isRootfsInstalled(context)) {
            throw new IOException("Debian rootfs is not installed.");
        }
        if (!isProotInstalled(context)) {
            throw new IOException("Bundled PRoot payload is not installed.");
        }

        File acceptanceLog = new File(publicLogDir(context), "debian-acceptance.log");
        mkdirs(acceptanceLog.getParentFile());
        writeFile(acceptanceLog, "hedgeyos Debian acceptance checks\n");

        List<String> command = buildDebianRootCommand(context,
            "set -e; " +
                "echo '== privilege model =='; " +
                "id; " +
                "test \"$(id -u)\" = 0; " +
                "test \"$(stat -c %u /etc/sudo.conf)\" = 0; " +
                "sudo -n true; " +
                "echo '== os-release =='; cat /etc/os-release; " +
                "echo '== apt update =='; apt update; " +
                "echo '== apt install hello =='; env DEBIAN_FRONTEND=noninteractive apt install -y hello; " +
                "echo '== hello =='; hello; " +
                "echo '== dpkg hello =='; dpkg-query -W -f='${Package} ${Version} ${Status}\\n' hello");

        ProcessBuilder builder = createDebianProcessBuilder(context, command, acceptanceLog);

        appendLog(context, "debian-acceptance", "Starting Debian acceptance command.");
        Process process = builder.start();
        if (!process.waitFor(10, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new IOException("Debian acceptance command timed out.");
        }
        int exitCode = process.exitValue();
        appendLog(context, "debian-acceptance", "Debian acceptance command exited with code " + exitCode + ".");
        if (exitCode != 0) {
            throw new IOException("Debian acceptance command failed with exit code " + exitCode + ". See debian-acceptance.log.");
        }
    }

    private static List<String> buildDebianCommand(Context context, String shellCommand) {
        return buildDebianCommand(context, shellCommand, "0:0", "/home/hedgeyos", "root", false);
    }

    private static List<String> buildDebianRootCommand(Context context, String shellCommand) {
        return buildDebianCommand(context, shellCommand, "0:0", "/root", "root", false);
    }

    private static List<String> buildDebianCommand(Context context, String shellCommand, String changeId,
                                                   String home, String user, boolean killOnExit) {
        return HedgeyosGuestRuntime.buildCommand(
            prootBinary(context),
            rootfsDir(context),
            guestRuntime(context),
            exportDir(context),
            publicLogDir(context),
            shellCommand,
            changeId,
            home,
            user,
            HedgeyosX11Bridge.DISPLAY,
            killOnExit);
    }

    private static ProcessBuilder createDebianProcessBuilder(Context context, List<String> command,
                                                             File outputLog) throws IOException {
        HedgeyosGuestRuntime.Layout runtime = guestRuntime(context);
        HedgeyosGuestRuntime.prepare(runtime, rootfsDir(context), false);
        mkdirs(tmpDir(context));
        mkdirs(exportDir(context));
        mkdirs(publicLogDir(context));
        mkdirs(new File(rootfsDir(context), "home/hedgeyos/Downloads"));
        mkdirs(new File(rootfsDir(context), "home/hedgeyos/Logs"));
        mkdirs(new File(TermuxConstants.TERMUX_HOME_DIR_PATH));

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(filesDir(context));
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(outputLog));
        builder.environment().put("HOME", TermuxConstants.TERMUX_HOME_DIR_PATH);
        builder.environment().put("PATH", TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + ":/system/bin");
        builder.environment().put("LD_LIBRARY_PATH", TermuxConstants.TERMUX_LIB_PREFIX_DIR_PATH);
        builder.environment().put("PREFIX", TermuxConstants.TERMUX_PREFIX_DIR_PATH);
        builder.environment().put("TMPDIR", runtime.tmp.getAbsolutePath());
        builder.environment().put("PROOT_LOADER", prootLoader(context).getAbsolutePath());
        builder.environment().put("PROOT_TMP_DIR", tmpDir(context).getAbsolutePath());
        return builder;
    }

    private static void runLinuxRuntimePreflight(Context context, File desktopLog) throws Exception {
        String command =
            "set +e\n" +
            "report=\"$HEDGEYOS_RUNTIME_REPORT\"\n" +
            "temporary=\"${report}.tmp.$$\"\n" +
            "trap 'rm -f \"$temporary\"' EXIT INT TERM\n" +
            "/usr/local/libexec/hedgeyos-runtime-preflight >\"$temporary\" 2>&1\n" +
            "code=$?\n" +
            "mv -f \"$temporary\" \"$report\"\n" +
            "trap - EXIT INT TERM\n" +
            "exit \"$code\"";
        ProcessBuilder builder = createDebianProcessBuilder(
            context, buildDebianRootCommand(context, command), desktopLog);
        appendLog(desktopLog, "Running generic Linux runtime preflight.");
        Process process = builder.start();
        if (!process.waitFor(45, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("Linux runtime preflight timed out.");
        }
        int exitCode = process.exitValue();
        String report = readLinuxRuntimeReport(context);
        appendLog(context, "runtime", "Linux runtime preflight exited with code " + exitCode + ".\n" + report);
        if (exitCode != 0) {
            throw new IOException("Linux runtime preflight failed with exit code " + exitCode +
                ". Open Linux Runtime Report for details.");
        }
    }

    private static void stopOwnedDesktopProcesses(Context context, File desktopLog) {
        List<Integer> stopped = HedgeyosProcessOwner.stopRecordedAndMatching(
            desktopPidFile(context),
            prootBinary(context).getAbsolutePath(),
            "--rootfs=" + rootfsDir(context).getAbsolutePath(),
            "--kill-on-exit");
        if (!stopped.isEmpty()) {
            appendLog(desktopLog, "Stopped owned hedgeyos desktop PRoot processes: " + stopped + "\n");
            appendPublicLog(context, desktopLog.getName(),
                "Stopped owned hedgeyos desktop PRoot processes: " + stopped + "\n");
        }
    }

    private static void installProotPayload(Context context) throws Exception {
        File asset = copyRequiredAsset(context, PROOT_PAYLOAD_NAME);
        File checksumFile = copyRequiredAsset(context, PROOT_PAYLOAD_SHA_NAME);
        String expectedSha = readExpectedSha(checksumFile);
        if (isProotInstalled(context, expectedSha)) {
            return;
        }

        setState(context, STATE_INSTALLING_PROOT, "Installing bundled PRoot runtime.");
        String actualSha = sha256(asset);
        if (!expectedSha.equals(actualSha)) {
            throw new IOException("Bundled PRoot checksum mismatch: expected " + expectedSha + ", actual " + actualSha);
        }

        runShell(context,
            quote(new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "zstd").getAbsolutePath()) +
                " -dc " + quote(asset.getAbsolutePath()) + " | " +
                quote(new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "tar").getAbsolutePath()) +
                " --no-same-owner --no-same-permissions --delay-directory-restore" +
                " -C " + quote(filesDir(context).getAbsolutePath()) + " -xf -",
            new File(logDir(context), "firstboot.log"));

        Os.chmod(prootBinary(context).getAbsolutePath(), 0700);
        Os.chmod(prootLoader(context).getAbsolutePath(), 0700);
        File loader32 = new File(TermuxConstants.TERMUX_LIBEXEC_PREFIX_DIR_PATH, "proot/loader32");
        if (loader32.exists()) {
            Os.chmod(loader32.getAbsolutePath(), 0700);
        }

        writeFile(prootMarkerFile(context),
            "version=5.1.107.86\npayload_sha256=" + expectedSha + "\npackage=org.hedgeyos\n");
        appendLog(context, "firstboot", "Installed bundled PRoot runtime at " + TermuxConstants.TERMUX_PREFIX_DIR_PATH);
    }

    private static void configureRootfs(Context context, File rootfs, String rootfsSha) throws Exception {
        File tmp = new File(rootfs, "tmp");
        File home = new File(rootfs, "home/hedgeyos");
        File sudoersDir = new File(rootfs, "etc/sudoers.d");
        mkdirs(tmp);
        mkdirs(home);
        mkdirs(new File(home, "Downloads"));
        mkdirs(sudoersDir);
        Os.chmod(tmp.getAbsolutePath(), 01777);

        ensureLine(new File(rootfs, "etc/group"), "hedgeyos:", "hedgeyos:x:1000:\n");
        ensureLine(new File(rootfs, "etc/passwd"), "hedgeyos:", "hedgeyos:x:1000:1000:hedgeyos User:/home/hedgeyos:/bin/bash\n");
        writeFile(new File(sudoersDir, "hedgeyos"), "hedgeyos ALL=(ALL) NOPASSWD:ALL\n");
        Os.chmod(new File(sudoersDir, "hedgeyos").getAbsolutePath(), 0440);
        writeFile(new File(rootfs, "etc/resolv.conf"), "nameserver 1.1.1.1\nnameserver 8.8.8.8\n");

        File osRelease = new File(rootfs, "etc/os-release");
        String osReleaseText = readFile(osRelease);
        if (!osReleaseText.contains("trixie") && !osReleaseText.contains("13")) {
            throw new IOException("Rootfs health check failed: /etc/os-release does not identify Debian 13/Trixie.");
        }
        if (!new File(rootfs, "bin/bash").exists()) {
            throw new IOException("Rootfs health check failed: /bin/bash is missing.");
        }
        if (!new File(rootfs, "usr/bin/devilspie2").exists()) {
            throw new IOException("Rootfs health check failed: /usr/bin/devilspie2 is missing.");
        }

        ensureLinuxBranding(context, rootfs);
        writeFile(new File(rootfs, VERSION_MARKER),
            "version=0.1.0-dev\nrootfs_sha256=" + rootfsSha + "\npackage=org.hedgeyos\n");
        appendLog(context, "firstboot", "Configured Debian rootfs at " + rootfs.getAbsolutePath());
    }

    private static void ensureLinuxBranding(Context context, File rootfs) throws IOException {
        File shareDir = new File(rootfs, LINUX_BRAND_DIR);
        File backgroundDir = new File(rootfs, LINUX_BACKGROUND_DIR);
        mkdirs(shareDir);
        mkdirs(backgroundDir);

        File linuxIcon = new File(shareDir, HEDGEYOS_ICON_ASSET);
        File linuxWallpaper = new File(backgroundDir, HEDGEYOS_WALLPAPER_ASSET);
        copyBundledAsset(context, HEDGEYOS_ICON_ASSET, linuxIcon);
        copyBundledAsset(context, HEDGEYOS_WALLPAPER_ASSET, linuxWallpaper);
        installLinuxRuntimeAsset(context, rootfs, "hedgeyos-apply-defaults",
            "usr/local/libexec/hedgeyos-apply-defaults", 0755);
        installLinuxRuntimeAsset(context, rootfs, "hedgeyos-runtime-preflight",
            "usr/local/libexec/hedgeyos-runtime-preflight", 0755);
        installLinuxRuntimeAsset(context, rootfs, "hedgeyos-start-desktop",
            "usr/local/libexec/hedgeyos-start-desktop", 0755);
        installLinuxRuntimeAsset(context, rootfs, "hedgeyos-window-rules.desktop",
            "etc/xdg/autostart/hedgeyos-window-rules.desktop", 0644);
        installLinuxRuntimeAsset(context, rootfs, "hedgeyos-window-rules.lua",
            "etc/hedgeyos/devilspie2/hedgeyos-window-rules.lua", 0644);
        installLinuxRuntimeAsset(context, rootfs, "hedgeyos-menu.png",
            "usr/share/hedgeyos/hedgeyos-menu.png", 0644);
        installLinuxRuntimeAsset(context, rootfs, "Terminal.desktop",
            "usr/share/hedgeyos/defaults/Terminal.desktop", 0644);
        installLinuxRuntimeAsset(context, rootfs, "terminalrc",
            "usr/share/hedgeyos/defaults/terminalrc", 0644);
    }

    private static void installLinuxRuntimeAsset(Context context, File rootfs, String assetName,
                                                 String rootfsPath, int mode) throws IOException {
        File output = new File(rootfs, rootfsPath);
        copyBundledAsset(context, "hedgeyos-linux/" + assetName, output);
        try {
            Os.chmod(output.getAbsolutePath(), mode);
        } catch (Exception e) {
            throw new IOException("Failed to set mode on rootfs asset /" + rootfsPath, e);
        }
    }

    private static void ensureLinuxWindowPolicy(Context context) throws Exception {
        File rootfs = rootfsDir(context);
        if (new File(rootfs, "usr/bin/devilspie2").exists()) {
            return;
        }

        String assetDirectory = "hedgeyos-linux/packages";
        String[] packageNames = context.getAssets().list(assetDirectory);
        if (packageNames == null) {
            packageNames = new String[0];
        }
        Arrays.sort(packageNames);

        File packageDirectory = new File(rootfs, "var/cache/hedgeyos-migration-packages");
        deleteRecursively(packageDirectory);
        mkdirs(packageDirectory);

        int copiedPackages = 0;
        for (String packageName : packageNames) {
            if (!packageName.endsWith(".deb")) {
                continue;
            }
            copyBundledAsset(context, assetDirectory + "/" + packageName,
                new File(packageDirectory, packageName));
            copiedPackages++;
        }
        if (copiedPackages != 2) {
            throw new IOException("Offline Linux window-policy migration is incomplete: expected 2 packages, found " +
                copiedPackages + ".");
        }

        File migrationLog = new File(logDir(context), "linux-migration.log");
        List<String> command = buildDebianRootCommand(context,
            "set -e; export DEBIAN_FRONTEND=noninteractive; " +
                "dpkg -i /var/cache/hedgeyos-migration-packages/*.deb; " +
                "rm -rf /var/cache/hedgeyos-migration-packages");
        ProcessBuilder builder = createDebianProcessBuilder(context, command, migrationLog);

        appendLog(migrationLog, "Installing offline Linux window-policy migration.");
        Process process = builder.start();
        if (!process.waitFor(2, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new IOException("Offline Linux window-policy migration timed out.");
        }
        if (process.exitValue() != 0 || !new File(rootfs, "usr/bin/devilspie2").exists()) {
            throw new IOException("Offline Linux window-policy migration failed with exit code " +
                process.exitValue() + ". See linux-migration.log.");
        }
        appendLog(migrationLog, "Installed offline Linux window-policy migration.");
    }

    private static void ensureDirectories(Context context) throws IOException {
        mkdirs(assetDir(context));
        mkdirs(logDir(context));
        mkdirs(stateDir(context));
        mkdirs(exportDir(context));
        mkdirs(tmpDir(context));
        HedgeyosGuestRuntime.prepare(
            guestRuntime(context),
            rootfsDir(context).isDirectory() ? rootfsDir(context) : null,
            false);
    }

    private static File copyRequiredAsset(Context context, String name) throws IOException {
        File output = new File(assetDir(context), name);
        AssetManager assets = context.getAssets();
        try (InputStream input = assets.open(name); FileOutputStream outputStream = new FileOutputStream(output)) {
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
        } catch (IOException e) {
            throw new IOException("Missing required bundled asset " + name +
                ". Build hedgeyos with ./scripts/build-hedgeyos.sh so runtime assets are packaged.", e);
        }
        return output;
    }

    private static void copyBundledAsset(Context context, String name, File output) throws IOException {
        mkdirs(output.getParentFile());
        AssetManager assets = context.getAssets();
        try (InputStream input = assets.open(name); FileOutputStream outputStream = new FileOutputStream(output)) {
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
        } catch (IOException e) {
            throw new IOException("Missing bundled hedgeyos asset " + name, e);
        }
    }

    private static void runShell(Context context, String command, File logFile) throws Exception {
        mkdirs(logFile.getParentFile());
        String shell = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "bash").getAbsolutePath();
        ProcessBuilder builder = new ProcessBuilder(shell, "-c", "set -e\n" + command);
        builder.directory(filesDir(context));
        builder.redirectErrorStream(true);
        builder.environment().put("HOME", TermuxConstants.TERMUX_HOME_DIR_PATH);
        builder.environment().put("PATH", TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + ":/system/bin");
        builder.environment().put("LD_LIBRARY_PATH", TermuxConstants.TERMUX_LIB_PREFIX_DIR_PATH);
        Process process = builder.start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream input = process.getInputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
        int exitCode = process.waitFor();
        String text = output.toString("UTF-8");
        appendLog(logFile, "$ " + command + "\n" + text + "\nexit=" + exitCode + "\n");
        appendPublicLog(context, logFile.getName(), "$ " + command + "\n" + text + "\nexit=" + exitCode + "\n");
        if (exitCode != 0) {
            throw new IOException("Command failed with exit code " + exitCode + ": " + command + "\n" + tailText(text, 2400));
        }
    }

    private static String readExpectedSha(File checksumFile) throws IOException {
        String line = readFile(checksumFile).trim();
        if (line.length() < 64) {
            throw new IOException("Invalid rootfs checksum file: " + checksumFile.getAbsolutePath());
        }
        return line.substring(0, 64).toLowerCase(Locale.ROOT);
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder result = new StringBuilder();
        for (byte b : digest.digest()) {
            result.append(String.format(Locale.ROOT, "%02x", b));
        }
        return result.toString();
    }

    private static void setState(Context context, String state, String detail) {
        try {
            mkdirs(stateDir(context));
            writeAtomicFile(stateFile(context), state + "\n");
            writeAtomicFile(detailFile(context), detail + "\n");
            appendLog(context, "runtime", state + ": " + detail);
            writePublicFile(context, "runtime.state", state + "\n");
            writePublicFile(context, "runtime.detail", detail + "\n");
        } catch (Exception ignored) {
        }
    }

    private static boolean isRootfsInstalled(Context context) {
        return new File(rootfsDir(context), VERSION_MARKER).exists();
    }

    private static boolean isProotInstalled(Context context) {
        return prootMarkerFile(context).exists() && prootBinary(context).exists() && prootLoader(context).exists();
    }

    private static boolean isProotInstalled(Context context, String expectedSha) {
        return isProotInstalled(context) && readFile(prootMarkerFile(context)).contains("payload_sha256=" + expectedSha);
    }

    private static String defaultDetailForState(String state) {
        if (STATE_NOT_INSTALLED.equals(state)) {
            return "Debian rootfs has not been installed yet.";
        }
        if (STATE_READY.equals(state)) {
            return "Debian rootfs is ready.";
        }
        if (STATE_FAILED.equals(state)) {
            return "hedgeyos runtime failed. Open logs for details.";
        }
        return "hedgeyos runtime state: " + state;
    }

    private static void ensureLine(File file, String prefix, String line) throws IOException {
        String existing = readFile(file);
        if (!existing.contains(prefix)) {
            appendLog(file, line);
        }
    }

    private static void writeFile(File file, String content) throws IOException {
        mkdirs(file.getParentFile());
        if (file.exists() && !file.canWrite()) {
            try {
                Os.chmod(file.getAbsolutePath(), 0600);
            } catch (Exception e) {
                throw new IOException("Failed to make file writable: " + file.getAbsolutePath(), e);
            }
        }
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(content);
        }
    }

    private static void writeAtomicFile(File file, String content) throws IOException {
        HedgeyosAtomicFile.write(file, content);
    }

    private static String readFile(File file) {
        if (file == null || !file.exists()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line).append('\n');
            }
        } catch (IOException ignored) {
        }
        return result.toString();
    }

    private static void appendLog(Context context, String basename, String text) {
        appendLog(new File(logDir(context), basename + ".log"), text + "\n");
        appendPublicLog(context, basename + ".log", text + "\n");
    }

    private static void appendLog(File file, String text) {
        try {
            mkdirs(file.getParentFile());
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8)) {
                writer.write(text);
            }
        } catch (IOException ignored) {
        }
    }

    private static void appendFileTail(StringBuilder builder, File file, int maxChars) {
        String content = readFile(file);
        if (content.isEmpty()) {
            return;
        }
        if (content.length() > maxChars) {
            content = content.substring(content.length() - maxChars);
        }
        builder.append("== ").append(file.getName()).append(" ==\n").append(content).append('\n');
    }

    static void appendPublicLog(Context context, String name, String text) {
        try {
            File file = new File(publicLogDir(context), name);
            mkdirs(file.getParentFile());
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8)) {
                writer.write(text);
            }
        } catch (IOException ignored) {
        }
    }

    private static void writePublicFile(Context context, String name, String content) {
        try {
            File file = new File(publicLogDir(context), name);
            writeAtomicFile(file, content);
        } catch (IOException ignored) {
        }
    }

    private static void mkdirs(File dir) throws IOException {
        if (dir == null) {
            return;
        }
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Failed to create directory: " + dir.getAbsolutePath());
        }
    }

    private static void deleteRecursively(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }
        Path path = file.toPath();
        if (Files.isDirectory(path, java.nio.file.LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        if (!file.delete()) {
            throw new IOException("Failed to delete " + file.getAbsolutePath());
        }
    }

    private static void deleteFile(File file) {
        if (file != null && file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static String formatBytes(long bytes) {
        long mib = bytes / (1024L * 1024L);
        return mib + " MiB";
    }

    private static String tailText(String text, int maxChars) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(text.length() - maxChars);
    }

    private static String stackTrace(Exception e) {
        StringBuilder result = new StringBuilder(e.toString()).append('\n');
        for (StackTraceElement element : e.getStackTrace()) {
            result.append("  at ").append(element).append('\n');
        }
        return result.toString();
    }

    private static File filesDir(Context context) {
        return context.getFilesDir();
    }

    private static File rootfsDir(Context context) {
        return new File(filesDir(context), "debian");
    }

    private static File stagingDir(Context context) {
        return new File(filesDir(context), "debian.staging");
    }

    private static File assetDir(Context context) {
        return new File(filesDir(context), "assets");
    }

    private static File logDir(Context context) {
        return new File(filesDir(context), "logs");
    }

    private static File publicLogDir(Context context) {
        File external = context.getExternalFilesDir(null);
        if (external != null) {
            return new File(external, "logs");
        }
        return new File(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "hedgeyos"), "logs");
    }

    private static File tmpDir(Context context) {
        return new File(filesDir(context), "tmp");
    }

    private static HedgeyosGuestRuntime.Layout guestRuntime(Context context) {
        return HedgeyosGuestRuntime.layout(filesDir(context));
    }

    private static File exportDir(Context context) {
        return new File(filesDir(context), "export");
    }

    private static File stateDir(Context context) {
        return new File(filesDir(context), "hedgeyos-state");
    }

    private static File stateFile(Context context) {
        return new File(stateDir(context), "runtime.state");
    }

    private static File detailFile(Context context) {
        return new File(stateDir(context), "runtime.detail");
    }

    private static File desktopPidFile(Context context) {
        return new File(guestRuntime(context).processes, "desktop.pid");
    }

    private static File x11PidFile(Context context) {
        return new File(guestRuntime(context).processes, "x11.pid");
    }

    private static File prootBinary(Context context) {
        return new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "proot");
    }

    private static File prootLoader(Context context) {
        return new File(TermuxConstants.TERMUX_LIBEXEC_PREFIX_DIR_PATH, "proot/loader");
    }

    private static File prootMarkerFile(Context context) {
        return new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH, PROOT_MARKER);
    }

    public static final class RuntimeStatus {
        public final String state;
        public final String detail;
        public final boolean workerRunning;

        RuntimeStatus(String state, String detail, boolean workerRunning) {
            this.state = state;
            this.detail = detail;
            this.workerRunning = workerRunning;
        }
    }
}
