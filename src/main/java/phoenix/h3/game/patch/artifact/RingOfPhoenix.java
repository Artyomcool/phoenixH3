package phoenix.h3.game.patch.artifact;

import phoenix.h3.game.Hero;
import phoenix.h3.game.patch.CombatManagerPatcher;

public class RingOfPhoenix extends CustomArtifact implements CombatManagerPatcher.PhoenixResurrectCountEvaluator {
    public RingOfPhoenix() {
        super(
                "Ring of Phoenix",
                "Forged from the remains of a fallen dragon, this shield carries the scars of a dragon’s last breath. Its presence is a grim testament to battles where legends clashed with monsters.",
                COST_RELIC,
                SLOT_RING,
                RARITY_RELIC
        );
        bonuses[PRIMARY_POWER] = 4;
        bonuses[PRIMARY_KNOWLEDGE] = 1;
    }

    @Override
    public int phoenixResurrectCnt(int hero, int currentAmount, int stackSize) {
        if (hero != 0 && Hero.hasArtifact(hero, id) > 0) {
            return stackSize;
        }
        return currentAmount;
    }
}
