package com.termux.app;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class HedgeyosBoundedLog {

    static final long DEFAULT_MAX_BYTES = 1024L * 1024L;
    static final int DEFAULT_ROTATIONS = 3;
    static final long LEGACY_THRESHOLD_BYTES = 8L * 1024L * 1024L;
    static final long DIRECTORY_BUDGET_BYTES = 16L * 1024L * 1024L;
    private static final long DIRECTORY_BUDGET_RESERVE_BYTES = 64L * 1024L;
    private static final long STREAM_RATE_BYTES_PER_SECOND = 128L * 1024L;
    private static final Object LOCK = new Object();

    private HedgeyosBoundedLog() {}

    static void append(File file, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        append(file, text.getBytes(StandardCharsets.UTF_8));
    }

    static void append(File file, byte[] data) {
        if (file == null || data == null || data.length == 0) {
            return;
        }
        synchronized (LOCK) {
            try {
                mkdirs(file.getParentFile());
                byte[] bounded = data;
                if (bounded.length > DEFAULT_MAX_BYTES) {
                    int offset = bounded.length - (int) DEFAULT_MAX_BYTES;
                    byte[] tail = new byte[(int) DEFAULT_MAX_BYTES];
                    System.arraycopy(bounded, offset, tail, 0, tail.length);
                    bounded = tail;
                }
                if (file.length() + bounded.length > DEFAULT_MAX_BYTES) {
                    rotate(file, DEFAULT_ROTATIONS);
                }
                File budgetRoot = budgetRoot(file.getParentFile());
                long currentBytes = enforceDirectoryBudget(budgetRoot);
                if (currentBytes + bounded.length >
                    DIRECTORY_BUDGET_BYTES - DIRECTORY_BUDGET_RESERVE_BYTES) {
                    return;
                }
                try (FileOutputStream output = new FileOutputStream(file, true)) {
                    output.write(bounded);
                }
                enforceDirectoryBudget(budgetRoot);
            } catch (IOException ignored) {
            }
        }
    }

    static String readTail(File file, int maxBytes) {
        if (file == null || !file.isFile() || maxBytes <= 0) {
            return "";
        }
        synchronized (LOCK) {
            try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
                int length = (int) Math.min((long) maxBytes, input.length());
                byte[] data = new byte[length];
                input.seek(input.length() - length);
                input.readFully(data);
                return new String(data, StandardCharsets.UTF_8);
            } catch (IOException ignored) {
                return "";
            }
        }
    }

    static long repairOversizedLegacy(File file) {
        if (file == null || !file.isFile() || file.length() <= LEGACY_THRESHOLD_BYTES) {
            return 0;
        }
        synchronized (LOCK) {
            long originalBytes = file.length();
            long originalModifiedUnixMs = file.lastModified();
            long repairedAtUnixMs = System.currentTimeMillis();
            try {
                String header =
                    "[HedgeyOS repaired an oversized legacy managed log]\n" +
                    "original_bytes=" + originalBytes + "\n" +
                    "--- preserved tail ---\n";
                byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
                int tailLength = (int) Math.min(
                    originalBytes,
                    DEFAULT_MAX_BYTES - headerBytes.length);
                byte[] tail = new byte[tailLength];
                try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
                    input.seek(originalBytes - tailLength);
                    input.readFully(tail);
                }
                for (int index = 1; index <= DEFAULT_ROTATIONS; index++) {
                    delete(new File(file.getPath() + "." + index));
                }
                File temporary = new File(
                    file.getParentFile(), "." + file.getName() + ".repair." +
                    Thread.currentThread().getId());
                try (FileOutputStream output = new FileOutputStream(temporary)) {
                    output.write(headerBytes);
                    output.write(tail);
                }
                if (!temporary.renameTo(file)) {
                    delete(temporary);
                    return 0;
                }
                File metadata = new File(
                    file.getParentFile(), file.getName() + ".legacy-repair.json");
                HedgeyosAtomicFile.write(
                    metadata,
                    "{\"originalBytes\":" + originalBytes +
                    ",\"preservedBytes\":" + tailLength +
                    ",\"originalModifiedUnixMs\":" + originalModifiedUnixMs +
                    ",\"repairedAtUnixMs\":" + repairedAtUnixMs +
                    ",\"path\":\"" + escapeJson(file.getAbsolutePath()) + "\"}\n");
                return originalBytes;
            } catch (IOException ignored) {
                return 0;
            }
        }
    }

    static Thread pump(Process process, File... destinations) {
        Thread thread = new Thread(() -> {
            long windowStarted = System.currentTimeMillis();
            long windowBytes = 0;
            long suppressedBytes = 0;
            try (InputStream input = process.getInputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    long now = System.currentTimeMillis();
                    if (now - windowStarted >= 1000) {
                        if (suppressedBytes > 0) {
                            byte[] summary = (
                                "[HedgeyOS Android log rate limit: suppressed_bytes=" +
                                suppressedBytes + "]\n").getBytes(StandardCharsets.UTF_8);
                            appendUnique(destinations, summary);
                            suppressedBytes = 0;
                        }
                        windowStarted = now;
                        windowBytes = 0;
                    }
                    if (windowBytes + read > STREAM_RATE_BYTES_PER_SECOND) {
                        suppressedBytes += read;
                        continue;
                    }
                    byte[] chunk = new byte[read];
                    System.arraycopy(buffer, 0, chunk, 0, read);
                    appendUnique(destinations, chunk);
                    windowBytes += read;
                }
                if (suppressedBytes > 0) {
                    appendUnique(destinations, (
                        "[HedgeyOS Android log rate limit: suppressed_bytes=" +
                        suppressedBytes + "]\n").getBytes(StandardCharsets.UTF_8));
                }
            } catch (IOException ignored) {
            }
        }, "hedgeyos-bounded-log-pump");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static void appendUnique(File[] destinations, byte[] data) {
        String previousPath = null;
        for (File destination : destinations) {
            if (destination == null) {
                continue;
            }
            String path = destination.getAbsolutePath();
            if (path.equals(previousPath)) {
                continue;
            }
            append(destination, data);
            previousPath = path;
        }
    }

    private static void rotate(File file, int rotations) throws IOException {
        delete(new File(file.getPath() + "." + rotations));
        for (int index = rotations - 1; index >= 1; index--) {
            File source = new File(file.getPath() + "." + index);
            if (source.exists() &&
                !source.renameTo(new File(file.getPath() + "." + (index + 1)))) {
                throw new IOException("Failed to rotate managed log " + source);
            }
        }
        if (file.exists() && !file.renameTo(new File(file.getPath() + ".1"))) {
            throw new IOException("Failed to rotate managed log " + file);
        }
    }

    private static long enforceDirectoryBudget(File directory) {
        if (directory == null) {
            return 0;
        }
        long total = 0;
        List<File> files = new ArrayList<>();
        List<File> rotations = new ArrayList<>();
        collectFiles(directory, files);
        for (File candidate : files) {
            total += candidate.length();
            if (candidate.getName().matches(".*\\.log\\.[0-9]+")) {
                rotations.add(candidate);
            }
        }
        rotations.sort(Comparator.comparingLong(File::lastModified));
        for (File rotation : rotations) {
            if (total <= DIRECTORY_BUDGET_BYTES) {
                break;
            }
            long length = rotation.length();
            if (rotation.delete()) {
                total -= length;
            }
        }
        return total;
    }

    private static void collectFiles(File directory, List<File> files) {
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collectFiles(child, files);
            } else if (child.isFile()) {
                files.add(child);
            }
        }
    }

    private static File budgetRoot(File directory) {
        if (directory != null && "apps".equals(directory.getName()) &&
            directory.getParentFile() != null) {
            return directory.getParentFile();
        }
        return directory;
    }

    private static void mkdirs(File directory) throws IOException {
        if (directory != null && !directory.exists() && !directory.mkdirs()) {
            throw new IOException("Failed to create log directory " + directory);
        }
    }

    private static void delete(File file) {
        if (file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
