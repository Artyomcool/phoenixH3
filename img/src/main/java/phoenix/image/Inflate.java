package phoenix.image;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;

public final class Inflate {

    private static final boolean DEBUG = true;

    private Inflate() {}

    public static byte[] inflate(byte[] input) {
        if (input == null || input.length == 0) return new byte[0];

        // GZIP
        if (isGzip(input)) {
            int p = 0;
            req(input.length >= 10, "Truncated gzip header", p, null, 0, 0, 0, 0);
            if (u8(input[p++]) != 0x1F || u8(input[p++]) != 0x8B) fail("Bad gzip header", p, null, 0, 0, 0, 0);
            int cm = u8(input[p++]); if (cm != 8) fail("Unsupported gzip CM", p, null, 0, 0, 0, 0);
            int flg = u8(input[p++]);
            p += 4; // mtime
            p += 2; // xfl, os
            if ((flg & 0x04) != 0) { // FEXTRA
                req(p + 2 <= input.length, "Truncated gzip FEXTRA", p, null, 0, 0, 0, 0);
                int xlen = u8(input[p]) | (u8(input[p+1]) << 8);
                p += 2 + xlen;
            }
            if ((flg & 0x08) != 0) { while (p < input.length && input[p++] != 0) {} } // FNAME
            if ((flg & 0x10) != 0) { while (p < input.length && input[p++] != 0) {} } // FCOMMENT
            if ((flg & 0x02) != 0) p += 2; // FHCRC

            BitReader br = new BitReader(input, p);
            ByteSink out = new ByteSink();
            inflateBlocks(br, out);
            int end = br.bytePos;
            req(end + 8 <= input.length, "Truncated gzip trailer", end, br, 0, 0, 0, out.size());
            return out.toByteArray();
        }

        // zlib
        if (isZlib(input)) {
            int cmf = u8(input[0]), flg = u8(input[1]);
            req((cmf & 0x0F) == 8 && ((cmf << 8) + flg) % 31 == 0, "Bad zlib header", 0, null, 0, 0, 0, 0);
            int pos = 2;
            if ((flg & 0x20) != 0) pos += 4; // DICTID
            BitReader br = new BitReader(input, pos);
            ByteSink out = new ByteSink();
            inflateBlocks(br, out);
            return out.toByteArray();
        }

        // raw DEFLATE
        BitReader br = new BitReader(input, 0);
        ByteSink out = new ByteSink();
        inflateBlocks(br, out);
        return out.toByteArray();
    }

    private static void inflateBlocks(BitReader br, ByteSink out) {
        boolean last;
        do {
            last = br.readBits(1) != 0;
            int type = br.readBits(2);
            if (type == 0) { // stored
                br.alignToByte();
                int len  = br.readByte() | (br.readByte() << 8);
                int nlen = br.readByte() | (br.readByte() << 8);
                req((len ^ 0xFFFF) == nlen, "LEN/NLEN mismatch", br.bytePos, br, 0, 0, 0, out.size());
                out.ensure(len);
                for (int i = 0; i < len; i++) out.write((byte) br.readByte());
            } else if (type == 1) { // fixed trees
                Huffman litlen = fixedLitLen();
                Huffman dist   = fixedDist();
                decode(br, out, litlen, dist);
            } else if (type == 2) { // dynamic trees
                Huffman[] t = readDynamicTrees(br);
                decode(br, out, t[0], t[1]);
            } else {
                fail("BTYPE=3 reserved", br.bytePos, br, 0, 0, 0, out.size());
            }
        } while (!last);
    }

    private static void decode(BitReader br, ByteSink out, Huffman litlen, Huffman dist) {
        for (;;) {
            int sym = litlen.decode(br);
            if (sym < 256) {
                out.write((byte) sym);
            } else if (sym == 256) {
                return; // end of block
            } else {
                if (sym >= 286) fail("Reserved length symbol " + sym, br.bytePos, br, sym, 0, 0, out.size());
                int len  = readLen(br, sym);
                int dSym = dist.decode(br);         // 0..29
                if (dSym < 0 || dSym > 29) fail("Invalid distance symbol " + dSym, br.bytePos, br, sym, len, dSym, out.size());
                int distance = readDist(br, dSym);
                if (distance <= 0 || distance > out.size())
                    fail("Bad distance", br.bytePos, br, sym, len, distance, out.size());
                out.copyFromHistory(distance, len);
            }
        }
    }

    private static final int[] LEN_BASE = {
            3,4,5,6,7,8,9,10,11,13,15,17,19,23,27,31,
            35,43,51,59,67,83,99,115,131,163,195,227,258
    };
    private static final int[] LEN_EXTRA = {
            0,0,0,0,0,0,0,0,1,1,1,1,2,2,2,2,
            3,3,3,3,4,4,4,4,5,5,5,5,0
    };
    private static final int[] DIST_BASE = {
            1,2,3,4,5,7,9,13,17,25,33,49,65,97,129,193,
            257,385,513,769,1025,1537,2049,3073,4097,6145,8193,12289,16385,24577
    };
    private static final int[] DIST_EXTRA = {
            0,0,0,0,1,1,2,2,3,3,4,4,5,5,6,6,
            7,7,8,8,9,9,10,10,11,11,12,12,13,13
    };
    private static final int[] CL_ORDER = {16,17,18,0,8,7,9,6,10,5,11,4,12,3,13,2,14,1,15};

