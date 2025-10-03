package phoenix.h3.game;

import static phoenix.h3.game.stdlib.Memory.dwordAt;

public class CombatManager {

    public static final int OFFSET_HEROES = 0x53CC;

    public static int hero(int combatMgr, int lastSide) {
        return dwordAt(combatMgr + OFFSET_HEROES + lastSide * 4);
    }
}
