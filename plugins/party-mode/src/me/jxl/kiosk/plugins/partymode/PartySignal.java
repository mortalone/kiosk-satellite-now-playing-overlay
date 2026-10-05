// SPDX-License-Identifier: MIT
package me.jxl.kiosk.plugins.partymode;

final class PartySignal {
    private PartySignal() {}
    static float[] bounded(float[] values, boolean waveform) {
        if (values == null) return new float[0];
        float[] result = new float[Math.min(128, values.length)];
        for (int i = 0; i < result.length; i++) {
            float v = values[i];
            result[i] = Float.isNaN(v) || Float.isInfinite(v) ? 0 : Math.max(waveform ? -1 : 0, Math.min(1, v));
        }
        return result;
    }
    static String effect(String value) {
        for (String mode : new String[] {"off", "spectrum", "mirror", "radial", "wave", "particles", "tunnel"})
            if (mode.equals(value)) return mode;
        return "off";
    }
}
