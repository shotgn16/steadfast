package com.steadfast.guard;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** All settings, persisted as JSON. Collections are concurrent so the VPN and accessibility threads can read safely. */
final class Config {

    static final String ROLE_PROTECTED = "protected";
    static final String ROLE_PARTNER = "partner";

    /** Rules for one app. */
    static final class AppRule {
        boolean blocked;
        int limitMin = -1;   // daily limit in minutes, -1 = none
        int startMin = -1;   // allowed window start (minutes after midnight), -1 = any time
        int endMin = -1;
        boolean essential;   // allowed during downtime and NFC lockdown

        boolean isEmpty() { return !blocked && limitMin < 0 && startMin < 0 && !essential; }

        AppRule copy() {
            AppRule r = new AppRule();
            r.blocked = blocked; r.limitMin = limitMin; r.startMin = startMin; r.endMin = endMin; r.essential = essential;
            return r;
        }

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("b", blocked); o.put("l", limitMin); o.put("s", startMin); o.put("e", endMin); o.put("x", essential);
            return o;
        }

        static AppRule from(JSONObject o) {
            AppRule r = new AppRule();
            r.blocked = o.optBoolean("b"); r.limitMin = o.optInt("l", -1);
            r.startMin = o.optInt("s", -1); r.endMin = o.optInt("e", -1); r.essential = o.optBoolean("x");
            return r;
        }

        /** True if {@code next} gives more freedom than this rule. */
        boolean looserThan(AppRule next) {
            if (blocked && !next.blocked) return true;
            if (limitMin >= 0 && (next.limitMin < 0 || next.limitMin > limitMin)) return true;
            if (startMin >= 0 && (next.startMin < 0 || windowLen(next) > windowLen(this))) return true;
            return !essential && next.essential;
        }

