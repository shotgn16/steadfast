package com.steadfast.guard;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TimePicker;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
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
        if (!Auth.sessionOpen()) renderLocked(); else renderDashboard();
    }

    // ------------------------------------------------------------------ welcome

    private void renderWelcome() {
        LinearLayout p = Ui.page(this, "Steadfast");
        Ui.title(p, "Welcome to Steadfast");
        Ui.para(p, "Steadfast helps you guard your eyes and your time: app limits, website and keyword blocking, "
                + "NFC locking, and accountability with a trusted partner who sees when a line is crossed, "
                + "but never what you searched or watched.");
        Ui.header(p, "How will you use this phone?");
        Ui.button(p, "Protect this phone", new View.OnClickListener() {
            @Override public void onClick(View v) {
                Config c = Config.get(MainActivity.this);
                c.role = Config.ROLE_PROTECTED;
                c.save(MainActivity.this);
                render();
            }
        });
        Ui.button(p, "I'm an accountability partner", new View.OnClickListener() {
            @Override public void onClick(View v) {
                Config c = Config.get(MainActivity.this);
                c.role = Config.ROLE_PARTNER;
                c.save(MainActivity.this);
                render();
            }
        });
        Ui.para(p, "Partners only need this app to receive alerts, see reports and approve changes. "
                + "A partner with an iPhone can use the free ntfy and authenticator apps instead.");
    }

    // ------------------------------------------------------------------ locked view

    private void renderLocked() {
        Config c = Config.get(this);
        LinearLayout p = Ui.page(this, "Steadfast");
        Ui.title(p, "Steadfast");
        statusBlock(p, c, false);
        Ui.button(p, "Unlock settings (master PIN)", new View.OnClickListener() {
            @Override public void onClick(View v) {
                Auth.requirePin(MainActivity.this, new Runnable() { @Override public void run() { render(); } });
            }
        });
        if (!c.nfcTags.isEmpty()) {
            Ui.button(p, c.nfcLocked ? "Unlock with NFC tag" : "Lock with NFC tag", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    startActivity(new Intent(MainActivity.this, NfcActivity.class));
                }
            });
        }
        if (c.partnerEnabled) {
            Ui.para(p, "Your accountability partner can pause or unlock protection remotely.");
        }
    }

    private void statusBlock(LinearLayout p, Config c, boolean withFixes) {
        Ui.header(p, "Status");
        if (c.isPaused()) {
            Ui.status(p, "Restrictions paused until " + new SimpleDateFormat("HH:mm", Locale.UK).format(new Date(c.pausedUntil)), false);
        } else {
            Ui.status(p, "Restrictions are on", true);
        }
        if (c.nfcLocked) Ui.status(p, "Settings are NFC-locked" + (c.nfcLockdown ? " (lockdown: essential apps only)" : ""), true);
        boolean acc = Guard.accessibilityEnabled(this);
        boolean vpn = Guard.vpnPermitted(this) && FilterVpnService.running;
        boolean admin = Guard.adminActive(this);
        boolean usage = Guard.usageAccessGranted(this);
        Ui.status(p, acc ? "App & content protection on" : "App & content protection OFF", acc);
        if (withFixes && !acc) Ui.button(p, "Fix: turn on accessibility service", fixClick(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        Ui.status(p, vpn ? "Web filter on" : "Web filter OFF", vpn);
        if (withFixes && !vpn) Ui.button(p, "Fix: start web filter", new View.OnClickListener() {
            @Override public void onClick(View v) { startFilter(); }
        });
        Ui.status(p, admin ? "Uninstall protection on" : "Uninstall protection OFF", admin);
        if (withFixes && !admin) Ui.button(p, "Fix: turn on uninstall protection", new View.OnClickListener() {
            @Override public void onClick(View v) { openSettingsWindow(); requestAdmin(MainActivity.this); }
        });
        Ui.status(p, usage ? "Time tracking on" : "Time tracking OFF (usage access)", usage);
        if (withFixes && !usage) Ui.button(p, "Fix: allow usage access", fixClick(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        Ui.status(p, c.partnerEnabled ? "Accountability partner connected" : "No accountability partner yet", c.partnerEnabled);
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

    // ------------------------------------------------------------------ dashboard

    private void renderDashboard() {
        final Config c = Config.get(this);
        LinearLayout p = Ui.page(this, "Steadfast");
        Ui.title(p, "Steadfast");
        statusBlock(p, c, true);

        Ui.header(p, "Restrictions");
        if (c.isPaused()) {
            Ui.button(p, "Resume restrictions now", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    c.pausedUntil = 0; c.save(MainActivity.this);
                    EventLog.record(MainActivity.this, EventLog.CHANGE, "Restrictions resumed.");
                    render();
                }
            });
        } else {
            Ui.row(p, "Pause restrictions", "Temporarily switch rules off (your partner is told)", new View.OnClickListener() {
                @Override public void onClick(View v) { pause(); }
            });
        }
        Ui.row(p, "Apps & time limits", appsSummary(c), new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(MainActivity.this, AppsActivity.class)); }
        });
        Ui.row(p, "Downtime", c.downtimeEnabled ? "On, " + Guard.hhmm(c.downtimeStart) + "–" + Guard.hhmm(c.downtimeEnd)
                + " (essential apps only)" : "Off", new View.OnClickListener() {
            @Override public void onClick(View v) { downtime(); }
        });

        Ui.header(p, "Web & content");
        Ui.row(p, "Web filter level", Policy.filterLevelName(c.filterLevel), new View.OnClickListener() {
            @Override public void onClick(View v) { filterLevel(); }
        });
        listRow(p, "Blocked websites", c.blockedDomains.size() + " domains (subdomains included)", ListEditorActivity.LIST_BLOCKED);
        listRow(p, "Always-allowed websites", c.allowedDomains.size() + " exceptions", ListEditorActivity.LIST_ALLOWED);
        listRow(p, "Keyword filter", c.keywords.size() + " words: blocks pages, images (by description) and screens", ListEditorActivity.LIST_KEYWORDS);
        listRow(p, "Search filter", c.searchTerms.size() + " terms: blocks searches and typing", ListEditorActivity.LIST_SEARCH);
        toggleRow(p, "SafeSearch (Google, Bing, DuckDuckGo, YouTube restricted mode)", c.safeSearch, "turned SafeSearch off",
                new Toggle() { @Override public void set(boolean on) { c.safeSearch = on; } });
        toggleRow(p, "Scan text in all apps (not just browsers)", c.scanAllApps, "limited keyword scanning to browsers",
                new Toggle() { @Override public void set(boolean on) { c.scanAllApps = on; } });
        Ui.row(p, "Bible verse on block screen", c.showVerses ? "On" : "Off", new View.OnClickListener() {
            @Override public void onClick(View v) { c.showVerses = !c.showVerses; c.save(MainActivity.this); render(); }
        });

        Ui.header(p, "Locking & accountability");
        Ui.row(p, "NFC lock", c.nfcTags.isEmpty() ? "No tag registered" : c.nfcTags.size() + " tag(s) · "
                + (c.nfcLocked ? "LOCKED" : "unlocked"), new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, NfcActivity.class).putExtra(NfcActivity.EXTRA_MODE, NfcActivity.MODE_MANAGE));
            }
        });
        Ui.row(p, "Accountability partner", c.partnerEnabled ? "Connected" + (c.partnerApproval ? " · approval required" : "")
                : "Set up a partner", new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(MainActivity.this, AccountabilityActivity.class)); }
        });

        Ui.header(p, "Security");
        Ui.row(p, "Activity on this phone", "Your private log (details never leave this phone)", new View.OnClickListener() {
            @Override public void onClick(View v) { showLog(); }
        });
        Ui.row(p, "Change master PIN", null, new View.OnClickListener() {
            @Override public void onClick(View v) { changePin(); }
        });
        Ui.row(p, "Open Android settings for Steadfast", "Unlocks protected system pages for 5 minutes", new View.OnClickListener() {
            @Override public void onClick(View v) {
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
        Ui.row(p, "Turn Steadfast off completely", "Removes all protection so it can be uninstalled", new View.OnClickListener() {
            @Override public void onClick(View v) { turnOff(); }
        });
        Ui.button(p, "Lock settings now", new View.OnClickListener() {
            @Override public void onClick(View v) { Auth.endSession(); render(); }
        });
    }

    private String appsSummary(Config c) {
        int blocked = 0, limited = 0;
        for (Config.AppRule r : c.apps.values()) {
            if (r.blocked) blocked++;
            else if (r.limitMin >= 0 || r.startMin >= 0) limited++;
        }
        return blocked + " blocked · " + limited + " with limits";
    }

    private void listRow(LinearLayout p, String title, String sub, final String list) {
        Ui.row(p, title, sub, new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, ListEditorActivity.class).putExtra(ListEditorActivity.EXTRA_LIST, list));
            }
        });
    }

    interface Toggle { void set(boolean on); }

    private void toggleRow(LinearLayout p, String title, final boolean current, final String loosenText, final Toggle t) {
        Ui.row(p, title, current ? "On" : "Off", new View.OnClickListener() {
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
        String[] items = {c.downtimeEnabled ? "Turn downtime off" : "Turn downtime on", "Change start time", "Change end time"};
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
                        new TimePickerDialog(MainActivity.this, new TimePickerDialog.OnTimeSetListener() {
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
                "Adult content + malware (Cloudflare for Families), recommended",
                "Strict family (CleanBrowsing): also blocks proxy/VPN sites and mixed content"
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
            sb.append(f.format(new Date(e.optLong("t")))).append("  ").append(EventLog.label(e.optString("type")));
            String d = e.optString("d");
            if (!d.isEmpty()) sb.append(": ").append(d);
            sb.append("\n\n");
        }
        if (sb.length() == 0) sb.append("Nothing yet.");
        new AlertDialog.Builder(this).setTitle("Activity (this phone only)").setMessage(sb.toString())
                .setPositiveButton("Close", null).show();
    }

    private void turnOff() {
        Ui.confirm(MainActivity.this, "Turn off Steadfast?",
                "All protection will stop and Steadfast can then be uninstalled. Your partner will be told.", new Runnable() {
            @Override public void run() {
                Auth.requireLoosen(MainActivity.this, "turned Steadfast off completely",
                        new Runnable() {
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
                                Ui.alert(MainActivity.this, "Steadfast is off",
                                        "All protection has stopped. You can now uninstall Steadfast like any other app.");
                                render();
                            }
                        });
            }
        });
    }
}
