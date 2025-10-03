package phoenix.h3.game.common;

public class ByteArrayReader {

    public final byte[] buf;
    public int pos;

    public ByteArrayReader(byte[] buf) {
        this.buf = buf;
    }

    public int nextByte() {
        return buf[pos++] & 0xff;
    }

    public int nextIntLE() {
        return nextByte() | nextByte() << 8 | nextByte() << 16 | nextByte() << 24;
    }
}
