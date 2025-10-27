package phoenix.h3.game.patch.map.replacer;

import phoenix.h3.game.*;
import phoenix.h3.game.common.CustomMarker;
import phoenix.h3.game.patch.DefReplaceRepository;
import phoenix.h3.game.patch.artifact.ArtifactRepository;
import phoenix.h3.game.patch.map.crbank.CreatureBankRepository;
import phoenix.h3.game.stdlib.StdVector;

import static phoenix.h3.game.stdlib.Memory.*;

public class CustomBank extends Replacer {

    private final ArtifactRepository artifacts;
    private final CreatureBankRepository banks;
    private final DefReplaceRepository defs;

    public CustomBank(
            ArtifactRepository artifacts,
            CreatureBankRepository banks,
            DefReplaceRepository defs
    ) {
        this.artifacts = artifacts;
        this.banks = banks;
        this.defs = defs;
    }

    @Override
    public void performReplace(CustomMarker.Value info, int x, int y, int z, int cell, int typeAndSubtype, int event) {
        if ((typeAndSubtype & 0xffff) != 16) {
            throw new IllegalArgumentException(
                    "Expected to have type and subtype: 0x" +
                            Integer.toHexString(typeAndSubtype));
        }

        int eventGuardians = Blackbox.guardians(event);
        int eventCreatures = Blackbox.creatures(event);
        int crBankIndex = NewmapCell.creatureBankIndex(cell);
        int mapObjects = NewfullMap.objects(map);
        int objectIndex = NewmapCell.objectIndex(cell);
        int object = mapObjects + objectIndex * CObject.SIZE;
        int crBanks = Game.creatureBanks(game);
        int bank = crBanks + crBankIndex * CreatureBank.SIZE;
        int bankGuardians = CreatureBank.guards(bank);

        StdVector.copy(bank + CreatureBank.OFFSET_ARTIFACT_LIST, event + Blackbox.OFFSET_ARTIFACTS);
        memcpy(bank + CreatureBank.OFFSET_RESOURCES, event + Blackbox.OFFSET_RES_QTY, 28);
        memcpy(bankGuardians, eventGuardians, ArmyGroup.SIZE);
        CreatureBank.putCreature(bank, ArmyGroup.type(eventCreatures, 0), ArmyGroup.count(eventCreatures, 0));

        String artifact = info.ascii("artifact");
        String def = info.ascii("def");
        byte[] nameText = info.bytes("nameText");

        if (artifact != null) {
            putDword(
                    dwordAt(bank + CreatureBank.OFFSET_ARTIFACT_LIST + 4),
                    artifacts.artifact(artifact).id
            );
        }

        if (def != null) {
            defs.replaceDef(object, def);
        }

        if (nameText != null) {
            int subtype = banks.indexCreatureBank(nameText);

            putByte(cell + NewmapCell.OFFSET_SUBTYPE, subtype & 0xff);
            putByte(cell + NewmapCell.OFFSET_SUBTYPE + 1, (subtype >> 8) & 0xff);
        }
    }

}
