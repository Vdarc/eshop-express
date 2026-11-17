package com.traslation.backend.canal;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Minimal Canal 1.1.x TCP protobuf-wire client, intentionally dependency-free. */
final class CanalProtocol {
    static final int HANDSHAKE = 1,
            AUTH = 2,
            ACK = 3,
            SUB = 4,
            GET = 6,
            MESSAGES = 7,
            CLIENT_ACK = 8,
            HEARTBEAT = 11,
            ROLLBACK = 12;

    record Packet(int type, byte[] body) {}

    record Batch(long id, List<byte[]> entries) {}

    record ColumnChange(Map<String, String> before, Map<String, String> after) {}

    record Entry(String schema, String table, int type, boolean ddl, List<ColumnChange> rows) {}

    private CanalProtocol() {}

    static byte[] varint(long x) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        while ((x & ~0x7fL) != 0) {
            b.write((int) (x & 0x7f) | 0x80);
            x >>>= 7;
        }
        b.write((int) x);
        return b.toByteArray();
    }

    static byte[] v(int field, long x) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.writeBytes(varint((long) field << 3));
        b.writeBytes(varint(x));
        return b.toByteArray();
    }

    static byte[] bytes(int field, byte[] x) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.writeBytes(varint(((long) field << 3) | 2));
        b.writeBytes(varint(x.length));
        b.writeBytes(x);
        return b.toByteArray();
    }

    static byte[] str(int field, String s) {
        return s == null || s.isEmpty()
                ? new byte[0]
                : bytes(field, s.getBytes(StandardCharsets.UTF_8));
    }

    static Map<Integer, List<Object>> parse(byte[] input) throws IOException {
        Map<Integer, List<Object>> out = new HashMap<>();
        int[] p = {0};
        while (p[0] < input.length) {
            long key = readVarint(input, p);
            int f = (int) (key >>> 3), w = (int) (key & 7);
            Object value;
            if (w == 0) value = readVarint(input, p);
            else if (w == 2) {
                int n = (int) readVarint(input, p);
                if (n < 0 || p[0] + n > input.length)
                    throw new IOException("invalid protobuf length");
                value = Arrays.copyOfRange(input, p[0], p[0] + n);
                p[0] += n;
            } else if (w == 1) {
                if (p[0] + 8 > input.length) throw new IOException("truncated fixed64");
                value = Arrays.copyOfRange(input, p[0], p[0] + 8);
                p[0] += 8;
            } else if (w == 5) {
                if (p[0] + 4 > input.length) throw new IOException("truncated fixed32");
                value = Arrays.copyOfRange(input, p[0], p[0] + 4);
                p[0] += 4;
            } else throw new IOException("unsupported protobuf wire type " + w);
            out.computeIfAbsent(f, k -> new ArrayList<>()).add(value);
        }
        return out;
    }

    static long readVarint(byte[] b, int[] p) throws IOException {
        long x = 0;
        int shift = 0;
        while (p[0] < b.length && shift <= 63) {
            int c = b[p[0]++] & 255;
            x |= (long) (c & 127) << shift;
            if ((c & 128) == 0) return x;
            shift += 7;
        }
        throw new IOException("invalid varint");
    }

    static Object first(Map<Integer, List<Object>> m, int f, Object d) {
        List<Object> a = m.get(f);
        return a == null || a.isEmpty() ? d : a.get(0);
    }

    static long number(Map<Integer, List<Object>> m, int f, long d) {
        Object x = first(m, f, d);
        return x instanceof Number n ? n.longValue() : d;
    }

    static byte[] raw(Map<Integer, List<Object>> m, int f) {
        Object x = first(m, f, new byte[0]);
        return x instanceof byte[] b ? b : new byte[0];
    }

    static String text(Map<Integer, List<Object>> m, int f) {
        return new String(raw(m, f), StandardCharsets.UTF_8);
    }

    static byte[] frame(int type, byte[] body) {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.writeBytes(v(3, type));
        if (body.length > 0) payload.writeBytes(bytes(5, body));
        byte[] p = payload.toByteArray();
        ByteBuffer out = ByteBuffer.allocate(4 + p.length).order(ByteOrder.BIG_ENDIAN);
        out.putInt(p.length).put(p);
        return out.array();
    }

    static Packet read(Socket s) throws IOException {
        DataInputStream in = new DataInputStream(s.getInputStream());
        int n = in.readInt();
        if (n <= 0 || n > 64 * 1024 * 1024)
            throw new IOException("invalid Canal packet length=" + n);
        byte[] payload = in.readNBytes(n);
        if (payload.length != n) throw new EOFException("truncated Canal packet");
        Map<Integer, List<Object>> m = parse(payload);
        return new Packet((int) number(m, 3, 0), raw(m, 5));
    }

    static void send(Socket s, int type, byte[] body) throws IOException {
        s.getOutputStream().write(frame(type, body));
        s.getOutputStream().flush();
    }

    static byte[] auth(
            String user, String password, byte[] seed, String dest, String client, String filter)
            throws Exception {
        byte[] one =
                MessageDigest.getInstance("SHA-1")
                        .digest(password.getBytes(StandardCharsets.UTF_8));
        byte[] both = new byte[seed.length + one.length];
        System.arraycopy(seed, 0, both, 0, seed.length);
        System.arraycopy(one, 0, both, seed.length, one.length);
        byte[] digest = MessageDigest.getInstance("SHA-1").digest(both);
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.writeBytes(str(1, user));
        b.writeBytes(bytes(2, digest));
        b.writeBytes(str(5, dest));
        b.writeBytes(str(6, client));
        b.writeBytes(str(7, filter));
        return b.toByteArray();
    }

    static void expectAck(Socket s, String stage) throws IOException {
        Packet p = read(s);
        if (p.type() != ACK) throw new IOException(stage + " expected ACK, got " + p.type());
        Map<Integer, List<Object>> a = parse(p.body());
        long code = number(a, 1, 0);
        if (code != 0) throw new IOException(stage + " failed: " + text(a, 2));
    }

    static byte[] destClient(String dest, String client) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.writeBytes(str(1, dest));
        b.writeBytes(str(2, client));
        return b.toByteArray();
    }

    static Batch get(Socket s, String dest, String client, int size, int timeoutMs)
            throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.writeBytes(destClient(dest, client));
        b.writeBytes(v(3, size));
        b.writeBytes(v(4, timeoutMs));
        send(s, GET, b.toByteArray());
        while (true) {
            Packet p = read(s);
            if (p.type() == HEARTBEAT) continue;
            if (p.type() == ACK) {
                Map<Integer, List<Object>> a = parse(p.body());
                throw new IOException("GET failed: " + text(a, 2));
            }
            if (p.type() != MESSAGES) continue;
            Map<Integer, List<Object>> m = parse(p.body());
            long id = number(m, 1, -1);
            if (id > Long.MAX_VALUE / 2) id -= 1L << 64;
            List<byte[]> entries = new ArrayList<>();
            for (Object o : m.getOrDefault(2, List.of())) entries.add((byte[]) o);
            return new Batch(id, entries);
        }
    }

    static byte[] batchId(String dest, String client, long id) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.writeBytes(destClient(dest, client));
        b.writeBytes(v(3, id));
        return b.toByteArray();
    }

    static List<Entry> entries(List<byte[]> raw) throws IOException {
        List<Entry> out = new ArrayList<>();
        for (byte[] bytes : raw) {
            Map<Integer, List<Object>> ent = parse(bytes);
            Map<Integer, List<Object>> h = parse(raw(ent, 1));
            int entryType = (int) number(ent, 2, 2);
            if (entryType != 2) continue;
            byte[] store = raw(ent, 3);
            if (store.length == 0) continue;
            Map<Integer, List<Object>> rc = parse(store);
            boolean ddl = number(rc, 10, 0) != 0;
            List<ColumnChange> rows = new ArrayList<>();
            for (Object rdRaw : rc.getOrDefault(12, List.of())) {
                Map<Integer, List<Object>> rd = parse((byte[]) rdRaw);
                rows.add(
                        new ColumnChange(
                                columns(rd.getOrDefault(1, List.of())),
                                columns(rd.getOrDefault(2, List.of()))));
            }
            out.add(new Entry(text(h, 8), text(h, 9), (int) number(rc, 2, 0), ddl, rows));
        }
        return out;
    }

    private static Map<String, String> columns(List<Object> raws) throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        for (Object cRaw : raws) {
            Map<Integer, List<Object>> c = parse((byte[]) cRaw);
            String name = text(c, 3);
            if (name.isBlank()) continue;
            out.put(name, number(c, 6, 0) != 0 ? null : text(c, 8));
        }
        return out;
    }
}
