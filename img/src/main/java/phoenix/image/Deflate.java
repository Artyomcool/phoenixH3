package phoenix.image;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.GZIPInputStream;

public final class Deflate {
    public static byte[] compress(byte[] input) {
        Deflater d = new Deflater();
        return d.deflateFixed(input);
    }

    private static final class BitWriter {
        private byte[] buf = new byte[1 << 12];
        private int bitPos;

        private void ensureBits(int moreBits) {
            int needBytes = ((bitPos + moreBits + 7) >>> 3);
            if (needBytes > buf.length) {
                int newLen = buf.length;
                while (newLen < needBytes) newLen <<= 1;
                buf = copyOf(buf, newLen);
            }
        }

        void putBits(int value, int len) {
            if (len == 0) return;
            ensureBits(len);
            int byteIndex = bitPos >>> 3;
            int bitOffset = bitPos & 7;
            int v = value;
            int remaining = len;
            while (remaining > 0) {
                int free = 8 - bitOffset;
                int take = Math.min(free, remaining);
                int mask = (1 << take) - 1;
                buf[byteIndex] = (byte) (buf[byteIndex] | (((v & mask) << bitOffset) & 0xFF));
                v >>>= take;
                remaining -= take;
                bitPos += take;
                if ((bitPos & 7) == 0) {
                    byteIndex++;
                    bitOffset = 0;
                } else {
                    bitOffset = bitPos & 7;
                }
            }
        }

        void alignToByte() {
            int mod = bitPos & 7;
            if (mod != 0) putBits(0, 8 - mod);
        }

        byte[] toByteArray() {
            int size = (bitPos + 7) >>> 3;
            return copyOf(buf, size);
        }
    }

    private static final class FixedHuffman {
        final int[] litLenCode;
        final int[] litLenLen;
        final int[] distCode;
        final int[] distLen;

        FixedHuffman() {
            int[] llLen = new int[288];
            for (int i = 0; i <= 143; i++) llLen[i] = 8;
            for (int i = 144; i <= 255; i++) llLen[i] = 9;
            for (int i = 256; i <= 279; i++) llLen[i] = 7;
            for (int i = 280; i <= 287; i++) llLen[i] = 8;

            int[] distLenArr = new int[32];
            fill(distLenArr, 5);

            litLenCode = new int[288];
            litLenLen = llLen;
            distCode = new int[32];
            distLen = distLenArr;

            buildCanonical(litLenLen, litLenCode);
            buildCanonical(distLen, distCode);
        }

        private static void buildCanonical(int[] lengths, int[] codesOut) {
            int maxBits = 0;
            for (int l : lengths) if (l > maxBits) maxBits = l;
            int[] blCount = new int[maxBits + 1];
            for (int l : lengths) if (l != 0) blCount[l]++;

            int[] nextCode = new int[maxBits + 1];
            int code = 0;
            for (int bits = 1; bits <= maxBits; bits++) {
                code = (code + blCount[bits - 1]) << 1;
                nextCode[bits] = code;
            }

            for (int n = 0; n < lengths.length; n++) {
                int len = lengths[n];
                if (len != 0) {
                    int msbCode = nextCode[len]++;
                    codesOut[n] = bitReverse(msbCode, len);
                } else {
                    codesOut[n] = 0;
                }
            }
        }

        private static int bitReverse(int x, int bits) {
            int r = 0;
            for (int i = 0; i < bits; i++) {
                r = (r << 1) | (x & 1);
                x >>>= 1;
            }
            return r;
        }
    }

    private static final class Deflater {
        private static final int WSIZE = 1 << 15;
        private static final int MAX_MATCH = 258;
        private static final int MIN_MATCH = 3;

        private static final int HASH_BITS = 15;
        private static final int HASH_SIZE = 1 << HASH_BITS;
        private static final int HASH_MASK = HASH_SIZE - 1;
        private static final int HASH_SHIFT = (HASH_BITS + 2) / 3;

        private static final int[] LENGTH_BASE = {
                3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258
        };
        private static final int[] LENGTH_EXTRA = {
                0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0
        };
        private static final int[] DIST_BASE = {
                1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577
        };
        private static final int[] DIST_EXTRA = {
                0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13
        };

        byte[] deflateFixed(byte[] in) {
            BitWriter bw = new BitWriter();
            FixedHuffman fh = new FixedHuffman();

            bw.putBits(1, 1);
            bw.putBits(1, 1);
            bw.putBits(0, 1);

            lz77AndEncodeFixed(in, bw, fh);

            putCode(bw, fh.litLenCode[256], fh.litLenLen[256]);

            return bw.toByteArray();
        }

        private static void putCode(BitWriter bw, int code, int len) {
            bw.putBits(code, len);
        }

        private static int hash3(byte[] in, int p) {
            int v = ((in[p] & 0xFF) << 16) | ((in[p + 1] & 0xFF) << 8) | (in[p + 2] & 0xFF);
            int h = v;
            h ^= (h << 5) ^ (h >>> 13);
            return (h * 0x1e35a7bd) & HASH_MASK;
        }

