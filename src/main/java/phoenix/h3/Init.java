package phoenix.h3;

import phoenix.h3.game.patch.CombatManagerPatcher;
import phoenix.h3.game.patch.DefPatcher;
import phoenix.h3.game.patch.OwnResourceCache;
import phoenix.h3.game.patch.PatchRepository;
import phoenix.h3.game.patch.artifact.*;
import phoenix.h3.game.patch.map.EventPatcher;
import phoenix.h3.game.patch.map.SeerPatcher;
import phoenix.h3.game.patch.map.crbank.CreatureBankPatcher;
import phoenix.h3.game.patch.map.crbank.CreatureBankRepository;
import phoenix.h3.game.patch.map.replacer.CustomBank;
import phoenix.h3.game.patch.map.replacer.PhoenixForge;
import phoenix.h3.game.patch.map.replacer.ReplacerRepository;

public class Init {
    public static PatchRepository start() {
        OwnResourceCache resources = new OwnResourceCache();

        ArtifactRepository artifacts = ArtifactRepository.withCustomArtifacts(
                new TokenOfCoward(),
                new DragonSlayerShield(),
                new RingOfPhoenix(),
                new RuneOfAir()
        );

        CreatureBankRepository banks = new CreatureBankRepository();

        ReplacerRepository replacers = ReplacerRepository.withReplacers(
                new PhoenixForge(artifacts, banks, resources),
                new CustomBank(artifacts, banks, resources)
        );

        return PatchRepository.install(
                new CombatManagerPatcher(artifacts),
                new SeerPatcher(artifacts),
                new CustomArtifactPatcher(artifacts, resources),
                new DefPatcher(resources),

                new EventPatcher(replacers),
                new CreatureBankPatcher(banks)   // should be after ALL patchers that can trigger bank creations
        );
    }
}
