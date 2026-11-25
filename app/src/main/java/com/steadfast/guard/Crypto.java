package com.steadfast.guard;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** Hashing, HMAC signing, TOTP codes and random tokens. */
final class Crypto {
    private Crypto() {}

    static final SecureRandom RNG = new SecureRandom();
    private static final String B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    static byte[] random(int n) {
        byte[] b = new byte[n];
        RNG.nextBytes(b);
        return b;
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x & 0xff));
        return sb.toString();
    }

    static byte[] unhex(String s) {
        int n = s.length() / 2;
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return hex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static String hashPin(String pin, String saltHex) {
        try {
            PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), unhex(saltHex), 20000, 256);
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return hex(f.generateSecret(spec).getEncoded());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static boolean safeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    static String hmacHex(byte[] key, String msg) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return hex(mac.doFinal(msg.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static String base32(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                sb.append(B32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) sb.append(B32.charAt((buffer << (5 - bits)) & 31));
        return sb.toString();
    }

    static byte[] unbase32(String s) {
        String clean = s.toUpperCase().replace(" ", "").replace("=", "");
        byte[] out = new byte[clean.length() * 5 / 8];
        int buffer = 0, bits = 0, idx = 0;
        for (int i = 0; i < clean.length(); i++) {
            int v = B32.indexOf(clean.charAt(i));
            if (v < 0) throw new IllegalArgumentException("bad base32");
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                if (idx < out.length) out[idx++] = (byte) ((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out;
    }

    /** Lower-case random token suitable for ntfy topic names. */
    static String token(int bytes) {
        return base32(random(bytes)).toLowerCase();
    }

    /** RFC 6238 TOTP (SHA1, 6 digits, 30 seconds) — compatible with Google/Microsoft Authenticator. */
    static String totp(String base32Secret, long timeMillis) {
        try {
            byte[] key = unbase32(base32Secret);
            long counter = (timeMillis / 1000L) / 30L;
            byte[] msg = new byte[8];
            for (int i = 7; i >= 0; i--) {
                msg[i] = (byte) (counter & 0xff);
                counter >>= 8;
            }
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] h = mac.doFinal(msg);
            int off = h[h.length - 1] & 0x0f;
            int bin = ((h[off] & 0x7f) << 24) | ((h[off + 1] & 0xff) << 16)
                    | ((h[off + 2] & 0xff) << 8) | (h[off + 3] & 0xff);
            return String.format("%06d", bin % 1000000);
        } catch (Exception e) {
            return "------";
        }
    }

    static boolean verifyTotp(String base32Secret, String code) {
        if (base32Secret == null || base32Secret.isEmpty() || code == null) return false;
        String c = code.trim().replace(" ", "");
        long now = System.currentTimeMillis();
        for (int w = -1; w <= 1; w++) {
            if (safeEquals(totp(base32Secret, now + w * 30000L), c)) return true;
        }
        return false;
    }
}
