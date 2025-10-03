package phoenix.h3.game;

import static phoenix.h3.game.stdlib.Memory.putDword;

public class CreatureBank {

    public static final int OFFSET_GUARDS = 0;
    public static final int OFFSET_RESOURCES = 0x38;
    public static final int OFFSET_CREATURE_TYPE = 0x54;
    public static final int OFFSET_CREATURES_COUNT = 0x58;
    public static final int OFFSET_ARTIFACT_LIST = 0x5C;

    public static final int SIZE = 0x6C;

    public static final int PTR_CR_BANK_TABLE = 0x67029C;
    public static final int PTR_CR_BANK_TABLE_INITIAL_COUNT = 10;
    public static final int PTR_CR_BANK_TABLE_RECORD_SIZE = 0x190;

    public static int guards(int bank) {
        return bank + OFFSET_GUARDS;
    }

    public static void putCreature(int bank, int type, int count) {
        putDword(bank + OFFSET_CREATURE_TYPE, type);
        putDword(bank + OFFSET_CREATURES_COUNT, count);
    }

}
