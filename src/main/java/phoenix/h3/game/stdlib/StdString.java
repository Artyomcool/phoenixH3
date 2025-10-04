package phoenix.h3.game.stdlib;

import static phoenix.h3.game.stdlib.Memory.*;

public class StdString {
    private static final int[] BUFFER = new int[4];
    private static final byte[] EMPTY = new byte[0];

    public static final StdString tmp = new StdString();

    public int lastUsedThisRef;
    public int lastUsedCharsAddress;

    public byte[] data = EMPTY;
    public int length;
    public int capacity;

    public static void put(int ptr, int cStr, int len) {
        BUFFER[1] = cStr;
        BUFFER[2] = len;
        BUFFER[3] = len;

        putArray(ptr, BUFFER, 0, 16);
    }

    public StdString readFrom(int this_) {
        arrayAt(this_, BUFFER, 0, 16);

        lastUsedCharsAddress = BUFFER[1];
        length = BUFFER[2];
        capacity = BUFFER[3];

        if (lastUsedCharsAddress != 0) {
            if (data.length < capacity + 1) {
                data = new byte[capacity + 1];
            }

            arrayAt(lastUsedCharsAddress, data, 0, length + 1);
        }

        lastUsedThisRef = this_;
        return this;
    }

    public void trimInPlace(int newLength) {
        putDword(lastUsedThisRef + 8, newLength);
        putByte(lastUsedCharsAddress + newLength, 0);
        length = newLength;
    }

    @Override
    public String toString() {
        return new String(data, 0, length);
    }
}
