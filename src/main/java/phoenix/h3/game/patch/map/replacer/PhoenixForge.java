package phoenix.h3.game.patch.map.replacer;

import phoenix.h3.annotations.Dword;
import phoenix.h3.annotations.R;
import phoenix.h3.annotations.Upcall;
import phoenix.h3.game.Hero;
import phoenix.h3.game.NewfullMap;
import phoenix.h3.game.common.CustomMarker;
import phoenix.h3.game.patch.DefReplaceRepository;
import phoenix.h3.game.patch.Patcher;
import phoenix.h3.game.patch.artifact.ArtifactRepository;
import phoenix.h3.game.patch.map.crbank.CreatureBankRepository;

import java.util.Hashtable;
import java.util.Vector;

import static phoenix.h3.annotations.R.EBP;
import static phoenix.h3.annotations.R.ECX;
import static phoenix.h3.game.stdlib.Memory.*;

public class PhoenixForge extends CustomBank {

    private final Hashtable<Integer, Boolean> phoenixCells = new Hashtable<>();
    private Vector<Integer> cellsToRestore = new Vector<>();

    public PhoenixForge(ArtifactRepository artifacts, CreatureBankRepository banks, DefReplaceRepository defs) {
        super(artifacts, banks, defs);
    }

    @Override
    public void performReplace(CustomMarker.Value info, int x, int y, int z, int cell, int typeAndSubtype, int event) {
        super.performReplace(info, x, y, z, cell, typeAndSubtype, event);
        phoenixCells.put(cell, Boolean.TRUE);
        cellsToRestore.add(x | y << 8 | z << 16);
    }

    @Override
    public Patcher<Vector<Integer>> asPatcher() {
        return new Patcher<Vector<Integer>>() {
            @Upcall(base = 0x4AC163)
            public void afterFightBeforeLevelUp(@Dword(at = EBP, offset = 0xC) int cell, @R(ECX) int hero) {
                if (phoenixCells.containsKey(cell)) {
                    cleanupHero(hero);
                }
            }

            @Override
            protected void onSaveGameLoaded(Vector<Integer> objects) {
                cellsToRestore = objects;
                for (int coords : cellsToRestore) {
                    int cell = NewfullMap.cell(cells, size, coords & 0xff, (coords >> 8) & 0xff, coords >> 16);
                    phoenixCells.put(cell, Boolean.TRUE);
                }
            }

            @Override
            protected Vector<Integer> createInitialSaveData() {
                return cellsToRestore;
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
                if (order > 2) {
                    putByte(skills, 0);
                    putByte(skillsOrder, 0);
                    skillsNum--;
                }
            }
            skills++;
            skillsOrder++;
        }
        putDword(hero + Hero.OFFSET_SECONDARY_SKILL_NUM, skillsNum);
    }
}
