package com.steadfast.guard;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Partner mode: the devices this person is an accountability partner for. */
final class PartnerStore {
    private PartnerStore() {}

    static final class Device {
        String name = "", server = "https://ntfy.sh", alertTopic = "", commandTopic = "", secret = "", lastId = "";
        long lastMsgTime;
        JSONArray recent = new JSONArray();

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("n", name); o.put("s", server); o.put("a", alertTopic); o.put("c", commandTopic); o.put("k", secret);
            o.put("lastId", lastId); o.put("lastMsg", lastMsgTime); o.put("recent", recent);
            return o;
        }

        static Device from(JSONObject o) {
            Device d = new Device();
            d.name = o.optString("n"); d.server = o.optString("s", "https://ntfy.sh");
            d.alertTopic = o.optString("a"); d.commandTopic = o.optString("c"); d.secret = o.optString("k");
            d.lastId = o.optString("lastId"); d.lastMsgTime = o.optLong("lastMsg");
            JSONArray r = o.optJSONArray("recent");
            if (r != null) d.recent = r;
            return d;
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("partner_devices", Context.MODE_PRIVATE);
    }

    static synchronized List<Device> load(Context c) {
        List<Device> list = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("list", "[]"));
            for (int i = 0; i < a.length(); i++) list.add(Device.from(a.getJSONObject(i)));
        } catch (JSONException ignored) {}
        return list;
    }

    static synchronized void save(Context c, List<Device> list) {
        try {
            JSONArray a = new JSONArray();
            for (Device d : list) a.put(d.toJson());
            prefs(c).edit().putString("list", a.toString()).commit();
        } catch (JSONException ignored) {}
    }

    static Device parse(String code) throws Exception {
        String s = code.trim();
        int i = s.indexOf("SF1:");
        if (i < 0) throw new IllegalArgumentException("Not a Steadfast pairing code");
        s = s.substring(i + 4).split("\\s")[0];
        JSONObject o = new JSONObject(new String(Base64.decode(s, Base64.URL_SAFE | Base64.NO_WRAP), StandardCharsets.UTF_8));
        Device d = Device.from(o);
        if (d.alertTopic.isEmpty() || d.commandTopic.isEmpty() || d.secret.isEmpty()) throw new IllegalArgumentException("Incomplete code");
        return d;
    }

    static void sendCommand(Device d, String cmd, String arg) throws Exception {
        Net.post(Net.topicUrl(d.server, d.commandTopic), Partner.signedCommand(d.secret, cmd, arg), null);
    }

    /** Fetches new alerts for every device; shows notifications for new ones. Off the main thread. */
    static void pollAll(Context c) {
        List<Device> list = load(c);
        boolean changed = false;
        int notifId = 100;
        for (Device d : list) {
            notifId++;
            try {
                String since = d.lastId.isEmpty() ? "24h" : d.lastId;
                String body = Net.get(Net.topicUrl(d.server, d.alertTopic) + "/json?poll=1&since=" + since);
                for (String line : body.split("\n")) {
                    if (line.trim().isEmpty()) continue;
                    JSONObject o = new JSONObject(line);
                    if (!"message".equals(o.optString("event"))) continue;
                    d.lastId = o.optString("id", d.lastId);
                    JSONObject m = new JSONObject();
                    m.put("t", o.optLong("time") * 1000L);
                    m.put("title", o.optString("title", "Steadfast"));
                    m.put("msg", o.optString("message"));
                    d.recent.put(m);
                    d.lastMsgTime = Math.max(d.lastMsgTime, o.optLong("time") * 1000L);
                    changed = true;
                    Notify.show(c, notifId++, o.optString("title", "Steadfast"), o.optString("message"));
                }
                if (d.recent.length() > 60) {
                    JSONArray t = new JSONArray();
                    for (int i = d.recent.length() - 60; i < d.recent.length(); i++) t.put(d.recent.get(i));
                    d.recent = t;
                }
            } catch (Exception ignored) {}
        }
        if (changed) save(c, list);
    }
}
