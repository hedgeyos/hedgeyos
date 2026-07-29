package com.termux.app;

import android.system.Os;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class HedgeyosGuestRuntime {

    static final String GUEST_TMP = "/tmp";
    static final String GUEST_SHM = "/dev/shm";
    static final String GUEST_RUN = "/run";
    static final String GUEST_ROOT_RUNTIME = "/run/user/0";
    static final String RUNTIME_REPORT = "/home/hedgeyos/Logs/linux-runtime-report.txt";

    private HedgeyosGuestRuntime() {}

    static Layout layout(File filesDir) {
        File root = new File(filesDir, "linux-runtime");
        File run = new File(root, "run");
        return new Layout(
            root,
            new File(root, "tmp"),
            new File(root, "shm"),
            run,
            new File(run, "shm"),
            new File(run, "lock"),
            new File(run, "dbus"),
            new File(run, "user/0"),
            new File(run, "user/1000"),
            new File(root, "processes"));
    }

    static void prepare(Layout layout, File rootfs, boolean resetEphemeral) throws IOException {
        mkdirs(layout.root);
        if (resetEphemeral) {
            deleteRecursively(layout.tmp);
            deleteRecursively(layout.shm);
            deleteRecursively(layout.run);
        }

        ensureDirectory(layout.tmp, 01777);
        ensureDirectory(layout.shm, 01777);
        ensureDirectory(layout.run, 0755);
        ensureDirectory(layout.runShm, 01777);
        ensureDirectory(layout.runLock, 01777);
        ensureDirectory(layout.runDbus, 0755);
        ensureDirectory(layout.runUserRoot, 0700);
        ensureDirectory(layout.runUserHedgeyos, 0700);
        ensureDirectory(layout.processes, 0700);

        if (rootfs != null && rootfs.isDirectory()) {
            ensureDirectory(new File(rootfs, "dev/shm"), 01777);
            ensureDirectory(new File(rootfs, "run/shm"), 01777);
            ensureDirectory(new File(rootfs, "tmp"), 01777);
            ensureDirectory(new File(rootfs, "run"), 0755);
        }
    }

    static List<String> buildCommand(
        File prootBinary,
        File rootfs,
        Layout layout,
        File exportDir,
        File publicLogDir,
        String shellCommand,
        String changeId,
        String home,
        String user,
        String display,
        boolean killOnExit
    ) {
        List<String> command = new ArrayList<>();
        command.add(prootBinary.getAbsolutePath());
        command.add("--rootfs=" + rootfs.getAbsolutePath());
        command.add("--link2symlink");
        if (killOnExit) {
            command.add("--kill-on-exit");
        }
        command.add("--sysvipc");
        command.add("--ashmem-memfd");
        command.add("--change-id=" + changeId);
        command.add("--bind=/dev");
        command.add("--bind=" + layout.shm.getAbsolutePath() + ":" + GUEST_SHM);
        command.add("--bind=/proc");
        command.add("--bind=/sys");
        command.add("--bind=" + layout.tmp.getAbsolutePath() + ":" + GUEST_TMP);
        command.add("--bind=" + layout.run.getAbsolutePath() + ":" + GUEST_RUN);
        command.add("--bind=" + layout.shm.getAbsolutePath() + ":/run/shm");
        command.add("--bind=" + exportDir.getAbsolutePath() + ":/home/hedgeyos/Downloads");
        command.add("--bind=" + publicLogDir.getAbsolutePath() + ":/home/hedgeyos/Logs");
        command.add("--cwd=" + home);
        command.add("/usr/bin/env");
        command.add("-i");
        command.add("HOME=" + home);
        command.add("USER=" + user);
        command.add("LOGNAME=" + user);
        command.add("SHELL=/bin/bash");
        command.add("DISPLAY=" + display);
        command.add("LANG=C.UTF-8");
        command.add("TMPDIR=" + GUEST_TMP);
        command.add("XDG_RUNTIME_DIR=" + GUEST_ROOT_RUNTIME);
        command.add("HEDGEYOS_SESSION_LOG=/home/hedgeyos/Logs/xfce-session.log");
        command.add("HEDGEYOS_RUNTIME_REPORT=" + RUNTIME_REPORT);
        command.add("PATH=/usr/local/bin:/usr/bin:/bin:/usr/local/sbin:/usr/sbin:/sbin");
        command.add("/bin/bash");
        command.add("-lc");
        command.add(shellCommand);
        return command;
    }

    private static void ensureDirectory(File directory, int mode) throws IOException {
        if (directory.exists() && !directory.isDirectory()) {
            throw new IOException("Runtime path is not a directory: " + directory.getAbsolutePath());
        }
        mkdirs(directory);
        try {
            Os.chmod(directory.getAbsolutePath(), mode);
        } catch (Exception e) {
            throw new IOException("Failed to set runtime directory mode: " + directory.getAbsolutePath(), e);
        }
    }

    private static void mkdirs(File directory) throws IOException {
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Failed to create runtime directory: " + directory.getAbsolutePath());
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
            throw new IOException("Failed to delete runtime path: " + file.getAbsolutePath());
        }
    }

    static final class Layout {
        final File root;
        final File tmp;
        final File shm;
        final File run;
        final File runShm;
        final File runLock;
        final File runDbus;
        final File runUserRoot;
        final File runUserHedgeyos;
        final File processes;

        Layout(File root, File tmp, File shm, File run, File runShm, File runLock, File runDbus,
               File runUserRoot, File runUserHedgeyos, File processes) {
            this.root = root;
            this.tmp = tmp;
            this.shm = shm;
            this.run = run;
            this.runShm = runShm;
            this.runLock = runLock;
            this.runDbus = runDbus;
            this.runUserRoot = runUserRoot;
            this.runUserHedgeyos = runUserHedgeyos;
            this.processes = processes;
        }
    }
}
