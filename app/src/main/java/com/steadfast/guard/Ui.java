package com.steadfast.guard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Small helpers for building screens in code (no layout files, no extra libraries). */
final class Ui {
    private Ui() {}

    static final int PRIMARY = Color.parseColor("#2E5E8C");
    static final int OK = Color.parseColor("#2E7D32");
    static final int WARN = Color.parseColor("#C62828");
    static final int MUTED = Color.parseColor("#666666");

    interface TextCallback { void on(String text); }
    interface IntCallback { void on(int value); }

    static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    static LinearLayout page(Activity a, String title) {
        a.setTitle(title);
        ScrollView sv = new ScrollView(a);
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = dp(a, 16);
        l.setPadding(p, p, p, dp(a, 48));
        sv.addView(l);
        a.setContentView(sv);
        return l;
    }

    static TextView header(LinearLayout parent, String text) {
        Context c = parent.getContext();
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(13);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(PRIMARY);
        t.setAllCaps(true);
        t.setPadding(0, dp(c, 22), 0, dp(c, 6));
        parent.addView(t);
        return t;
    }

    static TextView title(LinearLayout parent, String text) {
        Context c = parent.getContext();
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(22);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Color.parseColor("#1B1B1B"));
        t.setPadding(0, dp(c, 4), 0, dp(c, 8));
        parent.addView(t);
        return t;
    }

    static TextView para(LinearLayout parent, String text) {
        Context c = parent.getContext();
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(15);
        t.setTextColor(Color.parseColor("#333333"));
        t.setPadding(0, dp(c, 4), 0, dp(c, 4));
        t.setLineSpacing(0, 1.15f);
        parent.addView(t);
        return t;
    }

    static TextView status(LinearLayout parent, String text, boolean ok) {
        TextView t = para(parent, (ok ? "✔  " : "✖  ") + text);
        t.setTextColor(ok ? OK : WARN);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static Button button(LinearLayout parent, String text, View.OnClickListener l) {
        Context c = parent.getContext();
        Button b = new Button(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(PRIMARY);
        bg.setCornerRadius(dp(c, 10));
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 48));
        lp.topMargin = dp(c, 6);
        lp.bottomMargin = dp(c, 6);
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        parent.addView(b);
        return b;
    }

    /** A tappable two-line settings row. */
    static LinearLayout row(LinearLayout parent, String title, String subtitle, View.OnClickListener l) {
        Context c = parent.getContext();
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.VERTICAL);
        int p = dp(c, 12);
        r.setPadding(p, p, p, p);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F3F6F9"));
        bg.setCornerRadius(dp(c, 10));
        r.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 4);
        lp.bottomMargin = dp(c, 4);
        r.setLayoutParams(lp);
        TextView t = new TextView(c);
        t.setText(title);
        t.setTextSize(16);
        t.setTextColor(Color.parseColor("#1B1B1B"));
        r.addView(t);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView s = new TextView(c);
            s.setText(subtitle);
            s.setTextSize(13);
            s.setTextColor(MUTED);
            r.addView(s);
        }
        if (l != null) {
            r.setClickable(true);
            r.setOnClickListener(l);
        }
        parent.addView(r);
        return r;
    }

    static void toast(Context c, String text) {
        Toast.makeText(c, text, Toast.LENGTH_LONG).show();
    }

    static void alert(Activity a, String title, String msg) {
        new AlertDialog.Builder(a).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show();
    }

    static void confirm(Activity a, String title, String msg, final Runnable yes) {
        new AlertDialog.Builder(a).setTitle(title).setMessage(msg)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { yes.run(); }
                })
                .setNegativeButton("Cancel", null).show();
    }

    static void prompt(Activity a, String title, String message, String initial, int inputType, final TextCallback cb) {
        final EditText input = new EditText(a);
        input.setInputType(inputType);
        if (initial != null) input.setText(initial);
        input.setSingleLine((inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0);
        LinearLayout box = new LinearLayout(a);
        int p = dp(a, 20);
        box.setPadding(p, dp(a, 8), p, 0);
        box.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        AlertDialog.Builder b = new AlertDialog.Builder(a).setTitle(title).setView(box)
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
        new AlertDialog.Builder(a).setTitle(title)
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) { cb.on(which); }
                })
                .setNegativeButton("Cancel", null).show();
    }

    static TextView big(LinearLayout parent, String text, int sizeSp, int color) {
        TextView t = para(parent, text);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }
}
