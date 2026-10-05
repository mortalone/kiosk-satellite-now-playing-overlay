// SPDX-License-Identifier: MIT
package me.jxl.kiosk.plugins.nowplayingoverlay;

import org.json.JSONArray;
import org.json.JSONObject;

final class PartyGuestConfig {
    private PartyGuestConfig() {}
    static String matchingInstance(JSONArray configs, String queue) {
        if (configs == null || queue == null || queue.isEmpty()) return "";
        String matched = "";
        for (int i = 0; i < configs.length(); i++) {
            JSONObject config = configs.optJSONObject(i);
            if (config == null || !"party".equals(config.optString("domain")) || !config.optBoolean("enabled", true)) continue;
            JSONObject values = config.optJSONObject("values");
            Object player = values == null ? null : values.opt("player");
            if (player instanceof JSONObject) player = ((JSONObject) player).opt("value");
            if (!PartyGuestLink.matches(queue, player)) continue;
            String id = config.optString("instance_id", "");
            if (id.isEmpty() || !matched.isEmpty()) return "";
            matched = id;
        }
        return matched;
    }
}
