package phoenix.h3.game;

import static phoenix.h3.game.stdlib.Memory.dwordAt;

public class Seer {

    public static final int OFFSET_QUEST = 0;

    public static int quest(int seer) {
        return dwordAt(seer + OFFSET_QUEST);
    }

}
