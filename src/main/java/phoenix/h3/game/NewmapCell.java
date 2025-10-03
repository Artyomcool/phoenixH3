package phoenix.h3.game;

import phoenix.h3.game.stdlib.StdVector;

import static phoenix.h3.game.stdlib.Memory.byteAt;
import static phoenix.h3.game.stdlib.Memory.dwordAt;

public class NewmapCell {
    public static final int OFFSET_EXTRA_INFO = 0;
    public static final int OFFSET_GROUND_SET = 0x4;
    public static final int OFFSET_GROUND_INDEX = 0x5;
    public static final int OFFSET_RIVER_SET = 0x6;
    public static final int OFFSET_RIVER_INDEX = 0x7;
    public static final int OFFSET_ROAD_SET = 0x8;
    public static final int OFFSET_ROAD_INDEX = 0x9;
    public static final int OFFSET_OBJECT_CELL_LIST = 0xE;
    public static final int OFFSET_TYPE = 0x1E;
    public static final int OFFSET_SUBTYPE = 0x22;
    public static final int OFFSET_OBJECT_INDEX = 0x24;
    public static final int SIZE = 0x26;

    public static class NewmapCellObjectCell {
        public static final int OFFSET_OBJECT_INDEX = 0;
        public static final int OFFSET_HEIGHT = 3;

        public static final int SIZE = 0x4;
    }

    public static int creatureBankIndex(int cell) {
        return (dwordAt(cell) >> 13) & 0xFFF;
    }

    public static int typeAndSubtype(int cell) {
        return dwordAt(cell + OFFSET_TYPE);
    }

    public static int objectIndex(int cell) {
        return byteAt(cell + OFFSET_OBJECT_INDEX)
                | byteAt(cell + OFFSET_OBJECT_INDEX  + 1) << 8;
    }

    public static int objectCells(int cell) {
        return StdVector.dataPtr(cell + OFFSET_OBJECT_CELL_LIST);
    }
}
