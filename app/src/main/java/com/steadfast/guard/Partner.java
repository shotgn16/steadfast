package com.steadfast.guard;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Accountability on the protected device: privacy-preserving alerts to the partner (via the free ntfy.sh
 * push service) and signed remote commands from the partner.
 * Alerts never contain the website, search term or keyword — only what kind of thing happened.
 */
final class Partner {
    private Partner() {}

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static final Map<String, Long> lastAlert = new HashMap<>();

    static final String CMD_PAUSE = "PAUSE", CMD_RESUME = "RESUME", CMD_UNLOCK = "UNLOCK_NFC",
            CMD_LOCK = "LOCK_NFC", CMD_APPROVAL = "APPROVAL", CMD_REPORT = "REPORT";

    // ------------------------------------------------------------------ setup

    static void enable(Context ctx, Config c) {
        if (c.alertTopic.isEmpty()) c.alertTopic = "sf-" + Crypto.token(12);
        if (c.commandTopic.isEmpty()) c.commandTopic = "sf-" + Crypto.token(12);
        if (c.secret.isEmpty()) c.secret = Crypto.base32(Crypto.random(20));
        if (c.deviceName.isEmpty()) c.deviceName = android.os.Build.MODEL;
        c.partnerEnabled = true;
        c.save(ctx);
    }

    static void regenerate(Context ctx, Config c) {
        c.alertTopic = ""; c.commandTopic = ""; c.secret = ""; c.lastCommandId = "";
        enable(ctx, c);
    }

    static String pairingCode(Config c) {
        try {
            JSONObject o = new JSONObject();
            o.put("n", c.deviceName); o.put("s", c.server); o.put("a", c.alertTopic);
            o.put("c", c.commandTopic); o.put("k", c.secret);
            return "SF1:" + Base64.encodeToString(o.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP | Base64.URL_SAFE);
        } catch (JSONException e) {
            return "";
        }
    }

    static String currentCode(Config c) {
        return Crypto.totp(c.secret, System.currentTimeMillis());
    }

    // ------------------------------------------------------------------ alerts

    static void onEvent(Context ctx, String type, String detail) {
        Config c = Config.get(ctx);
        if (!c.partnerEnabled || !c.isProtected()) return;
        String body;
        String tags;
        long gap;
        boolean always = false;
        switch (type) {
            case EventLog.SITE:
                body = "Tried to open a website on the blocked list."; tags = "no_entry"; gap = 10 * 60000L; break;
            case EventLog.KEYWORD:
                body = "Content matching the keyword filter was blocked."; tags = "no_entry"; gap = 10 * 60000L; break;
            case EventLog.SEARCH:
                body = "A search matching the search filter was blocked."; tags = "mag"; gap = 10 * 60000L; break;
            case EventLog.TAMPER:
                // TAMPER details are fixed phrases written by Steadfast, never user content.
                body = detail; tags = "warning"; gap = 2 * 60000L; always = true; break;
            case EventLog.CHANGE:
                body = detail; tags = "gear"; gap = 0; always = true; break;
            default:
                return; // app blocks, limits and filtered requests go in the daily report only
        }
        if (!always && !c.instantAlerts) return;
        synchronized (lastAlert) {
            long now = System.currentTimeMillis();
            Long last = lastAlert.get(type + (always ? body : ""));
            if (last != null && now - last < gap) return;
            lastAlert.put(type + (always ? body : ""), now);
        }
        enqueue(ctx, c.alertTopic, "Steadfast - " + ascii(c.deviceName), body, tags,
                EventLog.TAMPER.equals(type) ? "high" : "default");
        flushAsync(ctx);
    }

    static void sendTest(Context ctx) {
        Config c = Config.get(ctx);
        enqueue(ctx, c.alertTopic, "Steadfast - " + ascii(c.deviceName),
                "Test message: accountability is connected.", "white_check_mark", "default");
        flushAsync(ctx);
    }

    static void sendRaw(Context ctx, String title, String body, String tags) {
        Config c = Config.get(ctx);
        enqueue(ctx, c.alertTopic, title, body, tags, "default");
        flushAsync(ctx);
    }

    private static String ascii(String s) {
        return s == null ? "" : s.replaceAll("[^\\x20-\\x7E]", "");
    }

    private static SharedPreferences outbox(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences("outbox", Context.MODE_PRIVATE);
    }

    private static void enqueue(Context ctx, String topic, String title, String body, String tags, String prio) {
        if (topic == null || topic.isEmpty()) return;
        synchronized (Partner.class) {
            try {
                JSONArray q = new JSONArray(outbox(ctx).getString("q", "[]"));
                JSONObject m = new JSONObject();
                m.put("topic", topic); m.put("title", title); m.put("body", body); m.put("tags", tags); m.put("prio", prio);
                m.put("t", System.currentTimeMillis());
                q.put(m);
                JSONArray trimmed = q;
                if (q.length() > 100) {
                    trimmed = new JSONArray();
                    for (int i = q.length() - 100; i < q.length(); i++) trimmed.put(q.get(i));
                }
                outbox(ctx).edit().putString("q", trimmed.toString()).commit();
            } catch (JSONException ignored) {}
        }
    }

    static void flushAsync(final Context ctx) {
        final Context app = ctx.getApplicationContext();
        EXEC.execute(new Runnable() {
            @Override public void run() { flush(app); }
        });
    }

