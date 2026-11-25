package com.steadfast.guard;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Edits one of the lists: blocked domains, allowed domains, keywords, search terms. */
public class ListEditorActivity extends Activity {

    static final String EXTRA_LIST = "list";
    static final String LIST_BLOCKED = "blocked", LIST_ALLOWED = "allowed", LIST_KEYWORDS = "keywords", LIST_SEARCH = "search";

    static final String[] STARTER_KEYWORDS = {
            "porn", "porno", "pornography", "xxx", "nsfw", "nude", "nudes", "naked", "hentai", "onlyfans",
            "camgirl", "camgirls", "erotic", "erotica", "fetish", "milf", "stripper", "sexting", "escort", "escorts"
    };
    static final String[] STARTER_SEARCH = {
            "sex", "sexy", "porn*", "nude*", "naked", "boobs", "xxx", "nsfw", "hentai", "onlyfans", "hot girls", "bikini"
    };

    private String which;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        which = getIntent().getStringExtra(EXTRA_LIST);
        if (which == null) which = LIST_BLOCKED;
        render();
    }

    private Set<String> set(Config c) {
        switch (which) {
            case LIST_ALLOWED: return c.allowedDomains;
            case LIST_KEYWORDS: return c.keywords;
            case LIST_SEARCH: return c.searchTerms;
            default: return c.blockedDomains;
        }
    }

    private boolean isDomains() { return LIST_BLOCKED.equals(which) || LIST_ALLOWED.equals(which); }

    /** For the allow-list, adding is the loosening action; for every other list removing is. */
    private boolean addLoosens() { return LIST_ALLOWED.equals(which); }

    private void render() {
        String title, help;
        int icon;
        switch (which) {
            case LIST_ALLOWED:
                title = "Always allowed"; icon = R.drawable.ic_check;
                help = "Sites that are never blocked by your lists or keywords, e.g. a church site a keyword catches by mistake. "
                        + "The category filter still applies.";
                break;
            case LIST_KEYWORDS:
                title = "Keyword filter"; icon = R.drawable.ic_tag;
                help = "Blocks web addresses, pages and app screens showing these words, including image descriptions. "
                        + "Whole words only, so “sex” won't block “Essex”. End with * to match endings, e.g. nud*.";
                break;
            case LIST_SEARCH:
                title = "Search filter"; icon = R.drawable.ic_search;
                help = "Blocks searches and typing containing these words or phrases in any app: Google, YouTube, Instagram, TikTok. "
                        + "Keywords are checked too.";
                break;
            default:
                title = "Blocked websites"; icon = R.drawable.ic_globe;
                help = "Enter a domain like example.com. Every subdomain is blocked too. "
                        + "To block just one subdomain, enter it in full, e.g. news.example.com.";
        }
        final Ui.Screen s = Ui.screen(this, title, true);
        Ui.Palette p = s.p;
        LinearLayout v = s.content;
        Ui.banner(v, icon, title, help, 0, null, null);

        // input
        LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setBackground(Ui.round(this, p.surface, 26));
        box.setPadding(Ui.dp(this, 18), Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4));
        final EditText input = new EditText(this);
        input.setHint(isDomains() ? "example.com" : "Add a word or phrase");
        input.setHintTextColor(p.faint);
        input.setTextColor(p.text);
        input.setBackground(null);
        input.setInputType(isDomains() ? InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_CLASS_TEXT
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        box.addView(input, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        ImageView add = Ui.iconButton(this, R.drawable.ic_plus, p.onPrimary, "Add", new View.OnClickListener() {
            @Override public void onClick(View x) { add(input.getText().toString()); }
        });
        add.setBackground(Ui.ripple(this, p.primary, 22, Ui.alpha(p.onPrimary, 0.25f)));
        box.addView(add);
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView tv, int id, KeyEvent e) { add(input.getText().toString()); return true; }
        });
        LinearLayout.LayoutParams bl = Ui.lp(Ui.MATCH, Ui.WRAP);
        bl.bottomMargin = Ui.dp(this, 4);
        v.addView(box, bl);

        if (LIST_KEYWORDS.equals(which) || LIST_SEARCH.equals(which)) {
            TextView starter = Ui.text(this, "+ Add the starter list", 14, p.primary, Ui.medium());
            starter.setPadding(Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 4));
            starter.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View x) {
                    Config c = Config.get(ListEditorActivity.this);
                    String[] src = LIST_KEYWORDS.equals(which) ? STARTER_KEYWORDS : STARTER_SEARCH;
                    for (String w : src) set(c).add(w);
                    c.save(ListEditorActivity.this);
                    render();
                }
            });
            v.addView(starter);
        }

        Config c = Config.get(this);
        List<String> sorted = new ArrayList<>(set(c));
        Collections.sort(sorted);
        Ui.section(v, sorted.size() + (sorted.size() == 1 ? " entry" : " entries"));
        if (sorted.isEmpty()) {
            TextView e = Ui.text(this, "Nothing here yet.", 15, p.faint, null);
            e.setGravity(Gravity.CENTER);
            e.setPadding(0, Ui.dp(this, 20), 0, Ui.dp(this, 20));
            v.addView(e, Ui.lp(Ui.MATCH, Ui.WRAP));
            return;
        }
        LinearLayout card = Ui.card(v);
        boolean first = true;
        for (final String item : sorted) {
            if (!first) Ui.divider(card);
            first = false;
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(Ui.dp(this, 18), Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4));
            r.setMinimumHeight(Ui.dp(this, 52));
            r.addView(Ui.text(this, item, 16, p.text, null), new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
            ImageView del = Ui.iconButton(this, R.drawable.ic_close, p.muted, "Remove " + item, new View.OnClickListener() {
                @Override public void onClick(View x) { remove(item); }
            });
            del.setImageTintList(ColorStateList.valueOf(p.muted));
            r.addView(del);
            card.addView(r, Ui.lp(Ui.MATCH, Ui.WRAP));
        }
        // keep divider indentation sensible for rows without icons
        for (int i = 0; i < card.getChildCount(); i++) {
            View ch = card.getChildAt(i);
            if (!(ch instanceof LinearLayout)) {
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) ch.getLayoutParams();
                lp.leftMargin = Ui.dp(this, 18);
            }
        }
    }

    private void add(String raw) {
        String val = isDomains() ? Policy.normalizeDomain(raw) : raw.trim().toLowerCase(Locale.ROOT);
        if (val.isEmpty() || (isDomains() && !val.contains("."))) {
            Ui.toast(this, isDomains() ? "Enter a domain like example.com" : "Enter a word");
            return;
        }
        final String value = val;
        Runnable apply = new Runnable() {
            @Override public void run() {
                Config c = Config.get(ListEditorActivity.this);
                set(c).add(value);
                c.save(ListEditorActivity.this);
                render();
            }
        };
        if (addLoosens()) Auth.requireLoosen(this, "added an always-allowed website", apply);
        else apply.run();
    }

    private void remove(final String value) {
        Runnable apply = new Runnable() {
            @Override public void run() {
                Config c = Config.get(ListEditorActivity.this);
                set(c).remove(value);
                c.save(ListEditorActivity.this);
                render();
            }
        };
        if (addLoosens()) { apply.run(); return; }
        String what;
        switch (which) {
            case LIST_KEYWORDS: what = "removed a keyword"; break;
            case LIST_SEARCH: what = "removed a search filter term"; break;
            default: what = "removed a blocked website";
        }
        Auth.requireLoosen(this, what, apply);
    }
}
