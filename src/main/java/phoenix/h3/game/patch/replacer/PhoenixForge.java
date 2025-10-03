package phoenix.h3.game.patch.replacer;

import phoenix.h3.game.patch.artifact.ArtifactRepository;
import phoenix.h3.game.patch.crbank.CreatureBankRepository;

public class PhoenixForge extends CustomBank {

    public PhoenixForge(ArtifactRepository artifacts, CreatureBankRepository banks) {
        super(artifacts, banks);
    }
}
