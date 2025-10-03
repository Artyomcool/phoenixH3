package phoenix.h3.game;

public class Blackbox extends TreasureData {

    public static final int OFFSET_RES_QTY = 0x5C;
    public static final int OFFSET_ARTIFACTS = 0x8C;
    public static final int OFFSET_SPELLS = 0x9C;
    public static final int OFFSET_CREATURES = 0xAC;

    public static final int SIZE = 0xE4;

    public static int creatures(int blackbox) {
        return blackbox + OFFSET_CREATURES;
    }

}
