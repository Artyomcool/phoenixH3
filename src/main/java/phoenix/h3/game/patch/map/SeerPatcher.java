package phoenix.h3.game.patch.map;

import phoenix.h3.annotations.Dword;
import phoenix.h3.annotations.R;
import phoenix.h3.annotations.Upcall;
import phoenix.h3.game.Quest;
import phoenix.h3.game.Seer;
import phoenix.h3.game.patch.Patcher;
import phoenix.h3.game.stdlib.StdString;
import phoenix.h3.game.common.CustomMarker;
import phoenix.h3.game.patch.artifact.ArtifactRepository;

import java.util.Vector;

import static phoenix.h3.annotations.R.EBX;
import static phoenix.h3.game.stdlib.Memory.putDword;

public class SeerPatcher extends Patcher.Stateless {

    private final ArtifactRepository artifacts;

    public SeerPatcher(ArtifactRepository artifacts) {
        this.artifacts = artifacts;
    }

    @Upcall(base = 0x5749E0, offset = 0x316)
    public void newGameAfterSeerRead(@R(EBX) int seer, @Dword(at = EBX, offset = Seer.OFFSET_QUEST) int quest) {
        if (quest == 0) {
            return;
        }
        StdString textStart = Quest.startText(quest);
        Vector<Vector<String>> tokens = CustomMarker.parseForCustomMarkerAndFixup(textStart);
        if (tokens == null) {
            return;
        }

        for (Vector<String> line : tokens) {
            if (line.get(0).equals("GIVE")) {
                if (line.get(1).equals("ARTIFACT")) {
                    int id = artifacts.idOf(artifacts.artifact(line.get(2)));
                    // TODO check or fix type of reward
                    putDword(seer + 5 + 4, id); // replace reward's artifact
                }
            }
        }
    }

}