        private void lz77AndEncodeFixed(byte[] in, BitWriter bw, FixedHuffman fh) {
            int n = in.length;
            int[] head = new int[HASH_SIZE];
            fill(head, -1);
            int[] prev = new int[n];
            fill(prev, -1);

            int pos = 0;
            while (pos < n) {
                int bestLen = 0;
                int bestDist = 0;

                if (pos + MIN_MATCH <= n - 1 && n - pos >= MIN_MATCH) {
                    if (pos + 2 < n) {
                        int h = hash3(in, pos);
                        int chain = head[h];
                        prev[pos] = chain;
                        head[h] = pos;

                        int limit = Math.max(0, pos - WSIZE);
                        int tries = 256;
                        while (chain >= limit && tries-- > 0) {
                            int dist = pos - chain;
                            if (dist > 0 && dist <= WSIZE) {
                                int l = matchLen(in, pos, chain, n);
                                if (l > bestLen) {
                                    bestLen = l;
                                    bestDist = dist;
                                    if (l == MAX_MATCH) break;
                                }
                            }
                            chain = prev[chain];
                        }
                    }
                }

                if (bestLen >= MIN_MATCH) {
                    writeLength(bw, fh, bestLen);
                    writeDistance(bw, fh, bestDist);
                    int end = Math.min(pos + bestLen, n - 2);
                    for (int p = pos + 1; p < end; p++) {
                        int h2 = hash3(in, p);
                        prev[p] = head[h2];
                        head[h2] = p;
                    }
                    pos += bestLen;
                } else {
                    int lit = in[pos] & 0xFF;
                    putCode(bw, fh.litLenCode[lit], fh.litLenLen[lit]);
                    if (pos + 2 < n) {
                        int h = hash3(in, pos);
                        prev[pos] = head[h];
                        head[h] = pos;
                    }
                    pos++;
                }
            }
        }

        private static int matchLen(byte[] in, int p1, int p2, int n) {
            int max = Math.min(MAX_MATCH, n - p1);
            int i = 0;
            while (i < max && in[p1 + i] == in[p2 + i]) i++;
            return i;
        }

        private static void writeLength(BitWriter bw, FixedHuffman fh, int len) {
            int symbol;
            int extraBits = 0;
            int extraVal = 0;

            if (len == 258) {
                symbol = 285;
            } else {
                int idx = 0;
                while (idx < LENGTH_BASE.length - 1 && LENGTH_BASE[idx + 1] <= len) idx++;
                int base = LENGTH_BASE[idx];
                int eb = LENGTH_EXTRA[idx];
                symbol = 257 + idx;
                extraBits = eb;
                extraVal = len - base;
            }

            putCode(bw, fh.litLenCode[symbol], fh.litLenLen[symbol]);
            if (extraBits > 0) bw.putBits(extraVal, extraBits);
        }

        private static void writeDistance(BitWriter bw, FixedHuffman fh, int dist) {
            int idx = 0;
            while (idx < DIST_BASE.length - 1 && DIST_BASE[idx + 1] <= dist) idx++;
            int base = DIST_BASE[idx];
            int eb = DIST_EXTRA[idx];
            int extraVal = dist - base;
            putCode(bw, fh.distCode[idx], fh.distLen[idx]);
            if (eb > 0) bw.putBits(extraVal, eb);
        }
    }

    private static final int[] TABLE = new int[256];

    static {
        for (int i = 0; i < 256; i++) {
            int c = i;
            for (int k = 0; k < 8; k++) {
                if ((c & 1) != 0)
                    c = (c >>> 1) ^ 0xEDB88320;
                else
                    c >>>= 1;
            }
            TABLE[i] = c;
        }
    }

    public static int crc32(byte[] data) {
        int c = 0xFFFFFFFF;
        for (int i = 0; i < 0 + data.length; i++) {
            c = TABLE[(c ^ (data[i] & 0xFF)) & 0xFF] ^ (c >>> 8);
        }
        return c;
    }

    public static byte[] gzip(byte[] input) {
        byte[] deflated = compress(input);

        byte[] out = new byte[
                10 + deflated.length + 8
                ];
        int p = 0;

        // --- GZIP header (10 bytes) ---
        out[p++] = (byte) 0x1F; // ID1
        out[p++] = (byte) 0x8B; // ID2
        out[p++] = (byte) 8;    // CM = DEFLATE
        out[p++] = 0;           // FLG
        // MTIME (4 bytes) = 0
        out[p++] = 0; out[p++] = 0; out[p++] = 0; out[p++] = 0;
        out[p++] = 0;           // XFL
        out[p++] = (byte) 255;  // OS = unknown (255)

        // --- DEFLATE raw data ---
        System.arraycopy(deflated, 0, out, p, deflated.length);
        p += deflated.length;

        // --- Trailer (CRC32 + ISIZE) ---
        int crc32 = crc32(deflated);
        int isize = input.length & 0xFFFFFFFF;
        out[p++] = (byte) (crc32);
        out[p++] = (byte) (crc32 >>> 8);
        out[p++] = (byte) (crc32 >>> 16);
        out[p++] = (byte) (crc32 >>> 24);
        out[p++] = (byte) (isize);
        out[p++] = (byte) (isize >>> 8);
        out[p++] = (byte) (isize >>> 16);
        out[p++] = (byte) (isize >>> 24);

        return out;
    }


    public static byte[] copyOf(byte[] original, int newLength) {
        byte[] copy = new byte[newLength];
        int len = Math.min(original.length, newLength);
        System.arraycopy(original, 0, copy, 0, len);
        return copy;
    }

    public static void fill(int[] a, int val) {
        for (int i = 0; i < a.length; i++) {
            a[i] = val;
        }
    }
}