    static void flushNow(final Context ctx) {
        flushAsync(ctx);
    }

    /** Sends queued alerts. Must be called off the main thread. */
    static void flush(Context ctx) {
        Config c = Config.get(ctx);
        JSONArray q;
        synchronized (Partner.class) {
            try { q = new JSONArray(outbox(ctx).getString("q", "[]")); } catch (JSONException e) { q = new JSONArray(); }
            outbox(ctx).edit().putString("q", "[]").commit();
        }
        if (q.length() == 0) return;
        JSONArray failed = new JSONArray();
        for (int i = 0; i < q.length(); i++) {
            JSONObject m = q.optJSONObject(i);
            if (m == null) continue;
            try {
                Map<String, String> h = new HashMap<>();
                h.put("Title", m.optString("title"));
                h.put("Tags", m.optString("tags"));
                h.put("Priority", m.optString("prio", "default"));
                int code = Net.post(Net.topicUrl(c.server, m.optString("topic")), m.optString("body"), h);
                if (code >= 500 || code == 429) failed.put(m);
            } catch (Exception e) {
                failed.put(m);
            }
        }
        if (failed.length() > 0) {
            synchronized (Partner.class) {
                try {
                    JSONArray now = new JSONArray(outbox(ctx).getString("q", "[]"));
                    for (int i = 0; i < now.length(); i++) failed.put(now.get(i));
                    outbox(ctx).edit().putString("q", failed.toString()).commit();
                } catch (JSONException ignored) {}
            }
        }
    }

    // ------------------------------------------------------------------ commands

    static String signedCommand(String secretB32, String cmd, String arg) throws JSONException {
        long ts = System.currentTimeMillis() / 1000L;
        String nonce = Crypto.token(8);
        String sig = Crypto.hmacHex(Crypto.unbase32(secretB32), cmd + "|" + arg + "|" + ts + "|" + nonce);
        JSONObject o = new JSONObject();
        o.put("cmd", cmd); o.put("arg", arg); o.put("ts", ts); o.put("n", nonce); o.put("sig", sig);
        return o.toString();
    }

    /** Polls the command topic for signed commands from the partner. Must be called off the main thread. */
    static void pollCommands(Context ctx) {
        Config c = Config.get(ctx);
        if (!c.partnerEnabled || !c.isProtected() || c.commandTopic.isEmpty()) return;
        String since = c.lastCommandId.isEmpty() ? "12h" : c.lastCommandId;
        String body;
        try {
            body = Net.get(Net.topicUrl(c.server, c.commandTopic) + "/json?poll=1&since=" + since);
        } catch (Exception e) {
            return;
        }
        boolean changed = false;
        for (String line : body.split("\n")) {
            if (line.trim().isEmpty()) continue;
            try {
                JSONObject o = new JSONObject(line);
                if (!"message".equals(o.optString("event"))) continue;
                c.lastCommandId = o.optString("id", c.lastCommandId);
                changed = true;
                handleCommand(ctx, c, o.optString("message"));
            } catch (Exception ignored) {}
        }
        if (changed) c.save(ctx);
    }

    private static void handleCommand(Context ctx, Config c, String msg) throws JSONException {
        JSONObject o = new JSONObject(msg);
        String cmd = o.optString("cmd"), arg = o.optString("arg"), n = o.optString("n"), sig = o.optString("sig");
        long ts = o.optLong("ts");
        long now = System.currentTimeMillis() / 1000L;
        if (Math.abs(now - ts) > 15 * 60) return;              // too old / clock far off
        if (c.seenNonces.contains(n)) return;                    // replay
        String expect = Crypto.hmacHex(Crypto.unbase32(c.secret), cmd + "|" + arg + "|" + ts + "|" + n);
        if (!Crypto.safeEquals(expect, sig)) return;             // not from the partner
        c.seenNonces.add(n);
        while (c.seenNonces.size() > 100) c.seenNonces.remove(0);

        String ack;
        switch (cmd) {
            case CMD_PAUSE: {
                int min = 30;
                try { min = Integer.parseInt(arg); } catch (NumberFormatException ignored) {}
                min = Math.max(1, Math.min(min, 24 * 60));
                c.pausedUntil = System.currentTimeMillis() + min * 60000L;
                ack = "Partner paused restrictions for " + min + " minutes.";
                break;
            }
            case CMD_RESUME:
                c.pausedUntil = 0;
                ack = "Partner switched restrictions back on.";
                break;
            case CMD_UNLOCK:
                c.nfcLocked = false;
                ack = "Partner unlocked the NFC lock.";
                break;
            case CMD_LOCK:
                c.nfcLocked = true;
                c.settingsWindowUntil = 0;
                ack = "Partner locked settings (NFC lock on).";
                break;
            case CMD_APPROVAL:
                c.partnerApproval = "on".equals(arg);
                ack = c.partnerApproval ? "Partner approval is now required to loosen settings."
                        : "Partner approval is no longer required to loosen settings.";
                break;
            case CMD_REPORT:
                c.save(ctx);
                Reports.sendNow(ctx);
                return;
            default:
                return;
        }
        c.save(ctx);
        EventLog.record(ctx, EventLog.CHANGE, ack);
        Notify.show(ctx, 7, "Steadfast", ack);
    }
}
