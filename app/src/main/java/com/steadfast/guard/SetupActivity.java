package com.steadfast.guard;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** First-run wizard: PIN and the Android permissions Steadfast needs. */
public class SetupActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        final Config c = Config.get(this);
        Ui.Screen s = Ui.screen(this, "Set up protection", false);
        Ui.Palette p = s.p;
        LinearLayout v = s.content;

        boolean pin = !c.pinHash.isEmpty();
        boolean usage = Guard.usageAccessGranted(this);
        boolean acc = Guard.accessibilityEnabled(this);
        boolean vpn = Guard.vpnPermitted(this);
        boolean admin = Guard.adminActive(this);
        boolean notif = Build.VERSION.SDK_INT < 33
                || checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED;
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        boolean battery = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        boolean[] done = {pin, usage, acc, vpn, admin, notif, battery};
        int count = 0;
        for (boolean d : done) if (d) count++;

        // progress
        LinearLayout prog = Ui.paddedCard(v);
        LinearLayout pr = new LinearLayout(this);
        pr.setGravity(Gravity.CENTER_VERTICAL);
        pr.addView(Ui.text(this, count + " of 7 done", 17, p.text, Typeface.DEFAULT_BOLD), new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        pr.addView(Ui.text(this, "Steps 1, 3 & 4 required", 13, p.muted, null));
        prog.addView(pr);
        FrameLayout bar = new FrameLayout(this);
        bar.setBackground(Ui.round(this, p.surface2, 4));
        View fill = new View(this);
        fill.setBackground(Ui.round(this, p.primary, 4));
        LinearLayout.LayoutParams bl = Ui.lp(Ui.MATCH, Ui.dp(this, 8));
        bl.topMargin = Ui.dp(this, 12);
        prog.addView(bar, bl);
        final int pct = count;
        bar.addView(fill, new FrameLayout.LayoutParams(0, Ui.MATCH));
        final FrameLayout barRef = bar;
        final View fillRef = fill;
        bar.post(new Runnable() {
            @Override public void run() {
                FrameLayout.LayoutParams fl = (FrameLayout.LayoutParams) fillRef.getLayoutParams();
                fl.width = barRef.getWidth() * pct / 7;
                fillRef.setLayoutParams(fl);
            }
        });

        step(v, 1, "Create your master PIN", pin, "Needed to change any setting. Ideally your accountability partner chooses it.",
                "Create PIN", new Runnable() { @Override public void run() { createPin(); } });

        step(v, 2, "Allow usage access", usage, "Lets Steadfast measure how long you use each app, for time limits.",
                "Open usage access", new Runnable() {
                    @Override public void run() { open(Settings.ACTION_USAGE_ACCESS_SETTINGS); }
                });

        LinearLayout accCard = step(v, 3, "Turn on app & content protection", acc,
                "Accessibility → Steadfast → On. This blocks apps, keywords and searches, all on this phone.",
                "Open accessibility settings", new Runnable() {
                    @Override public void run() { open(Settings.ACTION_ACCESSIBILITY_SETTINGS); }
                });
        if (!acc) {
            Ui.banner(accCard, R.drawable.ic_warning, "Says “Restricted setting”?",
                    "Open App info, tap ⋮ in the top-right corner, choose “Allow restricted settings”, then try again.", 3,
                    "Open App info", new View.OnClickListener() {
                        @Override public void onClick(View x) {
                            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
                        }
                    });
        }

        step(v, 4, "Start the web filter", vpn,
                "An on-phone filter for website lookups. You'll see a key icon. Your browsing doesn't go through any server of ours.",
                "Start web filter", new Runnable() {
                    @Override public void run() {
                        Intent prep = VpnService.prepare(SetupActivity.this);
                        if (prep != null) startActivityForResult(prep, 1);
                        else { Guard.startVpn(SetupActivity.this); render(); }
                    }
                });

        step(v, 5, "Turn on uninstall protection", admin,
                "Steadfast can't be removed without switching this off first, and that alerts your partner.",
                "Turn on", new Runnable() { @Override public void run() { MainActivity.requestAdmin(SetupActivity.this); } });

        step(v, 6, "Allow notifications", notif, "So you can see when your partner changes something.",
                "Allow", new Runnable() {
                    @Override public void run() {
                        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 2);
                    }
                });

        step(v, 7, "Keep Steadfast running", battery, "Stops Android putting Steadfast to sleep.",
                "Allow background running", new Runnable() {
                    @Override public void run() {
                        try {
                            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:" + getPackageName())));
                        } catch (Exception e) {
                            open(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                        }
                    }
                });

        Ui.banner(v, R.drawable.ic_key, "Recommended: Always-on VPN",
                "In VPN settings, tap ⚙ next to Steadfast and turn on “Always-on VPN”. Leave “Block connections without VPN” off.",
                0, "Open VPN settings", new View.OnClickListener() {
                    @Override public void onClick(View x) { open(Settings.ACTION_VPN_SETTINGS); }
                });

        final boolean ready = pin && acc && vpn;
        Ui.button(v, ready ? "Finish setup" : "Finish setup (complete steps 1, 3 & 4)", new View.OnClickListener() {
            @Override public void onClick(View x) {
                if (!ready) { Ui.toast(SetupActivity.this, "Please finish steps 1, 3 and 4 first."); return; }
                c.setupComplete = true;
                c.setupAt = System.currentTimeMillis();
                if (c.deviceName.isEmpty()) c.deviceName = Build.MODEL;
                c.lastReportDay = EventLog.dayKey(System.currentTimeMillis() - 24 * 3600000L);
                if (c.keywords.isEmpty()) for (String k : ListEditorActivity.STARTER_KEYWORDS) c.keywords.add(k);
                if (c.searchTerms.isEmpty()) for (String k : ListEditorActivity.STARTER_SEARCH) c.searchTerms.add(k);
                c.save(SetupActivity.this);
                Guard.startVpn(SetupActivity.this);
                Jobs.schedule(SetupActivity.this);
                startActivity(new Intent(SetupActivity.this, MainActivity.class));
                finish();
            }
        });
        if (c.pinHash.isEmpty()) {
            TextView back = Ui.text(this, "Back: I'm an accountability partner instead", 14, p.primary, Ui.medium());
            back.setGravity(Gravity.CENTER);
            back.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
            back.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View x) {
                    c.role = "";
                    c.save(SetupActivity.this);
                    startActivity(new Intent(SetupActivity.this, MainActivity.class));
                    finish();
                }
            });
            v.addView(back, Ui.lp(Ui.MATCH, Ui.WRAP));
        }
    }

    private LinearLayout step(LinearLayout v, int n, String title, boolean done, String explain, String action, final Runnable run) {
        Ui.Palette p = Ui.p(this);
        LinearLayout card = Ui.paddedCard(v);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        if (done) {
            head.addView(Ui.iconBadge(this, R.drawable.ic_check, p.ok, p.okContainer, 36));
        } else {
            TextView num = Ui.text(this, String.valueOf(n), 15, p.dark ? p.onPrimaryContainer : p.primary, Typeface.DEFAULT_BOLD);
            num.setGravity(Gravity.CENTER);
            num.setBackground(Ui.oval(p.primaryContainer));
            head.addView(num, new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36)));
        }
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(this, 14), 0, 0, 0);
        col.addView(Ui.text(this, title, 16, done ? p.muted : p.text, Ui.medium()));
        if (done) col.addView(Ui.text(this, "Done", 13, p.ok, null));
        head.addView(col, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        card.addView(head);
        if (!done) {
            TextView e = Ui.text(this, explain, 14, p.muted, null);
            e.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 4));
            card.addView(e);
            Ui.button(card, action, new View.OnClickListener() {
                @Override public void onClick(View x) { run.run(); }
            });
        }
        return card;
    }

    private void open(String action) {
        try { startActivity(new Intent(action)); } catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
    }

    private void createPin() {
        Ui.pin(this, "Create master PIN", "4–10 digits. Don't reuse a code you use elsewhere.", new Ui.TextCallback() {
            @Override public void on(final String p1) {
                if (p1.length() < 4 || p1.length() > 10) { Ui.toast(SetupActivity.this, "Use 4–10 digits"); return; }
                Ui.pin(SetupActivity.this, "Repeat PIN", null, new Ui.TextCallback() {
                    @Override public void on(String p2) {
                        if (!p1.equals(p2)) { Ui.toast(SetupActivity.this, "PINs didn't match"); return; }
                        Auth.setPin(SetupActivity.this, p1);
                        render();
                    }
                });
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK) Guard.startVpn(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        render();
    }
}
