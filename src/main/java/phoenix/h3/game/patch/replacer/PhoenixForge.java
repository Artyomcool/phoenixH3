package phoenix.h3.game.patch.replacer;

import phoenix.h3.annotations.Dword;
import phoenix.h3.annotations.R;
import phoenix.h3.annotations.Upcall;
import phoenix.h3.game.Hero;
import phoenix.h3.game.patch.Patcher;
import phoenix.h3.game.patch.artifact.ArtifactRepository;
import phoenix.h3.game.patch.crbank.CreatureBankRepository;

import java.util.Hashtable;

import static phoenix.h3.annotations.R.EBP;
import static phoenix.h3.annotations.R.ECX;
import static phoenix.h3.game.stdlib.Memory.*;

public class PhoenixForge extends CustomBank {

    private final Hashtable<Integer, Boolean> cells = new Hashtable<>();

    public PhoenixForge(ArtifactRepository artifacts, CreatureBankRepository banks) {
        super(artifacts, banks);
    }

    @Override
    public void performReplace(String[] tokens, int x, int y, int z, int cell, int typeAndSubtype, int event) {
        super.performReplace(tokens, x, y, z, cell, typeAndSubtype, event);
        cells.put(cell, Boolean.TRUE);
    }

    @Override
    public Patcher asPatcher() {
        return new Patcher() {
            @Upcall(base = 0x4AC163)
            public void afterFightBeforeLevelUp(@Dword(at = EBP, offset = 0xC) int cell, @R(ECX) int hero) {
                if (cells.containsKey(cell)) {
                    cleanupHero(hero);
                }
            }
        };
    }

    private void cleanupHero(int hero) {
        int skillsNum = dwordAt(hero + Hero.OFFSET_SECONDARY_SKILL_NUM);
        int skills = hero + Hero.OFFSET_SECONDARY_SKILL_LEVELS;
        int skillsOrder = hero + Hero.OFFSET_SECONDARY_SKILL_ORDER;

        while (skillsNum > 2) {
            int currentSkillLevel = byteAt(skills);
            if (currentSkillLevel > 0) {
                int order = byteAt(skillsOrder);
                if (order >= 2) {
                    putByte(skills, 0);
                    putByte(skillsOrder, -1);
                    skillsNum--;
                }
            }
            skills++;
            skillsOrder++;
        }
        putDword(hero + Hero.OFFSET_SECONDARY_SKILL_NUM, skillsNum);
    }
}
