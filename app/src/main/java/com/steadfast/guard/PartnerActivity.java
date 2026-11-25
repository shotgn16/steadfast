package com.steadfast.guard;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Partner mode: see alerts and reports, approve changes, and pause/unlock remotely. */
public class PartnerActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<TextView> codeViews = new ArrayList<>();
    private final List<View> codeBars = new ArrayList<>();
    private final List<String> codeSecrets = new ArrayList<>();

    private final Runnable codeTicker = new Runnable() {
        @Override public void run() {
            long now = System.currentTimeMillis();
            int left = 30 - (int) ((now / 1000) % 30);
            for (int i = 0; i < codeViews.size(); i++) {
                String code = Crypto.totp(codeSecrets.get(i), now);
                codeViews.get(i).setText(code.substring(0, 3) + " " + code.substring(3));
                View bar = codeBars.get(i);
                View parent = (View) bar.getParent();
                if (parent != null && parent.getWidth() > 0) {
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) bar.getLayoutParams();
                    lp.width = parent.getWidth() * left / 30;
                    bar.setLayoutParams(lp);
                }
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Jobs.schedule(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
        refresh();
        handler.post(codeTicker);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(codeTicker);
        super.onPause();
    }

    private void refresh() {
        new Thread(new Runnable() {
            @Override public void run() {
                PartnerStore.pollAll(PartnerActivity.this);
                runOnUiThread(new Runnable() { @Override public void run() { render(); } });
            }
        }).start();
    }

    private void render() {
        codeViews.clear();
        codeBars.clear();
        codeSecrets.clear();
        Ui.Screen s = Ui.screen(this, "Partner", false);
        Ui.headerAction(s, R.drawable.ic_refresh, "Check for new messages", new View.OnClickListener() {
            @Override public void onClick(View v) { refresh(); Ui.toast(PartnerActivity.this, "Checking…"); }
        });
        Ui.headerAction(s, R.drawable.ic_plus, "Add device", new View.OnClickListener() {
            @Override public void onClick(View v) { addDevice(); }
        });
        Ui.Palette p = s.p;
        LinearLayout v = s.content;
        final List<PartnerStore.Device> devices = PartnerStore.load(this);

        if (devices.isEmpty()) {
            LinearLayout e = Ui.paddedCard(v);
            e.setGravity(Gravity.CENTER_HORIZONTAL);
            e.setPadding(Ui.dp(this, 22), Ui.dp(this, 28), Ui.dp(this, 22), Ui.dp(this, 22));
            e.addView(Ui.iconBadge(this, R.drawable.ic_people, p.dark ? p.onPrimaryContainer : p.primary, p.primaryContainer, 72));
            TextView h = Ui.text(this, "Thank you for standing with them", 20, p.text, Typeface.DEFAULT_BOLD);
            h.setGravity(Gravity.CENTER);
            h.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 6));
            e.addView(h, Ui.lp(Ui.MATCH, Ui.WRAP));
            TextView b = Ui.text(this, "Ask the person you're supporting for their pairing code. You'll be told when a block happens "
                    + "or protection changes, never what they searched or watched.", 15, p.muted, null);
            b.setGravity(Gravity.CENTER);
            e.addView(b, Ui.lp(Ui.MATCH, Ui.WRAP));
            Ui.button(e, "Add device (paste code)", new View.OnClickListener() {
                @Override public void onClick(View x) { addDevice(); }
            });
            TextView sw = Ui.text(this, "Use this phone to protect myself instead", 14, p.primary, Ui.medium());
            sw.setGravity(Gravity.CENTER);
            sw.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 8));
            sw.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View x) {
                    Config c = Config.get(PartnerActivity.this);
                    c.role = "";
                    c.save(PartnerActivity.this);
                    startActivity(new Intent(PartnerActivity.this, MainActivity.class));
                    finish();
                }
            });
            v.addView(sw, Ui.lp(Ui.MATCH, Ui.WRAP));
            return;
        }

        for (int idx = 0; idx < devices.size(); idx++) {
            final PartnerStore.Device d = devices.get(idx);
            final int index = idx;
            Ui.section(v, d.name.isEmpty() ? "Device" : d.name);

            boolean stale = d.lastMsgTime > 0 && System.currentTimeMillis() - d.lastMsgTime > 36 * 3600000L;
            String last = d.lastMsgTime == 0 ? "No messages yet"
                    : "Last heard " + new SimpleDateFormat("EEE d MMM, HH:mm", Locale.UK).format(new Date(d.lastMsgTime));
            if (stale) Ui.banner(v, R.drawable.ic_warning, "No report for over 36 hours", last + ". It may be worth checking in.", 3, null, null);

            // approval code card
            LinearLayout cc = Ui.paddedCard(v);
            LinearLayout ch = new LinearLayout(this);
            ch.setGravity(Gravity.CENTER_VERTICAL);
            ch.addView(Ui.iconBadge(this, R.drawable.ic_key, p.dark ? p.onPrimaryContainer : p.primary, p.primaryContainer, 36));
            LinearLayout ct = new LinearLayout(this);
            ct.setOrientation(LinearLayout.VERTICAL);
            ct.setPadding(Ui.dp(this, 12), 0, 0, 0);
            ct.addView(Ui.text(this, "Approval code", 15, p.text, Ui.medium()));
            ct.addView(Ui.text(this, stale ? "Give this when they ask to loosen a setting" : last, 13, p.muted, null));
            ch.addView(ct, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
            cc.addView(ch);
            TextView code = Ui.text(this, "--- ---", 40, p.text, Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
            code.setLetterSpacing(0.05f);
            code.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 8));
            cc.addView(code);
            FrameLayout track = new FrameLayout(this);
            track.setBackground(Ui.round(this, p.surface2, 3));
            View bar = new View(this);
            bar.setBackground(Ui.round(this, p.accent, 3));
            track.addView(bar, new FrameLayout.LayoutParams(0, Ui.MATCH));
            cc.addView(track, Ui.lp(Ui.MATCH, Ui.dp(this, 5)));
            codeViews.add(code);
            codeBars.add(bar);
            codeSecrets.add(d.secret);

            // remote controls
            LinearLayout rc = Ui.card(v);
            Ui.row(rc, R.drawable.ic_pause, "Pause their restrictions", "15 min to 2 hours", Ui.T_CHEVRON, new View.OnClickListener() {
                @Override public void onClick(View x) {
                    final String[] mins = {"15", "30", "60", "120"};
                    Ui.choose(PartnerActivity.this, "Pause for", new String[]{"15 minutes", "30 minutes", "1 hour", "2 hours"},
                            new Ui.IntCallback() { @Override public void on(int w) { send(d, Partner.CMD_PAUSE, mins[w]); } });
                }
            });
            Ui.divider(rc);
            Ui.row(rc, R.drawable.ic_play, "Turn restrictions back on", null, Ui.T_NONE, click(d, Partner.CMD_RESUME, ""));
            Ui.divider(rc);
            Ui.row(rc, R.drawable.ic_unlock, "Unlock their NFC lock", null, Ui.T_NONE, click(d, Partner.CMD_UNLOCK, ""));
            Ui.divider(rc);
            Ui.row(rc, R.drawable.ic_lock, "Lock their settings now", null, Ui.T_NONE, click(d, Partner.CMD_LOCK, ""));
            Ui.divider(rc);
            Ui.row(rc, R.drawable.ic_key, "Approval required", "Choose whether loosening needs your code", Ui.T_CHEVRON,
                    new View.OnClickListener() {
                        @Override public void onClick(View x) {
                            Ui.choose(PartnerActivity.this, "Partner approval", new String[]{"Require my code to loosen settings", "Don't require my code"},
                                    new Ui.IntCallback() { @Override public void on(int w) { send(d, Partner.CMD_APPROVAL, w == 0 ? "on" : "off"); } });
                        }
                    });
            Ui.divider(rc);
            Ui.row(rc, R.drawable.ic_chart, "Ask for a report now", null, Ui.T_NONE, click(d, Partner.CMD_REPORT, ""));

            // messages
            LinearLayout mc = Ui.card(v);
            SimpleDateFormat f = new SimpleDateFormat("EEE HH:mm", Locale.UK);
            int shown = 0;
            for (int i = d.recent.length() - 1; i >= 0 && shown < 20; i--) {
                JSONObject m = d.recent.optJSONObject(i);
                if (m == null) continue;
                if (shown > 0) Ui.divider(mc);
                String title = m.optString("title");
                String msg = m.optString("msg");
                boolean warn = title.contains("report") ? false : msg.toLowerCase(Locale.ROOT).contains("off")
                        || msg.toLowerCase(Locale.ROOT).contains("tried") || msg.toLowerCase(Locale.ROOT).contains("removed");
                int icon = title.contains("report") ? R.drawable.ic_chart : warn ? R.drawable.ic_warning : R.drawable.ic_bell;
                Ui.row(mc, icon, msg, f.format(new Date(m.optLong("t"))), Ui.T_NONE, null, null, warn ? 3 : 0);
                shown++;
            }
            if (shown == 0) Ui.row(mc, R.drawable.ic_bell, "No messages yet", "Alerts and daily reports will appear here", Ui.T_NONE, null);

            TextView rm = Ui.text(this, "Remove this device", 14, p.danger, Ui.medium());
            rm.setGravity(Gravity.CENTER);
            rm.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
            rm.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View x) {
                    Ui.confirm(PartnerActivity.this, "Remove device?", "You'll stop receiving its alerts here.", new Runnable() {
                        @Override public void run() {
                            List<PartnerStore.Device> l = PartnerStore.load(PartnerActivity.this);
                            if (index < l.size()) l.remove(index);
                            PartnerStore.save(PartnerActivity.this, l);
                            render();
                        }
                    });
                }
            });
            v.addView(rm, Ui.lp(Ui.MATCH, Ui.WRAP));
        }
        TextView tip = Ui.text(this, "Tip: for instant alerts, also install the free ntfy app and subscribe to their topic. "
                + "This app checks every 15 minutes.", 13, p.faint, null);
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), 0);
        v.addView(tip, Ui.lp(Ui.MATCH, Ui.WRAP));
    }

    private void addDevice() {
        Ui.prompt(this, "Pairing code", "Paste the code that starts with SF1:", "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE, new Ui.TextCallback() {
                    @Override public void on(String text) {
                        try {
                            PartnerStore.Device d = PartnerStore.parse(text);
                            List<PartnerStore.Device> l = PartnerStore.load(PartnerActivity.this);
                            l.add(d);
                            PartnerStore.save(PartnerActivity.this, l);
                            Ui.toast(PartnerActivity.this, "Added " + d.name);
                            refresh();
                        } catch (Exception e) {
                            Ui.toast(PartnerActivity.this, "That doesn't look like a Steadfast pairing code.");
                        }
                    }
                });
    }

    private View.OnClickListener click(final PartnerStore.Device d, final String cmd, final String arg) {
        return new View.OnClickListener() {
            @Override public void onClick(View v) { send(d, cmd, arg); }
        };
    }

    private void send(final PartnerStore.Device d, final String cmd, final String arg) {
        new Thread(new Runnable() {
            @Override public void run() {
                String msg;
                try {
                    PartnerStore.sendCommand(d, cmd, arg);
                    msg = "Sent. Their phone will confirm within about a minute.";
                } catch (Exception e) {
                    msg = "Couldn't send. Check your internet connection.";
                }
                final String m = msg;
                runOnUiThread(new Runnable() { @Override public void run() { Ui.toast(PartnerActivity.this, m); } });
            }
        }).start();
    }
}
