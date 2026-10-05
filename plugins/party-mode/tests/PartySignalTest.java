package me.jxl.kiosk.plugins.partymode;

public final class PartySignalTest {
    public static void main(String[] args) {
        float[] input = new float[] {-2, 0.5f, 8, Float.NaN, Float.POSITIVE_INFINITY};
        float[] bands = PartySignal.bounded(input, false), wave = PartySignal.bounded(input, true);
        check(bands[0] == 0 && bands[1] == 0.5f && bands[2] == 1 && bands[3] == 0 && bands[4] == 0, "valid bands");
        check(wave[0] == -1 && wave[2] == 1, "bipolar waveform");
        check(PartySignal.bounded(new float[500], false).length == 128, "bounded frame");
        check(PartySignal.bounded(null, false).length == 0, "missing frame");
        check(PartySignal.effect("unknown").equals("off"), "unknown effect");
        check(PartySignal.effect("radial").equals("radial"), "known effect");
        System.out.println("Party signal checks passed");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
