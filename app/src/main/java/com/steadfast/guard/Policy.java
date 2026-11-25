package com.steadfast.guard;

import android.content.Context;
import android.net.Uri;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Decides what is blocked. Shared by the VPN filter and the accessibility service. */
final class Policy {
    private Policy() {}

    /** Result of a block decision. */
    static final class Verdict {
        final String type;
        final String message;
        Verdict(String type, String message) { this.type = type; this.message = message; }
    }

    /** DNS-over-HTTPS / DoT resolvers that browsers could use to get around the filter. */
    static final Set<String> DOH_HOSTS = new HashSet<>();
    static {
        String[] h = {
                "dns.google", "dns.google.com", "cloudflare-dns.com", "mozilla.cloudflare-dns.com",
                "chrome.cloudflare-dns.com", "one.one.one.one", "1dot1dot1dot1.cloudflare-dns.com",
                "security.cloudflare-dns.com", "family.cloudflare-dns.com",
                "dns.quad9.net", "dns9.quad9.net", "dns10.quad9.net", "dns11.quad9.net",
                "doh.opendns.com", "doh.familyshield.opendns.com", "dns.nextdns.io", "doh.cleanbrowsing.org",
                "dns.adguard.com", "dns.adguard-dns.com", "family.adguard-dns.com", "doh.dns.sb", "dns.sb",
                "doh.mullvad.net", "dns.mullvad.net", "dns.controld.com", "freedns.controld.com",
                "doh.libredns.gr", "dns.alidns.com", "doh.pub", "dns.twnic.tw",
                // Firefox "canary" domain: answering NXDOMAIN makes Firefox switch its built-in DoH off.
                "use-application-dns.net"
        };
        Collections.addAll(DOH_HOSTS, h);
    }

    // ------------------------------------------------------------------ domains

    static String normalizeDomain(String input) {
        String d = input.trim().toLowerCase(Locale.ROOT);
        int scheme = d.indexOf("://");
        if (scheme >= 0) d = d.substring(scheme + 3);
        int slash = d.indexOf('/');
        if (slash >= 0) d = d.substring(0, slash);
        int at = d.lastIndexOf('@');
        if (at >= 0) d = d.substring(at + 1);
        int colon = d.indexOf(':');
        if (colon >= 0) d = d.substring(0, colon);
        if (d.startsWith("*.")) d = d.substring(2);
        if (d.startsWith("www.")) d = d.substring(4);
        while (d.endsWith(".")) d = d.substring(0, d.length() - 1);
        return d;
    }

    /** True if the domain or any parent domain is in the set (so example.com also covers a.b.example.com). */
    static boolean matchesDomain(Set<String> set, String domain) {
        if (set.isEmpty()) return false;
        String d = domain;
        while (true) {
            if (set.contains(d)) return true;
            int i = d.indexOf('.');
            if (i < 0) return false;
            d = d.substring(i + 1);
        }
    }

    static boolean isDoh(String domain) {
        return matchesDomain(DOH_HOSTS, domain);
    }

    /** Returns null if allowed, otherwise an EventLog type explaining why the domain is blocked. */
    static String domainBlockType(Config c, String domain) {
        if (!c.isProtected()) return null;
        if (isDoh(domain)) return EventLog.DNS;
        if (!c.rulesActive()) return null;
        if (matchesDomain(c.allowedDomains, domain)) return null;
        if (matchesDomain(c.blockedDomains, domain)) return EventLog.SITE;
        if (findKeyword(c, domain.replace('.', ' ')) != null) return EventLog.KEYWORD;
        return null;
    }

    /** SafeSearch: hostnames that should be answered with a "safe" variant. Returns null if none. */
    static String safeSearchTarget(Config c, String domain) {
        if (!c.isProtected() || !c.safeSearch) return null;
        if (domain.equals("google.com") || domain.equals("www.google.com")
                || domain.matches("(www\\.)?google\\.[a-z]{2,3}(\\.[a-z]{2})?")) {
            if (!domain.contains("googleapis") && !domain.startsWith("mail.") && !domain.startsWith("accounts.")) {
                return "forcesafesearch.google.com";
            }
        }
        switch (domain) {
            case "www.youtube.com": case "m.youtube.com": case "youtube.com":
            case "youtubei.googleapis.com": case "youtube.googleapis.com": case "www.youtube-nocookie.com":
                return "restrict.youtube.com";
            case "www.bing.com": case "bing.com":
                return "strict.bing.com";
            case "duckduckgo.com": case "www.duckduckgo.com": case "start.duckduckgo.com":
                return "safe.duckduckgo.com";
            case "search.yahoo.com":
                return null;
            default:
                return null;
        }
    }

    static String[] upstreamDns(Config c) {
        int level = c.isProtected() ? c.filterLevel : 0;
        switch (level) {
            case 1: return new String[]{"1.1.1.2", "1.0.0.2"};
            case 2: return new String[]{"1.1.1.3", "1.0.0.3"};
            case 3: return new String[]{"185.228.168.168", "185.228.169.168"};
            default: return new String[]{"1.1.1.1", "1.0.0.1"};
        }
    }

