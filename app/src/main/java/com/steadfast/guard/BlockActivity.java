package com.steadfast.guard;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Full-screen "blocked" page shown instead of the blocked app, site or search. */
public class BlockActivity extends Activity {

    static final String EXTRA_TYPE = "type";
    static final String EXTRA_MESSAGE = "message";

    /** World English Bible (public domain). */
    static final String[][] VERSES = {
            {"No temptation has taken you except what is common to man. God is faithful, who will not allow you to be tempted above what you are able, but will with the temptation also make the way of escape, that you may be able to endure it.", "1 Corinthians 10:13 (WEB)"},
            {"Turn my eyes away from looking at worthless things. Revive me in your ways.", "Psalm 119:37 (WEB)"},
            {"I will set no vile thing before my eyes.", "Psalm 101:3 (WEB)"},
            {"Be subject therefore to God. But resist the devil, and he will flee from you.", "James 4:7 (WEB)"},
            {"But I say, walk by the Spirit, and you won't fulfill the lust of the flesh.", "Galatians 5:16 (WEB)"},
            {"Finally, brothers, whatever things are true, whatever things are honorable, whatever things are just, whatever things are pure, whatever things are lovely, whatever things are of good report: if there is any virtue, and if there is any praise, think about these things.", "Philippians 4:8 (WEB)"},
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        render(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        render(intent);
    }

    private void render(Intent intent) {
        String type = intent.getStringExtra(EXTRA_TYPE);
        String message = intent.getStringExtra(EXTRA_MESSAGE);
        Config c = Config.get(this);
        boolean tamper = EventLog.TAMPER.equals(type);
        boolean timeRule = EventLog.LIMIT.equals(type) || EventLog.DOWNTIME.equals(type) || EventLog.LOCKDOWN.equals(type);

        int top = Color.parseColor("#13263D"), bottom = Color.parseColor("#0A1220");
        int gold = Color.parseColor("#E3B65C"), soft = Color.parseColor("#B9C6D6"), white = Color.parseColor("#F4F7FB");
        getWindow().setStatusBarColor(top);
        getWindow().setNavigationBarColor(bottom);

        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom}));
        int pad = Ui.dp(this, 28);
        l.setPadding(pad, Ui.dp(this, 56), pad, Ui.dp(this, 28));

        // halo + icon
        FrameLayout halo = new FrameLayout(this);
        GradientDrawable ring = Ui.oval(Color.argb(28, 227, 182, 92));
        ring.setStroke(Ui.dp(this, 1), Color.argb(90, 227, 182, 92));
        halo.setBackground(ring);
        ImageView icon = new ImageView(this);
        icon.setImageResource(timeRule ? R.drawable.ic_clock : tamper ? R.drawable.ic_shield_alert : R.drawable.ic_shield);
        icon.setImageTintList(ColorStateList.valueOf(gold));
        FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48));
        il.gravity = Gravity.CENTER;
        halo.addView(icon, il);
        l.addView(halo, new LinearLayout.LayoutParams(Ui.dp(this, 108), Ui.dp(this, 108)));

        TextView title = text(tamper ? "Protected" : timeRule ? "Time's up" : "Blocked", 32, white, Typeface.DEFAULT_BOLD);
        title.setPadding(0, Ui.dp(this, 24), 0, Ui.dp(this, 8));
        l.addView(title, Ui.lp(Ui.MATCH, Ui.WRAP));
        l.addView(text(message == null ? "Steadfast blocked this." : message, 17, soft, null), Ui.lp(Ui.MATCH, Ui.WRAP));

        View grow = new View(this);
        l.addView(grow, new LinearLayout.LayoutParams(1, 0, 1));

        if (c.showVerses && !tamper) {
            String[] v = VERSES[(int) ((System.currentTimeMillis() / 1000) % VERSES.length)];
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(Ui.round(this, Color.argb(20, 255, 255, 255), 22));
            card.setPadding(Ui.dp(this, 22), Ui.dp(this, 20), Ui.dp(this, 22), Ui.dp(this, 18));
            TextView q = text("“" + v[0] + "”", 17, white, Typeface.create(Typeface.SERIF, Typeface.ITALIC));
            card.addView(q, Ui.lp(Ui.MATCH, Ui.WRAP));
            TextView ref = text(v[1], 13, gold, Ui.medium());
            ref.setPadding(0, Ui.dp(this, 12), 0, 0);
            card.addView(ref, Ui.lp(Ui.MATCH, Ui.WRAP));
            l.addView(card, Ui.lp(Ui.MATCH, Ui.WRAP));
        }
        if (c.partnerEnabled && !timeRule && !EventLog.APP.equals(type)) {
            TextView note = text("Your accountability partner may be told that a block happened, never what it was.", 13,
                    Color.argb(170, 185, 198, 214), null);
            note.setPadding(Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8), 0);
            l.addView(note, Ui.lp(Ui.MATCH, Ui.WRAP));
        }

        TextView home = text("Go to home screen", 16, Color.parseColor("#0A1220"), Ui.medium());
        home.setBackground(Ui.ripple(this, gold, 28, Color.argb(60, 0, 0, 0)));
        home.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 56));
        lp.topMargin = Ui.dp(this, 22);
        l.addView(home, lp);
        home.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { goHome(); }
        });
        setContentView(l);
    }

    private TextView text(String s, int size, int color, Typeface tf) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setLineSpacing(0, 1.15f);
        if (tf != null) t.setTypeface(tf);
        return t;
    }

    private void goHome() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
        finish();
    }

    @Override
    public void onBackPressed() {
        goHome();
    }
}
