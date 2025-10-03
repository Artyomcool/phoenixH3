package phoenix.h3.game;

import phoenix.h3.annotations.Downcall;
import phoenix.h3.annotations.Thiscall;

import static phoenix.h3.game.stdlib.Memory.byteAt;
import static phoenix.h3.game.stdlib.Memory.putByte;

public class Hero {

    public static final int OFFSET_PRIMARY_SKILLS = 0x476;

    @Thiscall
    @Downcall(0x4D9460)
    public static native int hasArtifact(int hero, int artifactId);

    public static void putPrimarySkill(int hero, int primarySkill, int value) {
        putByte(hero + OFFSET_PRIMARY_SKILLS + primarySkill, value);
    }

    public static int getPrimarySkill(int hero, int primarySkill) {
        return byteAt(hero + OFFSET_PRIMARY_SKILLS + primarySkill);
    }

}

