// SPDX-License-Identifier: MIT
package me.jxl.kiosk.plugins.partymode;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class PartyQueueModel {
    static final class Track {
        final String id, title, artist, artwork;
        final boolean current;
        Track(String id, String title, String artist, String artwork, boolean current) {
            this.id = id; this.title = title; this.artist = artist;
            this.artwork = artwork; this.current = current;
        }
    }
    final List<Track> tracks;
    final double elapsed, duration;
    final String state;
    PartyQueueModel(List<Track> tracks, double elapsed, double duration, String state) {
        this.tracks = Collections.unmodifiableList(new ArrayList<>(tracks));
        this.elapsed = elapsed; this.duration = duration; this.state = state;
    }

    static int offset(JSONObject queue) {
        return Math.max(0, queue.optInt("current_index", 0) - 2);
    }

    static PartyQueueModel parse(JSONObject queue, JSONArray items, String base) {
        List<Track> tracks = new ArrayList<>();
        JSONObject current = queue.optJSONObject("current_item");
        String currentId = text(current, "queue_item_id");
        int currentIndex = queue.optInt("current_index", 0);
        int start = offset(queue);
        boolean found = false;
        if (items != null) for (int i = 0; i < items.length() && i < 5; i++) {
            if (start + i > currentIndex + 2) break;
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            boolean selected = !currentId.isEmpty()
                    ? currentId.equals(text(item, "queue_item_id")) : start + i == currentIndex;
            found |= selected;
            tracks.add(track(item, base, selected));
        }
        // Never invent past tracks or guess another queue when item access fails.
        if (!found) {
            tracks.clear();
            if (current != null) tracks.add(track(current, base, true));
            JSONObject next = queue.optJSONObject("next_item");
            if (next != null) tracks.add(track(next, base, false));
        }
        JSONObject media = current == null ? null : current.optJSONObject("media_item");
        double duration = current == null ? 0 : current.optDouble("duration", 0);
        if (duration <= 0 && media != null) duration = media.optDouble("duration", 0);
        return new PartyQueueModel(tracks, Math.max(0, queue.optDouble("elapsed_time", 0)),
                Math.max(0, duration), text(queue, "state"));
    }

    private static Track track(JSONObject item, String base, boolean current) {
        JSONObject media = item.optJSONObject("media_item");
        String title = text(media, "name");
        if (title.isEmpty()) title = text(item, "name");
        String artist = text(item, "artist");
        JSONArray artists = media == null ? null : media.optJSONArray("artists");
        if (artist.isEmpty() && artists != null) {
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < artists.length(); i++) {
                String name = text(artists.optJSONObject(i), "name");
                if (!name.isEmpty()) { if (names.length() > 0) names.append(", "); names.append(name); }
            }
            artist = names.toString();
        }
        Object image = item.opt("image");
        if (!(image instanceof JSONObject) && media != null) {
            JSONObject metadata = media.optJSONObject("metadata");
            JSONArray images = metadata == null ? null : metadata.optJSONArray("images");
            if (images != null) for (int i = 0; i < images.length(); i++) {
                JSONObject candidate = images.optJSONObject(i);
                if (candidate != null && (image == null || "thumb".equals(text(candidate, "type")))) image = candidate;
                if (candidate != null && "thumb".equals(text(candidate, "type"))) break;
            }
        }
        String artwork = "";
        if (image instanceof JSONObject) {
            JSONObject object = (JSONObject) image;
            String path = text(object, "path");
            String proxy = text(object, "proxy_id");
            if (object.optBoolean("remotely_accessible", false) &&
                    (path.startsWith("https://") || path.startsWith("http://"))) artwork = path;
            else if (!proxy.isEmpty()) artwork = base + "/imageproxy/" + proxy + "?size=256&fmt=jpg";
        }
        return new Track(text(item, "queue_item_id"), title.isEmpty() ? "Ukendt titel" : title, artist, artwork, current);
    }

    private static String text(JSONObject object, String key) {
        return object == null || object.isNull(key) ? "" : object.optString(key, "").trim();
    }
}
