package com.steadfast.guard;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Minimal DNS message parsing/building for the filtering VPN. */
final class Dns {
    private Dns() {}

    static final int TYPE_A = 1;
    static final int TYPE_AAAA = 28;

    /** Parsed first question of a query. */
    static final class Question {
        String name;
        int type;
        int qclass;
        int end; // offset just after the question
    }

    static int u16(byte[] b, int off) {
        return ((b[off] & 0xff) << 8) | (b[off + 1] & 0xff);
    }

    static void put16(byte[] b, int off, int v) {
        b[off] = (byte) (v >> 8);
        b[off + 1] = (byte) v;
    }

    static Question parseQuestion(byte[] msg) {
        if (msg.length < 17) return null;
        if (u16(msg, 4) < 1) return null; // QDCOUNT
        StringBuilder sb = new StringBuilder();
        int p = 12;
        int guard = 0;
        while (p < msg.length) {
            int len = msg[p] & 0xff;
            if (len == 0) { p++; break; }
            if ((len & 0xc0) != 0) return null; // compression not expected in a question
            if (p + 1 + len > msg.length || ++guard > 127) return null;
            if (sb.length() > 0) sb.append('.');
            for (int i = 0; i < len; i++) sb.append((char) (msg[p + 1 + i] & 0xff));
            p += len + 1;
        }
        if (p + 4 > msg.length) return null;
        Question q = new Question();
        q.name = sb.toString().toLowerCase(Locale.ROOT);
        q.type = u16(msg, p);
        q.qclass = u16(msg, p + 2);
        q.end = p + 4;
        return q;
    }

    /** Response that sends a blocked name to 0.0.0.0 / :: (or NXDOMAIN for other record types). */
    static byte[] blockedResponse(byte[] query, Question q, boolean nxdomain) {
        boolean answer = !nxdomain && (q.type == TYPE_A || q.type == TYPE_AAAA);
        int rdlen = q.type == TYPE_AAAA ? 16 : 4;
        int size = q.end + (answer ? 12 + rdlen : 0);
        byte[] r = new byte[size];
        System.arraycopy(query, 0, r, 0, q.end);
        int rd = u16(query, 2) & 0x0100;
        put16(r, 2, 0x8000 | rd | 0x0080 | (answer ? 0 : 3)); // QR, RD, RA, rcode
        put16(r, 4, 1);
        put16(r, 6, answer ? 1 : 0);
        put16(r, 8, 0);
        put16(r, 10, 0);
        if (answer) {
            int p = q.end;
            r[p] = (byte) 0xc0; r[p + 1] = 0x0c;           // pointer to question name
            put16(r, p + 2, q.type);
            put16(r, p + 4, 1);                             // IN
            r[p + 6] = 0; r[p + 7] = 0; r[p + 8] = 0; r[p + 9] = 60; // TTL 60s
            put16(r, p + 10, rdlen);
            // rdata already zero = 0.0.0.0 or ::
        }
        return r;
    }

    static byte[] buildQuery(String name, int type) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int id = Crypto.RNG.nextInt(0xffff);
        out.write(id >> 8); out.write(id);
        out.write(0x01); out.write(0x00);  // RD
        out.write(0); out.write(1);        // QDCOUNT
        for (int i = 0; i < 6; i++) out.write(0);
        for (String label : name.split("\\.")) {
            out.write(label.length());
            for (int i = 0; i < label.length(); i++) out.write(label.charAt(i));
        }
        out.write(0);
        out.write(type >> 8); out.write(type);
        out.write(0); out.write(1);
        return out.toByteArray();
    }

    private static int skipName(byte[] m, int p) {
        while (p < m.length) {
            int len = m[p] & 0xff;
            if (len == 0) return p + 1;
            if ((len & 0xc0) == 0xc0) return p + 2;
            p += len + 1;
        }
        return -1;
    }

    /** Extracts A/AAAA records of the requested type from a response: each entry is {ttl(4 bytes) + rdata}. */
    static List<byte[]> extractAddresses(byte[] resp, int type) {
        List<byte[]> out = new ArrayList<>();
        if (resp == null || resp.length < 12) return out;
        int qd = u16(resp, 4), an = u16(resp, 6);
        int p = 12;
        for (int i = 0; i < qd; i++) {
            p = skipName(resp, p);
            if (p < 0) return out;
            p += 4;
        }
        for (int i = 0; i < an && p + 10 <= resp.length; i++) {
            p = skipName(resp, p);
            if (p < 0 || p + 10 > resp.length) break;
            int t = u16(resp, p);
            int rdlen = u16(resp, p + 8);
            int rdStart = p + 10;
            if (rdStart + rdlen > resp.length) break;
            if (t == type) {
                byte[] rec = new byte[4 + rdlen];
                System.arraycopy(resp, p + 4, rec, 0, 4);
                System.arraycopy(resp, rdStart, rec, 4, rdlen);
                out.add(rec);
            }
            p = rdStart + rdlen;
        }
        return out;
    }

    /** Builds an answer for the original question using the given address records (SafeSearch rewrite). */
    static byte[] rewrittenResponse(byte[] query, Question q, List<byte[]> records) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] head = new byte[q.end];
        System.arraycopy(query, 0, head, 0, q.end);
        int rd = u16(query, 2) & 0x0100;
        put16(head, 2, 0x8000 | rd | 0x0080);
        put16(head, 4, 1);
        put16(head, 6, records.size());
        put16(head, 8, 0);
        put16(head, 10, 0);
        out.write(head, 0, head.length);
        for (byte[] rec : records) {
            out.write(0xc0); out.write(0x0c);
            out.write(q.type >> 8); out.write(q.type);
            out.write(0); out.write(1);
            out.write(rec, 0, 4);                 // TTL
            int rdlen = rec.length - 4;
            out.write(rdlen >> 8); out.write(rdlen);
            out.write(rec, 4, rdlen);
        }
        return out.toByteArray();
    }
}
