package phoenix.h3.game;

import phoenix.h3.annotations.Downcall;
import phoenix.h3.annotations.Thiscall;

import static phoenix.h3.game.stdlib.Memory.mallocAuto;

public class PaletteRGB565 {

    public static final int SIZE = 0x21C;

    public static int allocate() {
        return mallocAuto(SIZE);
    }

    public static int createNewFromPalette888(int palette888) {
        int result = allocate();
        createFromPalette888(result, palette888, 5, 11, 6, 5, 5, 0);
        return result;
    }

    @Thiscall
    @Downcall(0x522BC0)
    public static native void createFromPalette888(
            int where,
            int palette24,
            int rbits,
            int rpos,
            int gbits,
            int gpos,
            int bbits,
            int bpos
    );

}
