package phoenix.h3.game.patch.artifact;

import phoenix.h3.game.Hero;
import phoenix.h3.game.patch.CombatManagerPatcher;

public class DragonSlayerShield extends CustomArtifact implements CombatManagerPatcher.FearEvaluator {
    public DragonSlayerShield() {
        super(
                "Dragon Slayer Shield",
                "Forged from the remains of a fallen dragon, this shield carries the scars of a dragon’s last breath. Its presence is a grim testament to battles where legends clashed with monsters.",
                COST_RELIC,
                SLOT_SHIELD,
                RARITY_RELIC
        );
        bonuses[PRIMARY_ATTACK] = 2;
        bonuses[PRIMARY_DEFENSE] = 10;
    }

    @Override
    public int onFearEvaluated(int hero) {
        if (hero != 0 && Hero.hasArtifact(hero, id) > 0) {
            return 1;
        }
        return Integer.MIN_VALUE;
    }
}
