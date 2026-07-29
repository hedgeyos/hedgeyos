package com.termux.app;

import android.system.Os;
import android.system.OsConstants;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class HedgeyosProcessOwner {

    private HedgeyosProcessOwner() {}

    static int recordMatching(File pidFile, String... requiredCommandArguments) throws IOException {
        for (int attempt = 0; attempt < 20; attempt++) {
            List<Integer> matching = findMatching(requiredCommandArguments);
            if (!matching.isEmpty()) {
                int pid = Collections.max(matching);
                HedgeyosAtomicFile.write(pidFile, Integer.toString(pid) + "\n");
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

    static List<Integer> stopRecordedAndMatching(File pidFile, String... requiredCommandArguments) {
        List<Integer> stopped = new ArrayList<>();
        int recordedPid = readPid(pidFile);
        if (recordedPid > 0 && stopIfOwned(recordedPid, requiredCommandArguments)) {
            stopped.add(recordedPid);
        }
        clear(pidFile);

        for (int pid : findMatching(requiredCommandArguments)) {
            if (pid == recordedPid || stopped.contains(pid)) {
                continue;
            }
            if (stopIfOwned(pid, requiredCommandArguments)) {
                stopped.add(pid);
            }
        }
        return stopped;
    }

    static boolean stopRecordedOwnedChild(File childPidFile, File parentPidFile) {
        int childPid = readPid(childPidFile);
        int parentPid = readPid(parentPidFile);
        boolean stopped = childPid > 0 && parentPid > 0 &&
            stopIfOwnedChild(
                childPid,
                parentPid,
                "logcat",
                "--pid",
                Integer.toString(parentPid));
        clear(childPidFile);
        return stopped;
    }

    private static List<Integer> findMatching(String... requiredCommandArguments) {
        List<Integer> matching = new ArrayList<>();
        File[] entries = new File("/proc").listFiles();
        if (entries == null) {
            return matching;
        }
        int ownPid = android.os.Process.myPid();
        for (File entry : entries) {
            int pid = parsePid(entry.getName());
            if (pid <= 0 || pid == ownPid) {
                continue;
            }
            List<String> commandArguments = readCommandArguments(pid);
            String status = readText(new File("/proc/" + pid + "/status"));
            if (matchesOwnedProcess(
                status, commandArguments, Os.getuid(), requiredCommandArguments)) {
                matching.add(pid);
            }
        }
        return matching;
    }

    private static boolean stopIfOwned(int pid, String... requiredCommandArguments) {
        List<String> commandArguments = readCommandArguments(pid);
        String status = readText(new File("/proc/" + pid + "/status"));
        if (!matchesOwnedProcess(
            status, commandArguments, Os.getuid(), requiredCommandArguments)) {
            return false;
        }
        return stopProcess(pid);
    }

    private static boolean stopIfOwnedChild(int pid, int expectedParentPid,
                                            String... requiredCommandArguments) {
        List<String> commandArguments = readCommandArguments(pid);
        String status = readText(new File("/proc/" + pid + "/status"));
        if (!matchesOwnedChild(
            status,
            commandArguments,
            Os.getuid(),
            expectedParentPid,
            requiredCommandArguments)) {
            return false;
        }
        return stopProcess(pid);
    }

    private static boolean stopProcess(int pid) {
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

    static boolean matchesOwnedProcess(String status, List<String> commandArguments,
                                       int expectedUid, String... requiredCommandArguments) {
        if (!statusHasUid(status, expectedUid) ||
            commandArguments == null || commandArguments.isEmpty()) {
            return false;
        }
        for (String argument : requiredCommandArguments) {
            if (argument == null || argument.isEmpty() || !commandArguments.contains(argument)) {
                return false;
            }
        }
        return true;
    }

    static boolean matchesOwnedChild(String status, List<String> commandArguments,
                                     int expectedUid, int expectedParentPid,
                                     String... requiredCommandArguments) {
        return statusHasParentPid(status, expectedParentPid) &&
            matchesOwnedProcess(
                status,
                commandArguments,
                expectedUid,
                requiredCommandArguments);
    }

    private static boolean statusHasUid(String status, int expectedUid) {
        String expected = Integer.toString(expectedUid);
        for (String line : status.split("\n")) {
            if (!line.startsWith("Uid:")) {
                continue;
            }
            String[] fields = line.substring(4).trim().split("\\s+");
            return fields.length > 0 && expected.equals(fields[0]);
        }
        return false;
    }

    private static boolean statusHasParentPid(String status, int expectedParentPid) {
        String expected = Integer.toString(expectedParentPid);
        for (String line : status.split("\n")) {
            if (line.startsWith("PPid:")) {
                return expected.equals(line.substring(5).trim());
            }
        }
        return false;
    }

    private static List<String> readCommandArguments(int pid) {
        File file = new File("/proc/" + pid + "/cmdline");
        if (!file.isFile()) {
            return Collections.emptyList();
        }
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] data = new byte[64 * 1024];
            int read = input.read(data);
            if (read <= 0) {
                return Collections.emptyList();
            }
            List<String> arguments = new ArrayList<>();
            int start = 0;
            for (int index = 0; index <= read; index++) {
                if (index < read && data[index] != 0) {
                    continue;
                }
                if (index > start) {
                    arguments.add(new String(
                        data, start, index - start, StandardCharsets.UTF_8));
                }
                start = index + 1;
            }
            return arguments;
        } catch (IOException ignored) {
            return Collections.emptyList();
        }
    }

    static int readPid(File pidFile) {
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

}
