package phoenix.h3.game.patch.map.replacer;

import phoenix.h3.game.*;
import phoenix.h3.game.patch.OwnResourceCache;
import phoenix.h3.game.patch.Patcher;
import phoenix.h3.game.patch.artifact.ArtifactRepository;
import phoenix.h3.game.patch.map.crbank.CreatureBankRepository;
import phoenix.h3.game.stdlib.Memory;
import phoenix.h3.game.stdlib.StdVector;

import java.util.Vector;

import static phoenix.h3.game.stdlib.Memory.*;

public class CustomBank extends Replacer {

    public static final String ARTIFACT = "artifact/";
    public static final String DEF = "def/";
    public static final String NAME = "name/";

    private final ArtifactRepository artifacts;
    private final CreatureBankRepository banks;
    private final OwnResourceCache resources;

    private Vector<String> savedSprites = new Vector<>();
    private Vector<Integer> savedTypesIndexes = new Vector<>();

    public CustomBank(
            ArtifactRepository artifacts,
            CreatureBankRepository banks,
            OwnResourceCache resources
    ) {
        this.artifacts = artifacts;
        this.banks = banks;
        this.resources = resources;
    }

    @Override
    public StatePatcher asPatcher() {
        return new StatePatcher();
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
                int newType = NewfullMap.addNewType(map, type);

                int address = NewfullMap.sprites(map) + newType * 4;
                String defName = token.substring(DEF.length());
                putDef(address, defName);

                savedSprites.add(defName);
                savedTypesIndexes.add(newType);

                putByte(object + CObject.OFFSET_TYPE_ID, newType & 0xff);
                putByte(object + CObject.OFFSET_TYPE_ID + 1, (newType >> 8) & 0xff);
            } else if (token.startsWith(NAME)) {
                int subtype = banks.indexCreatureBank(token.substring(NAME.length()));

                putByte(cell + NewmapCell.OFFSET_SUBTYPE, subtype & 0xff);
                putByte(cell + NewmapCell.OFFSET_SUBTYPE + 1, (subtype >> 8) & 0xff);
            }
        }
    }

    private void putDef(int address, String def) {
        Memory.registerDwordPatch(address);
        putDword(address, resources.customDef(def));
    }

    protected void restoreDefs() {
        int sprites = NewfullMap.sprites(map);
        for (int i = 0; i < savedSprites.size(); i++) {
            putDef(sprites + savedTypesIndexes.get(i) * 4, savedSprites.get(i));
        }
    }

    public class StatePatcher extends Patcher<Vector<Object>> {
        @Override
        protected Vector<Object> createInitialSaveData() {
            Vector<Object> objects = new Vector<>();
            objects.add(savedSprites);
            objects.add(savedTypesIndexes);
            return objects;
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void onSaveGameLoaded(Vector<Object> objects) {
            savedSprites = (Vector<String>) objects.get(0);
            savedTypesIndexes = (Vector<Integer>) objects.get(1);
            restoreDefs();
        }
    }

}
