package com.steadfast.guard;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;

/** First-run wizard: PIN and the Android permissions Steadfast needs. */
public class SetupActivity extends Activity {

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        final Config c = Config.get(this);
        LinearLayout p = Ui.page(this, "Set up Steadfast");
        Ui.title(p, "Set up protection");
        Ui.para(p, "Work through each step. Android will show its own screens. Come back here after each one.");

        boolean pin = !c.pinHash.isEmpty();
        boolean usage = Guard.usageAccessGranted(this);
        boolean acc = Guard.accessibilityEnabled(this);
        boolean vpn = Guard.vpnPermitted(this);
        boolean admin = Guard.adminActive(this);
        boolean notif = Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        boolean battery = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());

        step(p, "1. Create your master PIN", pin, "Needed to change any setting. Ideally your accountability partner sets it.",
                "Create PIN", new Runnable() { @Override public void run() { createPin(); } });

        step(p, "2. Allow usage access", usage, "Lets Steadfast measure how long you use each app (for time limits).",
                "Open usage access", new Runnable() {
                    @Override public void run() { open(Settings.ACTION_USAGE_ACCESS_SETTINGS); }
                });

        step(p, "3. Turn on app & content protection", acc,
                "Settings → Accessibility → Steadfast → turn on. This blocks apps, keywords and searches, all on this phone.\n\n"
                        + "If Android says “Restricted setting”: tap the button below to open App info, tap ⋮ (top right) → "
                        + "“Allow restricted settings”, then try again.",
                "Open accessibility settings", new Runnable() {
                    @Override public void run() { open(Settings.ACTION_ACCESSIBILITY_SETTINGS); }
                });
        if (!acc) {
            Ui.button(p, "Open App info (to allow restricted settings)", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
                }
            });
        }

        step(p, "4. Start the web filter", vpn,
                "Creates an on-device filter for website lookups (shows a key icon). Your browsing does not go through any server of ours.",
                "Start web filter", new Runnable() {
                    @Override public void run() {
                        Intent prep = VpnService.prepare(SetupActivity.this);
                        if (prep != null) startActivityForResult(prep, 1);
                        else { Guard.startVpn(SetupActivity.this); render(); }
                    }
                });

        step(p, "5. Turn on uninstall protection", admin,
                "Android won't let Steadfast be removed without first switching this off, which alerts your partner.",
                "Turn on", new Runnable() { @Override public void run() { MainActivity.requestAdmin(SetupActivity.this); } });

        step(p, "6. Allow notifications", notif, "So you can see when your partner changes something.",
                "Allow", new Runnable() {
                    @Override public void run() {
                        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2);
                    }
                });

        step(p, "7. Keep Steadfast running", battery, "Stops Android putting Steadfast to sleep.",
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

        Ui.header(p, "8. Recommended: Always-on VPN");
        Ui.para(p, "In VPN settings tap the ⚙ next to Steadfast and turn on “Always-on VPN” so the filter starts by itself. "
                + "Leave “Block connections without VPN” OFF.");
        Ui.button(p, "Open VPN settings", new View.OnClickListener() {
            @Override public void onClick(View v) { open(Settings.ACTION_VPN_SETTINGS); }
        });

        Ui.header(p, "Finish");
        final boolean ready = pin && acc && vpn;
        Ui.para(p, ready ? "Steadfast is ready. You can add apps, websites and keywords next, then connect your accountability partner."
                : "Steps 1, 3 and 4 are required. The others are strongly recommended.");
        Ui.button(p, "Finish setup", new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!ready) { Ui.toast(SetupActivity.this, "Please finish steps 1, 3 and 4 first."); return; }
                c.setupComplete = true;
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
        Ui.button(p, "Back (change role)", new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!c.pinHash.isEmpty()) { Ui.toast(SetupActivity.this, "A PIN is already set. Finish setup instead."); return; }
                c.role = "";
                c.save(SetupActivity.this);
                startActivity(new Intent(SetupActivity.this, MainActivity.class));
                finish();
            }
        });
    }

    private void step(LinearLayout p, String title, boolean done, String explain, String action, final Runnable run) {
        Ui.header(p, title);
        Ui.status(p, done ? "Done" : "Not done yet", done);
        Ui.para(p, explain);
        if (!done) Ui.button(p, action, new View.OnClickListener() {
            @Override public void onClick(View v) { run.run(); }
        });
    }

    private void open(String action) {
        try { startActivity(new Intent(action)); } catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
    }

    private void createPin() {
        Ui.pin(this, "Create master PIN", "4–10 digits. Don't use a code you use elsewhere.", new Ui.TextCallback() {
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

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
    }
}
