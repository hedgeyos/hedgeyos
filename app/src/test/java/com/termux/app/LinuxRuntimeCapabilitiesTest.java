package com.termux.app;

import org.junit.Assert;
import org.junit.Test;

public class LinuxRuntimeCapabilitiesTest {

    @Test
    public void parsesGenericCapabilitiesAndWarnings() {
        LinuxRuntimeCapabilities capabilities = LinuxRuntimeCapabilities.parse(
            "hedgeyos Linux runtime report\n" +
                "PASS|tmp|/tmp is writable with mode 1777\n" +
                "PASS|shm|/dev/shm is writable with mode 1777\n" +
                "PASS|run|/run is writable with mode 755\n" +
                "PASS|xdg-runtime|/run/user/0 is writable with mode 700\n" +
                "PASS|posix-shm|shared memory works\n" +
                "PASS|sysv-ipc|System V shared memory works\n" +
                "WARNING|memfd|blocked by Android\n" +
                "PASS|session-dbus|session bus works\n" +
                "WARNING|system-dbus|not provided under PRoot\n" +
                "WARNING|inotify|UNSUPPORTED_BY_ANDROID_PROCFS\n" +
                "PASS|x11|X1 is available\n" +
                "summary=PASS fatal=0\n");

        Assert.assertTrue(capabilities.reportAvailable);
        Assert.assertTrue(capabilities.passed);
        Assert.assertTrue(capabilities.writableTmp);
        Assert.assertTrue(capabilities.writableSharedMemory);
        Assert.assertTrue(capabilities.writableRun);
        Assert.assertTrue(capabilities.xdgRuntimeDir);
        Assert.assertTrue(capabilities.posixSharedMemory);
        Assert.assertTrue(capabilities.sysvIpc);
        Assert.assertFalse(capabilities.memfd);
        Assert.assertTrue(capabilities.sessionDbus);
        Assert.assertFalse(capabilities.systemDbus);
        Assert.assertFalse(capabilities.procSysctlVisibility);
        Assert.assertTrue(capabilities.x11Socket);
        Assert.assertEquals(3, capabilities.warnings.size());
        Assert.assertTrue(capabilities.toDisplayText().contains("Overall: PASS"));
    }

    @Test
    public void emptyReportIsExplicitlyUnavailable() {
        LinuxRuntimeCapabilities capabilities = LinuxRuntimeCapabilities.parse("");

        Assert.assertFalse(capabilities.reportAvailable);
        Assert.assertFalse(capabilities.passed);
        Assert.assertTrue(capabilities.toDisplayText().contains("No Linux runtime report"));
    }
}
