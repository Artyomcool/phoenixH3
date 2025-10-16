package phoenix.h3.game;

import static phoenix.h3.game.stdlib.Memory.dwordAt;
import static phoenix.h3.game.stdlib.Memory.putDword;

public class MouseManager {
    public static final int INSTANCE = 0x6992B0;

    public static final int OFFSET_NO_CHANGE_POINTER = 0x38;
    public static final int OFFSET_LAST_DRAW = 0x3c; // tagRECT
    public static final int OFFSET_SET = 0x4c;
    public static final int OFFSET_FRAME = 0x50;
    public static final int OFFSET_SPRITE = 0x54;
    public static final int OFFSET_IMAGE_X = 0x58;
    public static final int OFFSET_IMAGE_Y = 0x5C;
    public static final int OFFSET_DISABLE_COUNT = 0x60;

    public static int sprite(int this_) {
        return dwordAt(this_ + OFFSET_SPRITE);
    }

    public static void putSprite(int this_, int def) {
        putDword(this_ + OFFSET_SPRITE, def);
    }

}
