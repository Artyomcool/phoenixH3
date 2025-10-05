package phoenix.h3.game;

import phoenix.h3.annotations.Downcall;
import phoenix.h3.annotations.VTable;

import static phoenix.h3.game.stdlib.Memory.*;
import static phoenix.h3.game.stdlib.Memory.dwordAt;

public class GzFile {

    private static final int TMP_NATIVE = mallocAuto(1024);

    @VTable
    @Downcall(1)
    public static native int read(int file, int buffer, int size);

    @VTable
    @Downcall(2)
    public static native int write(int file, int buffer, int size);

    public static int readDword(int gzFile) {
        read(gzFile, TMP_NATIVE, 4);
        return dwordAt(TMP_NATIVE);
    }

    public static void readFully(int gzFile, byte[] out) {
        for (int i = 0; i < out.length; i += 1024) {
            int sizeToRead = Math.min(out.length - i, 1024);
            read(gzFile, TMP_NATIVE, sizeToRead);
            arrayAt(TMP_NATIVE, out, i, sizeToRead);
        }
    }

}
