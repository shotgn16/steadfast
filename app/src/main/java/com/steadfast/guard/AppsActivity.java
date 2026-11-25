package com.steadfast.guard;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TimePicker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Choose which apps are blocked, limited, time-windowed or essential. */
public class AppsActivity extends Activity {

    private static final class AppItem {
        String pkg;
        String label;
        Drawable icon;
    }

    private final List<AppItem> apps = new ArrayList<>();
    private LinearLayout ruled, others;
    private TextView ruledLabel, othersLabel;
    private String filter = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.Screen s = Ui.screen(this, "Apps & time limits", true);
        Ui.Palette p = s.p;
        LinearLayout v = s.content;
        Ui.body(v, "Tap an app to block it, limit it, or allow it only at certain hours. "
                + "Phone, keyboard and your home screen are never blocked.");

        LinearLayout searchBox = new LinearLayout(this);
        searchBox.setGravity(Gravity.CENTER_VERTICAL);
        searchBox.setBackground(Ui.round(this, p.surface, 26));
        searchBox.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 12), 0);
        ImageView si = new ImageView(this);
        si.setImageResource(R.drawable.ic_search);
        si.setImageTintList(ColorStateList.valueOf(p.muted));
        searchBox.addView(si, new LinearLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 20)));
        EditText search = new EditText(this);
        search.setHint("Search apps");
        search.setHintTextColor(p.faint);
        search.setTextColor(p.text);
        search.setBackground(null);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setSingleLine(true);
        search.setPadding(Ui.dp(this, 10), Ui.dp(this, 14), 0, Ui.dp(this, 14));
        searchBox.addView(search, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        LinearLayout.LayoutParams sl = Ui.lp(Ui.MATCH, Ui.WRAP);
        sl.topMargin = Ui.dp(this, 6);
        sl.bottomMargin = Ui.dp(this, 4);
        v.addView(searchBox, sl);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence x, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence x, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable x) { filter = x.toString().toLowerCase(Locale.ROOT); renderList(); }
        });

        ruledLabel = Ui.section(v, "With rules");
        ruled = Ui.card(v);
        othersLabel = Ui.section(v, "All apps");
        others = Ui.card(v);
        Ui.row(others, 0, "Loading apps…", null, Ui.T_NONE, null);

        new Thread(new Runnable() {
            @Override public void run() {
                final List<AppItem> loaded = loadApps();
                runOnUiThread(new Runnable() {
                    @Override public void run() { apps.clear(); apps.addAll(loaded); renderList(); }
                });
            }
        }).start();
    }

    private List<AppItem> loadApps() {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> infos = pm.queryIntentActivities(main, 0);
        Set<String> seen = new HashSet<>();
        List<AppItem> out = new ArrayList<>();
        for (ResolveInfo ri : infos) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
            AppItem a = new AppItem();
            a.pkg = pkg;
            a.label = ri.loadLabel(pm).toString();
            try { a.icon = ri.loadIcon(pm); } catch (Exception ignored) {}
            out.add(a);
        }
        Collections.sort(out, new Comparator<AppItem>() {
            @Override public int compare(AppItem x, AppItem y) { return x.label.compareToIgnoreCase(y.label); }
        });
        return out;
    }

    private void renderList() {
        ruled.removeAllViews();
        others.removeAllViews();
        Config c = Config.get(this);
        Set<String> always = Guard.alwaysAllowed(this);
        int nr = 0, no = 0;
        for (final AppItem a : apps) {
            if (!filter.isEmpty() && !a.label.toLowerCase(Locale.ROOT).contains(filter)) continue;
            Config.AppRule r = c.apps.get(a.pkg);
            boolean locked = always.contains(a.pkg);
            String status = locked ? "Always allowed" : describe(r);
            long used = Usage.todayMs(this, a.pkg);
            if (used > 60000) status += " · " + Guard.duration(used) + " today";
            boolean hasRule = r != null && !r.isEmpty() && !locked;
            LinearLayout target = hasRule ? ruled : others;
            if ((hasRule ? nr : no) > 0) Ui.divider(target);
            if (hasRule) nr++; else no++;
            addRow(target, a, status, locked, r);
        }
        ruledLabel.setVisibility(nr > 0 ? View.VISIBLE : View.GONE);
        ruled.setVisibility(nr > 0 ? View.VISIBLE : View.GONE);
        othersLabel.setVisibility(no > 0 ? View.VISIBLE : View.GONE);
        others.setVisibility(no > 0 ? View.VISIBLE : View.GONE);
    }

    static String describe(Config.AppRule r) {
        if (r == null || r.isEmpty()) return "No limits";
        if (r.blocked) return "Blocked";
        List<String> parts = new ArrayList<>();
        if (r.limitMin >= 0) parts.add(r.limitMin + " min a day");
        if (r.startMin >= 0) parts.add(Guard.hhmm(r.startMin) + "–" + Guard.hhmm(r.endMin));
        if (r.essential) parts.add("Essential");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) { if (i > 0) sb.append(" · "); sb.append(parts.get(i)); }
        return sb.toString();
    }

    private void addRow(LinearLayout card, final AppItem a, String status, final boolean locked, Config.AppRule r) {
        Ui.Palette p = Ui.p(this);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        row.setMinimumHeight(Ui.dp(this, 62));
        ImageView iv = new ImageView(this);
        if (a.icon != null) iv.setImageDrawable(a.icon);
        if (r != null && r.blocked) iv.setAlpha(0.45f);
        row.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 8), 0);
        texts.addView(Ui.text(this, a.label, 16, p.text, null));
        texts.addView(Ui.text(this, status, 13, p.muted, null));
        row.addView(texts, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        if (r != null && !r.isEmpty() && !locked) {
            int fg = r.blocked ? p.danger : p.dark ? p.onPrimaryContainer : p.primary;
            int bg = r.blocked ? p.dangerContainer : p.primaryContainer;
            row.addView(Ui.chip(this, r.blocked ? "Blocked" : r.limitMin >= 0 ? r.limitMin + "m" : r.essential && r.startMin < 0 ? "Essential" : "Hours", fg, bg));
        } else if (locked) {
            ImageView lk = new ImageView(this);
            lk.setImageResource(R.drawable.ic_lock);
            lk.setImageTintList(ColorStateList.valueOf(p.faint));
            row.addView(lk, new LinearLayout.LayoutParams(Ui.dp(this, 18), Ui.dp(this, 18)));
        }
        row.setBackground(Ui.ripple(this, android.graphics.Color.TRANSPARENT, 0, Ui.alpha(p.primary, 0.12f)));
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (locked) { Ui.toast(AppsActivity.this, "This app is always allowed so your phone stays usable."); return; }
                edit(a);
            }
        });
        card.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP));
    }

    private void edit(final AppItem a) {
        final Config c = Config.get(this);
        final Config.AppRule cur = c.apps.containsKey(a.pkg) ? c.apps.get(a.pkg) : new Config.AppRule();
        String[] items = {
                cur.blocked ? "Blocked ✓" : "Block completely",
                "Daily time limit…" + (cur.limitMin >= 0 ? " (" + cur.limitMin + " min)" : ""),
                "Allowed hours…" + (cur.startMin >= 0 ? " (" + Guard.hhmm(cur.startMin) + "–" + Guard.hhmm(cur.endMin) + ")" : ""),
                cur.essential ? "Remove from essential apps" : "Mark as essential (allowed in downtime/lockdown)",
                "Remove all rules"
        };
        Ui.choose(this, a.label, items, new Ui.IntCallback() {
            @Override public void on(int which) {
                final Config.AppRule next = cur.copy();
                switch (which) {
                    case 0:
                        next.blocked = true;
                        apply(a, cur, next);
                        break;
                    case 1:
                        Ui.prompt(AppsActivity.this, "Daily limit for " + a.label, "Minutes per day. Leave empty to remove the limit.",
                                cur.limitMin >= 0 ? String.valueOf(cur.limitMin) : "", InputType.TYPE_CLASS_NUMBER, new Ui.TextCallback() {
                                    @Override public void on(String text) {
                                        String tt = text.trim();
                                        try {
                                            next.limitMin = tt.isEmpty() ? -1 : Math.min(Integer.parseInt(tt), 1440);
                                        } catch (NumberFormatException e) { return; }
                                        next.blocked = false;
                                        apply(a, cur, next);
                                    }
                                });
                        break;
                    case 2:
                        pickHours(a, cur, next);
                        break;
                    case 3:
                        next.essential = !cur.essential;
                        apply(a, cur, next);
                        break;
                    default:
                        apply(a, cur, new Config.AppRule());
                }
            }
        });
    }

    private int pickerTheme() {
        return Ui.isDark(this) ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert;
    }

    private void pickHours(final AppItem a, final Config.AppRule cur, final Config.AppRule next) {
        Ui.toast(this, "First choose when " + a.label + " becomes available");
        int s0 = cur.startMin >= 0 ? cur.startMin : 8 * 60;
        new TimePickerDialog(this, pickerTheme(), new TimePickerDialog.OnTimeSetListener() {
            @Override public void onTimeSet(TimePicker v, int h, int m) {
                final int start = h * 60 + m;
                Ui.toast(AppsActivity.this, "Now choose when it stops being available");
                int e0 = cur.endMin >= 0 ? cur.endMin : 21 * 60;
                new TimePickerDialog(AppsActivity.this, pickerTheme(), new TimePickerDialog.OnTimeSetListener() {
                    @Override public void onTimeSet(TimePicker v2, int h2, int m2) {
                        next.startMin = start;
                        next.endMin = h2 * 60 + m2;
                        next.blocked = false;
                        apply(a, cur, next);
                    }
                }, e0 / 60, e0 % 60, true).show();
            }
        }, s0 / 60, s0 % 60, true).show();
    }

    private void apply(final AppItem a, Config.AppRule cur, final Config.AppRule next) {
        Runnable save = new Runnable() {
            @Override public void run() {
                Config c = Config.get(AppsActivity.this);
                if (next.isEmpty()) c.apps.remove(a.pkg); else c.apps.put(a.pkg, next);
                c.save(AppsActivity.this);
                renderList();
            }
        };
        if (cur.looserThan(next)) Auth.requireLoosen(this, "relaxed an app rule", save);
        else save.run();
    }
}
