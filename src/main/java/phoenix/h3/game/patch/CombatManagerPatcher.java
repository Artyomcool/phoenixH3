package phoenix.h3.game.patch;

import phoenix.h3.annotations.Arg;
import phoenix.h3.annotations.Dword;
import phoenix.h3.annotations.R;
import phoenix.h3.annotations.Upcall;
import phoenix.h3.game.CombatManager;
import phoenix.h3.game.patch.artifact.ArtifactRepository;
import phoenix.h3.game.patch.artifact.CustomArtifact;
import phoenix.h3.game.stdlib.Stack;

import java.util.Vector;

import static phoenix.h3.annotations.R.*;
import static phoenix.h3.game.stdlib.Memory.dwordAt;
import static phoenix.h3.game.stdlib.Memory.putDword;

public class CombatManagerPatcher extends Patcher.Stateless {

    public interface FightCostMultiplier {
        // todo make more sophisticated logic here
        double onFightCostEvaluation(int hero);
    }

    public interface FearEvaluator {
        // todo make more sophisticated logic here
        int onFearEvaluated(int hero);
    }

    public interface CastListener {
        void onCast(int hero, int spell, int hexId);
    }

    public interface PhoenixResurrectCountEvaluator {
        int phoenixResurrectCnt(int hero, int currentAmount, int stackSize);
    }

    private final ArtifactRepository repository;

    public CombatManagerPatcher(ArtifactRepository repository) {
        this.repository = repository;
    }

    @Override
    public Vector<Patcher<?>> createChildPatches() {
        Vector<Patcher<?>> children = new Vector<>();

        Vector<FightCostMultiplier> fightcostAware = new Vector<>();
        Vector<FearEvaluator> fearAware = new Vector<>();
        Vector<PhoenixResurrectCountEvaluator> phoenixResurrectCountEvaluators = new Vector<>();
        Vector<CastListener> castListeners = new Vector<>();

        Vector<CustomArtifact> artifacts = repository.artifacts;
        for (CustomArtifact artifact : artifacts) {
            if (artifact instanceof FightCostMultiplier) {
                fightcostAware.add((FightCostMultiplier) artifact);
            }
            if (artifact instanceof FearEvaluator) {
                fearAware.add((FearEvaluator) artifact);
            }
            if (artifact instanceof PhoenixResurrectCountEvaluator) {
                phoenixResurrectCountEvaluators.add((PhoenixResurrectCountEvaluator) artifact);
            }
            if (artifact instanceof CastListener) {
                castListeners.add((CastListener) artifact);
            }
        }

        if (fightcostAware.size() > 0) {
            children.add(createFightcostPatcher(fightcostAware));
        }

        if (fearAware.size() > 0) {
            children.add(createFearPatcher(fearAware));
        }

        if (phoenixResurrectCountEvaluators.size() > 0) {
            children.add(createPhoenixResurrectPatcher(phoenixResurrectCountEvaluators));
        }

        if (castListeners.size() > 0) {
            children.add(createCastPatcher(castListeners));
        }

        return children;
    }

    private Patcher<?> createFightcostPatcher(final Vector<FightCostMultiplier> delegates) {
        return new Patcher.Stateless() {
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

    private Patcher<?> createFearPatcher(final Vector<FearEvaluator> delegates) {
        return new Patcher.Stateless() {
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
                    Stack.putEax(esp, value);
                }
            }

        };
    }

    private Patcher<?> createPhoenixResurrectPatcher(final Vector<PhoenixResurrectCountEvaluator> delegates) {
        return new Patcher.Stateless() {
            @Upcall(base = 0x4690E2)
            public void phoenix(@R(ESP) int esp, @R(EBX) int amount, @R(ESI) int battleStack, @Dword(at = ESI, offset = 0x60) int countAtStart) {
                int hypnosisRounds = dwordAt(battleStack + 0x198 + 0x3C * 4); // hypnosis
                int player = dwordAt(battleStack + 0xF4);
                if (hypnosisRounds != 0) {
                    player = 1 - player;
                }
                int hero = CombatManager.hero(dwordAt(0x699420), player);

                int oldAmount = amount;
                for (PhoenixResurrectCountEvaluator delegate : delegates) {
                    amount = delegate.phoenixResurrectCnt(hero, amount, countAtStart);
                }

                if (oldAmount != amount) {
                    Stack.putEbx(esp, amount);
                }
            }
        };
    }

    private Patcher<?> createCastPatcher(final Vector<CastListener> delegates) {
        return new Patcher.Stateless() {

            @Upcall(base = 0x5A0140)
            public void cast(@R(ECX) int combatMgr, @Arg(1) int spellId, @Arg(2) int hexId) {
                int hero = CombatManager.hero(combatMgr);
                for (CastListener delegate : delegates) {
                    delegate.onCast(hero, spellId, hexId);
                }
            }
        };
    }

}
