package phoenix.h3;

import phoenix.h3.game.patch.*;
import phoenix.h3.game.patch.artifact.*;
import phoenix.h3.game.patch.crbank.CreatureBankPatcher;
import phoenix.h3.game.patch.crbank.CreatureBankRepository;
import phoenix.h3.game.patch.replacer.CustomBank;
import phoenix.h3.game.patch.replacer.PhoenixForge;
import phoenix.h3.game.patch.replacer.ReplacerRepository;

public class Init {
    public static PatchRepository start() {

        OwnResourceCache resources = new OwnResourceCache();

        ArtifactRepository artifacts = ArtifactRepository.withCustomArtifacts(
                new TokenOfCoward(),
                new DragonSlayerShield(),
                new RingOfPhoenix()
        );

        CreatureBankRepository banks = new CreatureBankRepository();

        ReplacerRepository replacers = ReplacerRepository.withReplacers(
                new PhoenixForge(artifacts, banks),
                new CustomBank(artifacts, banks)
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
