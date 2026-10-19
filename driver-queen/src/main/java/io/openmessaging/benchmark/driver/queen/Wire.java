/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.openmessaging.benchmark.driver.queen;


import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

/**
 * The byte-level helpers of Queen's binary forms: little-endian integers, decimal and JSON text,
 * version-7 UUIDs, and the JSON document each benchmark message travels in.
 *
 * <p>A message is pushed as one JSON document carrying the producer's send time, which the consumer
 * hands to OMB as the publish timestamp (OMB's end-to-end latency is receive time minus that):
 * {@code {"t":<ms>,"v":"<payload>"}} when the payload is printable ASCII without '"' or '\' (OMB's
 * payload files are hex text), {@code {"t":<ms>,"b":"<base64>"}} otherwise.
 */
final class Wire {
    private Wire() {}

    private static final VarHandle I16 =
            MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle I32 =
            MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle I64 =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

    static final byte[] HEX = ascii("0123456789abcdef");
    /** The start of every message document. */
    static final byte[] T_OPEN = ascii("{\"t\":");

    static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    static void putU16(byte[] b, int at, int v) {
        I16.set(b, at, (short) v);
    }

    static void putU32(byte[] b, int at, int v) {
        I32.set(b, at, v);
    }

    static int u16(byte[] b, int at) {
        return ((short) I16.get(b, at)) & 0xffff;
    }

    /* A u32 used as a length or a count: refused above 2^31 - 1. */
    static int u32(byte[] b, int at) {
        int v = (int) I32.get(b, at);
        if (v < 0) {
            throw new IllegalArgumentException("a length or count above 2^31");
        }
        return v;
    }

    static long u64(byte[] b, int at) {
        return (long) I64.get(b, at);
    }

    /* Digits of v >= 0. */
    static int decimalLength(long v) {
        int n = 1;
        long x = 10;
        while (n < 19 && v >= x) {
            n++;
            x *= 10;
        }
        return n;
    }

    /* Writes v >= 0 in decimal at b[at..]; returns the end. */
    static int putDecimal(byte[] b, int at, long v) {
        int end = at + decimalLength(v);
        for (int i = end - 1; i >= at; i--) {
            b[i] = (byte) ('0' + (int) (v % 10));
            v /= 10;
        }
        return end;
    }

    /*
     * Writes a version-7 UUID at b[at..at+16]: 48 bits of ms, the version, 12 bits of randA, the
     * variant, 62 bits of randB.
     */
    static void putUuidV7(byte[] b, int at, long ms, int randA, long randB) {
        b[at] = (byte) (ms >>> 40);
        b[at + 1] = (byte) (ms >>> 32);
        b[at + 2] = (byte) (ms >>> 24);
        b[at + 3] = (byte) (ms >>> 16);
        b[at + 4] = (byte) (ms >>> 8);
        b[at + 5] = (byte) ms;
        b[at + 6] = (byte) (0x70 | ((randA >>> 8) & 0x0f));
        b[at + 7] = (byte) randA;
        b[at + 8] = (byte) (0x80 | ((int) (randB >>> 56) & 0x3f));
        for (int i = 0; i < 7; i++) {
            b[at + 9 + i] = (byte) (randB >>> (48 - 8 * i));
        }
    }

    /* Writes the 36-byte lower-case hyphenated text of the UUID at id[idAt..] to out[at..]. */
    static void putUuidText(byte[] id, int idAt, byte[] out, int at) {
        int p = at;
        for (int i = 0; i < 16; i++) {
            if (i == 4 || i == 6 || i == 8 || i == 10) {
                out[p++] = '-';
            }
            int c = id[idAt + i] & 0xff;
            out[p++] = HEX[c >>> 4];
            out[p++] = HEX[c & 0x0f];
        }
    }

    /*
     * The rest of a message document after its time: {@code ,"v":"<payload>"}} for printable ASCII
     * without '"' or '\', {@code ,"b":"<base64>"}} otherwise.
     */
    static byte[] payloadTail(byte[] payload) {
        boolean text = true;
        for (byte c : payload) {
            // bytes >= 0x80 are negative here, so non-ASCII goes to base64 too
            if (c < 0x20 || c == 0x7f || c == '"' || c == '\\') {
                text = false;
                break;
            }
        }
        byte[] body = text ? payload : Base64.getEncoder().encode(payload);
        byte[] tail = new byte[6 + body.length + 2];
        tail[0] = ',';
        tail[1] = '"';
        tail[2] = (byte) (text ? 'v' : 'b');
        tail[3] = '"';
        tail[4] = ':';
        tail[5] = '"';
        System.arraycopy(body, 0, tail, 6, body.length);
        tail[tail.length - 2] = '"';
        tail[tail.length - 1] = '}';
        return tail;
    }

    private static final byte[] B64 = new byte[256];