    static String filterLevelName(int level) {
        switch (level) {
            case 1: return "Malware & phishing only";
            case 2: return "Adult content + malware (Cloudflare for Families)";
            case 3: return "Strict family (CleanBrowsing Family)";
            default: return "Off";
        }
    }

    // ------------------------------------------------------------------ text matching

    private static Pattern keywordPattern, searchPattern;
    private static int patternVersion = -1;

    private static synchronized void ensurePatterns(Config c) {
        if (patternVersion == c.version && patternVersion != -1) return;
        keywordPattern = build(c.keywords);
        searchPattern = build(c.searchTerms);
        patternVersion = c.version;
    }

    /** Whole-word, case-insensitive. A trailing * matches any word ending (e.g. "nud*"). */
    static Pattern build(Set<String> terms) {
        List<String> list = new ArrayList<>(terms);
        if (list.isEmpty()) return null;
        Collections.sort(list, new Comparator<String>() {
            @Override public int compare(String a, String b) { return b.length() - a.length(); }
        });
        StringBuilder sb = new StringBuilder("(?<![\\p{L}\\p{N}])(?:");
        boolean first = true;
        for (String t : list) {
            String term = t.trim();
            if (term.isEmpty()) continue;
            if (!first) sb.append('|');
            first = false;
            boolean wildcard = term.endsWith("*");
            if (wildcard) term = term.substring(0, term.length() - 1);
            // spaces in a phrase match any run of whitespace/punctuation
            String[] words = term.split("\\s+");
            for (int i = 0; i < words.length; i++) {
                if (i > 0) sb.append("[\\s\\p{P}+]+");
                sb.append(Pattern.quote(words[i]));
            }
            if (wildcard) sb.append("[\\p{L}\\p{N}]*");
        }
        if (first) return null;
        sb.append(")(?![\\p{L}\\p{N}])");
        return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    static String findKeyword(Config c, CharSequence text) {
        if (text == null || text.length() == 0) return null;
        ensurePatterns(c);
        return find(keywordPattern, text);
    }

    static String findSearchTerm(Config c, CharSequence text) {
        if (text == null || text.length() == 0) return null;
        ensurePatterns(c);
        String s = find(searchPattern, text);
        return s != null ? s : find(keywordPattern, text);
    }

    private static String find(Pattern p, CharSequence text) {
        if (p == null) return null;
        Matcher m = p.matcher(text);
        return m.find() ? m.group() : null;
    }

    /** Pulls the search query out of a search-engine URL, if there is one. */
    static String searchQueryFromUrl(String url) {
        try {
            String u = url.contains("://") ? url : "https://" + url;
            Uri uri = Uri.parse(u);
            String[] keys = {"q", "query", "search_query", "p", "text", "wd", "k"};
            for (String k : keys) {
                String v = uri.getQueryParameter(k);
                if (v != null && !v.isEmpty()) return v;
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ------------------------------------------------------------------ apps

    static boolean inWindow(int now, int start, int end) {
        if (start == end) return true;
        if (start < end) return now >= start && now < end;
        return now >= start || now < end;
    }

    static int nowMinutes() {
        Calendar cal = Calendar.getInstance();
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
    }

    static Verdict appVerdict(Context ctx, Config c, String pkg) {
        if (!c.rulesActive()) return null;
        if (Guard.alwaysAllowed(ctx).contains(pkg)) return null;
        Config.AppRule r = c.apps.get(pkg);
        boolean essential = r != null && r.essential;
        if (c.nfcLocked && c.nfcLockdown && !essential) {
            return new Verdict(EventLog.LOCKDOWN, "Lockdown is on. Scan your NFC tag to unlock your apps.");
        }
        if (r != null && r.blocked) {
            return new Verdict(EventLog.APP, "This app is blocked.");
        }
        int now = nowMinutes();
        if (c.downtimeEnabled && !essential && inWindow(now, c.downtimeStart, c.downtimeEnd)) {
            return new Verdict(EventLog.DOWNTIME, "Downtime (" + Guard.hhmm(c.downtimeStart) + "–"
                    + Guard.hhmm(c.downtimeEnd) + "). Only essential apps are available.");
        }
        if (r != null && r.startMin >= 0 && !inWindow(now, r.startMin, r.endMin)) {
            return new Verdict(EventLog.LIMIT, "This app is only allowed between "
                    + Guard.hhmm(r.startMin) + " and " + Guard.hhmm(r.endMin) + ".");
        }
        if (r != null && r.limitMin >= 0) {
            long used = Usage.todayMs(ctx, pkg);
            if (used >= r.limitMin * 60000L) {
                return new Verdict(EventLog.LIMIT, "You've used your " + r.limitMin + " minutes for today.");
            }
        }
        return null;
    }
}
