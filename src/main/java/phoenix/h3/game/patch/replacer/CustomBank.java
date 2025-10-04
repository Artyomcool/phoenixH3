package phoenix.h3.game.patch.replacer;

import phoenix.h3.game.*;
import phoenix.h3.game.patch.artifact.ArtifactRepository;
import phoenix.h3.game.patch.crbank.CreatureBankRepository;
import phoenix.h3.game.stdlib.Memory;
import phoenix.h3.game.stdlib.StdVector;

import static phoenix.h3.game.stdlib.Memory.*;

public class CustomBank extends Replacer {

    public static final String ARTIFACT = "artifact/";
    public static final String DEF = "def/";
    public static final String NAME = "name/";

    private final ArtifactRepository artifacts;
    private final CreatureBankRepository banks;

    public CustomBank(ArtifactRepository artifacts, CreatureBankRepository banks) {
        this.artifacts = artifacts;
        this.banks = banks;
    }

    @Override
    public void performReplace(String[] tokens, int x, int y, int z, int cell, int typeAndSubtype, int event) {
        if ((typeAndSubtype & 0xffff) != 16) {
            throw new IllegalArgumentException(
                    new StringBuffer("Expected to have type and subtype: 0x")
                            .append(Integer.toHexString(typeAndSubtype)).toString());
        }

        int eventGuardians = Blackbox.guardians(event);
        int eventCreatures = Blackbox.creatures(event);
        int crBankIndex = NewmapCell.creatureBankIndex(cell);
        int game = Game.instance();
        int map = Game.map(game);
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
        for (String token : tokens) {
            if (token.startsWith(ARTIFACT)) {
                putDword(
                        dwordAt(bank + CreatureBank.OFFSET_ARTIFACT_LIST + 4),
                        artifacts.artifact(token.substring(ARTIFACT.length())).id
                );
            } else if (token.startsWith(DEF)) {
                int type = CObject.type(object);
                int index = NewfullMap.addNewType(map, type);
                int newDef = Def.loadFromJar(token.substring(DEF.length()));
                int address = NewfullMap.sprites(map) + index * 4;
                Memory.registerDwordPatch(address);
                putDword(address, newDef);

                putByte(object + CObject.OFFSET_TYPE_ID, index & 0xff);
                putByte(object + CObject.OFFSET_TYPE_ID + 1, (index >> 8) & 0xff);
            } else if (token.startsWith(NAME)) {
                int subtype = banks.getOrCreate(token.substring(NAME.length())).id;

                putByte(cell + NewmapCell.OFFSET_SUBTYPE, subtype & 0xff);
                putByte(cell + NewmapCell.OFFSET_SUBTYPE + 1, (subtype >> 8) & 0xff);
            }
        }
    }
}
