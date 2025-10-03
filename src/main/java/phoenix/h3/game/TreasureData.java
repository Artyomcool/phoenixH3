package phoenix.h3.game;

import phoenix.h3.game.stdlib.StdString;

public class TreasureData {

    public static final int OFFSET_MESSAGE = 0;
    public static final int OFFSET_HAS_CUSTOM_GUARDIANS = 0x10;
    public static final int OFFSET_GUARDIANS = 0x14;

    public static StdString msg(int blackbox) {
        return StdString.tmp.readFrom(blackbox + Blackbox.OFFSET_MESSAGE);
    }

    public static int guardians(int event) {
        return event + OFFSET_GUARDIANS;
    }
}
