package me.jxl.kiosk.plugins.nowplayingoverlay;

import org.json.JSONArray;
import org.json.JSONObject;

public final class PartyQueueModelTest {
    public static void main(String[] args) throws Exception {
        JSONObject queue = new JSONObject();
        queue.put("current_index", 4).put("current_item", item(4)).put("next_item", item(5));
        queue.put("elapsed_time", 42).put("state", "playing");
        JSONArray window = new JSONArray();
        for (int i = 2; i <= 6; i++) window.put(item(i));
        PartyQueueModel model = PartyQueueModel.parse(queue, window, "http://ma.test");
        check(PartyQueueModel.offset(queue) == 2, "two previous tracks");
        check(model.tracks.size() == 5, "bounded five-track window");
        check(model.tracks.get(2).current, "current item by queue item id");
        check(!model.tracks.get(1).current, "history not current");
        check(model.tracks.get(2).title.equals("Track 4"), "media title");
        check(model.tracks.get(2).artist.equals("Artist"), "artist parsing");
        check(model.tracks.get(2).artwork.equals("http://ma.test/imageproxy/image4?size=256&fmt=jpg"), "private proxy artwork");
        check(model.duration == 180 && model.elapsed == 42, "timeline");
        PartyQueueModel fallback = PartyQueueModel.parse(queue, null, "http://ma.test");
        check(fallback.tracks.size() == 2 && fallback.tracks.get(0).current, "current/next fallback without invented history");
        JSONArray wrong = new JSONArray().put(item(0)).put(item(1));
        check(PartyQueueModel.parse(queue, wrong, "http://ma.test").tracks.get(0).id.equals("id4"), "changed queue window falls back to snapshot");
        queue.put("current_index", 0).put("current_item", item(0));
        JSONArray beginning = new JSONArray(); for (int i = 0; i < 5; i++) beginning.put(item(i));
        check(PartyQueueModel.offset(queue) == 0, "start offset never negative");
        check(PartyQueueModel.parse(queue, beginning, "http://ma.test").tracks.size() == 3, "no more than two upcoming tracks at start");
        check(PartyQueueModel.parse(new JSONObject(), new JSONArray(), "http://ma.test").tracks.isEmpty(), "empty queue");
        queue.put("current_item", item(0).put("image", new JSONObject().put("path", "https://art.test/cover.jpg").put("remotely_accessible", true)));
        check(PartyQueueModel.parse(queue, null, "http://ma.test").tracks.get(0).artwork.equals("https://art.test/cover.jpg"), "public image URL");
        System.out.println("Party queue checks passed");
    }

    private static JSONObject item(int index) throws Exception {
        return new JSONObject().put("queue_item_id", "id" + index).put("duration", 180)
                .put("media_item", new JSONObject().put("name", "Track " + index)
                        .put("artists", new JSONArray().put(new JSONObject().put("name", "Artist"))))
                .put("image", new JSONObject().put("proxy_id", "image" + index));
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
