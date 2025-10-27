package com.github.artyomcool.classflow;

import java.util.Arrays;

public class ByteArrayInOut {

    public byte[] in;
    public byte[] out;

    public int ip = 0;
    public int op = 0;

    public ByteArrayInOut(byte[] in) {
        this(in, in == null ? null : new byte[in.length + 1024]);
    }

    public ByteArrayInOut(byte[] in, byte[] out) {
        this.in = in;
        this.out = out;
    }

    public ByteArrayInOut in(byte[] in) {
        this.in = in;
        return this;
    }

    private void ensureOutCapacity(int needed) {
        if (needed <= out.length) {
            return;
        }
        int newLength = needed + needed / 2;
        recreateOutput(newLength);
    }

    protected void recreateOutput(int newLength) {
        out = Arrays.copyOf(out, newLength);
    }

    public void wu4(int v) {
        ensureOutCapacity(op + 4);
        out[op++] = (byte) (v >>> 24);
        out[op++] = (byte) (v >>> 16);
        out[op++] = (byte) (v >>> 8);
        out[op++] = (byte) v;
    }

    public void wu2(int v) {
        ensureOutCapacity(op + 2);
        out[op++] = (byte) (v >>> 8);
        out[op++] = (byte) v;
    }

    public void wu1(int v) {
        ensureOutCapacity(op + 1);
        out[op++] = (byte) v;
    }

    public void w(byte[] bytes, int offset, int length) {
        ensureOutCapacity(op + length);
        System.arraycopy(bytes, offset, out, op, length);
        op += length;
    }

    public void w(int pos, byte[] bytes, int offset, int length) {
        System.arraycopy(bytes, offset, out, pos, length);
    }

    public void wu1(int pos, int v) {
        out[pos] = (byte) v;
    }

    public void wu2(int pos, int v) {
        out[pos] = (byte) (v >>> 8);
        out[++pos] = (byte) v;
    }

    public void wu4(int pos, int v) {
        out[pos] = (byte) (v >>> 24);
        out[++pos] = (byte) (v >>> 16);
        out[++pos] = (byte) (v >>> 8);
        out[++pos] = (byte) v;
    }

    public void insert(int pos, int length) {
        ensureOutCapacity(op + length);
        System.arraycopy(out, pos, out, pos + length, op - pos);
        op += length;
    }

    public void transfer(int len) {
        ensureOutCapacity(op + len);
        System.arraycopy(in, ip, out, op, len);
        ip += len;
        op += len;
    }

    public int u1(int pos) {
        return in[pos] & 0xFF;
    }

    public int u2(int pos) {
        return ((in[pos] & 0xFF) << 8) |
                (in[pos + 1] & 0xFF);
    }

    public int u4(int pos) {
        return ((in[pos] & 0xFF) << 24) |
                ((in[pos + 1] & 0xFF) << 16) |
                ((in[pos + 2] & 0xFF) << 8) |
                (in[pos + 3] & 0xFF);
    }

    public int pos() {
        return ip;
    }

    public int wpos() {
        return op;
    }

    public int u1() {
        return in[ip++] & 0xFF;
    }

    public int u2() {
        return u1() << 8 | u1();
    }

    public int u4() {
        return u2() << 16 | u2();
    }

    public void skip(int n) {
        ip += n;
    }
}
