package phoenix.h3.game.patch.map;

import phoenix.h3.annotations.Dword;
import phoenix.h3.annotations.R;
import phoenix.h3.annotations.Upcall;
import phoenix.h3.game.Quest;
import phoenix.h3.game.Seer;
import phoenix.h3.game.common.CustomMarker;
import phoenix.h3.game.patch.DefReplaceRepository;
import phoenix.h3.game.patch.Patcher;
import phoenix.h3.game.patch.artifact.ArtifactRepository;
import phoenix.h3.game.stdlib.StdString;

import static phoenix.h3.annotations.R.EAX;
import static phoenix.h3.annotations.R.EDI;
import static phoenix.h3.game.stdlib.Memory.putDword;

public class SeerPatcher extends Patcher.Stateless {

    private final ArtifactRepository artifacts;
    private final DefReplaceRepository defs;

    public SeerPatcher(ArtifactRepository artifacts, DefReplaceRepository defs) {
        this.artifacts = artifacts;
        this.defs = defs;
    }

    @Upcall(base = 0x5037C5)
    public void afterSeerRead(@R(EDI) int obj, @R(value = EAX) int seer, @Dword(at = EAX, offset = Seer.OFFSET_QUEST) int quest) {
        if (quest == 0) {
            return;
        }
        StdString textStart = Quest.startText(quest);
        CustomMarker.Value customs = CustomMarker.parseForCustomMarkerAndFixup(textStart);
        if (customs == null) {
            return;
        }

        CustomMarker.Value give = customs.val("give");
        if (give != null) {
            String artifact = give.ascii("artifact");
            if (artifact != null) {
                int id = artifacts.idOf(artifacts.artifact(artifact));
                // TODO check or fix type of reward
                putDword(seer + 5 + 4, id); // replace reward's artifact
            }
        }

        String def = customs.ascii("def");
        if (def != null) {
            defs.replaceDef(obj, def);
        }
    }

}