        private static int windowLen(AppRule r) {
            if (r.startMin < 0) return 24 * 60;
            int len = r.endMin - r.startMin;
            return len <= 0 ? len + 24 * 60 : len;
        }
    }

    // ---- role & setup
    String role = "";
    boolean setupComplete;
    String pinHash = "", pinSalt = "";
    String deviceName = "";

    // ---- rules
    final Map<String, AppRule> apps = new ConcurrentHashMap<>();
    final Set<String> blockedDomains = newSet();
    final Set<String> allowedDomains = newSet();
    final Set<String> keywords = newSet();
    final Set<String> searchTerms = newSet();
    int filterLevel = 2;        // 0 off, 1 malware, 2 family (adult), 3 strict family
    boolean safeSearch = true;
    boolean scanAllApps = true;
    boolean showVerses = true;
    boolean downtimeEnabled;
    int downtimeStart = 22 * 60, downtimeEnd = 7 * 60;

    // ---- NFC
    final Set<String> nfcTags = newSet();
    boolean nfcLocked;
    boolean nfcLockdown;

    // ---- accountability (protected device side)
    boolean partnerEnabled, partnerApproval, instantAlerts = true, shareAppNames;
    String server = "https://ntfy.sh", alertTopic = "", commandTopic = "", secret = "";
    String lastCommandId = "";
    final List<String> seenNonces = Collections.synchronizedList(new ArrayList<String>());
    String lastReportDay = "";
    String lastHealth = "";

    // ---- temporary state
    long pausedUntil;
    long settingsWindowUntil;

    volatile int version;

    private static Config instance;

    static Set<String> newSet() {
        return Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    }

    static synchronized Config get(Context c) {
        if (instance == null) {
            instance = new Config();
            SharedPreferences p = c.getApplicationContext().getSharedPreferences("config", Context.MODE_PRIVATE);
            String json = p.getString("json", null);
            if (json != null) {
                try { instance.load(new JSONObject(json)); } catch (JSONException ignored) {}
            }
        }
        return instance;
    }

    synchronized void save(Context c) {
        version++;
        try {
            c.getApplicationContext().getSharedPreferences("config", Context.MODE_PRIVATE)
                    .edit().putString("json", toJson().toString()).commit();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    /** Wipes everything (used when Steadfast is fully turned off). */
    static synchronized void reset(Context c) {
        c.getApplicationContext().getSharedPreferences("config", Context.MODE_PRIVATE).edit().clear().commit();
        instance = null;
    }

    boolean isPaused() { return System.currentTimeMillis() < pausedUntil; }

    boolean isProtected() { return setupComplete && ROLE_PROTECTED.equals(role); }

    /** Content and app rules are being enforced right now. */
    boolean rulesActive() { return isProtected() && !isPaused(); }

    /** Android settings for Steadfast are guarded right now. */
    boolean settingsGuarded() { return isProtected() && System.currentTimeMillis() > settingsWindowUntil; }

    // ---------------------------------------------------------------- JSON
    private JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("role", role); o.put("setup", setupComplete); o.put("pinHash", pinHash); o.put("pinSalt", pinSalt);
        o.put("deviceName", deviceName);
        JSONObject a = new JSONObject();
        for (Map.Entry<String, AppRule> e : apps.entrySet()) a.put(e.getKey(), e.getValue().toJson());
        o.put("apps", a);
        o.put("blockedDomains", arr(blockedDomains)); o.put("allowedDomains", arr(allowedDomains));
        o.put("keywords", arr(keywords)); o.put("searchTerms", arr(searchTerms));
        o.put("filterLevel", filterLevel); o.put("safeSearch", safeSearch); o.put("scanAllApps", scanAllApps);
        o.put("showVerses", showVerses);
        o.put("downtimeEnabled", downtimeEnabled); o.put("downtimeStart", downtimeStart); o.put("downtimeEnd", downtimeEnd);
        o.put("nfcTags", arr(nfcTags)); o.put("nfcLocked", nfcLocked); o.put("nfcLockdown", nfcLockdown);
        o.put("partnerEnabled", partnerEnabled); o.put("partnerApproval", partnerApproval);
        o.put("instantAlerts", instantAlerts); o.put("shareAppNames", shareAppNames);
        o.put("server", server); o.put("alertTopic", alertTopic); o.put("commandTopic", commandTopic); o.put("secret", secret);
        o.put("lastCommandId", lastCommandId);
        synchronized (seenNonces) { o.put("seenNonces", new JSONArray(seenNonces)); }
        o.put("lastReportDay", lastReportDay); o.put("lastHealth", lastHealth);
        o.put("pausedUntil", pausedUntil); o.put("settingsWindowUntil", settingsWindowUntil);
        return o;
    }

    private void load(JSONObject o) {
        role = o.optString("role", ""); setupComplete = o.optBoolean("setup");
        pinHash = o.optString("pinHash", ""); pinSalt = o.optString("pinSalt", "");
        deviceName = o.optString("deviceName", "");
        JSONObject a = o.optJSONObject("apps");
        if (a != null) {
            Iterator<String> it = a.keys();
            while (it.hasNext()) {
                String k = it.next();
                JSONObject r = a.optJSONObject(k);
                if (r != null) apps.put(k, AppRule.from(r));
            }
        }
        fill(blockedDomains, o.optJSONArray("blockedDomains")); fill(allowedDomains, o.optJSONArray("allowedDomains"));
        fill(keywords, o.optJSONArray("keywords")); fill(searchTerms, o.optJSONArray("searchTerms"));
        filterLevel = o.optInt("filterLevel", 2); safeSearch = o.optBoolean("safeSearch", true);
        scanAllApps = o.optBoolean("scanAllApps", true); showVerses = o.optBoolean("showVerses", true);
        downtimeEnabled = o.optBoolean("downtimeEnabled");
        downtimeStart = o.optInt("downtimeStart", 22 * 60); downtimeEnd = o.optInt("downtimeEnd", 7 * 60);
        fill(nfcTags, o.optJSONArray("nfcTags")); nfcLocked = o.optBoolean("nfcLocked"); nfcLockdown = o.optBoolean("nfcLockdown");
        partnerEnabled = o.optBoolean("partnerEnabled"); partnerApproval = o.optBoolean("partnerApproval");
        instantAlerts = o.optBoolean("instantAlerts", true); shareAppNames = o.optBoolean("shareAppNames");
        server = o.optString("server", "https://ntfy.sh"); alertTopic = o.optString("alertTopic", "");
        commandTopic = o.optString("commandTopic", ""); secret = o.optString("secret", "");
        lastCommandId = o.optString("lastCommandId", "");
        JSONArray n = o.optJSONArray("seenNonces");
        if (n != null) for (int i = 0; i < n.length(); i++) seenNonces.add(n.optString(i));
        lastReportDay = o.optString("lastReportDay", ""); lastHealth = o.optString("lastHealth", "");
        pausedUntil = o.optLong("pausedUntil"); settingsWindowUntil = o.optLong("settingsWindowUntil");
    }

    private static JSONArray arr(Set<String> s) {
        JSONArray a = new JSONArray();
        for (String x : s) a.put(x);
        return a;
    }

    private static void fill(Set<String> s, JSONArray a) {
        if (a == null) return;
        for (int i = 0; i < a.length(); i++) {
            String v = a.optString(i, "");
            if (!v.isEmpty()) s.add(v);
        }
    }
}
