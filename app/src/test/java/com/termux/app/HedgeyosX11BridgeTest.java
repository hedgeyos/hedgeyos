package com.termux.app;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class HedgeyosX11BridgeTest {

    @Test
    public void normalEnvironmentRemovesPresenceSensitiveDebugVariable() {
        Map<String, String> environment = new HashMap<>();
        environment.put("TERMUX_X11_DEBUG", "1");

        boolean diagnostic = HedgeyosX11Bridge.configureDebugEnvironment(
            environment, false, false);

        Assert.assertFalse(diagnostic);
        Assert.assertFalse(environment.containsKey("TERMUX_X11_DEBUG"));
    }

    @Test
    public void oneShotDiagnosticExportsOne() {
        Map<String, String> environment = new HashMap<>();

        boolean diagnostic = HedgeyosX11Bridge.configureDebugEnvironment(
            environment, false, true);

        Assert.assertTrue(diagnostic);
        Assert.assertEquals("1", environment.get("TERMUX_X11_DEBUG"));
    }

    @Test
    public void explicitDiagnosticBuildExportsOne() {
        Map<String, String> environment = new HashMap<>();

        boolean diagnostic = HedgeyosX11Bridge.configureDebugEnvironment(
            environment, true, false);

        Assert.assertTrue(diagnostic);
        Assert.assertEquals("1", environment.get("TERMUX_X11_DEBUG"));
    }
}
