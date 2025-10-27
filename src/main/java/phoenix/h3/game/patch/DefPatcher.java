package phoenix.h3.game.patch;

import phoenix.h3.annotations.Arg;
import phoenix.h3.annotations.R;
import phoenix.h3.annotations.Upcall;
import phoenix.h3.game.stdlib.Stack;

import java.util.Vector;

import static phoenix.h3.annotations.R.ECX;
import static phoenix.h3.annotations.R.ESP;
import static phoenix.h3.game.stdlib.Memory.putDword;

public class DefPatcher extends Patcher<Vector<Object>> {

    private final OwnResourceCache ownCache;
    private final DefReplaceRepository replaces;

    public DefPatcher(OwnResourceCache ownCache, DefReplaceRepository replaces) {
        this.ownCache = ownCache;
        this.replaces = replaces;
    }

    @Upcall(base = 0x47B820)
    public void onInterfaceDrawFrame(@R(ESP) int esp, @R(ECX) int _this, @Arg(1) int frameIndex) {
        int replacement = ownCache.findPatchedFrame(_this, 0, frameIndex);
        if (replacement != 0) {
            Stack.putEcx(esp, replacement);
            putDword(esp + 8, 0); // arg 1
        }
    }

    @Upcall(base = 0x47B7D0)
    public void onPointerDrawFrame(@R(ESP) int esp, @R(ECX) int _this, @Arg(1) int frameIndex) {
        int replacement = ownCache.findPatchedFrame(_this, 0, frameIndex);
        if (replacement != 0) {
            Stack.putEcx(esp, replacement);
            putDword(esp + 8, 0); // arg 1
        }
    }

    @Upcall(base = 0x47B610)
    public void onSpriteDrawFrame(@R(ESP) int esp, @R(ECX) int _this, @Arg(1) int groupIndex, @Arg(2) int frameIndex) {
        int replacement = ownCache.findPatchedFrame(_this, groupIndex, frameIndex);
        if (replacement != 0) {
            Stack.putEcx(esp, replacement);
            putDword(esp + 8, 0); // arg 1
            putDword(esp + 12, 0); // arg 2
        }
    }

    @Override
    protected Vector<Object> createInitialSaveData() {
        return replaces.createInitialSaveData();
    }

    @Override
    protected void onNewGameStarted(Vector<Object> objects) {
        replaces.onNewGameStarted();
    }

    @Override
    protected void onSaveGameLoaded(Vector<Object> objects) {
        replaces.onSaveGameLoaded(objects);
    }
}
