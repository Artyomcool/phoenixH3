package phoenix.h3.game;

import static phoenix.h3.game.stdlib.Memory.dwordAt;

public class CombatManager {

    public static final int OFFSET_SIDE = 0x132C0;
    public static final int OFFSET_HEROES = 0x53CC;

    public static int side(int combatMgr) {
        return dwordAt(combatMgr + OFFSET_SIDE);
    }

    public static int hero(int combatMgr) {
        return hero(combatMgr, side(combatMgr));
    }

    public static int hero(int combatMgr, int lastSide) {
        return dwordAt(combatMgr + OFFSET_HEROES + lastSide * 4);
    }
}
