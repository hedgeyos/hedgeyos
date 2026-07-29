package com.termux.app;

import org.junit.Assert;
import org.junit.Test;

public class HedgeyosX11DiagnosticStateTest {

    @Test
    public void oneShotRequestIsConsumedExactlyOnce() {
        HedgeyosX11DiagnosticState state = new HedgeyosX11DiagnosticState();

        state.requestOneShot();

        Assert.assertEquals(
            HedgeyosX11DiagnosticState.SessionMode.DIAGNOSTIC,
            state.consume(false));
        Assert.assertEquals(
            HedgeyosX11DiagnosticState.SessionMode.NORMAL,
            state.consume(false));
    }

    @Test
    public void diagnosticBuildAlwaysUsesDiagnosticMode() {
        HedgeyosX11DiagnosticState state = new HedgeyosX11DiagnosticState();

        Assert.assertEquals(
            HedgeyosX11DiagnosticState.SessionMode.DIAGNOSTIC,
            state.consume(true));
        Assert.assertEquals(
            HedgeyosX11DiagnosticState.SessionMode.NORMAL,
            state.consume(false));
    }

    @Test
    public void clearDropsUnconsumedRequestAfterShutdown() {
        HedgeyosX11DiagnosticState state = new HedgeyosX11DiagnosticState();

        state.requestOneShot();
        state.clear();

        Assert.assertEquals(
            HedgeyosX11DiagnosticState.SessionMode.NORMAL,
            state.consume(false));
    }
}
