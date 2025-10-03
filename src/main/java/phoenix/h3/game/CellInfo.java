package phoenix.h3.game;

import static phoenix.h3.game.stdlib.Memory.dwordAt;

public class CellInfo {

    public static final int OFFSET_INDEX = 0;
    public static final int OFFSET_COORDS = 4;

    public static int eventId(int cellInfo) {
        return dwordAt(cellInfo + OFFSET_INDEX) & 0x3FF;
    }

    public static int coords(int cellInfo) {
        return dwordAt(cellInfo + OFFSET_COORDS);
    }

}
