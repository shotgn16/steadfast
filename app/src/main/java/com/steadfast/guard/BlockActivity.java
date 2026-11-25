package com.steadfast.guard;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Full-screen "blocked" page shown instead of the blocked app, site or search. */
public class BlockActivity extends Activity {

    static final String EXTRA_TYPE = "type";
    static final String EXTRA_MESSAGE = "message";

    /** World English Bible (public domain). */
    private static final String[][] VERSES = {
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

        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        l.setBackgroundColor(Color.parseColor("#14263A"));
        int p = Ui.dp(this, 28);
        l.setPadding(p, p, p, p);

        TextView icon = new TextView(this);
        icon.setText("🛡️");
        icon.setTextSize(64);
        icon.setGravity(Gravity.CENTER);
        l.addView(icon);

        TextView title = text(EventLog.TAMPER.equals(type) ? "Protected" : "Blocked", 30, true, Color.WHITE);
        l.addView(title);
        l.addView(text(message == null ? "Steadfast blocked this." : message, 17, false, Color.parseColor("#DCE6F0")));

        if (c.showVerses && !EventLog.TAMPER.equals(type)) {
            String[] v = VERSES[(int) ((System.currentTimeMillis() / 1000) % VERSES.length)];
            TextView verse = text("“" + v[0] + "”", 16, false, Color.parseColor("#F3E6BF"));
            verse.setTypeface(Typeface.SERIF, Typeface.ITALIC);
            verse.setPadding(0, Ui.dp(this, 28), 0, Ui.dp(this, 4));
            l.addView(verse);
            l.addView(text(v[1], 13, false, Color.parseColor("#B8C4D0")));
        }
        if (c.partnerEnabled && !EventLog.APP.equals(type) && !EventLog.LIMIT.equals(type)
                && !EventLog.DOWNTIME.equals(type) && !EventLog.LOCKDOWN.equals(type)) {
            TextView note = text("Your accountability partner may get a notification that a block happened, but not what it was.", 13, false, Color.parseColor("#B8C4D0"));
            note.setPadding(0, Ui.dp(this, 20), 0, 0);
            l.addView(note);
        }

        Button home = new Button(this);
        home.setText("Go to home screen");
        home.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 52));
        lp.topMargin = Ui.dp(this, 36);
        l.addView(home, lp);
        home.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { goHome(); }
        });
        setContentView(l);
    }

    private TextView text(String s, int size, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
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
