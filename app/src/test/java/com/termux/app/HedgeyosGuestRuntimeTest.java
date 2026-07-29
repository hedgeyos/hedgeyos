package com.termux.app;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class HedgeyosGuestRuntimeTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void layoutKeepsEphemeralStateOutsidePersistentRootfs() throws Exception {
        File files = temporaryFolder.newFolder("files");
        HedgeyosGuestRuntime.Layout layout = HedgeyosGuestRuntime.layout(files);

        Assert.assertEquals(new File(files, "linux-runtime"), layout.root);
        Assert.assertEquals(new File(files, "linux-runtime/tmp"), layout.tmp);
        Assert.assertEquals(new File(files, "linux-runtime/shm"), layout.shm);
        Assert.assertEquals(new File(files, "linux-runtime/run"), layout.run);
        Assert.assertEquals(new File(files, "linux-runtime/processes"), layout.processes);
        Assert.assertFalse(layout.tmp.getAbsolutePath().contains(File.separator + "debian" + File.separator));
    }

    @Test
    public void commandUsesOneOrderedRuntimeMountContract() throws Exception {
        File files = temporaryFolder.newFolder("files");
        File rootfs = new File(files, "debian");
        File proot = new File(files, "usr/bin/proot");
        File exports = new File(files, "export");
        File logs = new File(files, "public-logs");
        HedgeyosGuestRuntime.Layout layout = HedgeyosGuestRuntime.layout(files);

        List<String> command = HedgeyosGuestRuntime.buildCommand(
            proot,
            rootfs,
            layout,
            exports,
            logs,
            "exec true",
            "0:0",
            "/home/hedgeyos",
            "root",
            ":1",
            true);

        String dev = "--bind=/dev";
        String shm = "--bind=" + layout.shm.getAbsolutePath() + ":/dev/shm";
        String proc = "--bind=/proc";
        String sys = "--bind=/sys";
        String tmp = "--bind=" + layout.tmp.getAbsolutePath() + ":/tmp";
        String run = "--bind=" + layout.run.getAbsolutePath() + ":/run";

        Assert.assertEquals(1, Collections.frequency(command, dev));
        Assert.assertTrue(command.indexOf(dev) < command.indexOf(shm));
        Assert.assertTrue(command.indexOf(shm) < command.indexOf(proc));
        Assert.assertTrue(command.indexOf(proc) < command.indexOf(sys));
        Assert.assertTrue(command.indexOf(sys) < command.indexOf(tmp));
        Assert.assertTrue(command.indexOf(tmp) < command.indexOf(run));
        Assert.assertTrue(command.contains("--kill-on-exit"));
        Assert.assertTrue(command.contains("TMPDIR=/tmp"));
        Assert.assertTrue(command.contains("XDG_RUNTIME_DIR=/run/user/0"));
        Assert.assertTrue(command.contains(
            "HEDGEYOS_RUNTIME_REPORT=/home/hedgeyos/Logs/linux-runtime-report.txt"));
        Assert.assertFalse(command.toString().contains(rootfs.getAbsolutePath() + "/tmp:/tmp"));
    }

    @Test
    public void shortLivedCommandsUseSameMountsWithoutOwningTheSession() throws Exception {
        File files = temporaryFolder.newFolder("files");
        HedgeyosGuestRuntime.Layout layout = HedgeyosGuestRuntime.layout(files);

        List<String> command = HedgeyosGuestRuntime.buildCommand(
            new File(files, "usr/bin/proot"),
            new File(files, "debian"),
            layout,
            new File(files, "export"),
            new File(files, "public-logs"),
            "id",
            "0:0",
            "/root",
            "root",
            ":1",
            false);

        Assert.assertFalse(command.contains("--kill-on-exit"));
        Assert.assertTrue(command.contains("--bind=" + layout.shm.getAbsolutePath() + ":/dev/shm"));
        Assert.assertTrue(command.contains("--bind=" + layout.run.getAbsolutePath() + ":/run"));
    }

    @Test
    public void xdgRuntimeDirectoryTracksGuestUid() throws Exception {
        File files = temporaryFolder.newFolder("files");

        List<String> command = HedgeyosGuestRuntime.buildCommand(
            new File(files, "usr/bin/proot"),
            new File(files, "debian"),
            HedgeyosGuestRuntime.layout(files),
            new File(files, "export"),
            new File(files, "public-logs"),
            "id",
            "1000:1000",
            "/home/hedgeyos",
            "hedgeyos",
            ":1",
            false);

        Assert.assertTrue(command.contains("XDG_RUNTIME_DIR=/run/user/1000"));
    }
}
