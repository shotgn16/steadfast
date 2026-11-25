package com.steadfast.guard;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Daily usage report for the accountability partner. Totals and counts only, unless app names are opted in. */
final class Reports {
    private Reports() {}

    static void maybeSendDaily(Context ctx) {
        Config c = Config.get(ctx);
        if (!c.partnerEnabled || !c.isProtected()) return;
        long now = System.currentTimeMillis();
        long todayStart = Usage.startOfDay(now);
        long yStart = Usage.startOfDay(todayStart - 3600000L);
        String yKey = EventLog.dayKey(yStart);
        if (!c.lastReportDay.isEmpty() && c.lastReportDay.compareTo(yKey) >= 0) return;
        if (now - todayStart < 5 * 60000L) return; // just after midnight, wait a bit
        String text = build(ctx, c, yStart, todayStart, "Daily report");
        c.lastReportDay = yKey;
        c.save(ctx);
        Partner.sendRaw(ctx, "Steadfast report - " + c.deviceName.replaceAll("[^\\x20-\\x7E]", ""), text, "bar_chart");
    }

    static void sendNow(Context ctx) {
        Config c = Config.get(ctx);
        long now = System.currentTimeMillis();
        String text = build(ctx, c, Usage.startOfDay(now), now, "Report so far today");
        Partner.sendRaw(ctx, "Steadfast report - " + c.deviceName.replaceAll("[^\\x20-\\x7E]", ""), text, "bar_chart");
    }

    static String build(Context ctx, Config c, long start, long end, String heading) {
        String day = EventLog.dayKey(start);
        Map<String, Long> usage = Usage.forRange(ctx, start, end);
        Set<String> exclude = Guard.launchers(ctx);
        exclude.add(ctx.getPackageName());
        exclude.add("com.android.systemui");
        long total = 0;
        List<Map.Entry<String, Long>> list = new ArrayList<>();
        for (Map.Entry<String, Long> e : usage.entrySet()) {
            if (exclude.contains(e.getKey())) continue;
            total += e.getValue();
            list.add(e);
        }
        Map<String, Integer> n = EventLog.countsFor(ctx, day);
        StringBuilder sb = new StringBuilder();
        sb.append(heading).append(" for ")
                .append(new SimpleDateFormat("EEE d MMM", Locale.UK).format(new Date(start))).append('\n');
        sb.append("Screen time: ").append(Guard.usageAccessGranted(ctx) ? Guard.duration(total) : "unknown (usage access off)").append('\n');
        int site = get(n, EventLog.SITE), kw = get(n, EventLog.KEYWORD), se = get(n, EventLog.SEARCH), dns = get(n, EventLog.DNS);
        sb.append("Blocked: ").append(site).append(" website attempts, ").append(se).append(" searches, ")
                .append(kw).append(" keyword hits, ").append(dns).append(" filtered web requests\n");
        sb.append("App rules: ").append(get(n, EventLog.APP)).append(" blocked-app opens, ")
                .append(get(n, EventLog.LIMIT)).append(" time-limit blocks, ")
                .append(get(n, EventLog.DOWNTIME) + get(n, EventLog.LOCKDOWN)).append(" downtime/lockdown blocks\n");
        int tamper = get(n, EventLog.TAMPER), change = get(n, EventLog.CHANGE);
        sb.append("Protection: ").append(tamper == 0 ? "no tamper alerts" : tamper + " tamper alert(s)")
                .append(", ").append(change).append(" settings change(s)\n");
        sb.append("Status now: ").append(Health.allGood(ctx) ? "all protections on" : "SOME PROTECTION IS OFF");
        if (c.isPaused()) sb.append(" (paused)");
        if (c.shareAppNames && !list.isEmpty()) {
            Collections.sort(list, new Comparator<Map.Entry<String, Long>>() {
                @Override public int compare(Map.Entry<String, Long> a, Map.Entry<String, Long> b) {
                    return Long.compare(b.getValue(), a.getValue());
                }
            });
            sb.append("\nTop apps: ");
            for (int i = 0; i < Math.min(5, list.size()); i++) {
                if (i > 0) sb.append(", ");
                sb.append(Guard.appLabel(ctx, list.get(i).getKey())).append(' ').append(Guard.duration(list.get(i).getValue()));
            }
        }
        return sb.toString();
    }

    private static int get(Map<String, Integer> m, String k) {
        Integer v = m.get(k);
        return v == null ? 0 : v;
    }
}
