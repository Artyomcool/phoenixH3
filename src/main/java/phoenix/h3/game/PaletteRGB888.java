package phoenix.h3.game;

import phoenix.h3.annotations.Downcall;
import phoenix.h3.annotations.Thiscall;

import static phoenix.h3.game.stdlib.Memory.malloc;
import static phoenix.h3.game.stdlib.Memory.mallocAuto;

public class PaletteRGB888 {

    public static final int SIZE = 0x31C;

    public static final int OFFSET_COLORS = 0x1C;

    public static int allocate() {
        return mallocAuto(SIZE);
    }

    @Thiscall
    @Downcall(0x523370)
    public static native void createFromColors(int where, int colors);

    @Thiscall
    @Downcall(0x523370)
    public static native void adjustHsv(int palette24, float hue, float huePower, float saturation, float value);

}
