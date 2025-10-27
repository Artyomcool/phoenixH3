package phoenix.h3.game.patch;

import phoenix.h3.game.CObject;
import phoenix.h3.game.Game;
import phoenix.h3.game.NewfullMap;
import phoenix.h3.game.stdlib.Memory;

import java.util.Vector;

import static phoenix.h3.game.stdlib.Memory.putByte;
import static phoenix.h3.game.stdlib.Memory.putDword;

public class DefReplaceRepository {

    private final OwnResourceCache ownCache;

    private Vector<String> savedSprites;
    private Vector<Integer> savedTypesIndexes;

    private final Vector<String> savedSpritesDelayed = new Vector<>();
    private final Vector<Integer> savedObjectsDelayed = new Vector<>();

    public DefReplaceRepository(OwnResourceCache ownCache) {
        this.ownCache = ownCache;
    }

    Vector<Object> createInitialSaveData() {
        Vector<Object> objects = new Vector<>();
        objects.add(savedSprites = new Vector<>());
        objects.add(savedTypesIndexes = new Vector<>());
        return objects;
    }

    public void onNewGameStarted() {
        applyDelayed();
    }

    @SuppressWarnings("unchecked")
    void onSaveGameLoaded(Vector<Object> objects) {
        savedSprites = (Vector<String>) objects.get(0);
        savedTypesIndexes = (Vector<Integer>) objects.get(1);
        restoreDefs();
        applyDelayed();
    }

    private void applyDelayed() {
        for (int i = 0, savedSpritesDelayedSize = savedSpritesDelayed.size(); i < savedSpritesDelayedSize; i++) {
            replaceDef(savedObjectsDelayed.get(i), savedSpritesDelayed.get(i));
        }
        savedSpritesDelayed.clear();
        savedObjectsDelayed.clear();
    }

    public void replaceDef(int object, String def) {
        if (savedSprites == null) {
            savedSpritesDelayed.add(def);
            savedObjectsDelayed.add(object);
            return;
        }
        int game = Game.instance();
        int map = Game.map(game);

        int type = CObject.type(object);
        int newType = NewfullMap.addNewType(map, type);

        int address = NewfullMap.sprites(map) + newType * 4;
        putDef(address, def);

        savedSprites.add(def);
        savedTypesIndexes.add(newType);

        putByte(object + CObject.OFFSET_TYPE_ID, newType & 0xff);
        putByte(object + CObject.OFFSET_TYPE_ID + 1, (newType >> 8) & 0xff);
    }

    private void restoreDefs() {
        int game = Game.instance();
        int map = Game.map(game);

        int sprites = NewfullMap.sprites(map);
        for (int i = 0; i < savedSprites.size(); i++) {
            putDef(sprites + savedTypesIndexes.get(i) * 4, savedSprites.get(i));
        }
    }

    private void putDef(int address, String def) {
        Memory.registerDwordPatch(address);
        int value = ownCache.customDef(def);
        putDword(address, value);
    }
}
