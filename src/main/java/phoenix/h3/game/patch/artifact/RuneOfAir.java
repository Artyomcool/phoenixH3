package phoenix.h3.game.patch.artifact;

import phoenix.h3.game.Hero;
import phoenix.h3.game.patch.CombatManagerPatcher;

import static phoenix.h3.game.Spell.SPL_SUMMON_AIR_ELEMENTAL;

public class RuneOfAir extends CustomArtifact implements CombatManagerPatcher.CastListener {

    public RuneOfAir() {
        super(
                "Rune Of Air",
                "This rune summons Air Elements, but only once.",
                COST_TREASURE,
                SLOT_MISC,
                RARITY_TREASURE
        );
        spell = SPL_SUMMON_AIR_ELEMENTAL;
    }

    @Override
    public void onCast(int hero, int spell, int hexId) {
        if (spell == this.spell) {
            if (Hero.hasArtifact(hero, id) > 0) {
                Hero.removeArtifact(hero, id);
            }
        }
    }
}
