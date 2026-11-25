package com.steadfast.guard;

import android.content.Context;

/** Notices when a protection layer has been switched off and tells the partner. */
final class Health {
    private Health() {}

    static synchronized void check(Context c) {
        Config cfg = Config.get(c);
        if (!cfg.isProtected()) return;
        if (!FilterVpnService.running && Guard.vpnPermitted(c)) Guard.startVpn(c);

        boolean acc = Guard.accessibilityEnabled(c);
        boolean vpn = Guard.vpnPermitted(c);
        boolean admin = Guard.adminActive(c);
        boolean usage = Guard.usageAccessGranted(c);
        String state = (acc ? "A" : "a") + (vpn ? "V" : "v") + (admin ? "D" : "d") + (usage ? "U" : "u");
        String prev = cfg.lastHealth;
        if (state.equals(prev)) return;

        if (prev != null && prev.length() == 4) {
            if (prev.charAt(0) == 'A' && !acc)
                EventLog.record(c, EventLog.TAMPER, "App & content protection (accessibility service) was switched off.");
            if (prev.charAt(1) == 'V' && !vpn)
                EventLog.record(c, EventLog.TAMPER, "The web filter permission was removed.");
            if (prev.charAt(2) == 'D' && !admin)
                EventLog.record(c, EventLog.TAMPER, "Uninstall protection was switched off.");
            if (prev.charAt(3) == 'U' && !usage)
                EventLog.record(c, EventLog.TAMPER, "Usage access was removed (time limits can't be measured).");
            if (prev.charAt(0) == 'a' && acc || prev.charAt(1) == 'v' && vpn)
                EventLog.record(c, EventLog.CHANGE, "Protection restored.");
        }
        cfg.lastHealth = state;
        cfg.save(c);
    }

    static boolean allGood(Context c) {
        return Guard.accessibilityEnabled(c) && Guard.vpnPermitted(c) && Guard.adminActive(c) && Guard.usageAccessGranted(c);
    }
}
