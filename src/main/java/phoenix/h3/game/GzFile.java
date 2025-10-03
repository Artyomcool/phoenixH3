package phoenix.h3.game;

import phoenix.h3.annotations.Downcall;
import phoenix.h3.annotations.VTable;

public class GzFile {

    @VTable
    @Downcall(2)
    public static native int write(int file, int buffer, int size);

}
