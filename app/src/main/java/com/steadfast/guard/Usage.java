package com.steadfast.guard;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/** Foreground-time accounting based on Android's usage events. */
final class Usage {
    private Usage() {}

    private static Map<String, Long> todayCache = new HashMap<>();
    private static long todayCacheTime;

    static long startOfDay(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    static synchronized long todayMs(Context ctx, String pkg) {
        long now = System.currentTimeMillis();
        if (now - todayCacheTime > 15000) {
            todayCache = forRange(ctx, startOfDay(now), now);
            todayCacheTime = now;
        }
        Long v = todayCache.get(pkg);
        return v == null ? 0 : v;
    }

    @SuppressWarnings("deprecation")
    static Map<String, Long> forRange(Context ctx, long start, long end) {
        Map<String, Long> total = new HashMap<>();
        if (!Guard.usageAccessGranted(ctx)) return total;
        UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return total;
        UsageEvents events = usm.queryEvents(start, end);
        Map<String, Long> resumed = new HashMap<>(); // key = package/class
        UsageEvents.Event e = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(e);
            String pkg = e.getPackageName();
            String key = pkg + "/" + e.getClassName();
            int type = e.getEventType();
            if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                resumed.put(key, e.getTimeStamp());
            } else if (type == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                Long st = resumed.remove(key);
                if (st != null) add(total, pkg, e.getTimeStamp() - st);
            }
        }
        // still in the foreground at the end of the range
        for (Map.Entry<String, Long> r : resumed.entrySet()) {
            String pkg = r.getKey().substring(0, r.getKey().indexOf('/'));
            long d = end - r.getValue();
            if (d > 0 && d < 6 * 3600000L) add(total, pkg, d);
        }
        return total;
    }

    private static void add(Map<String, Long> m, String pkg, long d) {
        if (d <= 0) return;
        Long v = m.get(pkg);
        m.put(pkg, (v == null ? 0 : v) + d);
    }
}
