package phoenix.h3.game.patch.artifact;

import phoenix.h3.game.Hero;
import phoenix.h3.game.patch.CombatManagerPatcher;

public class TokenOfCoward extends CustomArtifact implements
        CombatManagerPatcher.FearEvaluator,
        CombatManagerPatcher.FightCostMultiplier {

    public TokenOfCoward() {
        super(
                "Token Of Coward",
                "This small token makes all your fears impossible to resist. Enemies will see your cowardliness, because now it is too obvious.",
                COST_TREASURE,
                SLOT_MISC,
                RARITY_TREASURE
        );
        bonuses[PRIMARY_ATTACK] = -1;
        bonuses[PRIMARY_DEFENSE] = 1;
    }

    @Override
    public int onFearEvaluated(int hero) {
        if (hero != 0 && Hero.hasArtifact(hero, id) > 0) {
            return 0;
        }
        return Integer.MIN_VALUE;
    }

    @Override
    public double onFightCostEvaluation(int hero) {
        if (hero != 0 && Hero.hasArtifact(hero, id) > 0) {
            return 0;
        }
        return Double.NaN;
    }
}
