package com.steadfast.guard;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TimePicker;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {

    private static final int TAB_HOME = 0, TAB_RULES = 1, TAB_WEB = 2, TAB_PARTNER = 3, TAB_SETTINGS = 4;
    private static int tab = TAB_HOME;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    @Override
    public void onBackPressed() {
        if (Auth.sessionOpen() && tab != TAB_HOME) { tab = TAB_HOME; render(); return; }
        super.onBackPressed();
    }

    private void render() {
        Config c = Config.get(this);
        if (c.role.isEmpty()) { renderWelcome(); return; }
        if (Config.ROLE_PARTNER.equals(c.role)) {
            startActivity(new Intent(this, PartnerActivity.class));
            finish();
            return;
        }
        if (!c.setupComplete) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }
        if (!Auth.sessionOpen()) renderLocked(); else renderApp();
    }

    // ================================================================== welcome

    private void renderWelcome() {
        Ui.Screen s = Ui.screen(this, "", false);
        Ui.Palette p = s.p;
        LinearLayout v = s.content;
        v.setGravity(Gravity.CENTER_HORIZONTAL);
        space(v, 40);
        FrameLayout badge = Ui.iconBadge(this, R.drawable.ic_shield_check, p.dark ? p.onPrimaryContainer : p.primary, p.primaryContainer, 96);
        v.addView(badge);
        space(v, 24);
        TextView t = Ui.text(this, "Steadfast", 34, p.text, Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        v.addView(t, Ui.lp(Ui.MATCH, Ui.WRAP));
        TextView sub = Ui.text(this, "Guard your eyes and your time, with a friend walking alongside you.", 16, p.muted, null);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(Ui.dp(this, 20), Ui.dp(this, 10), Ui.dp(this, 20), Ui.dp(this, 28));
        v.addView(sub, Ui.lp(Ui.MATCH, Ui.WRAP));

        LinearLayout card = Ui.card(v);
        Ui.row(card, R.drawable.ic_shield, "Protect this phone", "Limits, website & keyword blocking, NFC lock", Ui.T_CHEVRON,
                new View.OnClickListener() {
                    @Override public void onClick(View x) { setRole(Config.ROLE_PROTECTED); }
                });
        Ui.divider(card);
        Ui.row(card, R.drawable.ic_people, "I'm an accountability partner", "Get alerts, see reports, approve changes", Ui.T_CHEVRON,
                new View.OnClickListener() {
                    @Override public void onClick(View x) { setRole(Config.ROLE_PARTNER); }
                });
        space(v, 8);
        TextView note = Ui.text(this, "Your partner learns when a line is crossed, never what you searched or watched.", 13, p.faint, null);
        note.setGravity(Gravity.CENTER);
        note.setPadding(Ui.dp(this, 24), 0, Ui.dp(this, 24), 0);
        v.addView(note, Ui.lp(Ui.MATCH, Ui.WRAP));
    }

    private void setRole(String role) {
        Config c = Config.get(this);
        c.role = role;
        c.save(this);
        render();
    }

    // ================================================================== locked (PIN pad)

    private void renderLocked() {
        final Config c = Config.get(this);
        final Ui.Screen s = Ui.screen(this, "", false);
        Ui.Palette p = s.p;
        LinearLayout v = s.content;
        v.setGravity(Gravity.CENTER_HORIZONTAL);
        space(v, 12);
        int tone = statusTone(c);
        int fg = tone == 1 ? p.ok : tone == 2 ? p.danger : p.warn;
        int bg = tone == 1 ? p.okContainer : tone == 2 ? p.dangerContainer : p.warnContainer;
        v.addView(Ui.iconBadge(this, tone == 1 ? R.drawable.ic_shield_check : R.drawable.ic_shield_alert, fg, bg, 76));
        space(v, 14);
        TextView t = Ui.text(this, statusTitle(c), 22, p.text, Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        v.addView(t, Ui.lp(Ui.MATCH, Ui.WRAP));
        TextView sub = Ui.text(this, "Enter your master PIN to change settings", 14, p.muted, null);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 26));
        v.addView(sub, Ui.lp(Ui.MATCH, Ui.WRAP));

        final Ui.PinPad[] holder = new Ui.PinPad[1];
        holder[0] = Ui.pinPad(v, 10, new Ui.TextCallback() {
            @Override public void on(String pin) {
                if (Auth.checkPin(MainActivity.this, pin)) { tab = TAB_HOME; render(); }
                else { holder[0].clear(MainActivity.this); holder[0].shake(); }
            }
        });
        space(v, 12);
        if (!c.nfcTags.isEmpty()) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(Ui.dp(this, 24), 0, Ui.dp(this, 24), 0);
            Ui.tonalButton(row, c.nfcLocked ? "Unlock with NFC tag" : "Lock with NFC tag", new View.OnClickListener() {
                @Override public void onClick(View x) { startActivity(new Intent(MainActivity.this, NfcActivity.class)); }
            });
            v.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP));
        }
    }

    // ================================================================== app shell

    private void renderApp() {
        String[] titles = {"Steadfast", "Rules", "Web & content", "Accountability", "Settings"};
        Ui.Screen s = Ui.screen(this, titles[tab], false);
        Ui.headerAction(s, R.drawable.ic_sliders, "Settings", new View.OnClickListener() {
            @Override public void onClick(View v) { tab = TAB_SETTINGS; render(); }
        });
        Ui.headerAction(s, R.drawable.ic_lock, "Lock settings", new View.OnClickListener() {
            @Override public void onClick(View v) { Auth.endSession(); tab = TAB_HOME; render(); }
        });
        bottomNav(s);
        switch (tab) {
            case TAB_RULES: rules(s); break;
            case TAB_WEB: web(s); break;
            case TAB_PARTNER: partner(s); break;
            case TAB_SETTINGS: settings(s); break;
            default: home(s);
        }
    }

    private void bottomNav(Ui.Screen s) {
        Ui.Palette p = s.p;
        View line = new View(this);
        line.setBackgroundColor(p.outline);
        s.bottom.addView(line, Ui.lp(Ui.MATCH, Math.max(1, Ui.dp(this, 1) / 2)));
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(p.surface);
        bar.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 10));
        int[] icons = {R.drawable.ic_home, R.drawable.ic_apps, R.drawable.ic_globe, R.drawable.ic_people};
        String[] labels = {"Home", "Rules", "Web", "Partner"};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            boolean sel = tab == i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            FrameLayout pill = new FrameLayout(this);
            if (sel) pill.setBackground(Ui.round(this, p.primaryContainer, 16));
            ImageView iv = new ImageView(this);
            iv.setImageResource(icons[i]);
            iv.setImageTintList(ColorStateList.valueOf(sel ? (p.dark ? p.onPrimaryContainer : p.primary) : p.muted));
            FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22));
            il.gravity = Gravity.CENTER;
            pill.addView(iv, il);
            item.addView(pill, new LinearLayout.LayoutParams(Ui.dp(this, 60), Ui.dp(this, 32)));
            TextView lbl = Ui.text(this, labels[i], 12, sel ? p.text : p.muted, sel ? Ui.medium() : null);
            lbl.setPadding(0, Ui.dp(this, 4), 0, 0);
            item.addView(lbl);
            item.setClickable(true);
            item.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { tab = idx; render(); }
            });
            bar.addView(item, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        }
        s.bottom.addView(bar, Ui.lp(Ui.MATCH, Ui.WRAP));
    }

    // ================================================================== Home

    private int statusTone(Config c) {
        if (!healthy()) return 2;
        if (c.isPaused()) return 3;
        return 1;
    }

    private String statusTitle(Config c) {
        if (!healthy()) return "Protection needs attention";
        if (c.isPaused()) return "Restrictions paused";
        return "You're protected";
    }

    private boolean healthy() {
        return Guard.accessibilityEnabled(this) && Guard.vpnPermitted(this) && FilterVpnService.running;
    }

    private void home(Ui.Screen s) {
        final Config c = Config.get(this);
        Ui.Palette p = s.p;
        LinearLayout v = s.content;

        // ---- hero
        int tone = statusTone(c);
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        int heroBg = tone == 1 ? p.okContainer : tone == 2 ? p.dangerContainer : p.warnContainer;
        int heroFg = tone == 1 ? p.ok : tone == 2 ? p.danger : p.warn;
        hero.setBackground(Ui.round(this, heroBg, 24));
        hero.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 18));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(Ui.iconBadge(this, tone == 1 ? R.drawable.ic_shield_check : R.drawable.ic_shield_alert,
                p.dark ? heroBg : Color.WHITE, heroFg, 56));
        LinearLayout tt = new LinearLayout(this);
        tt.setOrientation(LinearLayout.VERTICAL);
        tt.setPadding(Ui.dp(this, 16), 0, 0, 0);
        tt.addView(Ui.text(this, statusTitle(c), 21, p.text, Typeface.DEFAULT_BOLD));
        String sub;
        if (c.isPaused()) sub = "Back on at " + new SimpleDateFormat("HH:mm", Locale.UK).format(new Date(c.pausedUntil));
        else if (tone == 2) sub = "Something was switched off. Fix it below.";
        else if (c.setupAt > 0) {
            long days = (System.currentTimeMillis() - c.setupAt) / 86400000L;
            sub = days < 1 ? "Protection started today" : "Protected for " + days + (days == 1 ? " day" : " days");
        } else sub = "All protections are running";
        TextView st = Ui.text(this, sub, 14, p.dark ? p.muted : Ui.alpha(p.text, 0.75f), null);
        st.setPadding(0, Ui.dp(this, 2), 0, 0);
        tt.addView(st);
        top.addView(tt, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        hero.addView(top);
        // chips
        LinearLayout chips = new LinearLayout(this);
        chips.setPadding(0, Ui.dp(this, 14), 0, 0);
        int chipBg = p.dark ? Ui.alpha(Color.WHITE, 0.06f) : Ui.alpha(Color.WHITE, 0.7f);
        addChip(chips, Policy.filterLevelName(c.filterLevel).startsWith("Off") ? "Filter off" : "Web filter", p.text, chipBg);
        if (c.nfcLocked) addChip(chips, "NFC locked", p.text, chipBg);
        addChip(chips, c.partnerEnabled ? "Partner connected" : "No partner", p.text, chipBg);
        hero.addView(chips);
        LinearLayout.LayoutParams hl = Ui.lp(Ui.MATCH, Ui.WRAP);
        hl.bottomMargin = Ui.dp(this, 12);
        v.addView(hero, hl);

        // ---- problems
        if (!Guard.accessibilityEnabled(this)) {
            Ui.banner(v, R.drawable.ic_warning, "App & content protection is off",
                    "App limits, keywords and searches aren't being checked.", 2, "Turn it on",
                    fixClick(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        }
        if (!(Guard.vpnPermitted(this) && FilterVpnService.running)) {
            Ui.banner(v, R.drawable.ic_warning, "Web filter is off", "Websites aren't being filtered.", 2, "Start web filter",
                    new View.OnClickListener() { @Override public void onClick(View x) { startFilter(); } });
        }
        if (!Guard.adminActive(this)) {
            Ui.banner(v, R.drawable.ic_shield_alert, "Uninstall protection is off", "Steadfast could be removed without anyone knowing.", 3,
                    "Turn it on", new View.OnClickListener() {
                        @Override public void onClick(View x) { openSettingsWindow(); requestAdmin(MainActivity.this); }
                    });
        }
        if (!Guard.usageAccessGranted(this)) {
            Ui.banner(v, R.drawable.ic_clock, "Time tracking is off", "Daily time limits can't be measured.", 3, "Allow usage access",
                    fixClick(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        }

        // ---- today
        Ui.section(v, "Today");
        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        final TextView screenTime = statTile(stats, R.drawable.ic_clock, "…", "Screen time", true);
        Map<String, Integer> n = EventLog.countsFor(this, EventLog.dayKey(System.currentTimeMillis()));
        int blocks = 0;
        String[] types = {EventLog.SITE, EventLog.KEYWORD, EventLog.SEARCH, EventLog.APP, EventLog.LIMIT, EventLog.DOWNTIME, EventLog.LOCKDOWN};
        for (String t : types) { Integer x = n.get(t); if (x != null) blocks += x; }
        Integer dns = n.get(EventLog.DNS);
        statTile(stats, R.drawable.ic_shield, String.valueOf(blocks), "Blocks" + (dns != null && dns > 0 ? " · " + dns + " filtered" : ""), false);
        LinearLayout.LayoutParams sl = Ui.lp(Ui.MATCH, Ui.WRAP);
        sl.bottomMargin = Ui.dp(this, 10);
        v.addView(stats, sl);
        new Thread(new Runnable() {
            @Override public void run() {
                long now = System.currentTimeMillis();
                Map<String, Long> usage = Usage.forRange(MainActivity.this, Usage.startOfDay(now), now);
                Set<String> skip = Guard.launchers(MainActivity.this);
                skip.add(getPackageName());
                skip.add("com.android.systemui");
                long total = 0;
                for (Map.Entry<String, Long> e : usage.entrySet()) if (!skip.contains(e.getKey())) total += e.getValue();
                final String txt = Guard.usageAccessGranted(MainActivity.this) ? Guard.duration(total) : "–";
                runOnUiThread(new Runnable() { @Override public void run() { screenTime.setText(txt); } });
            }
        }).start();

        // ---- quick actions
        Ui.section(v, "Quick actions");
        LinearLayout qa = new LinearLayout(this);
        qa.setOrientation(LinearLayout.HORIZONTAL);
        if (c.isPaused()) {
            quick(qa, R.drawable.ic_play, "Resume", new View.OnClickListener() {
                @Override public void onClick(View x) {
                    c.pausedUntil = 0; c.save(MainActivity.this);
                    EventLog.record(MainActivity.this, EventLog.CHANGE, "Restrictions resumed.");
                    render();
                }
            });
        } else {
            quick(qa, R.drawable.ic_pause, "Pause", new View.OnClickListener() {
                @Override public void onClick(View x) { pause(); }
            });
        }
        quick(qa, R.drawable.ic_nfc, c.nfcLocked ? "Unlock" : "NFC lock", new View.OnClickListener() {
            @Override public void onClick(View x) {
                startActivity(new Intent(MainActivity.this, NfcActivity.class).putExtra(NfcActivity.EXTRA_MODE, NfcActivity.MODE_MANAGE));
            }
        });
        quick(qa, R.drawable.ic_list, "Activity", new View.OnClickListener() {
            @Override public void onClick(View x) { showLog(); }
        });
        LinearLayout.LayoutParams ql = Ui.lp(Ui.MATCH, Ui.WRAP);
        ql.bottomMargin = Ui.dp(this, 10);
        v.addView(qa, ql);

        // ---- verse
        if (c.showVerses) {
            String[] verse = BlockActivity.VERSES[(int) ((System.currentTimeMillis() / 86400000L) % BlockActivity.VERSES.length)];
            Ui.section(v, "For today");
            LinearLayout vc = Ui.paddedCard(v);
            TextView q = Ui.text(this, "“" + verse[0] + "”", 16, p.text, Typeface.create(Typeface.SERIF, Typeface.ITALIC));
            vc.addView(q);
            TextView ref = Ui.text(this, verse[1], 13, p.accent, Ui.medium());
            ref.setPadding(0, Ui.dp(this, 10), 0, 0);
            vc.addView(ref);
        }
    }

    private void addChip(LinearLayout row, String label, int fg, int bg) {
        TextView chip = Ui.chip(this, label, fg, bg);
        LinearLayout.LayoutParams l = Ui.lp(Ui.WRAP, Ui.WRAP);
        l.rightMargin = Ui.dp(this, 6);
        row.addView(chip, l);
    }

    private TextView statTile(LinearLayout row, int icon, String value, String label, boolean first) {
        Ui.Palette p = Ui.p(this);
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setBackground(Ui.round(this, p.surface, 20));
        t.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setImageTintList(ColorStateList.valueOf(p.primary));
        t.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22)));
        TextView val = Ui.text(this, value, 26, p.text, Typeface.DEFAULT_BOLD);
        val.setPadding(0, Ui.dp(this, 10), 0, 0);
        t.addView(val);
        TextView lbl = Ui.text(this, label, 13, p.muted, null);
        t.addView(lbl);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, Ui.WRAP, 1);
        if (first) l.rightMargin = Ui.dp(this, 5); else l.leftMargin = Ui.dp(this, 5);
        row.addView(t, l);
        return val;
    }

    private void quick(LinearLayout row, int icon, String label, View.OnClickListener l) {
        Ui.Palette p = Ui.p(this);
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setBackground(Ui.ripple(this, p.surface, 20, Ui.alpha(p.primary, 0.15f)));
        t.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 14));
        t.addView(Ui.iconBadge(this, icon, p.dark ? p.onPrimaryContainer : p.primary, p.primaryContainer, 44));
        TextView lbl = Ui.text(this, label, 13, p.text, Ui.medium());
        lbl.setPadding(0, Ui.dp(this, 8), 0, 0);
        t.addView(lbl);
        t.setClickable(true);
        t.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.WRAP, 1);
        lp.leftMargin = Ui.dp(this, 4);
        lp.rightMargin = Ui.dp(this, 4);
        row.addView(t, lp);
    }

    // ================================================================== Rules

    private void rules(Ui.Screen s) {
        final Config c = Config.get(this);
        LinearLayout v = s.content;
        Ui.body(v, "Decide which apps you can use, for how long, and when.");
        LinearLayout card = Ui.card(v);
        Ui.row(card, R.drawable.ic_apps, "Apps & time limits", appsSummary(c), Ui.T_CHEVRON, new View.OnClickListener() {
            @Override public void onClick(View x) { startActivity(new Intent(MainActivity.this, AppsActivity.class)); }
        });
        Ui.divider(card);
        Ui.row(card, R.drawable.ic_moon, "Downtime", c.downtimeEnabled
                ? Guard.hhmm(c.downtimeStart) + " – " + Guard.hhmm(c.downtimeEnd) + " · essential apps only"
                : "Off · set quiet hours, e.g. bedtime", c.downtimeEnabled ? Ui.T_SWITCH_ON : Ui.T_SWITCH_OFF,
                new View.OnClickListener() { @Override public void onClick(View x) { downtime(); } });

        Ui.section(v, "Locking");
        LinearLayout lock = Ui.card(v);
        Ui.row(lock, R.drawable.ic_nfc, "NFC lock", c.nfcTags.isEmpty() ? "Register a tag to lock and unlock with a tap"
                        : c.nfcTags.size() + " tag(s) · " + (c.nfcLocked ? "locked" : "unlocked") + (c.nfcLockdown ? " · lockdown mode" : ""),
                Ui.T_CHEVRON, new View.OnClickListener() {
                    @Override public void onClick(View x) {
                        startActivity(new Intent(MainActivity.this, NfcActivity.class).putExtra(NfcActivity.EXTRA_MODE, NfcActivity.MODE_MANAGE));
                    }
                });
        Ui.divider(lock);
        if (c.isPaused()) {
            Ui.row(lock, R.drawable.ic_play, "Resume restrictions", "Paused until "
                    + new SimpleDateFormat("HH:mm", Locale.UK).format(new Date(c.pausedUntil)), Ui.T_NONE, null,
                    new View.OnClickListener() {
                        @Override public void onClick(View x) {
                            c.pausedUntil = 0; c.save(MainActivity.this);
                            EventLog.record(MainActivity.this, EventLog.CHANGE, "Restrictions resumed.");
                            render();
                        }
                    }, 3);
        } else {
            Ui.row(lock, R.drawable.ic_pause, "Pause restrictions", "Temporarily switch rules off · your partner is told",
                    Ui.T_CHEVRON, new View.OnClickListener() { @Override public void onClick(View x) { pause(); } });
        }
    }

    // ================================================================== Web

    private void web(Ui.Screen s) {
        final Config c = Config.get(this);
        LinearLayout v = s.content;
        Ui.body(v, "Filtering happens on this phone. Your browsing never goes through a Steadfast server.");
        LinearLayout f = Ui.card(v);
        Ui.row(f, R.drawable.ic_filter, "Filter level", Policy.filterLevelName(c.filterLevel), Ui.T_CHEVRON,
                new View.OnClickListener() { @Override public void onClick(View x) { filterLevel(); } });
        Ui.divider(f);
        toggleRow(f, R.drawable.ic_search, "SafeSearch", "Google, Bing, DuckDuckGo & YouTube Restricted Mode", c.safeSearch,
                "turned SafeSearch off", new Toggle() { @Override public void set(boolean on) { c.safeSearch = on; } });
        Ui.divider(f);
        toggleRow(f, R.drawable.ic_apps, "Scan text in all apps", "Off = keywords only checked in browsers", c.scanAllApps,
                "limited keyword scanning to browsers", new Toggle() { @Override public void set(boolean on) { c.scanAllApps = on; } });

        Ui.section(v, "Your lists");
        LinearLayout l = Ui.card(v);
        listRow(l, R.drawable.ic_globe, "Blocked websites", count(c.blockedDomains.size(), "domain") + " · subdomains included", ListEditorActivity.LIST_BLOCKED);
        Ui.divider(l);
        listRow(l, R.drawable.ic_tag, "Keyword filter", count(c.keywords.size(), "word") + " · pages, images & screens", ListEditorActivity.LIST_KEYWORDS);
        Ui.divider(l);
        listRow(l, R.drawable.ic_search, "Search filter", count(c.searchTerms.size(), "term") + " · searches & typing", ListEditorActivity.LIST_SEARCH);
        Ui.divider(l);
        listRow(l, R.drawable.ic_check, "Always allowed", count(c.allowedDomains.size(), "exception"), ListEditorActivity.LIST_ALLOWED);
    }

    private static String count(int n, String noun) {
        return n + " " + noun + (n == 1 ? "" : "s");
    }

    // ================================================================== Partner

    private void partner(Ui.Screen s) {
        final Config c = Config.get(this);
        Ui.Palette p = s.p;
        LinearLayout v = s.content;
        if (!c.partnerEnabled) {
            LinearLayout e = Ui.paddedCard(v);
            e.setGravity(Gravity.CENTER_HORIZONTAL);
            e.setPadding(Ui.dp(this, 22), Ui.dp(this, 28), Ui.dp(this, 22), Ui.dp(this, 22));
            e.addView(Ui.iconBadge(this, R.drawable.ic_people, p.dark ? p.onPrimaryContainer : p.primary, p.primaryContainer, 72));
            TextView h = Ui.text(this, "Walk this road together", 20, p.text, Typeface.DEFAULT_BOLD);
            h.setGravity(Gravity.CENTER);
            h.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 6));
            e.addView(h, Ui.lp(Ui.MATCH, Ui.WRAP));
            TextView b = Ui.text(this, "An accountability partner is told when a block happens or protection is changed, "
                    + "never which site, word or search it was.", 15, p.muted, null);
            b.setGravity(Gravity.CENTER);
            e.addView(b, Ui.lp(Ui.MATCH, Ui.WRAP));
            Ui.button(e, "Connect a partner", new View.OnClickListener() {
                @Override public void onClick(View x) { startActivity(new Intent(MainActivity.this, AccountabilityActivity.class)); }
            });
            return;
        }
        Ui.banner(v, R.drawable.ic_check, "Partner connected",
                c.partnerApproval ? "Loosening any rule needs your partner's code." : "Your partner is told whenever you loosen a rule.",
                1, null, null);
        LinearLayout card = Ui.card(v);
        toggleRow(card, R.drawable.ic_key, "Partner approval", "Require their 6-digit code to loosen settings", c.partnerApproval,
                "turned off partner approval", new Toggle() {
                    @Override public void set(boolean on) {
                        c.partnerApproval = on;
                        if (on) EventLog.record(MainActivity.this, EventLog.CHANGE, "Partner approval is now required to loosen settings.");
                    }
                });
        Ui.divider(card);
        toggleRow(card, R.drawable.ic_bell, "Instant alerts", "Off = daily report only", c.instantAlerts,
                "turned off instant alerts", new Toggle() { @Override public void set(boolean on) { c.instantAlerts = on; } });
        Ui.divider(card);
        Ui.row(card, R.drawable.ic_link, "Pairing & partner setup", "Share code, iPhone setup, device name", Ui.T_CHEVRON,
                new View.OnClickListener() {
                    @Override public void onClick(View x) { startActivity(new Intent(MainActivity.this, AccountabilityActivity.class)); }
                });
        Ui.section(v, "Send");
        LinearLayout send = Ui.card(v);
        Ui.row(send, R.drawable.ic_send, "Send a test alert", null, Ui.T_NONE, new View.OnClickListener() {
            @Override public void onClick(View x) { Partner.sendTest(MainActivity.this); Ui.toast(MainActivity.this, "Test alert sent"); }
        });
        Ui.divider(send);
        Ui.row(send, R.drawable.ic_chart, "Send today's report now", null, Ui.T_NONE, new View.OnClickListener() {
            @Override public void onClick(View x) {
                new Thread(new Runnable() { @Override public void run() { Reports.sendNow(MainActivity.this); } }).start();
                Ui.toast(MainActivity.this, "Report sent");
            }
        });
    }

    // ================================================================== Settings

    private void settings(Ui.Screen s) {
        final Config c = Config.get(this);
        LinearLayout v = s.content;
        LinearLayout a = Ui.card(v);
        Ui.row(a, R.drawable.ic_list, "Activity on this phone", "Private log, details never leave this phone", Ui.T_CHEVRON,
                new View.OnClickListener() { @Override public void onClick(View x) { showLog(); } });
        Ui.divider(a);
        Ui.row(a, R.drawable.ic_key, "Change master PIN", null, Ui.T_CHEVRON,
                new View.OnClickListener() { @Override public void onClick(View x) { changePin(); } });
        Ui.divider(a);
        Ui.row(a, R.drawable.ic_book, "Bible verses", "On the home and block screens (WEB translation)",
                c.showVerses ? Ui.T_SWITCH_ON : Ui.T_SWITCH_OFF, new View.OnClickListener() {
                    @Override public void onClick(View x) { c.showVerses = !c.showVerses; c.save(MainActivity.this); render(); }
                });

        Ui.section(v, "Advanced");
        LinearLayout b = Ui.card(v);
        Ui.row(b, R.drawable.ic_sliders, "Android settings for Steadfast", "Unlocks protected system pages for 5 minutes", Ui.T_CHEVRON,
                new View.OnClickListener() {
                    @Override public void onClick(View x) {
                        Auth.requireLoosen(MainActivity.this, "opened protected system settings", new Runnable() {
                            @Override public void run() {
                                Config c2 = Config.get(MainActivity.this);
                                c2.settingsWindowUntil = System.currentTimeMillis() + 5 * 60000L;
                                c2.save(MainActivity.this);
                                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
                            }
                        });
                    }
                });
        Ui.divider(b);
        Ui.row(b, R.drawable.ic_power, "Turn Steadfast off completely", "Stops all protection so it can be uninstalled", Ui.T_NONE, null,
                new View.OnClickListener() { @Override public void onClick(View x) { turnOff(); } }, 2);

        TextView ver = Ui.text(this, "Steadfast " + versionName(), 12, Ui.p(this).faint, null);
        ver.setGravity(Gravity.CENTER);
        ver.setPadding(0, Ui.dp(this, 20), 0, 0);
        v.addView(ver, Ui.lp(Ui.MATCH, Ui.WRAP));
    }

    private String versionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return ""; }
    }

    // ================================================================== shared helpers

    private void space(LinearLayout v, int dp) {
        View s = new View(this);
        v.addView(s, new LinearLayout.LayoutParams(1, Ui.dp(this, dp)));
    }

    private View.OnClickListener fixClick(final String action) {
        return new View.OnClickListener() {
            @Override public void onClick(View v) {
                openSettingsWindow();
                try { startActivity(new Intent(action)); } catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            }
        };
    }

    /** Lets the user into Steadfast's system settings for 3 minutes (only used to switch protections ON). */
    private void openSettingsWindow() {
        Config c = Config.get(this);
        c.settingsWindowUntil = System.currentTimeMillis() + 3 * 60000L;
        c.save(this);
    }

    static void requestAdmin(Activity a) {
        Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, Guard.adminComponent(a));
        i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Stops Steadfast being uninstalled without your accountability partner knowing.");
        a.startActivity(i);
    }

    private void startFilter() {
        Intent prep = android.net.VpnService.prepare(this);
        if (prep != null) startActivityForResult(prep, 11);
        else { Guard.restartVpn(this); render(); }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 11 && resultCode == RESULT_OK) Guard.startVpn(this);
    }

    private String appsSummary(Config c) {
        int blocked = 0, limited = 0;
        for (Config.AppRule r : c.apps.values()) {
            if (r.blocked) blocked++;
            else if (r.limitMin >= 0 || r.startMin >= 0) limited++;
        }
        if (blocked == 0 && limited == 0) return "No app rules yet";
        return blocked + " blocked · " + limited + " with limits";
    }

    private void listRow(LinearLayout card, int icon, String title, String sub, final String list) {
        Ui.row(card, icon, title, sub, Ui.T_CHEVRON, new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, ListEditorActivity.class).putExtra(ListEditorActivity.EXTRA_LIST, list));
            }
        });
    }

    interface Toggle { void set(boolean on); }

    private void toggleRow(LinearLayout card, int icon, String title, String sub, final boolean current,
                           final String loosenText, final Toggle t) {
        Ui.row(card, icon, title, sub, current ? Ui.T_SWITCH_ON : Ui.T_SWITCH_OFF, new View.OnClickListener() {
            @Override public void onClick(View v) {
                final Runnable apply = new Runnable() {
                    @Override public void run() { t.set(!current); Config.get(MainActivity.this).save(MainActivity.this); render(); }
                };
                if (current) Auth.requireLoosen(MainActivity.this, loosenText, apply); else apply.run();
            }
        });
    }

    private void pause() {
        final int[] mins = {15, 30, 60, 120};
        Ui.choose(this, "Pause restrictions for…", new String[]{"15 minutes", "30 minutes", "1 hour", "2 hours"}, new Ui.IntCallback() {
            @Override public void on(final int which) {
                Auth.requireLoosen(MainActivity.this, "paused restrictions for " + mins[which] + " minutes", new Runnable() {
                    @Override public void run() {
                        Config c = Config.get(MainActivity.this);
                        c.pausedUntil = System.currentTimeMillis() + mins[which] * 60000L;
                        c.save(MainActivity.this);
                        render();
                    }
                });
            }
        });
    }

    private void downtime() {
        final Config c = Config.get(this);
        String[] items = {c.downtimeEnabled ? "Turn downtime off" : "Turn downtime on",
                "Start time (" + Guard.hhmm(c.downtimeStart) + ")", "End time (" + Guard.hhmm(c.downtimeEnd) + ")"};
        Ui.choose(this, "Downtime", items, new Ui.IntCallback() {
            @Override public void on(int which) {
                if (which == 0) {
                    if (c.downtimeEnabled) {
                        Auth.requireLoosen(MainActivity.this, "turned downtime off", new Runnable() {
                            @Override public void run() { c.downtimeEnabled = false; c.save(MainActivity.this); render(); }
                        });
                    } else {
                        c.downtimeEnabled = true; c.save(MainActivity.this); render();
                    }
                    return;
                }
                final boolean start = which == 1;
                Runnable pick = new Runnable() {
                    @Override public void run() {
                        int cur = start ? c.downtimeStart : c.downtimeEnd;
                        new TimePickerDialog(MainActivity.this, Ui.isDark(MainActivity.this)
                                ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert,
                                new TimePickerDialog.OnTimeSetListener() {
                                    @Override public void onTimeSet(TimePicker view, int h, int m) {
                                        if (start) c.downtimeStart = h * 60 + m; else c.downtimeEnd = h * 60 + m;
                                        c.save(MainActivity.this);
                                        render();
                                    }
                                }, cur / 60, cur % 60, true).show();
                    }
                };
                if (c.downtimeEnabled) Auth.requireLoosen(MainActivity.this, "changed downtime hours", pick); else pick.run();
            }
        });
    }

    private void filterLevel() {
        final Config c = Config.get(this);
        String[] items = {
                "Off: no category filtering",
                "Malware & phishing only",
                "Adult content + malware (recommended)",
                "Strict family: also blocks proxy/VPN sites"
        };
        Ui.choose(this, "Web filter level", items, new Ui.IntCallback() {
            @Override public void on(final int which) {
                Runnable apply = new Runnable() {
                    @Override public void run() { c.filterLevel = which; c.save(MainActivity.this); render(); }
                };
                if (which < c.filterLevel) Auth.requireLoosen(MainActivity.this, "lowered the web filter level", apply);
                else apply.run();
            }
        });
    }

    private void changePin() {
        Ui.pin(this, "New master PIN", "4–10 digits", new Ui.TextCallback() {
            @Override public void on(final String pin1) {
                if (pin1.length() < 4 || pin1.length() > 10) { Ui.toast(MainActivity.this, "Use 4–10 digits"); return; }
                Ui.pin(MainActivity.this, "Repeat new PIN", null, new Ui.TextCallback() {
                    @Override public void on(String pin2) {
                        if (!pin1.equals(pin2)) { Ui.toast(MainActivity.this, "PINs didn't match"); return; }
                        Auth.setPin(MainActivity.this, pin1);
                        EventLog.record(MainActivity.this, EventLog.CHANGE, "Master PIN was changed.");
                        Ui.toast(MainActivity.this, "PIN changed");
                    }
                });
            }
        });
    }

    private void showLog() {
        JSONArray log = EventLog.recent(this);
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat f = new SimpleDateFormat("EEE HH:mm", Locale.UK);
        for (int i = log.length() - 1, n = 0; i >= 0 && n < 120; i--, n++) {
            JSONObject e = log.optJSONObject(i);
            if (e == null) continue;
            sb.append(f.format(new Date(e.optLong("t")))).append("  ·  ").append(EventLog.label(e.optString("type")));
            String d = e.optString("d");
            if (!d.isEmpty()) sb.append("\n").append(d);
            sb.append("\n\n");
        }
        if (sb.length() == 0) sb.append("Nothing yet.");
        Ui.dialog(this).setTitle("Activity (this phone only)").setMessage(sb.toString())
                .setPositiveButton("Close", null).show();
    }

    private void turnOff() {
        Ui.confirm(MainActivity.this, "Turn off Steadfast?",
                "All protection will stop and Steadfast can then be uninstalled. Your partner will be told.", new Runnable() {
            @Override public void run() {
                Auth.requireLoosen(MainActivity.this, "turned Steadfast off completely", new Runnable() {
                    @Override public void run() {
                        Context ctx = MainActivity.this;
                        Partner.flushNow(ctx);
                        Guard.stopVpn(ctx);
                        try {
                            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
                            dpm.removeActiveAdmin(Guard.adminComponent(ctx));
                        } catch (Exception ignored) {}
                        Config.reset(ctx);
                        EventLog.clear(ctx);
                        Auth.endSession();
                        GuardAccessibilityService.shutdownService();
                        tab = TAB_HOME;
                        Ui.alert(MainActivity.this, "Steadfast is off",
                                "All protection has stopped. You can now uninstall Steadfast like any other app.");
                        render();
                    }
                });
            }
        });
    }
}