    private static int readLen(BitReader br, int sym) {
        int idx = sym - 257; // 0..28
        int base = LEN_BASE[idx];
        int ex   = LEN_EXTRA[idx];
        return base + (ex == 0 ? 0 : br.readBits(ex));
    }
    private static int readDist(BitReader br, int sym) {
        int base = DIST_BASE[sym];
        int ex   = DIST_EXTRA[sym];
        return base + (ex == 0 ? 0 : br.readBits(ex));
    }

    private static Huffman fixedLitLen() {
        int[] lens = new int[288];
        for (int i = 0; i <= 143; i++) lens[i] = 8;
        for (int i = 144; i <= 255; i++) lens[i] = 9;
        for (int i = 256; i <= 279; i++) lens[i] = 7;
        for (int i = 280; i <= 287; i++) lens[i] = 8;
        return Huffman.build(lens, 288);
    }
    private static Huffman fixedDist() {
        int[] lens = new int[32];
        for (int i = 0; i < 32; i++) lens[i] = 5;
        return Huffman.build(lens, 32);
    }

    private static Huffman[] readDynamicTrees(BitReader br) {
        int HLIT  = br.readBits(5) + 257;
        int HDIST = br.readBits(5) + 1;
        int HCLEN = br.readBits(4) + 4;

        int[] clLen = new int[19];
        for (int i = 0; i < HCLEN; i++) clLen[CL_ORDER[i]] = br.readBits(3);
        Huffman cl = Huffman.build(clLen, 19);

        int total = HLIT + HDIST;
        int[] lens = new int[total];
        for (int i = 0; i < total; ) {
            int s = cl.decode(br);
            if (s <= 15) {
                lens[i++] = s;
            } else if (s == 16) {
                int r = 3 + br.readBits(2);
                req(i > 0, "Repeat with no prev", br.bytePos, br, 0, 0, 0, 0);
                int prev = lens[i-1];
                for (int k = 0; k < r; k++) lens[i++] = prev;
            } else if (s == 17) {
                int r = 3 + br.readBits(3);
                for (int k = 0; k < r; k++) lens[i++] = 0;
            } else if (s == 18) {
                int r = 11 + br.readBits(7);
                for (int k = 0; k < r; k++) lens[i++] = 0;
            } else {
                fail("Bad code length symbol " + s, br.bytePos, br, 0, 0, 0, 0);
            }
            req(i <= total, "Code length overrun", br.bytePos, br, 0, 0, 0, 0);
        }

        int[] litLenLens = new int[HLIT];
        int[] distLens   = new int[HDIST];
        System.arraycopy(lens, 0,       litLenLens, 0, HLIT);
        System.arraycopy(lens, HLIT,    distLens,   0, HDIST);

        req(anyNonZero(litLenLens), "Empty lit/len tree", br.bytePos, br, 0, 0, 0, 0);
        req(anyNonZero(distLens),   "Empty dist tree",    br.bytePos, br, 0, 0, 0, 0);

        return new Huffman[]{ Huffman.build(litLenLens, HLIT), Huffman.build(distLens, HDIST) };
    }
    private static boolean anyNonZero(int[] a) { for (int v : a) if (v != 0) return true; return false; }

    private static final class BitReader {
        final byte[] a;
        int bytePos;
        long bitBuf; // LSB-first
        int  bitCnt;

        BitReader(byte[] a, int start) { this.a = a; this.bytePos = start; }

        int readBits(int n) {
            ensureBits(n);
            int v = (int)(bitBuf & ((1L << n) - 1));
            bitBuf >>>= n;
            bitCnt -= n;
            return v;
        }
        void ensureBits(int n) {
            while (bitCnt < n) {
                if (bytePos >= a.length) fail("Unexpected end", bytePos, this, 0, 0, 0, 0);
                bitBuf |= ((long)u8(a[bytePos++])) << bitCnt;
                bitCnt += 8;
            }
        }
        int readByte() {
            alignToByte();
            if (bytePos >= a.length) fail("Unexpected end", bytePos, this, 0, 0, 0, 0);
            return u8(a[bytePos++]);
        }
        void alignToByte() { bitBuf = 0; bitCnt = 0; }
    }

    private static final class Huffman {
        final int[] left, right, symbol;
        Huffman(int[] L, int[] R, int[] S) { left=L; right=R; symbol=S; }

