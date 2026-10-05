package me.jxl.kiosk.plugins.partymode;
import java.time.LocalTime;

public final class PartyActivationTest {
    public static void main(String[] args) {
        PartyActivation a = new PartyActivation();
        check(!a.visible(false, true), "manual default");
        a.show(); check(a.visible(false, true), "start action");
        check(!a.visible(false, false), "visibility blocks manual");
        check(a.visible(false, true), "requested view resumes after gate opens");
        a.hide(); check(!a.visible(true, true), "stop wins over automatic");
        check(!a.visible(true, false), "closed gate");
        check(a.visible(true, true), "new automatic eligibility");
        a.hide(); a.show(); check(a.visible(false, true), "HA can reopen with screen controls hidden");
        LocalTime night = LocalTime.of(23, 0), day = LocalTime.of(12, 0);
        check(PartyVisibility.allowed("Time between", "", "", "22:00-06:00", night), "overnight on");
        check(!PartyVisibility.allowed("Time between", "", "", "22:00-06:00", day), "overnight off");
        check(PartyVisibility.allowed("Active", "input_boolean.party", "on", "", day), "independent party entity");
        check(!PartyVisibility.allowed("Active", "", "", "", day), "missing configured gate stays hidden");
        check(!PartyVisibility.allowed("Inactive", "sensor.x", "unavailable", "", day), "unavailable not inactive");
        check(PartyVisibility.allowed("Numeric between", "sensor.lux", "12,5", "10..15", day), "numeric bounds");
        check(!PartyVisibility.allowed("Numeric above", "sensor.lux", "NaN", "10", day), "invalid number");
        System.out.println("Party activation and visibility checks passed");
    }
    private static void check(boolean ok, String text) { if (!ok) throw new AssertionError(text); }
}
