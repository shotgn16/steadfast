package com.steadfast.guard;

import android.app.Activity;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * NFC lock: scan a registered tag to lock, scan again to unlock.
 * Locked = settings can't be loosened (and optionally only essential apps open) until the tag or partner unlocks.
 * Registered tags get a small Steadfast link written to them so a scan opens this screen even when the app is closed.
 */
public class NfcActivity extends Activity implements NfcAdapter.ReaderCallback {

    static final String EXTRA_MODE = "mode";
    static final String MODE_TOGGLE = "toggle", MODE_UNLOCK = "unlock", MODE_MANAGE = "manage", MODE_REGISTER = "register";

    private static String lastTag = "";
    private static long lastTagTime;

    private String mode = MODE_TOGGLE;
    private NfcAdapter adapter;
    private TextView statusView;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        adapter = NfcAdapter.getDefaultAdapter(this);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @SuppressWarnings("deprecation")
    private void handleIntent(Intent intent) {
        String m = intent.getStringExtra(EXTRA_MODE);
        mode = m == null ? MODE_TOGGLE : m;
        render();
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if (tag != null) onTagDiscovered(tag);  // launched by scanning a tag
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null && adapter.isEnabled()) {
            adapter.enableReaderMode(this, this, NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B
                    | NfcAdapter.FLAG_READER_NFC_F | NfcAdapter.FLAG_READER_NFC_V | NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS, null);
        }
    }

    @Override
    protected void onPause() {
        if (adapter != null) {
            try { adapter.disableReaderMode(this); } catch (Exception ignored) {}
        }
        super.onPause();
    }

    private void render() {
        final Config c = Config.get(this);
        LinearLayout p = Ui.page(this, "NFC lock");
        Ui.title(p, c.nfcLocked ? "🔒 Locked" : "🔓 Unlocked");
        if (adapter == null) {
            Ui.status(p, "This phone has no NFC reader.", false);
            return;
        }
        if (!adapter.isEnabled()) {
            Ui.status(p, "NFC is switched off. Turn it on in Android settings.", false);
        }
        statusView = Ui.big(p, instruction(c), 18, Ui.PRIMARY);

        if (MODE_MANAGE.equals(mode) || MODE_REGISTER.equals(mode)) {
            Ui.header(p, "Tags");
            Ui.para(p, c.nfcTags.size() + " tag(s) registered. Any cheap NTAG213/215 sticker or card works. "
                    + "Keep one somewhere inconvenient, or give one to your partner.");
            Ui.button(p, "Register a new tag", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Auth.requirePin(NfcActivity.this, new Runnable() {
                        @Override public void run() { mode = MODE_REGISTER; render(); }
                    });
                }
            });
            Ui.header(p, "When locked");
            Ui.row(p, "Lockdown: only essential apps", c.nfcLockdown ? "On: every app except essentials is blocked while locked"
                    : "Off: locking only freezes settings", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (c.nfcLockdown) {
                        Auth.requireLoosen(NfcActivity.this, "turned off NFC lockdown", new Runnable() {
                            @Override public void run() { c.nfcLockdown = false; c.save(NfcActivity.this); render(); }
                        });
                    } else { c.nfcLockdown = true; c.save(NfcActivity.this); render(); }
                }
            });
            Ui.para(p, "While locked, nothing can be loosened, the PIN alone isn't enough. Unlock by scanning a tag "
                    + "or with your partner's code. Lost every tag? Your partner can unlock you remotely.");
            if (!c.nfcTags.isEmpty()) {
                Ui.row(p, "Remove all tags", null, new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        Auth.requireLoosen(NfcActivity.this, "removed NFC tags", new Runnable() {
                            @Override public void run() {
                                c.nfcTags.clear(); c.nfcLocked = false; c.save(NfcActivity.this); render();
                            }
                        });
                    }
                });
            }
        }
    }

    private String instruction(Config c) {
        if (MODE_REGISTER.equals(mode)) return "Hold the new tag against the back of your phone…";
        if (c.nfcTags.isEmpty()) return "No tags registered yet.";
        if (MODE_UNLOCK.equals(mode)) return "Scan your tag to unlock.";
        if (MODE_MANAGE.equals(mode)) return c.nfcLocked ? "Scan a registered tag to unlock." : "Scan a registered tag to lock.";
        return c.nfcLocked ? "Scan your tag to unlock." : "Scan your tag to lock.";
    }

    @Override
    public void onTagDiscovered(final Tag tag) {
        final Config c = Config.get(this);
        byte[] id = tag.getId();
        if (id == null || id.length == 0) { show("Couldn't read that tag."); return; }
        final String hash = Crypto.sha256Hex("steadfast-tag:" + Crypto.hex(id));
        synchronized (NfcActivity.class) {
            long now = System.currentTimeMillis();
            if (hash.equals(lastTag) && now - lastTagTime < 4000) return; // same tap seen twice
            lastTag = hash;
            lastTagTime = now;
        }

        if (MODE_REGISTER.equals(mode)) {
            c.nfcTags.add(hash);
            c.save(this);
            boolean wrote = writeLink(tag);
            mode = MODE_MANAGE;
            show(wrote ? "Tag registered ✔ Scan it any time to lock or unlock."
                    : "Tag registered ✔ (couldn't write a link to it, so open Steadfast before scanning).");
            handler.postDelayed(new Runnable() { @Override public void run() { render(); } }, 2500);
            return;
        }
        if (!c.nfcTags.contains(hash)) {
            show("That tag isn't registered with Steadfast.");
            return;
        }
        if (c.nfcLocked) {
            c.nfcLocked = false;
            c.save(this);
            show("🔓 Unlocked");
        } else {
            if (MODE_UNLOCK.equals(mode)) { show("Already unlocked."); return; }
            c.nfcLocked = true;
            c.settingsWindowUntil = 0;
            c.save(this);
            Auth.endSession();
            show("🔒 Locked");
        }
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (MODE_MANAGE.equals(mode)) render(); else finish();
            }
        }, 1500);
    }

    private boolean writeLink(Tag tag) {
        NdefMessage msg = new NdefMessage(NdefRecord.createUri("steadfast://nfc/toggle"));
        try {
            Ndef ndef = Ndef.get(tag);
            if (ndef != null) {
                ndef.connect();
                try {
                    if (!ndef.isWritable() || ndef.getMaxSize() < msg.toByteArray().length) return false;
                    ndef.writeNdefMessage(msg);
                    return true;
                } finally {
                    ndef.close();
                }
            }
            NdefFormatable f = NdefFormatable.get(tag);
            if (f != null) {
                f.connect();
                try {
                    f.format(msg);
                    return true;
                } finally {
                    f.close();
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void show(final String text) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                if (statusView != null) statusView.setText(text);
                Ui.toast(NfcActivity.this, text);
            }
        });
    }
}
