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
                "PASS|session-dbus-process|owned D-Bus process works\n" +
                "PASS|xfce-session-process|owned XFCE process works\n" +
                "WARNING|system-dbus|not provided under PRoot\n" +
                "WARNING|inotify|UNSUPPORTED_BY_ANDROID_PROCFS\n" +
                "PASS|x11|X1 is available\n" +
                "PASS|gtk-svg-loader|loader is installed\n" +
                "PASS|gtk-svg-cache|cache contains SVG\n" +
                "PASS|gtk-svg-decode|decode passed\n" +
                "PASS|app-supervision|ready\n" +
                "PASS|managed-log-budget|bounded\n" +
                "PASS|proot-recvmsg-normal|passed\n" +
                "PASS|proot-recvmsg-browser-abort|passed\n" +
                "PASS|proot-recvmsg-browser-kill|passed\n" +
                "PASS|x11-diagnostic|OFF (NORMAL session)\n" +
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
        Assert.assertTrue(capabilities.sessionDbusProcess);
        Assert.assertTrue(capabilities.xfceSessionProcess);
        Assert.assertFalse(capabilities.systemDbus);
        Assert.assertFalse(capabilities.procSysctlVisibility);
        Assert.assertTrue(capabilities.x11Socket);
        Assert.assertTrue(capabilities.gtkSvgLoader);
        Assert.assertTrue(capabilities.gtkSvgLoaderCache);
        Assert.assertTrue(capabilities.gtkSvgDecode);
        Assert.assertTrue(capabilities.appSupervision);
        Assert.assertTrue(capabilities.managedLogBudget);
        Assert.assertTrue(capabilities.prootRecvmsg);
        Assert.assertEquals("NORMAL", capabilities.x11SessionMode);
        Assert.assertEquals(3, capabilities.warnings.size());
        Assert.assertTrue(capabilities.toDisplayText().contains("Overall: PASS"));
        Assert.assertTrue(capabilities.toDisplayText().contains("GTK SVG loader: PASS"));
        Assert.assertTrue(capabilities.toDisplayText().contains("GTK SVG decode: PASS"));
        Assert.assertTrue(capabilities.toDisplayText().contains("PRoot recvmsg lifecycle: PASS"));
        Assert.assertTrue(capabilities.toDisplayText().contains("X11 diagnostic mode: OFF"));
    }

    @Test
    public void emptyReportIsExplicitlyUnavailable() {
        LinuxRuntimeCapabilities capabilities = LinuxRuntimeCapabilities.parse("");

        Assert.assertFalse(capabilities.reportAvailable);
        Assert.assertFalse(capabilities.passed);
        Assert.assertTrue(capabilities.toDisplayText().contains("No Linux runtime report"));
    }

    @Test
    public void diagnosticSessionIsReportedAsWarningAndOn() {
        LinuxRuntimeCapabilities capabilities = LinuxRuntimeCapabilities.parse(
            "WARNING|x11-diagnostic|ON (DIAGNOSTIC session)\n" +
                "summary=PASS fatal=0\n");

        Assert.assertEquals("DIAGNOSTIC", capabilities.x11SessionMode);
        Assert.assertTrue(capabilities.toDisplayText().contains("X11 diagnostic mode: ON"));
        Assert.assertEquals(1, capabilities.warnings.size());
    }
}
