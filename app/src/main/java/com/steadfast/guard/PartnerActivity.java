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
    private final List<String> codeSecrets = new ArrayList<>();

    private final Runnable codeTicker = new Runnable() {
        @Override public void run() {
            long now = System.currentTimeMillis();
            int left = 30 - (int) ((now / 1000) % 30);
            for (int i = 0; i < codeViews.size(); i++) {
                String code = Crypto.totp(codeSecrets.get(i), now);
                codeViews.get(i).setText(code.substring(0, 3) + " " + code.substring(3) + "   (" + left + "s)");
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
        codeSecrets.clear();
        LinearLayout p = Ui.page(this, "Steadfast partner");
        Ui.title(p, "Accountability partner");
        Ui.para(p, "You'll get a notification when someone you support hits a block or changes their protection. "
                + "You'll never see what they searched or watched. Pray for them and check in.");
        final List<PartnerStore.Device> devices = PartnerStore.load(this);
        if (devices.isEmpty()) Ui.para(p, "No devices yet. Ask the person you're supporting to send you their pairing code.");

        for (int idx = 0; idx < devices.size(); idx++) {
            final PartnerStore.Device d = devices.get(idx);
            final int index = idx;
            Ui.header(p, d.name.isEmpty() ? "Device" : d.name);
            String last = d.lastMsgTime == 0 ? "No messages yet"
                    : "Last heard from: " + new SimpleDateFormat("EEE d MMM HH:mm", Locale.UK).format(new Date(d.lastMsgTime));
            if (d.lastMsgTime > 0 && System.currentTimeMillis() - d.lastMsgTime > 36 * 3600000L) {
                Ui.status(p, last + " (no daily report for 36h+: check in)", false);
            } else {
                Ui.para(p, last);
            }
            Ui.para(p, "Approval code (they ask you for this to loosen settings):");
            TextView code = Ui.big(p, "--- ---", 30, Ui.PRIMARY);
            code.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            code.setGravity(Gravity.START);
            codeViews.add(code);
            codeSecrets.add(d.secret);

            Ui.button(p, "Pause their restrictions…", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    final String[] mins = {"15", "30", "60", "120"};
                    Ui.choose(PartnerActivity.this, "Pause for", new String[]{"15 minutes", "30 minutes", "1 hour", "2 hours"},
                            new Ui.IntCallback() {
                                @Override public void on(int w) { send(d, Partner.CMD_PAUSE, mins[w]); }
                            });
                }
            });
            Ui.button(p, "Turn restrictions back on", click(d, Partner.CMD_RESUME, ""));
            Ui.button(p, "Unlock their NFC lock", click(d, Partner.CMD_UNLOCK, ""));
            Ui.button(p, "Lock their settings now", click(d, Partner.CMD_LOCK, ""));
            Ui.button(p, "Require my approval to loosen settings", click(d, Partner.CMD_APPROVAL, "on"));
            Ui.button(p, "Stop requiring my approval", click(d, Partner.CMD_APPROVAL, "off"));
            Ui.button(p, "Ask for a report now", click(d, Partner.CMD_REPORT, ""));
            Ui.para(p, "Remote changes reach their phone within about a minute while it's online.");

            Ui.header(p, "Recent messages");
            SimpleDateFormat f = new SimpleDateFormat("EEE HH:mm", Locale.UK);
            int shown = 0;
            for (int i = d.recent.length() - 1; i >= 0 && shown < 25; i--, shown++) {
                JSONObject m = d.recent.optJSONObject(i);
                if (m == null) continue;
                Ui.row(p, f.format(new Date(m.optLong("t"))) + "  " + m.optString("title"), m.optString("msg"), null);
            }
            if (shown == 0) Ui.para(p, "Nothing yet.");
            Ui.row(p, "Remove this device", null, new View.OnClickListener() {
                @Override public void onClick(View v) {
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
        }

        Ui.header(p, "Manage");
        Ui.button(p, "Add device (paste pairing code)", new View.OnClickListener() {
            @Override public void onClick(View v) {
                Ui.prompt(PartnerActivity.this, "Pairing code", "Paste the code that starts with SF1:", "",
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
        });
        Ui.button(p, "Check for new messages", new View.OnClickListener() {
            @Override public void onClick(View v) { refresh(); }
        });
        Ui.para(p, "Tip: for instant alerts, also install the free ntfy app and subscribe to their topic. "
                + "This app checks every 15 minutes.");
        if (devices.isEmpty()) {
            Ui.row(p, "Use this phone to protect myself instead", null, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Config c = Config.get(PartnerActivity.this);
                    c.role = "";
                    c.save(PartnerActivity.this);
                    startActivity(new Intent(PartnerActivity.this, MainActivity.class));
                    finish();
                }
            });
        }
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
                    msg = "Sent. Their phone will confirm shortly.";
                } catch (Exception e) {
                    msg = "Couldn't send. Check your internet connection.";
                }
                final String m = msg;
                runOnUiThread(new Runnable() { @Override public void run() { Ui.toast(PartnerActivity.this, m); } });
            }
        }).start();
    }
}
