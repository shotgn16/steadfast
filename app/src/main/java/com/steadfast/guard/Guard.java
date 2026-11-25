package com.steadfast.guard;

import android.app.AppOpsManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.VpnService;
import android.os.Process;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Helpers for checking and starting the different protection layers. */
final class Guard {
    private Guard() {}

    static boolean accessibilityEnabled(Context c) {
        String enabled = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        String me = new ComponentName(c, GuardAccessibilityService.class).flattenToString();
        String meShort = new ComponentName(c, GuardAccessibilityService.class).flattenToShortString();
        for (String s : enabled.split(":")) {
            if (s.equalsIgnoreCase(me) || s.equalsIgnoreCase(meShort)) return true;
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    static boolean usageAccessGranted(Context c) {
        AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
        int mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    static ComponentName adminComponent(Context c) {
        return new ComponentName(c, AdminReceiver.class);
    }

    static boolean adminActive(Context c) {
        DevicePolicyManager dpm = (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm != null && dpm.isAdminActive(adminComponent(c));
    }

    static boolean vpnPermitted(Context c) {
        try {
            return VpnService.prepare(c) == null;
        } catch (Exception e) {
            return false;
        }
    }

    static void startVpn(Context c) {
        if (!vpnPermitted(c)) return;
        try {
            c.startService(new Intent(c, FilterVpnService.class).setAction(FilterVpnService.ACTION_START));
        } catch (Exception ignored) {
            // background start restrictions — the accessibility service / boot receiver will retry
        }
    }

    static void restartVpn(Context c) {
        try {
            c.startService(new Intent(c, FilterVpnService.class).setAction(FilterVpnService.ACTION_RESTART));
        } catch (Exception ignored) {}
    }

    static void stopVpn(Context c) {
        try {
            c.startService(new Intent(c, FilterVpnService.class).setAction(FilterVpnService.ACTION_STOP));
        } catch (Exception ignored) {}
    }

    private static Set<String> alwaysAllowedCache;
    private static long alwaysAllowedTime;

    /** Apps that are never blocked: launcher, phone, keyboards, system UI, settings and Steadfast itself. */
    static synchronized Set<String> alwaysAllowed(Context c) {
        long now = System.currentTimeMillis();
        if (alwaysAllowedCache != null && now - alwaysAllowedTime < 60000) return alwaysAllowedCache;
        Set<String> s = new HashSet<>();
        s.add(c.getPackageName());
        s.add("com.android.systemui");
        s.add("android");
        s.add("com.android.settings");
        s.add("com.android.phone");
        s.add("com.android.server.telecom");
        s.add("com.android.emergency");
        s.add("com.google.android.dialer");
        s.add("com.samsung.android.dialer");
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            List<ResolveInfo> homes = c.getPackageManager().queryIntentActivities(home, 0);
            for (ResolveInfo ri : homes) s.add(ri.activityInfo.packageName);
        } catch (Exception ignored) {}
        try {
            TelecomManager tm = (TelecomManager) c.getSystemService(Context.TELECOM_SERVICE);
            if (tm != null && tm.getDefaultDialerPackage() != null) s.add(tm.getDefaultDialerPackage());
        } catch (Exception ignored) {}
        try {
            InputMethodManager imm = (InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
            for (InputMethodInfo info : imm.getEnabledInputMethodList()) s.add(info.getPackageName());
        } catch (Exception ignored) {}
        alwaysAllowedCache = s;
        alwaysAllowedTime = now;
        return s;
    }

    static Set<String> launchers(Context c) {
        Set<String> s = new HashSet<>();
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            for (ResolveInfo ri : c.getPackageManager().queryIntentActivities(home, 0)) s.add(ri.activityInfo.packageName);
        } catch (Exception ignored) {}
        return s;
    }

    static String appLabel(Context c, String pkg) {
        try {
            return c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    static String hhmm(int minutes) {
        return String.format("%02d:%02d", (minutes / 60) % 24, minutes % 60);
    }

    static String duration(long ms) {
        long m = ms / 60000L;
        if (m < 60) return m + "m";
        return (m / 60) + "h " + (m % 60) + "m";
    }
}
