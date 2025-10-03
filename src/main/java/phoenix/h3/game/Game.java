package phoenix.h3.game;

import phoenix.h3.game.stdlib.StdVector;

import static phoenix.h3.game.stdlib.Memory.dwordAt;

public class Game {

    public static int OFFSET_MAP = 0x1FB70;
    public static int OFFSET_CREATURE_BANK_LIST = 0x4E3D8;

    public static int instance() {
        return dwordAt(0x699538);
    }

    public static int map(int game) {
        return game + OFFSET_MAP;
    }

    public static int creatureBanks(int game) {
        return StdVector.dataPtr(game + OFFSET_CREATURE_BANK_LIST);
    }
}
