package phoenix.h3.game;

import static phoenix.h3.game.stdlib.Memory.dwordAt;

public class ArmyGroup {

    public static final int OFFSET_TYPES = 0;
    public static final int OFFSET_COUNTS = 0x1C;

    public static final int SIZE = 0x38;

    public static int type(int armyGroup, int index) {
        return dwordAt(armyGroup + OFFSET_TYPES + index * 4);
    }

    public static int count(int armyGroup, int index) {
        return dwordAt(armyGroup + OFFSET_COUNTS + index * 4);
    }

}
