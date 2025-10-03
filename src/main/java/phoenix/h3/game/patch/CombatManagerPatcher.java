package phoenix.h3.game.patch;

import phoenix.h3.annotations.Dword;
import phoenix.h3.annotations.R;
import phoenix.h3.annotations.Upcall;
import phoenix.h3.game.CombatManager;
import phoenix.h3.game.patch.artifact.ArtifactRepository;
import phoenix.h3.game.patch.artifact.CustomArtifact;

import java.util.Vector;

import static phoenix.h3.annotations.R.*;
import static phoenix.h3.game.stdlib.Memory.dwordAt;
import static phoenix.h3.game.stdlib.Memory.putDword;

public class CombatManagerPatcher extends Patcher {

    public interface FightCostMultiplier {
        // todo make more sophisticated logic here
        double onFightCostEvaluation(int hero);
    }

    public interface FearEvaluator {
        // todo make more sophisticated logic here
        int onFearEvaluated(int hero);
    }

    public interface PhoenixResurrectCountEvaluator {
        int phoenixResurrectCnt(int hero, int currentAmount, int stackSize);
    }

    private final ArtifactRepository repository;

    public CombatManagerPatcher(ArtifactRepository repository) {
        this.repository = repository;
    }

    @Override
    public Vector<Patcher> createChildPatches() {
        Vector<Patcher> children = new Vector<>();

        Vector<FightCostMultiplier> fightcostAware = new Vector<>();
        Vector<FearEvaluator> fearAware = new Vector<>();
        Vector<PhoenixResurrectCountEvaluator> phoenixResurrectCountEvaluators = new Vector<>();

        Vector<CustomArtifact> artifacts = repository.artifacts;
        for (int i = 0, artifactsSize = artifacts.size(); i < artifactsSize; i++) {
            CustomArtifact artifact = artifacts.get(i);
            if (artifact instanceof FightCostMultiplier) {
                fightcostAware.add((FightCostMultiplier) artifact);
            }
            if (artifact instanceof FearEvaluator) {
                fearAware.add((FearEvaluator) artifact);
            }
            if (artifact instanceof PhoenixResurrectCountEvaluator) {
                phoenixResurrectCountEvaluators.add((PhoenixResurrectCountEvaluator) artifact);
            }
        }

        if (fightcostAware.size() > 0) {
            children.add(createFightcostPatcher(fightcostAware));
        }

        if (fearAware.size() > 0) {
            children.add(createFearPatcher(fearAware));
        }

        if (phoenixResurrectCountEvaluators.size() > 0) {
            children.add(createPhoenixResuurectPatcher(phoenixResurrectCountEvaluators));
        }

        return children;
    }

    private Patcher createFightcostPatcher(final Vector<FightCostMultiplier> delegates) {
        return new Patcher() {
            @Upcall(base = 0x0442AA9)
            public void onAiGetStackFightValue(@R(ESP) int esp, @R(EBP) int ebp, @R(ESI) int battleStack) {
                int hypnosisRounds = dwordAt(battleStack + 0x198 + 0x3C * 4); // hypnosis
                int player = dwordAt(battleStack + 0xF4);
                if (hypnosisRounds != 0) {
                    player = 1 - player;
                }
                int hero = CombatManager.hero(dwordAt(0x699420), player);

                double attackMultiplier = Double.NaN;
                for (int i = delegates.size() - 1; i >= 0; i--) {
                    attackMultiplier = delegates.get(i).onFightCostEvaluation(hero);
                    if (!Double.isNaN(attackMultiplier)) {
                        break;
                    }
                }

                if (!Double.isNaN(attackMultiplier)) {
                    long bits = Double.doubleToLongBits(attackMultiplier);
                    putDword(ebp - 0x20, (int) bits); // replace double multiplier with 0
                    putDword(ebp - 0x1C, (int) (bits >>> 32)); // replace double multiplier with 0
                }
            }
        };
    }

    private Patcher createFearPatcher(final Vector<FearEvaluator> delegates) {
        return new Patcher() {
            private int lastStackOwnerSide;

            @Upcall(base = 0x464977)
            public void afterFearStackOwnerSide(@R(ECX) int side) {
                lastStackOwnerSide = side;
            }

            @Upcall(base = 0x4649D6)
            public void onCheckRndForFear(@R(ESP) int esp, @R(ESI) int combatMgr) {
                int hero = CombatManager.hero(combatMgr, lastStackOwnerSide);

                int value = Integer.MIN_VALUE;
                for (int i = delegates.size() - 1; i >= 0; i--) {
                    value = delegates.get(i).onFearEvaluated(hero);
                    if (value != Integer.MIN_VALUE) {
                        break;
                    }
                }

                if (value != Integer.MIN_VALUE) {
                    putDword(esp - 4, value); // replace eax
                }
            }

        };
    }

    private Patcher createPhoenixResuurectPatcher(final Vector<PhoenixResurrectCountEvaluator> delegates) {
        return new Patcher() {
            @Upcall(base = 0x4690E2)
            public void phoenix(@R(ESP) int esp, @R(EBX) int amount, @R(ESI) int battleStack, @Dword(at = ESI, offset = 0x60) int countAtStart) {
                int hypnosisRounds = dwordAt(battleStack + 0x198 + 0x3C * 4); // hypnosis
                int player = dwordAt(battleStack + 0xF4);
                if (hypnosisRounds != 0) {
                    player = 1 - player;
                }
                int hero = CombatManager.hero(dwordAt(0x699420), player);

                int oldAmount = amount;
                for (int i = 0; i < delegates.size(); i++) {
                    amount = delegates.get(i).phoenixResurrectCnt(hero, amount, countAtStart);
                }

                if (oldAmount != amount) {
                    putDword(esp - 16, amount); // ebx
                }
            }
        };
    }

}
