package me.jxl.kiosk.plugins.partymode;
import org.json.JSONArray;
import org.json.JSONObject;

public final class PartyGuestConfigTest {
    public static void main(String[] args) throws Exception {
        JSONObject party = config("party_1", "group", true);
        check(PartyGuestConfig.matchingInstance(new JSONArray().put(party), "group").equals("party_1"), "matching explicit queue");
        check(PartyGuestConfig.matchingInstance(new JSONArray().put(config("other", "other-room", true)).put(party), "group").equals("party_1"), "skip other room");
        check(PartyGuestConfig.matchingInstance(new JSONArray().put(party).put(config("party_2", "group", true)), "group").isEmpty(), "ambiguous instance cannot be changed");
        check(PartyGuestConfig.matchingInstance(new JSONArray().put(config("party_1", "__auto__", true)), "group").isEmpty(), "auto queue not changed");
        check(PartyGuestConfig.matchingInstance(new JSONArray().put(config("party_1", "group", false)), "group").isEmpty(), "disabled plugin not changed");
        check(PartyGuestConfig.matchingInstance(new JSONArray().put(party.put("domain", "spotify")), "group").isEmpty(), "other provider never changed");
        System.out.println("Party guest settings checks passed");
    }
    private static JSONObject config(String id, String player, boolean enabled) throws Exception {
        return new JSONObject().put("domain", "party").put("instance_id", id).put("enabled", enabled)
                .put("values", new JSONObject().put("player", new JSONObject().put("value", player)));
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
