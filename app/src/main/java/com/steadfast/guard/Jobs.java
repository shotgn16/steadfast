package com.steadfast.guard;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

/** Schedules the 15-minute background check (health, partner commands, alerts, daily report). */
final class Jobs {
    private Jobs() {}

    static final int JOB_ID = 4242;

    static void schedule(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        if (js.getPendingJob(JOB_ID) != null) return;
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(c, GuardJobService.class))
                .setPeriodic(15 * 60 * 1000L)
                .setPersisted(true)
                .build();
        try { js.schedule(job); } catch (Exception ignored) {}
    }

    static void runOnce(final Context c) {
        new Thread(new Runnable() {
            @Override public void run() { work(c.getApplicationContext()); }
        }).start();
    }

    static void work(Context c) {
        Config cfg = Config.get(c);
        if (Config.ROLE_PARTNER.equals(cfg.role)) {
            PartnerStore.pollAll(c);
            return;
        }
        if (!cfg.isProtected()) return;
        Health.check(c);
        Partner.pollCommands(c);
        Reports.maybeSendDaily(c);
        Partner.flush(c);
    }
}
