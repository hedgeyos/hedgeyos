package com.termux.app;

import android.system.Os;
import android.system.OsConstants;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class HedgeyosProcessOwner {

    private HedgeyosProcessOwner() {}

    static int recordMatching(File pidFile, String... requiredCommandParts) throws IOException {
        for (int attempt = 0; attempt < 20; attempt++) {
            List<Integer> matching = findMatching(requiredCommandParts);
            if (!matching.isEmpty()) {
                int pid = Collections.max(matching);
                atomicWrite(pidFile, Integer.toString(pid) + "\n");
                return pid;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while recording owned process.", e);
            }
        }
        throw new IOException("Started process did not expose the expected command identity.");
    }

    static void clear(File pidFile) {
        if (pidFile != null && pidFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            pidFile.delete();
        }
    }

    static List<Integer> stopRecordedAndMatching(File pidFile, String... requiredCommandParts) {
        List<Integer> stopped = new ArrayList<>();
        int recordedPid = readPid(pidFile);
        if (recordedPid > 0 && stopIfOwned(recordedPid, requiredCommandParts)) {
            stopped.add(recordedPid);
        }
        clear(pidFile);

        for (int pid : findMatching(requiredCommandParts)) {
            if (pid == recordedPid || stopped.contains(pid)) {
                continue;
            }
            if (stopIfOwned(pid, requiredCommandParts)) {
                stopped.add(pid);
            }
        }
        return stopped;
    }

    private static List<Integer> findMatching(String... requiredCommandParts) {
        List<Integer> matching = new ArrayList<>();
        File[] entries = new File("/proc").listFiles();
        if (entries == null) {
            return matching;
        }
        int ownPid = android.os.Process.myPid();
        for (File entry : entries) {
            int pid = parsePid(entry.getName());
            if (pid <= 0 || pid == ownPid || !isSameUid(pid)) {
                continue;
            }
            String commandLine = readCommandLine(pid);
            boolean matches = !commandLine.isEmpty();
            for (String part : requiredCommandParts) {
                if (part == null || part.isEmpty() || !commandLine.contains(part)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                matching.add(pid);
            }
        }
        return matching;
    }

    private static boolean stopIfOwned(int pid, String... requiredCommandParts) {
        if (!isSameUid(pid)) {
            return false;
        }
        String commandLine = readCommandLine(pid);
        if (commandLine.isEmpty()) {
            return false;
        }
        for (String part : requiredCommandParts) {
            if (part == null || part.isEmpty() || !commandLine.contains(part)) {
                return false;
            }
        }

        try {
            Os.kill(pid, OsConstants.SIGTERM);
        } catch (Exception ignored) {
            return false;
        }
        for (int attempt = 0; attempt < 20 && new File("/proc/" + pid).exists(); attempt++) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (new File("/proc/" + pid).exists()) {
            try {
                Os.kill(pid, OsConstants.SIGKILL);
            } catch (Exception ignored) {
            }
        }
        return true;
    }

    private static boolean isSameUid(int pid) {
        String status = readText(new File("/proc/" + pid + "/status"));
        String expected = Integer.toString(Os.getuid());
        for (String line : status.split("\n")) {
            if (!line.startsWith("Uid:")) {
                continue;
            }
            String[] fields = line.substring(4).trim().split("\\s+");
            return fields.length > 0 && expected.equals(fields[0]);
        }
        return false;
    }

    private static String readCommandLine(int pid) {
        File file = new File("/proc/" + pid + "/cmdline");
        if (!file.isFile()) {
            return "";
        }
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] data = new byte[64 * 1024];
            int read = input.read(data);
            if (read <= 0) {
                return "";
            }
            String value = new String(data, 0, read, StandardCharsets.UTF_8);
            return value.replace('\0', ' ');
        } catch (IOException ignored) {
            return "";
        }
    }

    private static int readPid(File pidFile) {
        String value = readText(pidFile).trim();
        return parsePid(value);
    }

    private static int parsePid(String value) {
        if (value == null || value.isEmpty()) {
            return -1;
        }
        try {
            long pid = Long.parseLong(value);
            return pid > 0 && pid <= Integer.MAX_VALUE ? (int) pid : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static String readText(File file) {
        if (file == null || !file.isFile()) {
            return "";
        }
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return "";
        }
    }

    private static void atomicWrite(File file, String content) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create process-state directory: " + parent.getAbsolutePath());
        }
        File temporary = new File(parent, file.getName() + ".tmp." +
            Long.toUnsignedString(System.nanoTime(), 36).toLowerCase(Locale.ROOT));
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(content.getBytes(StandardCharsets.UTF_8));
                output.getFD().sync();
            }
            try {
                Files.move(temporary.toPath(), file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicMoveFailed) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            if (temporary.exists()) {
                //noinspection ResultOfMethodCallIgnored
                temporary.delete();
            }
        }
    }
}
