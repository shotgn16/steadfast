package com.steadfast.guard;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Enforces app rules, time limits, downtime, keyword & search filtering and protects Steadfast's own settings.
 * It reads screen text locally; nothing it reads is stored or sent anywhere except the TYPE of a block.
 */
public class GuardAccessibilityService extends AccessibilityService {

    /** URL bar view ids of common browsers. */
    private static final String[][] BROWSERS = {
            {"com.android.chrome", "com.android.chrome:id/url_bar"},
            {"com.chrome.beta", "com.chrome.beta:id/url_bar"},
            {"com.chrome.dev", "com.chrome.dev:id/url_bar"},
            {"com.brave.browser", "com.brave.browser:id/url_bar"},
            {"com.microsoft.emmx", "com.microsoft.emmx:id/url_bar"},
            {"com.kiwibrowser.browser", "com.kiwibrowser.browser:id/url_bar"},
            {"com.vivaldi.browser", "com.vivaldi.browser:id/url_bar"},
            {"org.mozilla.firefox", "org.mozilla.firefox:id/mozac_browser_toolbar_url_view"},
            {"org.mozilla.fenix", "org.mozilla.fenix:id/mozac_browser_toolbar_url_view"},
            {"org.mozilla.focus", "org.mozilla.focus:id/display_url"},
            {"com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser:id/location_bar_edit_text"},
            {"com.opera.browser", "com.opera.browser:id/url_field"},
            {"com.opera.mini.native", "com.opera.mini.native:id/url_field"},
            {"com.duckduckgo.mobile.android", "com.duckduckgo.mobile.android:id/omnibarTextInput"},
            {"com.mi.globalbrowser", "com.mi.globalbrowser:id/url"},
            {"com.UCMobile.intl", "com.UCMobile.intl:id/url"},
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private long lastScan;
    private String lastScanPkg = "";
    private long lastBlockTime;
    private long lastPoll;
    private String appLabelLower = "steadfast";

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            try { periodic(); } catch (Exception ignored) {}
            handler.postDelayed(this, 15000);
        }
    };

    static volatile GuardAccessibilityService instance;

    /** Used by "Turn Steadfast off completely". */
    static void shutdownService() {
        GuardAccessibilityService s = instance;
        if (s != null) {
            try { s.disableSelf(); } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        appLabelLower = getString(R.string.app_name).toLowerCase(Locale.ROOT);
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, 5000);
        Jobs.schedule(this);
        Guard.startVpn(this);
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        instance = null;
        handler.removeCallbacks(tick);
        bg.shutdownNow();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ events

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getPackageName() == null) return;
        Config cfg = Config.get(this);
        if (!cfg.isProtected()) return;
        String pkg = e.getPackageName().toString();
        if (pkg.equals(getPackageName())) return;
        int type = e.getEventType();

        if (isSystemSettings(pkg)) {
            if (cfg.settingsGuarded()) guardSettings();
            return;
        }
        if ("com.android.systemui".equals(pkg)) {
            // e.g. the "Disconnect VPN" dialog from quick settings
            if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && cfg.settingsGuarded()) guardSettings();
            return;
        }
        if (!cfg.rulesActive()) return;

        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            String fg = foregroundPackage();
            if (fg != null && checkApp(cfg, fg)) return;
        }
        if (type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            if (checkTyped(cfg, e)) return;
        }
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (checkBrowser(cfg, pkg)) return;
            maybeScanScreen(cfg, pkg);
        }
    }

    private String foregroundPackage() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null) return null;
        return root.getPackageName().toString();
    }

    private void periodic() {
        Config cfg = Config.get(this);
        if (cfg.isProtected()) {
            if (cfg.rulesActive()) {
                String fg = foregroundPackage();
                if (fg != null && !fg.equals(getPackageName()) && !isSystemSettings(fg)) checkApp(cfg, fg);
            }
            if (!FilterVpnService.running) Guard.startVpn(this);
        }
        long now = System.currentTimeMillis();
        if (now - lastPoll > 60000) {
            lastPoll = now;
            bg.execute(new Runnable() {
                @Override public void run() {
                    Partner.pollCommands(GuardAccessibilityService.this);
                    Partner.flush(GuardAccessibilityService.this);
                }
            });
        }
    }

    // ------------------------------------------------------------------ checks

    private boolean checkApp(Config cfg, String pkg) {
        Policy.Verdict v = Policy.appVerdict(this, cfg, pkg);
        if (v == null) return false;
        block(v.type, v.message, Guard.appLabel(this, pkg), true);
        return true;
    }

    private boolean checkTyped(Config cfg, AccessibilityEvent e) {
        if (e.isPassword()) return false;
        StringBuilder sb = new StringBuilder();
        List<CharSequence> texts = e.getText();
        if (texts != null) for (CharSequence t : texts) if (t != null) sb.append(t).append(' ');
        String hit = Policy.findSearchTerm(cfg, sb);
        if (hit == null) return false;
        AccessibilityNodeInfo src = e.getSource();
        if (src != null && src.isEditable()) {
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "");
            src.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        }
        block(EventLog.SEARCH, "That search matches your search filter.", hit, true);
        return true;
    }

    private boolean checkBrowser(Config cfg, String pkg) {
        String id = null;
        for (String[] b : BROWSERS) if (b[0].equals(pkg)) { id = b[1]; break; }
        if (id == null) return false;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
        if (nodes == null || nodes.isEmpty() || nodes.get(0).getText() == null) return false;
        String url = nodes.get(0).getText().toString().trim();
        if (url.isEmpty()) return false;

        // search query in the URL (google.com/search?q=...)
        String query = Policy.searchQueryFromUrl(url);
        if (query != null) {
            String hit = Policy.findSearchTerm(cfg, query);
            if (hit != null) {
                block(EventLog.SEARCH, "That search matches your search filter.", hit, true);
                return true;
            }
        }
        // domain rules
        if (!url.contains(" ") && url.contains(".")) {
            String domain = Policy.normalizeDomain(url);
            String full = url.toLowerCase(Locale.ROOT);
            int s = full.indexOf("://");
            String host = s >= 0 ? full.substring(s + 3) : full;
            int slash = host.indexOf('/');
            if (slash >= 0) host = host.substring(0, slash);
            if (!Policy.matchesDomain(cfg.allowedDomains, domain)
                    && (Policy.matchesDomain(cfg.blockedDomains, domain) || Policy.matchesDomain(cfg.blockedDomains, host))) {
                block(EventLog.SITE, "This website is on your blocked list.", domain, true);
                return true;
            }
            if (FilterVpnService.recentlyCategoryBlocked(domain) || FilterVpnService.recentlyCategoryBlocked(host)) {
                block(EventLog.SITE, "This website is blocked by your web filter (adult or harmful content).", domain, true);
                return true;
            }
            if (!Policy.matchesDomain(cfg.allowedDomains, domain)) {
                String hit = Policy.findKeyword(cfg, url);
                if (hit != null) {
                    block(EventLog.KEYWORD, "This address matches your keyword filter.", hit, true);
                    return true;
                }
            }
        } else {
            // user is typing in the address bar
            String hit = Policy.findSearchTerm(cfg, url);
            if (hit != null) {
                block(EventLog.SEARCH, "That search matches your search filter.", hit, true);
                return true;
            }
        }
        return false;
    }

    private void maybeScanScreen(Config cfg, String pkg) {
        if (cfg.keywords.isEmpty()) return;
        boolean isBrowser = false;
        for (String[] b : BROWSERS) if (b[0].equals(pkg)) { isBrowser = true; break; }
        if (!cfg.scanAllApps && !isBrowser) return;
        Set<String> skip = Guard.alwaysAllowed(this);
        if (skip.contains(pkg)) return; // launcher, keyboard, system UI, phone
        long now = System.currentTimeMillis();
        if (pkg.equals(lastScanPkg) && now - lastScan < 1200) return;
        lastScan = now;
        lastScanPkg = pkg;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        StringBuilder sb = new StringBuilder();
        collectText(root, sb, 0, new int[]{0});
        String hit = Policy.findKeyword(cfg, sb);
        if (hit != null) block(EventLog.KEYWORD, "Content on this screen matches your keyword filter.", hit, true);
    }

    /** Collects visible text and image descriptions (alt text). */
    private void collectText(AccessibilityNodeInfo n, StringBuilder sb, int depth, int[] count) {
        if (n == null || depth > 40 || count[0] > 600 || sb.length() > 12000) return;
        count[0]++;
        if (n.isPassword()) return;
        CharSequence t = n.getText();
        if (t != null) sb.append(t).append('\n');
        CharSequence d = n.getContentDescription();
        if (d != null) sb.append(d).append('\n');
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo child = n.getChild(i);
            if (child != null && child.isVisibleToUser()) collectText(child, sb, depth + 1, count);
        }
    }

    // ------------------------------------------------------------------ self-protection

    private static boolean isSystemSettings(String pkg) {
        return pkg.contains("settings") || pkg.contains("packageinstaller")
                || pkg.contains("permissioncontroller") || pkg.contains("securitycenter")
                || pkg.contains("safecenter") || pkg.equals("com.android.vpndialogs");
    }

    private void guardSettings() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        StringBuilder sb = new StringBuilder();
        collectText(root, sb, 0, new int[]{0});
        String s = sb.toString().toLowerCase(Locale.ROOT);
        boolean mentionsUs = s.contains(appLabelLower);
        boolean dangerous = s.contains("uninstall") || s.contains("force stop") || s.contains("deactivate")
                || s.contains("use " + appLabelLower) || s.contains("clear storage") || s.contains("clear data")
                || s.contains("always-on vpn") || s.contains("forget vpn") || s.contains("disconnect")
                || s.contains("stop " + appLabelLower) || s.contains("usage access")
                || s.contains(appLabelLower + " shortcut");
        boolean privateDns = s.contains("private dns provider hostname") || (s.contains("private dns") && s.contains("hostname"));
        if ((mentionsUs && dangerous) || privateDns) {
            block(EventLog.TAMPER, "Steadfast's system settings are protected. Unlock Steadfast with your PIN, then use “Open system settings”.",
                    "Tried to open protected system settings (uninstall / disable / private DNS).", true);
        }
    }

    // ------------------------------------------------------------------ blocking

    private void block(String type, String message, String detail, boolean goHome) {
        long now = System.currentTimeMillis();
        if (now - lastBlockTime < 1500) return;
        lastBlockTime = now;
        if (goHome) performGlobalAction(GLOBAL_ACTION_HOME);
        Intent i = new Intent(this, BlockActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(BlockActivity.EXTRA_TYPE, type)
                .putExtra(BlockActivity.EXTRA_MESSAGE, message);
        try { startActivity(i); } catch (Exception ignored) {}
        final String t = type, d = detail;
        bg.execute(new Runnable() {
            @Override public void run() { EventLog.record(GuardAccessibilityService.this, t, d); }
        });
    }
}
