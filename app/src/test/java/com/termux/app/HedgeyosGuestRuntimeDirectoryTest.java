package com.termux.app;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@RunWith(RobolectricTestRunner.class)
public class HedgeyosGuestRuntimeDirectoryTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void prepareAppliesModesAndClearsOnlyEphemeralState() throws Exception {
        File files = temporaryFolder.newFolder("files");
        File rootfs = temporaryFolder.newFolder("rootfs");
        HedgeyosGuestRuntime.Layout layout = HedgeyosGuestRuntime.layout(files);
        Assert.assertTrue(layout.tmp.mkdirs());
        File staleSocket = new File(layout.tmp, ".X1-lock");
        Assert.assertTrue(staleSocket.createNewFile());
        Assert.assertTrue(layout.runUserRoot.mkdirs());
        File brokenIceAuthority = new File(layout.runUserRoot, "ICEauthority");
        Files.createSymbolicLink(
            brokenIceAuthority.toPath(),
            new File(layout.runUserRoot, ".missing-ICEauthority").toPath());
        Assert.assertFalse(brokenIceAuthority.exists());
        Assert.assertTrue(Files.exists(
            brokenIceAuthority.toPath(),
            java.nio.file.LinkOption.NOFOLLOW_LINKS));
        Assert.assertTrue(layout.processes.mkdirs());
        File ownedPid = new File(layout.processes, "desktop.pid");
        Assert.assertTrue(ownedPid.createNewFile());

        HedgeyosGuestRuntime.prepare(
            layout,
            rootfs,
            true,
            posixModeAccess());

        Assert.assertFalse(staleSocket.exists());
        Assert.assertFalse(Files.exists(
            brokenIceAuthority.toPath(),
            java.nio.file.LinkOption.NOFOLLOW_LINKS));
        Assert.assertTrue(ownedPid.exists());
        Assert.assertEquals(01777, mode(layout.tmp));
        Assert.assertEquals(01777, mode(layout.shm));
        Assert.assertEquals(0755, mode(layout.run));
        Assert.assertEquals(01777, mode(layout.runShm));
        Assert.assertEquals(01777, mode(layout.runLock));
        Assert.assertEquals(0755, mode(layout.runDbus));
        Assert.assertEquals(0700, mode(layout.runUserRoot));
        Assert.assertEquals(0700, mode(layout.runUserHedgeyos));
        Assert.assertEquals(0700, mode(layout.processes));
        Assert.assertEquals(01777, mode(new File(rootfs, "dev/shm")));
        Assert.assertEquals(01777, mode(new File(rootfs, "run/shm")));
    }

    @Test
    public void prepareRejectsModeThatCannotBeApplied() throws Exception {
        File files = temporaryFolder.newFolder("mode-failure-files");
        HedgeyosGuestRuntime.Layout layout = HedgeyosGuestRuntime.layout(files);

        try {
            HedgeyosGuestRuntime.prepare(
                layout,
                null,
                true,
                new HedgeyosGuestRuntime.DirectoryModeAccess() {
                    @Override
                    public void apply(File directory, int requestedMode) {
                    }

                    @Override
                    public int read(File directory) {
                        return 0;
                    }
                });
            Assert.fail("Expected a mode verification failure.");
        } catch (IOException e) {
            Assert.assertTrue(e.getMessage().contains("expected 1777"));
        }
    }

    private static HedgeyosGuestRuntime.DirectoryModeAccess posixModeAccess() {
        return new HedgeyosGuestRuntime.DirectoryModeAccess() {
            @Override
            public void apply(File directory, int requestedMode) throws Exception {
                Files.setAttribute(directory.toPath(), "unix:mode", requestedMode);
            }

            @Override
            public int read(File directory) throws Exception {
                return mode(directory);
            }
        };
    }

    private static int mode(File file) throws Exception {
        return ((Number) Files.getAttribute(file.toPath(), "unix:mode")).intValue() & 07777;
    }
}
