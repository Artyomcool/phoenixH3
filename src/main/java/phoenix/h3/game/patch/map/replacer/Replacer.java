package phoenix.h3.game.patch.map.replacer;

import phoenix.h3.game.common.CustomMarker;
import phoenix.h3.game.patch.Patcher;

public abstract class Replacer {

    public final String name = createName();

    protected int game;
    protected int map;
    protected int cells;
    protected int size;

    public void init(int game, int map, int cells, int size) {
        this.game = game;
        this.map = map;
        this.cells = cells;
        this.size = size;
    }

    public abstract void performReplace(CustomMarker.Value info, int x, int y, int z, int cell, int typeAndSubtype, int event);

    public Patcher asPatcher() {
        return null;
    }

    // todo reuse?
    protected String createName() {
        String n = getClass().getName();
        return n.substring(n.lastIndexOf('.') + 1);
    }
}
