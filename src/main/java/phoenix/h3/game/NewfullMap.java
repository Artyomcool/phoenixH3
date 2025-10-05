package phoenix.h3.game;

import phoenix.h3.game.stdlib.StdVector;

import static phoenix.h3.game.stdlib.Memory.*;

public class NewfullMap {
    public static final int OFFSET_OBJECT_TYPES = 0;
    public static final int OFFSET_OBJECTS = 0x10;
    public static final int OFFSET_SPRITES = 0x20;
    public static final int OFFSET_CUSTOM_TREASURE_LIST = 0x30;
    public static final int OFFSET_CUSTOM_MONSTER_LIST = 0x40;
    public static final int OFFSET_BLACK_BOX_LIST = 0x50;
    public static final int OFFSET_SEER_HUT_LIST = 0x60;
    public static final int OFFSET_QUEST_GUARD_LIST = 0x70;
    public static final int OFFSET_TIMED_EVENT_LIST = 0x80;
    public static final int OFFSET_TOWN_EVENT_LIST = 0x90;
    public static final int OFFSET_PLACEHOLDER_LIST = 0xA0;
    public static final int OFFSET_QUEST_LIST = 0xB0;
    public static final int OFFSET_RANDOM_DWELLING_LIST = 0xC0;
    public static final int OFFSET_CELL_DATA_PTR = 0xD0;
    public static final int OFFSET_SIZE = 0xD4;
    public static final int OFFSET_HAS_TWO_LEVELS = 0xD8;
    public static final int OFFSET_OBJECT_TEMPLATES = 0xDC;

    public static int types(int map) {
        return StdVector.dataPtr(map + OFFSET_OBJECT_TYPES);
    }

    public static int typesCount(int map) {
        return StdVector.sizeInBytes(map + OFFSET_OBJECT_TYPES) / CObjectType.SIZE;
    }

    public static int sprites(int map) {
        return StdVector.dataPtr(map + OFFSET_SPRITES);
    }

    public static int addNewType(int map, int copyFrom) {
        int pos = StdVector.add(map + OFFSET_OBJECT_TYPES, CObjectType.SIZE);
        memcpy(pos, types(map) + copyFrom * CObjectType.SIZE, CObjectType.SIZE);
        putDword(pos + 0, 0);
        putDword(pos + 4, 0);
        putDword(pos + 8, 0);
        putDword(pos + 12, 0);

        pos = StdVector.add(map + OFFSET_SPRITES, 4);
        int sprites = sprites(map);
        int def = dwordAt(sprites + copyFrom * 4);
        Resource.incRefCount(def);
        putDword(pos, def);
        return  (pos - sprites) / 4;
    }

    public static int objects(int map) {
        return StdVector.dataPtr(map + OFFSET_OBJECTS);
    }

    public static int blackboxListData(int map) {
        return StdVector.dataPtr(map + OFFSET_BLACK_BOX_LIST);
    }

    public static int cells(int map) {
        return dwordAt(map + OFFSET_CELL_DATA_PTR);
    }

    public static int cell(int map, int x, int y, int z) {
        return cell(cells(map), size(map), x, y, z);
    }

    public static int cell(int cells, int size, int x, int y, int z) {
        return cells + (x + y * size + z * size * size) * NewmapCell.SIZE;
    }

    public static int size(int map) {
        return dwordAt(map + OFFSET_SIZE);
    }
}