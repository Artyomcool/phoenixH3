package phoenix.h3.game;

import static phoenix.h3.game.stdlib.Memory.dwordAt;
import static phoenix.h3.game.stdlib.Memory.putDword;

public class Resource {
    public static int OFFSET_NAME = 0x4;
    public static int OFFSET_REF_COUNT = 0x18;

    public static void incRefCount(int def) {
        putDword(def + OFFSET_REF_COUNT, dwordAt(def + OFFSET_REF_COUNT) + 1);
    }
}
