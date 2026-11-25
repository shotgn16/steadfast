package com.steadfast.guard;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

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
            Auth.requirePin(this, new Runnable() { @Override public void run() { render(); } });
            Ui.page(this, "Accountability partner");
            return;
        }
        render();
    }

    private void render() {
        final Config c = Config.get(this);
        LinearLayout p = Ui.page(this, "Accountability partner");
        Ui.title(p, "Accountability partner");
        Ui.para(p, "Your partner is told WHEN you hit a block, try to get around protection, or loosen a setting, "
                + "plus a daily report of totals. They are never told which website, word or search it was.");

        if (!c.partnerEnabled) {
            Ui.button(p, "Set up accountability", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Partner.enable(AccountabilityActivity.this, c);
                    render();
                }
            });
            Ui.para(p, "Uses the free ntfy.sh notification service. Messages go to private random channel names "
                    + "that only you and your partner know.");
            return;
        }

        Ui.header(p, "Step 1: Send your partner this pairing code");
        final String code = Partner.pairingCode(c);
        TextView codeView = Ui.para(p, code);
        codeView.setTypeface(Typeface.MONOSPACE);
        codeView.setTextIsSelectable(true);
        codeView.setTextSize(12);
        Ui.button(p, "Share pairing code…", new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, "Install Steadfast, choose “I'm an accountability partner”, tap Add device and paste:\n\n" + code);
                startActivity(Intent.createChooser(send, "Send pairing code"));
            }
        });
        Ui.button(p, "Copy pairing code", new View.OnClickListener() {
            @Override public void onClick(View v) { copy("Steadfast pairing code", code); }
        });

        Ui.header(p, "Partner uses an iPhone or no Steadfast app?");
        Ui.para(p, "1. Install the free “ntfy” app and subscribe to this topic (server ntfy.sh):");
        TextView topic = Ui.para(p, c.alertTopic);
        topic.setTypeface(Typeface.MONOSPACE);
        topic.setTextIsSelectable(true);
        Ui.para(p, "2. In Google Authenticator / Microsoft Authenticator choose “Enter a setup key”, name it Steadfast, and use this key (time-based):");
        TextView key = Ui.para(p, c.secret);
        key.setTypeface(Typeface.MONOSPACE);
        key.setTextIsSelectable(true);
        Ui.para(p, "The 6-digit code in their authenticator is the “partner code” you'll ask for to loosen settings or unlock.");
        Ui.button(p, "Copy topic + key", new View.OnClickListener() {
            @Override public void onClick(View v) { copy("Steadfast", "ntfy topic: " + c.alertTopic + "\nAuthenticator key: " + c.secret); }
        });

        Ui.header(p, "Options");
        Ui.row(p, "Partner approval to loosen settings", c.partnerApproval
                ? "On: removing rules, pausing, etc. needs your partner's code"
                : "Off: your master PIN is enough (partner is still told)", new View.OnClickListener() {
            @Override public void onClick(View v) {
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
        Ui.row(p, "Instant alerts for blocked content", c.instantAlerts ? "On" : "Off (daily report only)", new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (c.instantAlerts) {
                    Auth.requireLoosen(AccountabilityActivity.this, "turned off instant alerts", new Runnable() {
                        @Override public void run() { c.instantAlerts = false; c.save(AccountabilityActivity.this); render(); }
                    });
                } else { c.instantAlerts = true; c.save(AccountabilityActivity.this); render(); }
            }
        });
        Ui.row(p, "Include top app names in daily report", c.shareAppNames ? "On" : "Off (totals only)", new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (c.shareAppNames) {
                    Auth.requireLoosen(AccountabilityActivity.this, "stopped sharing app names in reports", new Runnable() {
                        @Override public void run() { c.shareAppNames = false; c.save(AccountabilityActivity.this); render(); }
                    });
                } else { c.shareAppNames = true; c.save(AccountabilityActivity.this); render(); }
            }
        });
        Ui.row(p, "Device name", c.deviceName, new View.OnClickListener() {
            @Override public void onClick(View v) {
                Ui.prompt(AccountabilityActivity.this, "Device name", "Shown to your partner", c.deviceName,
                        InputType.TYPE_CLASS_TEXT, new Ui.TextCallback() {
                            @Override public void on(String t) {
                                if (!t.trim().isEmpty()) { c.deviceName = t.trim(); c.save(AccountabilityActivity.this); render(); }
                            }
                        });
            }
        });

        Ui.header(p, "Test");
        Ui.button(p, "Send a test alert", new View.OnClickListener() {
            @Override public void onClick(View v) { Partner.sendTest(AccountabilityActivity.this); Ui.toast(AccountabilityActivity.this, "Sent"); }
        });
        Ui.button(p, "Send today's report now", new View.OnClickListener() {
            @Override public void onClick(View v) {
                new Thread(new Runnable() { @Override public void run() { Reports.sendNow(AccountabilityActivity.this); } }).start();
                Ui.toast(AccountabilityActivity.this, "Sending");
            }
        });

        Ui.header(p, "Advanced");
        Ui.row(p, "Notification server", c.server + " (change only if you self-host ntfy)", new View.OnClickListener() {
            @Override public void onClick(View v) {
                Auth.requireLoosen(AccountabilityActivity.this, "changed the notification server", new Runnable() {
                    @Override public void run() {
                        Ui.prompt(AccountabilityActivity.this, "ntfy server", null, c.server, InputType.TYPE_TEXT_VARIATION_URI,
                                new Ui.TextCallback() {
                                    @Override public void on(String t) {
                                        if (t.startsWith("https://")) { c.server = t.trim(); c.save(AccountabilityActivity.this); render(); }
                                        else Ui.toast(AccountabilityActivity.this, "Must start with https://");
                                    }
                                });
                    }
                });
            }
        });
        Ui.row(p, "Disconnect partner / new keys", "Your current partner will stop receiving anything", new View.OnClickListener() {
            @Override public void onClick(View v) {
                Auth.requireLoosen(AccountabilityActivity.this, "disconnected the accountability partner", new Runnable() {
                    @Override public void run() {
                        Partner.flushNow(AccountabilityActivity.this);
                        Partner.regenerate(AccountabilityActivity.this, c);
                        render();
                    }
                });
            }
        });
    }

    private void copy(String label, String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(label, text));
        Ui.toast(this, "Copied");
    }
}
