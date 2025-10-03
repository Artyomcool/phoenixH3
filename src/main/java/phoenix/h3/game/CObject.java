package phoenix.h3.game;

import static phoenix.h3.game.stdlib.Memory.dwordAt;

public class CObject {
    public static final int OFFSET_EXTRA_INFO = 0;
    public static final int OFFSET_X = 0x4;
    public static final int OFFSET_Y = 0x5;
    public static final int OFFSET_Z = 0x6;
    public static final int OFFSET_TYPE_ID = 0x8;
    public static final int OFFSET_FRAME_OFFSET = 0xA;
    public static final int SIZE = 0xC;

    public static int type(int object) {
        return dwordAt(object + OFFSET_TYPE_ID) & 0xffff;
    }
}
