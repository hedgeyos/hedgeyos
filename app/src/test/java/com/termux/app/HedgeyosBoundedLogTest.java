package com.termux.app;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class HedgeyosBoundedLogTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void sparseLegacyLogIsRepairedOnceAndPreservesTail() throws Exception {
        File log = new File(temporaryFolder.getRoot(), "xfce-session.log");
        try (RandomAccessFile output = new RandomAccessFile(log, "rw")) {
            output.setLength(6L * 1024L * 1024L * 1024L);
            output.seek(output.length());
            output.write("preserved session tail\n".getBytes(StandardCharsets.UTF_8));
        }

        long originalBytes = log.length();
        Assert.assertEquals(
            originalBytes,
            HedgeyosBoundedLog.repairOversizedLegacy(log));
        Assert.assertTrue(log.length() <= HedgeyosBoundedLog.DEFAULT_MAX_BYTES);
        Assert.assertTrue(
            new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8)
                .contains("preserved session tail"));
        File metadata = new File(
            temporaryFolder.getRoot(),
            "xfce-session.log.legacy-repair.json");
        Assert.assertTrue(metadata.isFile());
        Assert.assertTrue(
            new String(Files.readAllBytes(metadata.toPath()), StandardCharsets.UTF_8)
                .contains("\"originalBytes\":" + originalBytes));

        byte[] repaired = Files.readAllBytes(log.toPath());
        Assert.assertEquals(0, HedgeyosBoundedLog.repairOversizedLegacy(log));
        Assert.assertArrayEquals(repaired, Files.readAllBytes(log.toPath()));
    }

    @Test
    public void appendRotatesWithoutExceedingManagedBudget() throws Exception {
        File log = new File(temporaryFolder.getRoot(), "runtime.log");
        byte[] chunk = new byte[600 * 1024];
        for (int index = 0; index < 8; index++) {
            HedgeyosBoundedLog.append(log, chunk);
        }

        Assert.assertTrue(log.length() <= HedgeyosBoundedLog.DEFAULT_MAX_BYTES);
        long total = 0;
        for (File file : temporaryFolder.getRoot().listFiles()) {
            total += file.length();
        }
        Assert.assertTrue(total <= 4L * HedgeyosBoundedLog.DEFAULT_MAX_BYTES);
    }

    @Test
    public void multipleManagedLogsStayWithinDirectoryBudget() {
        byte[] chunk = new byte[600 * 1024];
        for (int logIndex = 0; logIndex < 40; logIndex++) {
            File log = new File(
                temporaryFolder.getRoot(), "managed-" + logIndex + ".log");
            for (int writeIndex = 0; writeIndex < 2; writeIndex++) {
                HedgeyosBoundedLog.append(log, chunk);
            }
        }

        long total = recursiveSize(temporaryFolder.getRoot());
        Assert.assertTrue(total <= HedgeyosBoundedLog.DIRECTORY_BUDGET_BYTES);
    }

    @Test
    public void nestedApplicationLogsShareTheTopLevelBudget() throws Exception {
        File apps = temporaryFolder.newFolder("apps");
        byte[] chunk = new byte[600 * 1024];
        for (int logIndex = 0; logIndex < 40; logIndex++) {
            HedgeyosBoundedLog.append(
                new File(apps, "application-" + logIndex + ".log"), chunk);
        }

        Assert.assertTrue(
            recursiveSize(temporaryFolder.getRoot()) <=
                HedgeyosBoundedLog.DIRECTORY_BUDGET_BYTES);
    }

    private static long recursiveSize(File directory) {
        long total = 0;
        File[] children = directory.listFiles();
        if (children == null) {
            return 0;
        }
        for (File child : children) {
            total += child.isDirectory() ? recursiveSize(child) : child.length();
        }
        return total;
    }
}
