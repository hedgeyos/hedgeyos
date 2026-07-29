package com.termux.app;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class HedgeyosProcessOwnerTest {

    private static final String STATUS =
        "Name:\tproot\n" +
            "PPid:\t7654\n" +
            "Uid:\t10234\t10234\t10234\t10234\n";
    private static final java.util.List<String> COMMAND = Arrays.asList(
        "/data/user/0/org.hedgeyos/files/usr/bin/proot",
        "--rootfs=/data/user/0/org.hedgeyos/files/debian",
        "--kill-on-exit");

    @Test
    public void exactSameUidRuntimeProcessMatches() {
        Assert.assertTrue(HedgeyosProcessOwner.matchesOwnedProcess(
            STATUS,
            COMMAND,
            10234,
            "/data/user/0/org.hedgeyos/files/usr/bin/proot",
            "--rootfs=/data/user/0/org.hedgeyos/files/debian",
            "--kill-on-exit"));
    }

    @Test
    public void differentUidIsRejected() {
        Assert.assertFalse(HedgeyosProcessOwner.matchesOwnedProcess(
            STATUS, COMMAND, 10235, "proot", "--kill-on-exit"));
    }

    @Test
    public void genericProotWithoutExactRootfsIsRejected() {
        Assert.assertFalse(HedgeyosProcessOwner.matchesOwnedProcess(
            STATUS,
            Arrays.asList(
                "/data/user/0/org.hedgeyos/files/usr/bin/proot",
                "--rootfs=/somewhere/else",
                "--kill-on-exit"),
            10234,
            "/data/user/0/org.hedgeyos/files/usr/bin/proot",
            "--rootfs=/data/user/0/org.hedgeyos/files/debian",
            "--kill-on-exit"));
    }

    @Test
    public void missingRoleTokenIsRejected() {
        Assert.assertFalse(HedgeyosProcessOwner.matchesOwnedProcess(
            STATUS,
            Arrays.asList(
                "/data/user/0/org.hedgeyos/files/usr/bin/proot",
                "--rootfs=/data/user/0/org.hedgeyos/files/debian"),
            10234,
            "/data/user/0/org.hedgeyos/files/usr/bin/proot",
            "--rootfs=/data/user/0/org.hedgeyos/files/debian",
            "--kill-on-exit"));
    }

    @Test
    public void argumentSubstringIsRejected() {
        Assert.assertFalse(HedgeyosProcessOwner.matchesOwnedProcess(
            STATUS,
            Arrays.asList(
                "/data/user/0/org.hedgeyos/files/usr/bin/proot.backup",
                "--rootfs=/data/user/0/org.hedgeyos/files/debian-old",
                "--kill-on-exit"),
            10234,
            "/data/user/0/org.hedgeyos/files/usr/bin/proot",
            "--rootfs=/data/user/0/org.hedgeyos/files/debian",
            "--kill-on-exit"));
    }

    @Test
    public void exactSameUidDiagnosticChildMatchesRecordedParent() {
        Assert.assertTrue(HedgeyosProcessOwner.matchesOwnedChild(
            STATUS,
            Arrays.asList("logcat", "--pid", "7654"),
            10234,
            7654,
            "logcat",
            "--pid",
            "7654"));
    }

    @Test
    public void diagnosticChildWithDifferentParentIsRejected() {
        Assert.assertFalse(HedgeyosProcessOwner.matchesOwnedChild(
            STATUS,
            Arrays.asList("logcat", "--pid", "7654"),
            10234,
            9999,
            "logcat",
            "--pid",
            "7654"));
    }

    @Test
    public void rendererDiagnosticChildMatchesRecordedAppParent() {
        Assert.assertTrue(HedgeyosProcessOwner.matchesOwnedChild(
            STATUS,
            Arrays.asList("logcat", "--pid=7654"),
            10234,
            7654,
            "logcat",
            "--pid=7654"));
    }
}
