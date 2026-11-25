package com.steadfast.guard;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Tiny HTTP helper (no third-party libraries). */
final class Net {
    private Net() {}

    static String topicUrl(String server, String topic) {
        String s = server.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s + "/" + topic;
    }

    static int post(String url, String body, Map<String, String> headers) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        try {
            con.setConnectTimeout(10000);
            con.setReadTimeout(15000);
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            if (headers != null) for (Map.Entry<String, String> h : headers.entrySet()) con.setRequestProperty(h.getKey(), h.getValue());
            byte[] data = body.getBytes(StandardCharsets.UTF_8);
            con.setFixedLengthStreamingMode(data.length);
            OutputStream os = con.getOutputStream();
            os.write(data);
            os.close();
            int code = con.getResponseCode();
            InputStream is = code < 400 ? con.getInputStream() : con.getErrorStream();
            if (is != null) { while (is.read() != -1) { /* drain */ } is.close(); }
            return code;
        } finally {
            con.disconnect();
        }
    }

    static String get(String url) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        try {
            con.setConnectTimeout(10000);
            con.setReadTimeout(20000);
            int code = con.getResponseCode();
            if (code >= 400) throw new IOException("HTTP " + code);
            BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            r.close();
            return sb.toString();
        } finally {
            con.disconnect();
        }
    }
}
