// SPDX-License-Identifier: MIT
package me.jxl.kiosk.plugins.partymode;

/** Explicit stop wins until the automatic eligibility condition falls and rises. */
final class PartyActivation {
    private boolean requested, stopped;
    void show() { requested = true; stopped = false; }
    void hide() { requested = false; stopped = true; }
    boolean visible(boolean automatic, boolean eligible) {
        if (!eligible) { stopped = false; return false; }
        return !stopped && (requested || automatic);
    }
}