    static {
        java.util.Arrays.fill(B64, (byte) -1);
        byte[] alphabet = ascii("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/");
        for (int i = 0; i < alphabet.length; i++) {
            B64[alphabet[i]] = (byte) i;
        }
    }

    /* Bytes that base64 text of length len (with its padding) decodes to. */
    static int base64Length(byte[] s, int off, int len) {
        if (len == 0 || (len & 3) != 0) {
            return -1;
        }
        int pad = s[off + len - 1] == '=' ? (s[off + len - 2] == '=' ? 2 : 1) : 0;
        return len / 4 * 3 - pad;
    }

    /*
     * Decodes standard padded base64 s[off, off+len) into dst (at least base64Length bytes); returns
     * the decoded length, or -1 when s is not base64.
     */
    static int base64Decode(byte[] s, int off, int len, byte[] dst) {
        int n = base64Length(s, off, len);
        if (n < 0) {
            return -1;
        }
        int end = off + len;
        int o = 0;
        int bad = 0;
        int full = n / 3 * 4; // chars of complete groups
        int i = off;
        for (int stop = off + full; i < stop; i += 4) {
            int a = B64[s[i] & 0xff];
            int b = B64[s[i + 1] & 0xff];
            int c = B64[s[i + 2] & 0xff];
            int d = B64[s[i + 3] & 0xff];
            bad |= a | b | c | d;
            int v = a << 18 | b << 12 | c << 6 | d;
            dst[o] = (byte) (v >>> 16);
            dst[o + 1] = (byte) (v >>> 8);
            dst[o + 2] = (byte) v;
            o += 3;
        }
        if (i < end) { // the last group, with its padding
            int a = B64[s[i] & 0xff];
            int b = B64[s[i + 1] & 0xff];
            bad |= a | b;
            dst[o++] = (byte) (a << 2 | b >>> 4);
            if (s[i + 2] != '=') {
                int c = B64[s[i + 2] & 0xff];
                bad |= c;
                dst[o++] = (byte) (b << 4 | c >>> 2);
            }
        }
        return bad < 0 ? -1 : o;
    }

    /*
     * The value of {@code key} (given with its quotes and colon, e.g. {@code "count":}) as a
     * non-negative integer in the small JSON object b[0..len), or -1 when absent or not a number.
     */
    static long jsonLong(byte[] b, int len, byte[] key) {
        return jsonLong(b, 0, len, key);
    }

    /* {@link #jsonLong(byte[], int, byte[])} over b[off, off+len). */
    static long jsonLong(byte[] b, int off, int len, byte[] key) {
        if (b == null) {
            return -1;
        }
        len += off;
        outer:
        for (int i = off; i + key.length <= len; i++) {
            for (int k = 0; k < key.length; k++) {
                if (b[i + k] != key[k]) {
                    continue outer;
                }
            }
            int p = i + key.length;
            while (p < len && b[p] == ' ') {
                p++;
            }
            long v = 0;
            int d = 0;
            while (p < len && b[p] >= '0' && b[p] <= '9' && d < 18) {
                v = v * 10 + (b[p] - '0');
                p++;
                d++;
            }
            return d == 0 ? -1 : v;
        }
        return -1;
    }

    /* The start of an answer as text, for error messages. */
    static String excerpt(byte[] b, int len) {
        if (b == null || len <= 0) {
            return "(empty)";
        }
        int n = Math.min(len, 300);
        String s = new String(b, 0, n, StandardCharsets.UTF_8).trim();
        return n < len ? s + "..." : s;
    }

    /** A growable byte buffer. */
    static final class Buf {
        byte[] a;
        int n;

        Buf(int capacity) {
            a = new byte[capacity];
        }

        void ensure(int more) {
            if (n + more > a.length) {
                a = Arrays.copyOf(a, Math.max(a.length * 2, n + more));
            }
        }

        void put(byte c) {
            ensure(1);
            a[n++] = c;
        }

        void put(byte[] s) {
            put(s, 0, s.length);
        }

        void put(byte[] s, int off, int len) {
            ensure(len);
            System.arraycopy(s, off, a, n, len);
            n += len;
        }

        /* Appends s[off, off+len) as the content of a JSON string (without the quotes). */
        void putJsonChars(byte[] s, int off, int len) {
            int end = off + len;
            int run = off;
            for (int i = off; i < end; i++) {
                int c = s[i] & 0xff;
                if (c < 0x20 || c == '"' || c == '\\') {
                    put(s, run, i - run);
                    ensure(6);
                    a[n++] = '\\';
                    if (c == '"' || c == '\\') {
                        a[n++] = (byte) c;
                    } else {
                        a[n++] = 'u';
                        a[n++] = '0';
                        a[n++] = '0';
                        a[n++] = HEX[c >>> 4];
                        a[n++] = HEX[c & 0x0f];
                    }
                    run = i + 1;
                }
            }
            put(s, run, end - run);
        }

        void putJsonChars(String s) {
            byte[] b = utf8(s);
            putJsonChars(b, 0, b.length);
        }
    }
}
