package phoenix.h3.game.stdlib;

public class Memory {

    public static final int SHARED_TMP_NATIVE_BUFFER = malloc(4096);
    public static final int[] SHARED_TMP_INT_BUFFER = new int[32];
    public static final byte[] SHARED_TMP_BYTE_BUFFER = new byte[1024];

    public static native int malloc(int size);
    public static native void free(int ptr);
    public static native void memcpy(int dst, int src, int amount);
    public static native int byteAt(int address);
    public static native int dwordAt(int address);
    public static native void arrayAt(int address, Object array, int from, int length);
    public static native void putByte(int address, int value);
    public static native void putDword(int address, int value);
    public static native void putArray(int address, Object array, int from, int length);
    public static native String cstrAt(int address, int maxLength);
    public static native void putCstr(int address, String text);
}
