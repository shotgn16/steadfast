package com.steadfast.guard;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A local, on-device "VPN" that only captures DNS lookups. No browsing traffic leaves through it and
 * nothing is sent to any server other than the chosen family-safe DNS resolver.
 * Blocks domains/subdomains, keyword domains, bypass resolvers, and enforces SafeSearch.
 */
public class FilterVpnService extends VpnService implements Runnable {

    static final String ACTION_START = "com.steadfast.guard.START";
    static final String ACTION_STOP = "com.steadfast.guard.STOP";
    static final String ACTION_RESTART = "com.steadfast.guard.RESTART";

    private static final String VPN_ADDRESS = "10.111.222.1";
    private static final String DNS_ADDRESS = "10.111.222.2";

    static volatile boolean running;
    private static volatile boolean intentionalStop;

    private ParcelFileDescriptor tun;
    private FileOutputStream out;
    private Thread thread;
    private ExecutorService pool;
    private final Map<String, Long> recentBlocks = new ConcurrentHashMap<>();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            intentionalStop = true;
            shutdown();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_RESTART.equals(action)) {
            intentionalStop = true;
            shutdown();
        }
        intentionalStop = false;
        startTunnel();
        return START_STICKY;
    }

    private synchronized void startTunnel() {
        if (tun != null) return;
        Builder b = new Builder();
        b.setSession("Steadfast web filter");
        b.addAddress(VPN_ADDRESS, 32);
        b.addDnsServer(DNS_ADDRESS);
        b.addRoute(DNS_ADDRESS, 32);
        b.setMtu(1500);
        b.setBlocking(true);
        Intent open = new Intent(this, MainActivity.class);
        b.setConfigureIntent(PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE));
        try {
            tun = b.establish();
        } catch (Exception e) {
            tun = null;
        }
        if (tun == null) {
            running = false;
            return;
        }
        out = new FileOutputStream(tun.getFileDescriptor());
        pool = Executors.newFixedThreadPool(6);
        running = true;
        thread = new Thread(this, "steadfast-dns");
        thread.start();
    }

    private synchronized void shutdown() {
        running = false;
        if (thread != null) thread.interrupt();
        thread = null;
        if (pool != null) pool.shutdownNow();
        pool = null;
        try { if (tun != null) tun.close(); } catch (IOException ignored) {}
        tun = null;
    }

    @Override
    public void run() {
        ParcelFileDescriptor fd = tun;
        if (fd == null) return;
        FileInputStream in = new FileInputStream(fd.getFileDescriptor());
        byte[] buf = new byte[32767];
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                int n = in.read(buf);
                if (n <= 0) continue;
                final byte[] pkt = Arrays.copyOf(buf, n);
                ExecutorService p = pool;
                if (p != null) p.execute(new Runnable() {
                    @Override public void run() { handlePacket(pkt); }
                });
            } catch (IOException e) {
                break;
            } catch (Exception ignored) {
                // rejected execution during shutdown etc.
            }
        }
    }

    private void handlePacket(byte[] pkt) {
        try {
            if (pkt.length < 28 || ((pkt[0] >> 4) & 0xf) != 4) return; // IPv4 only
            int ihl = (pkt[0] & 0x0f) * 4;
            if (pkt[9] != 17) return;                                  // UDP only
            int total = Math.min(Dns.u16(pkt, 2), pkt.length);
            if (total < ihl + 8) return;
            int srcPort = Dns.u16(pkt, ihl);
            int dstPort = Dns.u16(pkt, ihl + 2);
            if (dstPort != 53) return;
            byte[] srcIp = Arrays.copyOfRange(pkt, 12, 16);
            byte[] dstIp = Arrays.copyOfRange(pkt, 16, 20);
            byte[] query = Arrays.copyOfRange(pkt, ihl + 8, total);
            byte[] resp = answer(query);
            if (resp != null) writePacket(dstIp, srcIp, 53, srcPort, resp);
        } catch (Exception ignored) {}
    }

    private byte[] answer(byte[] query) {
        Config cfg = Config.get(this);
        Dns.Question q = Dns.parseQuestion(query);
        String[] upstream = Policy.upstreamDns(cfg);
        if (q == null) return forward(query, upstream);

        String blockType = Policy.domainBlockType(cfg, q.name);
        if (blockType != null) {
            noteBlock(q.name, blockType);
            return Dns.blockedResponse(query, q, EventLog.DNS.equals(blockType));
        }

        String safe = (q.type == Dns.TYPE_A || q.type == Dns.TYPE_AAAA) ? Policy.safeSearchTarget(cfg, q.name) : null;
        if (safe != null) {
            byte[] r = forward(Dns.buildQuery(safe, q.type), upstream);
            List<byte[]> recs = Dns.extractAddresses(r, q.type);
            if (!recs.isEmpty()) return Dns.rewrittenResponse(query, q, recs);
            if (q.type == Dns.TYPE_AAAA) return Dns.rewrittenResponse(query, q, recs); // empty: force IPv4 safe path
        }
        byte[] r = forward(query, upstream);
        // Family resolvers answer 0.0.0.0 for adult/harmful sites: count it, and remember it so an
        // attempt to open it in a browser raises an alert.
        if (r != null && q.type == Dns.TYPE_A && cfg.isProtected() && cfg.filterLevel >= 1) {
            List<byte[]> a = Dns.extractAddresses(r, Dns.TYPE_A);
            if (a.size() == 1 && a.get(0).length == 8 && a.get(0)[4] == 0 && a.get(0)[5] == 0 && a.get(0)[6] == 0 && a.get(0)[7] == 0) {
                CATEGORY_BLOCKS.put(q.name, System.currentTimeMillis());
                if (CATEGORY_BLOCKS.size() > 300) CATEGORY_BLOCKS.clear();
                noteBlock(q.name, EventLog.DNS);
            }
        }
        return r;
    }

    /** Domains the family filter blocked recently (in memory only). */
    static final Map<String, Long> CATEGORY_BLOCKS = new ConcurrentHashMap<>();

    static boolean recentlyCategoryBlocked(String host) {
        Long t = CATEGORY_BLOCKS.get(host);
        if (t == null) t = CATEGORY_BLOCKS.get("www." + host);
        if (t == null) t = CATEGORY_BLOCKS.get("m." + host);
        return t != null && System.currentTimeMillis() - t < 120000;
    }

    private void noteBlock(String domain, String type) {
        long now = System.currentTimeMillis();
        Long last = recentBlocks.get(domain);
        if (last != null && now - last < 60000) return;
        recentBlocks.put(domain, now);
        if (recentBlocks.size() > 500) recentBlocks.clear();
        // Background lookups are only counted; the accessibility service raises alerts for pages actually opened.
        EventLog.record(this, EventLog.DNS, domain);
    }

    private byte[] forward(byte[] query, String[] servers) {
        for (String server : servers) {
            DatagramSocket s = null;
            try {
                s = new DatagramSocket();
                protect(s);
                s.setSoTimeout(4000);
                InetAddress addr = InetAddress.getByName(server);
                s.send(new DatagramPacket(query, query.length, addr, 53));
                byte[] buf = new byte[4096];
                DatagramPacket resp = new DatagramPacket(buf, buf.length);
                s.receive(resp);
                return Arrays.copyOf(buf, resp.getLength());
            } catch (Exception ignored) {
                // try the next server
            } finally {
                if (s != null) s.close();
            }
        }
        return null;
    }

    private void writePacket(byte[] srcIp, byte[] dstIp, int srcPort, int dstPort, byte[] payload) throws IOException {
        int total = 20 + 8 + payload.length;
        byte[] p = new byte[total];
        p[0] = 0x45;
        Dns.put16(p, 2, total);
        Dns.put16(p, 4, Crypto.RNG.nextInt(0xffff));
        p[8] = 64;
        p[9] = 17;
        System.arraycopy(srcIp, 0, p, 12, 4);
        System.arraycopy(dstIp, 0, p, 16, 4);
        Dns.put16(p, 10, ipChecksum(p, 20));
        Dns.put16(p, 20, srcPort);
        Dns.put16(p, 22, dstPort);
        Dns.put16(p, 24, 8 + payload.length);
        // UDP checksum 0 = not used (allowed for IPv4)
        System.arraycopy(payload, 0, p, 28, payload.length);
        FileOutputStream o = out;
        if (o == null) return;
        synchronized (this) {
            o.write(p);
        }
    }

    private static int ipChecksum(byte[] b, int len) {
        int sum = 0;
        for (int i = 0; i < len; i += 2) sum += Dns.u16(b, i);
        while ((sum >> 16) != 0) sum = (sum & 0xffff) + (sum >> 16);
        return ~sum & 0xffff;
    }

    @Override
    public void onRevoke() {
        Config c = Config.get(this);
        if (c.isProtected() && !intentionalStop) {
            EventLog.record(this, EventLog.TAMPER, "The web filter was switched off (VPN disconnected or replaced by another VPN).");
        }
        shutdown();
        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        shutdown();
        super.onDestroy();
    }
}
