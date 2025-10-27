package phoenix.h3;

import phoenix.h3.game.patch.*;
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
        DefReplaceRepository defs = new DefReplaceRepository(resources);

        ReplacerRepository replacers = ReplacerRepository.withReplacers(
                new PhoenixForge(artifacts, banks, defs),
                new CustomBank(artifacts, banks, defs)
        );

        return PatchRepository.install(
                new CombatManagerPatcher(artifacts),
                new SeerPatcher(artifacts, defs),
                new CustomArtifactPatcher(artifacts, resources),
                new DefPatcher(resources, defs),

                new EventPatcher(artifacts, replacers),
                new CreatureBankPatcher(banks)   // should be after ALL patchers that can trigger bank creations
        );
    }
}
