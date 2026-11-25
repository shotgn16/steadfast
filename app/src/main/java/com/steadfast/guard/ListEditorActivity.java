package com.steadfast.guard;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
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
    private LinearLayout items;

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

    private boolean isDomains() {
        return LIST_BLOCKED.equals(which) || LIST_ALLOWED.equals(which);
    }

    /** For the allow-list, adding is the loosening action; for every other list removing is. */
    private boolean addLoosens() {
        return LIST_ALLOWED.equals(which);
    }

    private void render() {
        String title, help;
        switch (which) {
            case LIST_ALLOWED:
                title = "Always-allowed websites";
                help = "Exceptions that are never blocked by your lists or keywords (e.g. a church site that a keyword catches). "
                        + "Category filtering by the web filter still applies.";
                break;
            case LIST_KEYWORDS:
                title = "Keyword filter";
                help = "Blocks web addresses, pages and app screens showing these words, including image descriptions (alt text). "
                        + "Whole words only, so “sex” won't block “Essex”. End a word with * to match endings (e.g. nud*). "
                        + "Pictures without text can't be read; the web filter level and SafeSearch handle explicit images.";
                break;
            case LIST_SEARCH:
                title = "Search filter";
                help = "Blocks searches and typing that contain these words or phrases in any app: Google, YouTube, Instagram, TikTok and so on. "
                        + "Keywords are also checked. End a word with * to match endings.";
                break;
            default:
                title = "Blocked websites";
                help = "Enter a domain like example.com. All its subdomains (www.example.com, m.example.com…) are blocked too. "
                        + "To block just one subdomain, enter it in full (e.g. news.example.com).";
        }
        LinearLayout p = Ui.page(this, title);
        Ui.para(p, help);

        LinearLayout addRow = new LinearLayout(this);
        addRow.setOrientation(LinearLayout.HORIZONTAL);
        final EditText input = new EditText(this);
        input.setHint(isDomains() ? "example.com" : "word or phrase");
        input.setInputType(isDomains() ? InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_CLASS_TEXT
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setSingleLine(true);
        addRow.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button add = new Button(this);
        add.setText("Add");
        add.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { add(input.getText().toString()); }
        });
        addRow.addView(add);
        p.addView(addRow);

        if (LIST_KEYWORDS.equals(which) || LIST_SEARCH.equals(which)) {
            Ui.button(p, "Add the starter list", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Config c = Config.get(ListEditorActivity.this);
                    String[] src = LIST_KEYWORDS.equals(which) ? STARTER_KEYWORDS : STARTER_SEARCH;
                    for (String s : src) set(c).add(s);
                    c.save(ListEditorActivity.this);
                    render();
                }
            });
        }

        Ui.header(p, set(Config.get(this)).size() + " entries");
        items = new LinearLayout(this);
        items.setOrientation(LinearLayout.VERTICAL);
        p.addView(items);
        List<String> sorted = new ArrayList<>(set(Config.get(this)));
        Collections.sort(sorted);
        for (final String s : sorted) {
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            TextView t = new TextView(this);
            t.setText(s);
            t.setTextSize(16);
            t.setTextColor(Color.parseColor("#1B1B1B"));
            r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            Button del = new Button(this);
            del.setText("Remove");
            del.setAllCaps(false);
            del.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { remove(s); }
            });
            r.addView(del);
            items.addView(r);
        }
    }

    private void add(String raw) {
        String v = isDomains() ? Policy.normalizeDomain(raw) : raw.trim().toLowerCase(Locale.ROOT);
        if (v.isEmpty() || (isDomains() && !v.contains("."))) {
            Ui.toast(this, isDomains() ? "Enter a domain like example.com" : "Enter a word");
            return;
        }
        final String value = v;
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