        static Huffman build(int[] lengths, int n) {
            int[] blCount = new int[16];
            int maxBits = 0;
            for (int i = 0; i < n; i++) {
                int l = (i < lengths.length) ? lengths[i] : 0;
                if (l < 0 || l > 15) fail("Code length out of range "+l, 0, null, 0, 0, 0, 0);
                if (l > 0) { blCount[l]++; if (l > maxBits) maxBits = l; }
            }
            req(maxBits > 0, "Empty Huffman", 0, null, 0, 0, 0, 0);

            // канонические коды
            int[] nextCode = new int[16];
            int code = 0;
            for (int bits=1; bits<=15; bits++) { code = (code + blCount[bits-1]) << 1; nextCode[bits] = code; }
            int[] codes = new int[n];
            int[] lens  = new int[n];
            for (int s=0; s<n; s++) {
                int l = (s < lengths.length) ? lengths[s] : 0;
                if (l != 0) { codes[s] = nextCode[l]++; lens[s] = l; }
            }

            int sum = 0; for (int l : lens) sum += l;
            int cap = Math.max(4, (sum << 1) + 4);
            int[] L = new int[cap], R = new int[cap], S = new int[cap];
            for (int i=0;i<cap;i++){L[i]=-1;R[i]=-1;S[i]=-1;}
            int nodes = 1; // 0=root

            for (int s=0; s<n; s++) {
                int l = lens[s]; if (l==0) continue;
                int c = reverseBits(codes[s], l);
                int cur = 0;
                for (int i=0; i<l; i++) {
                    int bit = (c>>>i) & 1;
                    if (bit==0) { int nx=L[cur]; if (nx==-1) { nx=nodes++; L[cur]=nx; } cur=nx; }
                    else        { int nx=R[cur]; if (nx==-1) { nx=nodes++; R[cur]=nx; } cur=nx; }
                }
                S[cur] = s;
            }
            return new Huffman(L,R,S);
        }

        int decode(BitReader br) {
            int cur = 0;
            for (;;) {
                int s = symbol[cur];
                if (s >= 0) return s;
                int bit = br.readBits(1);
                cur = (bit==0) ? left[cur] : right[cur];
                if (cur < 0) fail("Invalid Huffman code", br.bytePos, br, 0, 0, 0, 0);
            }
        }

        private static int reverseBits(int v, int len) {
            int r=0; for (int i=0;i<len;i++){ r=(r<<1)|(v&1); v>>>=1; } return r;
        }
    }

    private static final class ByteSink {
        private byte[] buf = new byte[1<<15];
        private int size = 0;

        int size() { return size; }

        void write(byte b) { ensure(1); buf[size++] = b; }

        void copyFromHistory(int distance, int length) {
            ensure(length);
            if (length <= distance) {
                System.arraycopy(buf, size - distance, buf, size, length);
                size += length;
                return;
            }
            for (int i = 0; i < length; i++) buf[size + i] = buf[size + i - distance];
            size += length;
        }

        void ensure(int add) {
            int need = size + add;
            if (need > buf.length) {
                int n = Math.max(need, buf.length << 1);
                byte[] nb = new byte[n];
                System.arraycopy(buf, 0, nb, 0, size);
                buf = nb;
            }
        }

        byte[] toByteArray() {
            byte[] out = new byte[size];
            System.arraycopy(buf, 0, out, 0, size);
            return out;
        }
    }

    private static boolean isZlib(byte[] in) {
        if (in.length < 2) return false;
        int cmf = u8(in[0]), flg = u8(in[1]);
        if ((cmf & 0x0F) != 8) return false;
        return ((cmf << 8) + flg) % 31 == 0;
    }
    private static boolean isGzip(byte[] in) {
        return in.length >= 2 && u8(in[0]) == 0x1F && u8(in[1]) == 0x8B;
    }
    private static int u8(byte b){ return b & 0xFF; }

    private static void req(boolean ok, String msg, int pos, BitReader br, int lenSym, int outLen, int dist, int outSize) {
        if (!ok) fail(msg, pos, br, lenSym, outLen, dist, outSize);
    }
    private static void fail(String msg, int bytePos, BitReader br, int lenSym, int outLen, int distOrSym, int outSize) {
        if (!DEBUG) throw new IllegalArgumentException(msg);
        StringBuilder sb = new StringBuilder(256);
        sb.append(msg).append(" | pos=").append(bytePos);
        if (lenSym != 0) sb.append(" lenSym=").append(lenSym);
        if (outLen != 0) sb.append(" len=").append(outLen);
        if (distOrSym != 0) sb.append(" dist/sym=").append(distOrSym);
        sb.append(" outSize=").append(outSize);
        if (br != null) {
            sb.append(" bitCnt=").append(br.bitCnt)
                    .append(" bitBufLSB=0x").append(Long.toHexString(br.bitBuf));
            sb.append(" nextBytes=[");
            for (int i = 0; i < 8; i++) {
                int p = br.bytePos + i;
                if (p >= br.a.length) break;
                if (i > 0) sb.append(' ');
                sb.append(String.format("%02X", u8(br.a[p])));
            }
            sb.append(']');
        }
        throw new IllegalArgumentException(sb.toString());
    }
}
