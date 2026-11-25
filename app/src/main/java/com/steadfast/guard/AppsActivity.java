package com.steadfast.guard;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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
    private LinearLayout list;
    private String filter = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout p = Ui.page(this, "Apps & time limits");
        Ui.para(p, "Tap an app to block it, give it a daily time limit, or only allow it at certain hours. "
                + "“Essential” apps stay available during downtime and NFC lockdown. Phone, keyboard and your home screen are never blocked.");
        EditText search = new EditText(this);
        search.setHint("Search apps");
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setSingleLine(true);
        p.addView(search);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { filter = s.toString().toLowerCase(Locale.ROOT); renderList(); }
        });
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        p.addView(list);
        Ui.para(list, "Loading apps…");
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
        list.removeAllViews();
        Config c = Config.get(this);
        Set<String> always = Guard.alwaysAllowed(this);
        for (final AppItem a : apps) {
            if (!filter.isEmpty() && !a.label.toLowerCase(Locale.ROOT).contains(filter)) continue;
            Config.AppRule r = c.apps.get(a.pkg);
            String status;
            if (always.contains(a.pkg)) status = "Always allowed";
            else status = describe(r);
            long used = Usage.todayMs(this, a.pkg);
            if (used > 0) status += " · today " + Guard.duration(used);
            addRow(a, status, always.contains(a.pkg));
        }
    }

    static String describe(Config.AppRule r) {
        if (r == null || r.isEmpty()) return "No limits";
        if (r.blocked) return "BLOCKED";
        List<String> parts = new ArrayList<>();
        if (r.limitMin >= 0) parts.add(r.limitMin + " min/day");
        if (r.startMin >= 0) parts.add("allowed " + Guard.hhmm(r.startMin) + "–" + Guard.hhmm(r.endMin));
        if (r.essential) parts.add("essential");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) { if (i > 0) sb.append(" · "); sb.append(parts.get(i)); }
        return sb.toString();
    }

    private void addRow(final AppItem a, String status, final boolean locked) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Ui.dp(this, 8);
        row.setPadding(pad, pad, pad, pad);
        ImageView iv = new ImageView(this);
        if (a.icon != null) iv.setImageDrawable(a.icon);
        int s = Ui.dp(this, 40);
        row.addView(iv, new LinearLayout.LayoutParams(s, s));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(Ui.dp(this, 12), 0, 0, 0);
        TextView t = new TextView(this);
        t.setText(a.label);
        t.setTextSize(16);
        t.setTextColor(Color.parseColor("#1B1B1B"));
        TextView st = new TextView(this);
        st.setText(status);
        st.setTextSize(13);
        st.setTextColor(status.startsWith("BLOCKED") ? Ui.WARN : Ui.MUTED);
        texts.addView(t);
        texts.addView(st);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.setClickable(true);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (locked) { Ui.toast(AppsActivity.this, "This app is always allowed so your phone stays usable."); return; }
                edit(a);
            }
        });
        list.addView(row);
    }

    private void edit(final AppItem a) {
        final Config c = Config.get(this);
        final Config.AppRule cur = c.apps.containsKey(a.pkg) ? c.apps.get(a.pkg) : new Config.AppRule();
        String[] items = {
                "Block completely",
                "Daily time limit…",
                "Allowed hours…",
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
                        Ui.prompt(AppsActivity.this, "Daily limit for " + a.label, "Minutes per day (0 = none allowed). Leave empty to remove the limit.",
                                cur.limitMin >= 0 ? String.valueOf(cur.limitMin) : "", InputType.TYPE_CLASS_NUMBER, new Ui.TextCallback() {
                                    @Override public void on(String text) {
                                        String tt = text.trim();
                                        next.limitMin = tt.isEmpty() ? -1 : Math.min(Integer.parseInt(tt), 1440);
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

    private void pickHours(final AppItem a, final Config.AppRule cur, final Config.AppRule next) {
        Ui.toast(this, "Choose the time the app becomes available");
        int s0 = cur.startMin >= 0 ? cur.startMin : 8 * 60;
        new TimePickerDialog(this, new TimePickerDialog.OnTimeSetListener() {
            @Override public void onTimeSet(TimePicker v, int h, int m) {
                final int start = h * 60 + m;
                Ui.toast(AppsActivity.this, "Now choose when it stops being available");
                int e0 = cur.endMin >= 0 ? cur.endMin : 21 * 60;
                new TimePickerDialog(AppsActivity.this, new TimePickerDialog.OnTimeSetListener() {
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
