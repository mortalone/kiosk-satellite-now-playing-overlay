package me.jxl.kiosk.plugins.partymode;

public final class PartyGuestLinkTest {
    public static void main(String[] args) {
        check(PartyGuestLink.matches("group", "group"), "same queue");
        check(!PartyGuestLink.matches("group", "other"), "different group never offers QR");
        check(!PartyGuestLink.matches("", ""), "unknown queue");
        check(!PartyGuestLink.matches("group", null), "disabled access");
        String local = "http://ma.local:8095/?join=test-code";
        check(PartyGuestLink.validated(local, "http://ma.local:8095").equals(local), "local URL");
        String remote = "https://app.music-assistant.io/?remote_id=test&join=guest-code";
        check(PartyGuestLink.validated(remote, "http://ma.local:8095").equals(remote), "remote URL retained");
        check(PartyGuestLink.validated("http://localhost:8095/?join=test", "http://192.168.0.18:8095").equals("http://192.168.0.18:8095/?join=test"), "legacy localhost fix");
        for (String bad : new String[] {"javascript:bad", "http://ma.local/", "http://ma.local/?join=", "http://user:pass@ma.local/?join=test", "http://ma.local/?join=test&auth_token=secret", "http://ma.local/?join=test#token"}) {
            check(PartyGuestLink.validated(bad, "http://ma.local:8095").isEmpty(), "reject unsafe URL");
        }
        System.out.println("Party guest link checks passed");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
