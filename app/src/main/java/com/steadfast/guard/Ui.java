package com.steadfast.guard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Steadfast design system, built in code (no extra libraries).
 * Follows the phone's light/dark mode. Deep blue for trust, warm gold accent, generous rounded cards.
 */
final class Ui {
    private Ui() {}

    interface TextCallback { void on(String text); }
    interface IntCallback { void on(int value); }

    // ------------------------------------------------------------------ palette

    static final class Palette {
        boolean dark;
        int bg, surface, surface2, outline, text, muted, faint;
        int primary, onPrimary, primaryContainer, onPrimaryContainer;
        int accent, ok, okContainer, danger, dangerContainer, warn, warnContainer;
    }

    static boolean isDark(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    static Palette p(Context c) {
        Palette p = new Palette();
        p.dark = isDark(c);
        if (p.dark) {
            p.bg = rgb("#0D1117"); p.surface = rgb("#161B23"); p.surface2 = rgb("#1F2630"); p.outline = rgb("#2A323D");
            p.text = rgb("#ECF0F5"); p.muted = rgb("#9AA5B4"); p.faint = rgb("#5E6876");
            p.primary = rgb("#8AB6E8"); p.onPrimary = rgb("#0B1B2D");
            p.primaryContainer = rgb("#1C3350"); p.onPrimaryContainer = rgb("#D4E5F8");
            p.accent = rgb("#E3B65C");
            p.ok = rgb("#5FCB8E"); p.okContainer = rgb("#143022");
            p.danger = rgb("#F2877E"); p.dangerContainer = rgb("#3A1D1C");
            p.warn = rgb("#F0B45A"); p.warnContainer = rgb("#382A14");
        } else {
            p.bg = rgb("#F3F5F8"); p.surface = rgb("#FFFFFF"); p.surface2 = rgb("#EDF1F5"); p.outline = rgb("#E1E6EC");
            p.text = rgb("#0F1A2A"); p.muted = rgb("#5A6576"); p.faint = rgb("#97A1AF");
            p.primary = rgb("#1F4E7A"); p.onPrimary = Color.WHITE;
            p.primaryContainer = rgb("#DCE8F5"); p.onPrimaryContainer = rgb("#0F2C49");
            p.accent = rgb("#B7862A");
            p.ok = rgb("#1C7F47"); p.okContainer = rgb("#DDF2E5");
            p.danger = rgb("#C0392B"); p.dangerContainer = rgb("#FBE3E1");
            p.warn = rgb("#B4600B"); p.warnContainer = rgb("#FCEBD3");
        }
        return p;
    }

    static int rgb(String hex) { return Color.parseColor(hex); }

    static int alpha(int color, float a) {
        return Color.argb(Math.round(a * 255), Color.red(color), Color.green(color), Color.blue(color));
    }

    static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    static Typeface medium() { return Typeface.create("sans-serif-medium", Typeface.NORMAL); }

    static GradientDrawable round(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static GradientDrawable oval(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    /** Background with touch ripple. */
    static RippleDrawable ripple(Context c, int color, float radiusDp, int rippleColor) {
        GradientDrawable mask = round(c, Color.WHITE, radiusDp);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), round(c, color, radiusDp), mask);
    }

    static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    // ------------------------------------------------------------------ screen scaffold

    static final class Screen {
        Activity activity;
        Palette p;
        LinearLayout root;
        LinearLayout header;
        LinearLayout headerActions;
        TextView titleView;
        ScrollView scroll;
        LinearLayout content;
        LinearLayout bottom;
    }

    @SuppressWarnings("deprecation")
    static void styleWindow(Activity a, int color) {
        Palette p = p(a);
        Window w = a.getWindow();
        w.setStatusBarColor(color);
        w.setNavigationBarColor(p.bg);
        int flags = 0;
        if (!p.dark) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        w.getDecorView().setSystemUiVisibility(flags);
    }

    /** Standard screen: top bar (optional back), scrolling content, optional bottom bar. */
    static Screen screen(final Activity a, String title, boolean back) {
        Screen s = new Screen();
        s.activity = a;
        s.p = p(a);
        styleWindow(a, s.p.bg);
        a.setTitle(title);

        s.root = new LinearLayout(a);
        s.root.setOrientation(LinearLayout.VERTICAL);
        s.root.setBackgroundColor(s.p.bg);

        s.header = new LinearLayout(a);
        s.header.setOrientation(LinearLayout.HORIZONTAL);
        s.header.setGravity(Gravity.CENTER_VERTICAL);
        s.header.setPadding(dp(a, back ? 8 : 20), dp(a, 10), dp(a, 12), dp(a, 6));
        if (back) {
            ImageView b = iconButton(a, R.drawable.ic_back, s.p.text, "Back", new View.OnClickListener() {
                @Override public void onClick(View v) { a.onBackPressed(); }
            });
            s.header.addView(b);
        }
        s.titleView = new TextView(a);
        s.titleView.setText(title);
        s.titleView.setTextColor(s.p.text);
        s.titleView.setTextSize(back ? 20 : 26);
        s.titleView.setTypeface(back ? medium() : Typeface.DEFAULT_BOLD);
        s.titleView.setSingleLine(true);
        s.titleView.setEllipsize(TextUtils.TruncateAt.END);
        s.titleView.setPadding(dp(a, back ? 4 : 0), 0, 0, 0);
        s.header.addView(s.titleView, new LinearLayout.LayoutParams(0, WRAP, 1));
        s.headerActions = new LinearLayout(a);
        s.headerActions.setOrientation(LinearLayout.HORIZONTAL);
        s.header.addView(s.headerActions);
        s.root.addView(s.header, lp(MATCH, WRAP));

        s.scroll = new ScrollView(a);
        s.scroll.setFillViewport(true);
        s.scroll.setClipToPadding(false);
        s.content = new LinearLayout(a);
        s.content.setOrientation(LinearLayout.VERTICAL);
        s.content.setPadding(dp(a, 16), dp(a, 4), dp(a, 16), dp(a, 32));
        s.scroll.addView(s.content, new FrameLayout.LayoutParams(MATCH, WRAP));
        s.root.addView(s.scroll, new LinearLayout.LayoutParams(MATCH, 0, 1));

        s.bottom = new LinearLayout(a);
        s.bottom.setOrientation(LinearLayout.VERTICAL);
        s.root.addView(s.bottom, lp(MATCH, WRAP));

        a.setContentView(s.root);
        return s;
    }

    static ImageView iconButton(Context c, int icon, int color, String desc, View.OnClickListener l) {
        ImageView b = new ImageView(c);
        b.setImageResource(icon);
        b.setImageTintList(ColorStateList.valueOf(color));
        b.setContentDescription(desc);
        int pad = dp(c, 10);
        b.setPadding(pad, pad, pad, pad);
        b.setBackground(ripple(c, Color.TRANSPARENT, 22, alpha(color, 0.15f)));
        b.setOnClickListener(l);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 44), dp(c, 44)));
        return b;
    }

    static void headerAction(Screen s, int icon, String desc, View.OnClickListener l) {
        s.headerActions.addView(iconButton(s.activity, icon, s.p.text, desc, l));
    }

    // ------------------------------------------------------------------ text

    static TextView text(Context c, String s, float sp, int color, Typeface tf) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (tf != null) t.setTypeface(tf);
        t.setLineSpacing(0, 1.12f);
        return t;
    }

    static TextView section(ViewGroup parent, String label) {
        Context c = parent.getContext();
        TextView t = text(c, label.toUpperCase(), 12, p(c).muted, medium());
        t.setLetterSpacing(0.08f);
        t.setPadding(dp(c, 6), dp(c, 22), dp(c, 6), dp(c, 8));
        parent.addView(t);
        return t;
    }

    static TextView headline(ViewGroup parent, String s) {
        Context c = parent.getContext();
        TextView t = text(c, s, 22, p(c).text, Typeface.DEFAULT_BOLD);
        t.setPadding(dp(c, 4), dp(c, 6), dp(c, 4), dp(c, 4));
        parent.addView(t);
        return t;
    }

    static TextView body(ViewGroup parent, String s) {
        Context c = parent.getContext();
        TextView t = text(c, s, 15, p(c).muted, null);
        t.setPadding(dp(c, 4), dp(c, 2), dp(c, 4), dp(c, 6));
        parent.addView(t);
        return t;
    }

    static TextView mono(ViewGroup parent, String s) {
        Context c = parent.getContext();
        Palette p = p(c);
        TextView t = text(c, s, 13, p.text, Typeface.MONOSPACE);
        t.setTextIsSelectable(true);
        int pad = dp(c, 12);
        t.setPadding(pad, pad, pad, pad);
        t.setBackground(round(c, p.surface2, 12));
        LinearLayout.LayoutParams l = lp(MATCH, WRAP);
        l.topMargin = dp(c, 4);
        l.bottomMargin = dp(c, 8);
        parent.addView(t, l);
        return t;
    }

    // ------------------------------------------------------------------ containers

    /** Rounded surface card; add rows/views to the returned layout. */
    static LinearLayout card(ViewGroup parent) {
        Context c = parent.getContext();
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(c, p(c).surface, 20));
        card.setClipToOutline(true);
        card.setPadding(0, dp(c, 4), 0, dp(c, 4));
        LinearLayout.LayoutParams l = lp(MATCH, WRAP);
        l.bottomMargin = dp(c, 10);
        parent.addView(card, l);
        return card;
    }

    static LinearLayout paddedCard(ViewGroup parent) {
        LinearLayout card = card(parent);
        Context c = parent.getContext();
        card.setPadding(dp(c, 18), dp(c, 16), dp(c, 18), dp(c, 16));
        return card;
    }

    static void divider(ViewGroup parent) {
        Context c = parent.getContext();
        View v = new View(c);
        v.setBackgroundColor(p(c).outline);
        LinearLayout.LayoutParams l = lp(MATCH, Math.max(1, dp(c, 1) / 2));
        l.leftMargin = dp(c, 68);
        parent.addView(v, l);
    }

    /** Round tinted icon "badge". */
    static FrameLayout iconBadge(Context c, int icon, int fg, int bg, int sizeDp) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(oval(bg));
        ImageView iv = new ImageView(c);
        iv.setImageResource(icon);
        iv.setImageTintList(ColorStateList.valueOf(fg));
        int inner = Math.round(sizeDp * 0.5f);
        FrameLayout.LayoutParams l = new FrameLayout.LayoutParams(dp(c, inner), dp(c, inner));
        l.gravity = Gravity.CENTER;
        f.addView(iv, l);
        f.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return f;
    }

    // ------------------------------------------------------------------ rows

    static final int T_NONE = 0, T_CHEVRON = 1, T_SWITCH_ON = 2, T_SWITCH_OFF = 3;

    /**
     * List row inside a card: icon badge, title, subtitle, trailing chevron/switch/value.
     * Switches are display-only; the row handles the tap so loosening can ask for approval first.
     */
    static LinearLayout row(ViewGroup card, int icon, String title, String subtitle, int trailing, View.OnClickListener l) {
        return row(card, icon, title, subtitle, trailing, null, l, 0);
    }

    static LinearLayout row(ViewGroup card, int icon, String title, String subtitle, int trailing, String value,
                            View.OnClickListener l, int tone) {
        Context c = card.getContext();
        Palette p = p(c);
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(c, 16), dp(c, 12), dp(c, 14), dp(c, 12));
        r.setMinimumHeight(dp(c, 64));
        if (icon != 0) {
            int fg = p.primary, bg = p.primaryContainer;
            if (tone == 1) { fg = p.ok; bg = p.okContainer; }
            if (tone == 2) { fg = p.danger; bg = p.dangerContainer; }
            if (tone == 3) { fg = p.warn; bg = p.warnContainer; }
            if (p.dark && tone == 0) fg = p.onPrimaryContainer;
            View badge = iconBadge(c, icon, fg, bg, 40);
            r.addView(badge);
        }
        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(c, icon != 0 ? 14 : 4), 0, dp(c, 8), 0);
        TextView t = text(c, title, 16, tone == 2 && icon == 0 ? p.danger : p.text, null);
        texts.addView(t);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView s = text(c, subtitle, 13, p.muted, null);
            s.setPadding(0, dp(c, 2), 0, 0);
            texts.addView(s);
        }
        r.addView(texts, new LinearLayout.LayoutParams(0, WRAP, 1));
        if (value != null) {
            TextView v = text(c, value, 14, p.muted, null);
            v.setPadding(dp(c, 4), 0, dp(c, 4), 0);
            r.addView(v);
        }
        if (trailing == T_CHEVRON) {
            ImageView ch = new ImageView(c);
            ch.setImageResource(R.drawable.ic_chevron);
            ch.setImageTintList(ColorStateList.valueOf(p.faint));
            r.addView(ch, new LinearLayout.LayoutParams(dp(c, 18), dp(c, 18)));
        } else if (trailing == T_SWITCH_ON || trailing == T_SWITCH_OFF) {
            Switch sw = toggle(c, trailing == T_SWITCH_ON);
            sw.setClickable(false);
            sw.setFocusable(false);
            r.addView(sw);
        }
        if (l != null) {
            r.setBackground(ripple(c, Color.TRANSPARENT, 0, alpha(p.primary, 0.12f)));
            r.setOnClickListener(l);
        }
        card.addView(r, lp(MATCH, WRAP));
        return r;
    }

    static Switch toggle(Context c, boolean on) {
        Palette p = p(c);
        Switch sw = new Switch(c);
        sw.setChecked(on);
        int[][] states = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new ColorStateList(states, new int[]{p.dark ? p.onPrimary : Color.WHITE, p.dark ? p.muted : Color.WHITE}));
        sw.setTrackTintList(new ColorStateList(states, new int[]{p.primary, p.dark ? p.surface2 : rgb("#C9D1DB")}));
        sw.setTrackTintMode(android.graphics.PorterDuff.Mode.SRC_IN);
        return sw;
    }

    // ------------------------------------------------------------------ buttons

    static TextView button(ViewGroup parent, String label, View.OnClickListener l) {
        Context c = parent.getContext();
        Palette p = p(c);
        return makeButton(parent, label, p.primary, p.onPrimary, l);
    }

    static TextView tonalButton(ViewGroup parent, String label, View.OnClickListener l) {
        Context c = parent.getContext();
        Palette p = p(c);
        return makeButton(parent, label, p.primaryContainer, p.onPrimaryContainer, l);
    }

    static TextView dangerButton(ViewGroup parent, String label, View.OnClickListener l) {
        Context c = parent.getContext();
        Palette p = p(c);
        return makeButton(parent, label, p.dangerContainer, p.danger, l);
    }

    private static TextView makeButton(ViewGroup parent, String label, int bg, int fg, View.OnClickListener l) {
        Context c = parent.getContext();
        TextView b = text(c, label, 16, fg, medium());
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 20), 0, dp(c, 20), 0);
        b.setBackground(ripple(c, bg, 26, alpha(fg, 0.2f)));
        b.setOnClickListener(l);
        b.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, dp(c, 52));
        lp.topMargin = dp(c, 6);
        lp.bottomMargin = dp(c, 6);
        parent.addView(b, lp);
        return b;
    }

    /** Small pill chip, e.g. "On", "Locked". */
    static TextView chip(Context c, String label, int fg, int bg) {
        TextView t = text(c, label, 12, fg, medium());
        t.setPadding(dp(c, 10), dp(c, 4), dp(c, 10), dp(c, 4));
        t.setBackground(round(c, bg, 12));
        return t;
    }

    /** Coloured information banner with optional action. */
    static LinearLayout banner(ViewGroup parent, int icon, String title, String body, int tone,
                               String action, View.OnClickListener l) {
        Context c = parent.getContext();
        Palette p = p(c);
        int fg = tone == 1 ? p.ok : tone == 2 ? p.danger : tone == 3 ? p.warn : p.primary;
        int bg = tone == 1 ? p.okContainer : tone == 2 ? p.dangerContainer : tone == 3 ? p.warnContainer : p.primaryContainer;
        if (p.dark && tone == 0) fg = p.onPrimaryContainer;
        LinearLayout b = new LinearLayout(c);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setBackground(round(c, bg, 18));
        b.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        ImageView iv = new ImageView(c);
        iv.setImageResource(icon);
        iv.setImageTintList(ColorStateList.valueOf(fg));
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(dp(c, 22), dp(c, 22));
        il.topMargin = dp(c, 1);
        b.addView(iv, il);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(c, 12), 0, 0, 0);
        col.addView(text(c, title, 15, fg, medium()));
        if (body != null) {
            TextView bt = text(c, body, 14, p.dark ? alpha(fg, 0.85f) : p.text, null);
            bt.setPadding(0, dp(c, 3), 0, 0);
            col.addView(bt);
        }
        if (action != null) {
            TextView a = text(c, action, 14, fg, Typeface.DEFAULT_BOLD);
            a.setPadding(0, dp(c, 10), 0, dp(c, 2));
            a.setOnClickListener(l);
            col.addView(a);
        }
        b.addView(col, new LinearLayout.LayoutParams(0, WRAP, 1));
        LinearLayout.LayoutParams l2 = lp(MATCH, WRAP);
        l2.bottomMargin = dp(c, 10);
        parent.addView(b, l2);
        return b;
    }

    // ------------------------------------------------------------------ PIN pad

    static final class PinPad {
        final StringBuilder digits = new StringBuilder();
        LinearLayout view;
        LinearLayout dots;
        int max;

        void refresh(Context c) {
            Palette p = p(c);
            dots.removeAllViews();
            int shown = Math.max(4, digits.length());
            for (int i = 0; i < shown; i++) {
                View d = new View(c);
                boolean filled = i < digits.length();
                GradientDrawable g = oval(filled ? p.primary : Color.TRANSPARENT);
                g.setStroke(dp(c, 2), filled ? p.primary : p.faint);
                d.setBackground(g);
                LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(dp(c, 14), dp(c, 14));
                l.leftMargin = dp(c, 8);
                l.rightMargin = dp(c, 8);
                dots.addView(d, l);
            }
        }

        void clear(Context c) { digits.setLength(0); refresh(c); }

        void shake() {
            view.animate().translationX(dp(view.getContext(), 12)).setDuration(50).withEndAction(new Runnable() {
                @Override public void run() {
                    view.animate().translationX(-dp(view.getContext(), 12)).setDuration(80).withEndAction(new Runnable() {
                        @Override public void run() { view.animate().translationX(0).setDuration(50).start(); }
                    }).start();
                }
            }).start();
        }
    }

    static PinPad pinPad(ViewGroup parent, final int max, final TextCallback onSubmit) {
        final Context c = parent.getContext();
        final Palette p = p(c);
        final PinPad pad = new PinPad();
        pad.max = max;
        pad.view = new LinearLayout(c);
        pad.view.setOrientation(LinearLayout.VERTICAL);
        pad.view.setGravity(Gravity.CENTER_HORIZONTAL);
        pad.dots = new LinearLayout(c);
        pad.dots.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dl = lp(MATCH, dp(c, 30));
        dl.bottomMargin = dp(c, 18);
        pad.view.addView(pad.dots, dl);
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "✓"};
        for (int r = 0; r < 4; r++) {
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER);
            for (int k = 0; k < 3; k++) {
                final String key = keys[r * 3 + k];
                final boolean submit = key.equals("✓"), del = key.equals("⌫");
                View v;
                if (submit || del) {
                    FrameLayout f = new FrameLayout(c);
                    ImageView iv = new ImageView(c);
                    iv.setImageResource(submit ? R.drawable.ic_check : R.drawable.ic_backspace);
                    iv.setImageTintList(ColorStateList.valueOf(submit ? p.onPrimary : p.text));
                    FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(dp(c, 26), dp(c, 26));
                    il.gravity = Gravity.CENTER;
                    f.addView(iv, il);
                    f.setBackground(submit ? ripple(c, p.primary, 36, alpha(p.onPrimary, 0.25f))
                            : ripple(c, Color.TRANSPARENT, 36, alpha(p.text, 0.12f)));
                    f.setContentDescription(submit ? "Enter" : "Delete");
                    v = f;
                } else {
                    TextView t = text(c, key, 28, p.text, null);
                    t.setGravity(Gravity.CENTER);
                    t.setBackground(ripple(c, p.surface, 36, alpha(p.primary, 0.18f)));
                    v = t;
                }
                v.setClickable(true);
                v.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        if (submit) {
                            if (pad.digits.length() > 0) onSubmit.on(pad.digits.toString());
                        } else if (del) {
                            if (pad.digits.length() > 0) pad.digits.setLength(pad.digits.length() - 1);
                        } else if (pad.digits.length() < max) {
                            pad.digits.append(key);
                        }
                        pad.refresh(c);
                    }
                });
                LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(dp(c, 72), dp(c, 72));
                kl.setMargins(dp(c, 12), dp(c, 7), dp(c, 12), dp(c, 7));
                row.addView(v, kl);
            }
            pad.view.addView(row);
        }
        pad.refresh(c);
        parent.addView(pad.view, lp(MATCH, WRAP));
        return pad;
    }

    // ------------------------------------------------------------------ dialogs

    static AlertDialog.Builder dialog(Context c) {
        return new AlertDialog.Builder(c, isDark(c) ? android.R.style.Theme_Material_Dialog_Alert
                : android.R.style.Theme_Material_Light_Dialog_Alert);
    }

    static void toast(Context c, String text) {
        Toast.makeText(c, text, Toast.LENGTH_LONG).show();
    }

    static void alert(Activity a, String title, String msg) {
        dialog(a).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show();
    }

    static void confirm(Activity a, String title, String msg, final Runnable yes) {
        dialog(a).setTitle(title).setMessage(msg)
                .setPositiveButton("Continue", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { yes.run(); }
                })
                .setNegativeButton("Cancel", null).show();
    }

    static void prompt(Activity a, String title, String message, String initial, int inputType, final TextCallback cb) {
        Palette p = p(a);
        final EditText input = new EditText(a);
        input.setInputType(inputType);
        input.setTextColor(p.text);
        input.setHintTextColor(p.faint);
        if (initial != null) input.setText(initial);
        input.setSingleLine((inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0);
        boolean isPin = (inputType & InputType.TYPE_NUMBER_VARIATION_PASSWORD) != 0;
        if (isPin) {
            input.setGravity(Gravity.CENTER);
            input.setTextSize(26);
            input.setLetterSpacing(0.4f);
        }
        input.setBackground(round(a, p.surface2, 12));
        int ip = dp(a, 14);
        input.setPadding(ip, ip, ip, ip);
        LinearLayout box = new LinearLayout(a);
        box.setPadding(dp(a, 22), dp(a, 8), dp(a, 22), 0);
        box.addView(input, lp(MATCH, WRAP));
        AlertDialog.Builder b = dialog(a).setTitle(title).setView(box)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { cb.on(input.getText().toString()); }
                })
                .setNegativeButton("Cancel", null);
        if (message != null) b.setMessage(message);
        b.show();
        input.requestFocus();
    }

    static void pin(Activity a, String title, String message, TextCallback cb) {
        prompt(a, title, message, null, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD, cb);
    }

    static void choose(Activity a, String title, String[] items, final IntCallback cb) {
        dialog(a).setTitle(title)
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) { cb.on(which); }
                })
                .setNegativeButton("Cancel", null).show();
    }
}
