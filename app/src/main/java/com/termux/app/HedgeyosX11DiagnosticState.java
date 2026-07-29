package com.termux.app;

final class HedgeyosX11DiagnosticState {

    enum SessionMode {
        NORMAL,
        DIAGNOSTIC
    }

    private boolean oneShotRequested;

    synchronized void requestOneShot() {
        oneShotRequested = true;
    }

    synchronized SessionMode consume(boolean diagnosticBuild) {
        boolean diagnostic = diagnosticBuild || oneShotRequested;
        oneShotRequested = false;
        return diagnostic ? SessionMode.DIAGNOSTIC : SessionMode.NORMAL;
    }

    synchronized void clear() {
        oneShotRequested = false;
    }
}
