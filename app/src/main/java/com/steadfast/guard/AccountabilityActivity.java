package com.steadfast.guard;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.LinearLayout;

/** Protected-device side of accountability: connect a partner and choose what they can do. */
public class AccountabilityActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!Auth.sessionOpen()) {
            Ui.screen(this, "Accountability partner", true);
            Auth.requirePin(this, new Runnable() { @Override public void run() { render(); } });
            return;
        }
        render();
    }

    private void render() {
        final Config c = Config.get(this);
        Ui.Screen s = Ui.screen(this, "Accountability partner", true);
        LinearLayout v = s.content;

        if (!c.partnerEnabled) {
            Ui.banner(v, R.drawable.ic_people, "How it works",
                    "Your partner is told WHEN you hit a block, try to get around protection, or loosen a setting, plus a daily "
                            + "report of totals. They're never told which website, word or search it was.", 0, null, null);
            Ui.body(v, "Messages use the free ntfy.sh service, on private random channel names only you and your partner know.");
            Ui.button(v, "Set up accountability", new View.OnClickListener() {
                @Override public void onClick(View x) { Partner.enable(AccountabilityActivity.this, c); render(); }
            });
            return;
        }

        final String code = Partner.pairingCode(c);
        Ui.section(v, "1 · Send your partner this code");
        LinearLayout pc = Ui.paddedCard(v);
        Ui.body(pc, "They install Steadfast, choose “I'm an accountability partner”, tap Add device and paste it.");
        Ui.mono(pc, code);
        Ui.button(pc, "Share pairing code", new View.OnClickListener() {
            @Override public void onClick(View x) {
                Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, "Install Steadfast, choose “I'm an accountability partner”, tap Add device and paste:\n\n" + code);
                startActivity(Intent.createChooser(send, "Send pairing code"));
            }
        });
        Ui.tonalButton(pc, "Copy code", new View.OnClickListener() {
            @Override public void onClick(View x) { copy("Steadfast pairing code", code); }
        });

        Ui.section(v, "Partner has an iPhone?");
        LinearLayout ip = Ui.paddedCard(v);
        Ui.body(ip, "1. Install the free ntfy app and subscribe to this topic (server ntfy.sh):");
        Ui.mono(ip, c.alertTopic);
        Ui.body(ip, "2. In Google or Microsoft Authenticator choose “Enter a setup key”, name it Steadfast, time-based:");
        Ui.mono(ip, c.secret);
        Ui.body(ip, "The 6-digit code in their authenticator is the partner code you'll ask for to loosen settings or unlock.");
        Ui.tonalButton(ip, "Copy topic and key", new View.OnClickListener() {
            @Override public void onClick(View x) { copy("Steadfast", "ntfy topic: " + c.alertTopic + "\nAuthenticator key: " + c.secret); }
        });

        Ui.section(v, "Options");
        LinearLayout o = Ui.card(v);
        Ui.row(o, R.drawable.ic_key, "Partner approval", "Their code is needed to loosen any setting",
                c.partnerApproval ? Ui.T_SWITCH_ON : Ui.T_SWITCH_OFF, new View.OnClickListener() {
                    @Override public void onClick(View x) {
                        if (c.partnerApproval) {
                            Auth.requireLoosen(AccountabilityActivity.this, "turned off partner approval", new Runnable() {
                                @Override public void run() { c.partnerApproval = false; c.save(AccountabilityActivity.this); render(); }
                            });
                        } else {
                            c.partnerApproval = true;
                            c.save(AccountabilityActivity.this);
                            EventLog.record(AccountabilityActivity.this, EventLog.CHANGE, "Partner approval is now required to loosen settings.");
                            render();
                        }
                    }
                });
        Ui.divider(o);
        Ui.row(o, R.drawable.ic_bell, "Instant alerts", "Off = daily report only",
                c.instantAlerts ? Ui.T_SWITCH_ON : Ui.T_SWITCH_OFF, new View.OnClickListener() {
                    @Override public void onClick(View x) {
                        if (c.instantAlerts) {
                            Auth.requireLoosen(AccountabilityActivity.this, "turned off instant alerts", new Runnable() {
                                @Override public void run() { c.instantAlerts = false; c.save(AccountabilityActivity.this); render(); }
                            });
                        } else { c.instantAlerts = true; c.save(AccountabilityActivity.this); render(); }
                    }
                });
        Ui.divider(o);
        Ui.row(o, R.drawable.ic_chart, "Top app names in reports", "Off = totals only",
                c.shareAppNames ? Ui.T_SWITCH_ON : Ui.T_SWITCH_OFF, new View.OnClickListener() {
                    @Override public void onClick(View x) {
                        if (c.shareAppNames) {
                            Auth.requireLoosen(AccountabilityActivity.this, "stopped sharing app names in reports", new Runnable() {
                                @Override public void run() { c.shareAppNames = false; c.save(AccountabilityActivity.this); render(); }
                            });
                        } else { c.shareAppNames = true; c.save(AccountabilityActivity.this); render(); }
                    }
                });
        Ui.divider(o);
        Ui.row(o, R.drawable.ic_shield, "Device name", c.deviceName, Ui.T_CHEVRON, new View.OnClickListener() {
            @Override public void onClick(View x) {
                Ui.prompt(AccountabilityActivity.this, "Device name", "Shown to your partner", c.deviceName,
                        InputType.TYPE_CLASS_TEXT, new Ui.TextCallback() {
                            @Override public void on(String t) {
                                if (!t.trim().isEmpty()) { c.deviceName = t.trim(); c.save(AccountabilityActivity.this); render(); }
                            }
                        });
            }
        });

        Ui.section(v, "Test");
        LinearLayout t = Ui.card(v);
        Ui.row(t, R.drawable.ic_send, "Send a test alert", null, Ui.T_NONE, new View.OnClickListener() {
            @Override public void onClick(View x) { Partner.sendTest(AccountabilityActivity.this); Ui.toast(AccountabilityActivity.this, "Sent"); }
        });
        Ui.divider(t);
        Ui.row(t, R.drawable.ic_chart, "Send today's report now", null, Ui.T_NONE, new View.OnClickListener() {
            @Override public void onClick(View x) {
                new Thread(new Runnable() { @Override public void run() { Reports.sendNow(AccountabilityActivity.this); } }).start();
                Ui.toast(AccountabilityActivity.this, "Sending");
            }
        });

        Ui.section(v, "Advanced");
        LinearLayout a = Ui.card(v);
        Ui.row(a, R.drawable.ic_globe, "Notification server", c.server, Ui.T_CHEVRON, new View.OnClickListener() {
            @Override public void onClick(View x) {
                Auth.requireLoosen(AccountabilityActivity.this, "changed the notification server", new Runnable() {
                    @Override public void run() {
                        Ui.prompt(AccountabilityActivity.this, "ntfy server", "Only change this if you host your own ntfy server.",
                                c.server, InputType.TYPE_TEXT_VARIATION_URI, new Ui.TextCallback() {
                                    @Override public void on(String t2) {
                                        if (t2.startsWith("https://")) { c.server = t2.trim(); c.save(AccountabilityActivity.this); render(); }
                                        else Ui.toast(AccountabilityActivity.this, "Must start with https://");
                                    }
                                });
                    }
                });
            }
        });
        Ui.divider(a);
        Ui.row(a, R.drawable.ic_close, "Disconnect partner", "Creates new keys; your current partner stops receiving anything",
                Ui.T_NONE, null, new View.OnClickListener() {
                    @Override public void onClick(View x) {
                        Auth.requireLoosen(AccountabilityActivity.this, "disconnected the accountability partner", new Runnable() {
                            @Override public void run() {
                                Partner.flushNow(AccountabilityActivity.this);
                                Partner.regenerate(AccountabilityActivity.this, c);
                                render();
                            }
                        });
                    }
                }, 2);
    }

    private void copy(String label, String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(label, text));
        Ui.toast(this, "Copied");
    }
}
