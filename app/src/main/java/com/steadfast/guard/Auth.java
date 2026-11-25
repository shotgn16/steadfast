package com.steadfast.guard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;

import java.util.ArrayList;
import java.util.List;

/**
 * Who may change what.
 *  - Opening settings / tightening rules: master PIN.
 *  - Loosening rules: master PIN, or the partner's 6-digit code if "partner approval" is on.
 *  - While NFC-locked: loosening needs the NFC tag or the partner's code.
 * Every loosening is reported to the partner (without details).
 */
final class Auth {
    private Auth() {}

    private static long sessionUntil;
    private static int fails;
    private static long lockoutUntil;

    static boolean sessionOpen() {
        return System.currentTimeMillis() < sessionUntil;
    }

    static void endSession() {
        sessionUntil = 0;
    }

    static void setPin(Activity a, String pin) {
        Config c = Config.get(a);
        c.pinSalt = Crypto.hex(Crypto.random(16));
        c.pinHash = Crypto.hashPin(pin, c.pinSalt);
        c.save(a);
        sessionUntil = System.currentTimeMillis() + 5 * 60000L;
    }

    static boolean checkPin(Activity a, String pin) {
        Config c = Config.get(a);
        long now = System.currentTimeMillis();
        if (now < lockoutUntil) {
            Ui.toast(a, "Too many attempts. Try again in a minute.");
            return false;
        }
        if (!c.pinHash.isEmpty() && Crypto.safeEquals(Crypto.hashPin(pin, c.pinSalt), c.pinHash)) {
            fails = 0;
            sessionUntil = now + 5 * 60000L;
            return true;
        }
        fails++;
        if (fails % 3 == 0) {
            lockoutUntil = now + 60000L;
            EventLog.record(a, EventLog.TAMPER, "Several wrong master PIN attempts.");
        }
        Ui.toast(a, "Incorrect PIN");
        return false;
    }

    /** Runs {@code ok} once the master PIN has been entered (re-uses an unlocked session for 5 minutes). */
    static void requirePin(final Activity a, final Runnable ok) {
        if (sessionOpen()) { ok.run(); return; }
        Ui.pin(a, "Master PIN", null, new Ui.TextCallback() {
            @Override public void on(String pin) { if (checkPin(a, pin)) ok.run(); }
        });
    }

    /**
     * Use for any change that gives more freedom. {@code what} must be a generic description
     * (e.g. "removed a blocked website") — it is sent to the partner.
     */
    static void requireLoosen(final Activity a, final String what, final Runnable ok) {
        final Config c = Config.get(a);
        final Runnable approved = new Runnable() {
            @Override public void run() {
                // Record first, so the partner is told even if the change disconnects them.
                EventLog.record(a, EventLog.CHANGE, "Restrictions loosened: " + what + ".");
                ok.run();
            }
        };
        if (c.nfcLocked) {
            List<String> opts = new ArrayList<>();
            final List<Runnable> actions = new ArrayList<>();
            opts.add("Scan my NFC tag to unlock");
            actions.add(new Runnable() {
                @Override public void run() {
                    a.startActivity(new Intent(a, NfcActivity.class).putExtra(NfcActivity.EXTRA_MODE, NfcActivity.MODE_UNLOCK));
                }
            });
            if (c.partnerEnabled) {
                opts.add("Enter my partner's code");
                actions.add(new Runnable() { @Override public void run() { askPartnerCode(a, approved); } });
            } else {
                opts.add("Lost tag? Use master PIN (recovery)");
                actions.add(new Runnable() {
                    @Override public void run() {
                        Ui.pin(a, "Recovery", "Enter the master PIN to remove the NFC lock.", new Ui.TextCallback() {
                            @Override public void on(String pin) {
                                if (checkPin(a, pin)) {
                                    c.nfcLocked = false;
                                    c.save(a);
                                    EventLog.record(a, EventLog.CHANGE, "NFC lock removed with the master PIN (recovery).");
                                    Ui.toast(a, "NFC lock removed");
                                }
                            }
                        });
                    }
                });
            }
            new AlertDialog.Builder(a).setTitle("Settings are NFC-locked")
                    .setItems(opts.toArray(new String[0]), new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) { actions.get(w).run(); }
                    })
                    .setNegativeButton("Cancel", null).show();
            return;
        }
        if (c.partnerEnabled && c.partnerApproval) {
            askPartnerCode(a, approved);
            return;
        }
        Ui.pin(a, "Confirm with master PIN", "This loosens your protection. Your partner will be told.", new Ui.TextCallback() {
            @Override public void on(String pin) { if (checkPin(a, pin)) approved.run(); }
        });
    }

    static void askPartnerCode(final Activity a, final Runnable ok) {
        final Config c = Config.get(a);
        Ui.pin(a, "Partner approval", "Ask your accountability partner for the 6-digit code shown in their Steadfast or authenticator app.",
                new Ui.TextCallback() {
                    @Override public void on(String code) {
                        if (Crypto.verifyTotp(c.secret, code)) {
                            sessionUntil = System.currentTimeMillis() + 5 * 60000L;
                            ok.run();
                        } else {
                            Ui.toast(a, "That code isn't right (codes change every 30 seconds).");
                            fails++;
                            if (fails % 3 == 0) EventLog.record(a, EventLog.TAMPER, "Several wrong partner-code attempts.");
                        }
                    }
                });
    }
}
