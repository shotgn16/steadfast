package com.steadfast.guard;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Local event log. Details (domains, search terms) stay on this device only.
 * Your accountability partner only ever gets the event TYPE and counts.
 */
final class EventLog {
    private EventLog() {}

    static final String SITE = "site";         // blocked website opened in a browser
    static final String KEYWORD = "keyword";   // on-screen content matched keyword filter
    static final String SEARCH = "search";     // search / typed text matched search filter
    static final String APP = "app";           // blocked app opened
    static final String LIMIT = "limit";       // time limit / allowed hours
    static final String DOWNTIME = "downtime";
    static final String LOCKDOWN = "lockdown";
    static final String DNS = "dns";           // filtered web request (background, counted only)
    static final String TAMPER = "tamper";     // attempts to get around protection
    static final String CHANGE = "change";     // settings loosened, paused, etc.

    private static final int MAX_LOG = 300;

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("events", Context.MODE_PRIVATE);
    }

    static String dayKey(long t) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(new Date(t));
    }

    static void record(Context ctx, String type, String detail) {
        synchronized (EventLog.class) {
            SharedPreferences p = prefs(ctx);
            long now = System.currentTimeMillis();
            try {
                JSONObject counters = new JSONObject(p.getString("counters", "{}"));
                String day = dayKey(now);
                JSONObject d = counters.optJSONObject(day);
                if (d == null) d = new JSONObject();
                d.put(type, d.optInt(type) + 1);
                counters.put(day, d);
                // keep 14 days
                if (counters.length() > 14) {
                    String oldest = null;
                    Iterator<String> it = counters.keys();
                    while (it.hasNext()) {
                        String k = it.next();
                        if (oldest == null || k.compareTo(oldest) < 0) oldest = k;
                    }
                    if (oldest != null) counters.remove(oldest);
                }
                SharedPreferences.Editor ed = p.edit().putString("counters", counters.toString());
                if (!DNS.equals(type)) {
                    JSONArray log = new JSONArray(p.getString("log", "[]"));
                    JSONObject e = new JSONObject();
                    e.put("t", now);
                    e.put("type", type);
                    e.put("d", detail == null ? "" : detail);
                    log.put(e);
                    JSONArray trimmed = log;
                    if (log.length() > MAX_LOG) {
                        trimmed = new JSONArray();
                        for (int i = log.length() - MAX_LOG; i < log.length(); i++) trimmed.put(log.get(i));
                    }
                    ed.putString("log", trimmed.toString());
                }
                ed.apply();
            } catch (JSONException ignored) {}
        }
        Partner.onEvent(ctx, type, detail);
    }

    static Map<String, Integer> countsFor(Context ctx, String day) {
        Map<String, Integer> m = new HashMap<>();
        synchronized (EventLog.class) {
            try {
                JSONObject counters = new JSONObject(prefs(ctx).getString("counters", "{}"));
                JSONObject d = counters.optJSONObject(day);
                if (d != null) {
                    Iterator<String> it = d.keys();
                    while (it.hasNext()) {
                        String k = it.next();
                        m.put(k, d.optInt(k));
                    }
                }
            } catch (JSONException ignored) {}
        }
        return m;
    }

    static JSONArray recent(Context ctx) {
        synchronized (EventLog.class) {
            try {
                return new JSONArray(prefs(ctx).getString("log", "[]"));
            } catch (JSONException e) {
                return new JSONArray();
            }
        }
    }

    static void clear(Context ctx) {
        synchronized (EventLog.class) {
            prefs(ctx).edit().clear().apply();
        }
    }

    static String label(String type) {
        switch (type) {
            case SITE: return "Blocked website";
            case KEYWORD: return "Keyword filter";
            case SEARCH: return "Search filter";
            case APP: return "Blocked app";
            case LIMIT: return "Time limit";
            case DOWNTIME: return "Downtime";
            case LOCKDOWN: return "Lockdown";
            case DNS: return "Filtered request";
            case TAMPER: return "Protection alert";
            case CHANGE: return "Settings change";
            default: return type;
        }
    }
}
