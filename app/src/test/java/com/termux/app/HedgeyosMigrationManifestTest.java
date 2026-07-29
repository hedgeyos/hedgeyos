package com.termux.app;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class HedgeyosMigrationManifestTest {

    private static final String SHA =
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    public void parsesGenerationsAndAcceptsArchitectureAll() throws Exception {
        HedgeyosMigrationManifest manifest = parse(
            "helper\t1.0\tall\tgeneration-v1\t" + SHA +
                "\thelper_1.0_all.deb\tportable helper\n" +
            "runtime\t2.0\tarm64\tgeneration-v2\t" + SHA +
                "\truntime_2.0_arm64.deb\tARM runtime\n");

        Assert.assertEquals(2, manifest.entries().size());
        Assert.assertEquals(2, manifest.generations().size());
        Assert.assertEquals(
            "all",
            manifest.entriesForGeneration("generation-v1").get(0).architecture);
    }

    @Test
    public void interruptedGenerationRetriesUntilDurableMarkerExists() throws Exception {
        File rootfs = Files.createTempDirectory("hedgeyos-migration").toFile();
        File marker = HedgeyosMigrationManifest.marker(rootfs, "gtk-svg-loader-v1");
        Assert.assertTrue(marker.getParentFile().mkdirs());
        Assert.assertTrue(new File(marker.getParentFile(), "gtk-svg-loader-v1.tmp").createNewFile());

        Assert.assertFalse(HedgeyosMigrationManifest.isGenerationComplete(
            rootfs, "gtk-svg-loader-v1"));

        Assert.assertTrue(marker.createNewFile());
        Assert.assertTrue(HedgeyosMigrationManifest.isGenerationComplete(
            rootfs, "gtk-svg-loader-v1"));
    }

    @Test(expected = java.io.IOException.class)
    public void rejectsDuplicatePackageRows() throws Exception {
        parse(
            "helper\t1.0\tall\tgeneration-v1\t" + SHA +
                "\thelper_1.0_all.deb\tportable helper\n" +
            "helper\t1.0\tall\tgeneration-v2\t" + SHA +
                "\thelper-second_1.0_all.deb\tduplicate helper\n");
    }

    private static HedgeyosMigrationManifest parse(String text) throws Exception {
        return HedgeyosMigrationManifest.parse(new ByteArrayInputStream(
            text.getBytes(StandardCharsets.UTF_8)));
    }
}
