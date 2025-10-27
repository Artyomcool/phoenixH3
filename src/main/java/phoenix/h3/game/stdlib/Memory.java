package phoenix.h3.game.stdlib;

public class Memory {

    public static final int[] SHARED_TMP_INT_BUFFER = new int[32];
    public static final byte[] SHARED_TMP_BYTE_BUFFER = new byte[1024];

    private static final AutoGrowArray autoAllocated = new AutoGrowArray();
    private static final AutoGrowArray dwordPatches = new AutoGrowArray();

    public static int mallocAuto(int size) {
        return autoAllocated.add(malloc(size));
    }

    public static void autoFree() {
        for (int i = autoAllocated.size - 1; i >= 0; i--) {
            free(autoAllocated.array[i]);
        }
        for (int i = dwordPatches.size - 1; i >= 0;) {
            int oldValue = dwordPatches.array[i--];
            int address = dwordPatches.array[i--];
            putDword(address, oldValue);
        }
        dwordPatches.size = 0;
    }

    public static int registerDwordPatch(int address) {
        dwordPatches.add(address);
        return dwordPatches.add(dwordAt(address));
    }

    public static native int malloc(int size);
    public static native void free(int ptr);
    public static native void memcpy(int dst, int src, int amount);
    public static native int byteAt(int address);
    public static native int dwordAt(int address);
    public static native void arrayAt(int address, Object array, int from, int length);
    public static native void putByte(int address, int value);
    public static native void putDword(int address, int value);
    public static native void putArray(int address, Object array, int from, int length);
    public static native void putCstr(int address, byte[] text);
}
