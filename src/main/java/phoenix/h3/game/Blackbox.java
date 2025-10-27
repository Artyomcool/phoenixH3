package phoenix.h3.game;

import phoenix.h3.game.stdlib.Memory;
import phoenix.h3.game.stdlib.StdVector;

import static phoenix.h3.game.stdlib.Memory.*;

public class Blackbox extends TreasureData {

    public static final int OFFSET_RES_QTY = 0x5C;
    public static final int OFFSET_ARTIFACTS = 0x8C;
    public static final int OFFSET_SPELLS = 0x9C;
    public static final int OFFSET_CREATURES = 0xAC;

    public static final int SIZE = 0xE4;

    public static int creatures(int blackbox) {
        return blackbox + OFFSET_CREATURES;
    }

    public static void replaceArtifact(int event, int i, int id) {
        int offset = StdVector.dataPtr(event + OFFSET_ARTIFACTS) + i * 4;
        putDword(offset, id);
    }
}
