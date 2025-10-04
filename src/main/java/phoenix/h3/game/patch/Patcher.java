package phoenix.h3.game.patch;

import java.util.Vector;

import static phoenix.h3.game.stdlib.Memory.*;

public abstract class Patcher {

    public final Class<? extends Patcher>[] dependencies;

    @SafeVarargs
    public Patcher(Class<? extends Patcher>... dependencies) {
        this.dependencies = dependencies;
    }

    public void installPatch() {
        performPatchInstallation(this);
    }

    public void uninstall() {
        uninstallPatches(this);
    }

    public void onGameCreated(boolean saveLoad) {
    }

    protected int patchArray(int address, int oldSize, int newSize) {
        int newArray = mallocAuto(newSize);
        int oldArray = patchDword(address, newArray);
        memcpy(newArray, oldArray, Math.min(oldSize, newSize));
        return newArray;
    }

    protected int patchDword(int address, int value) {
        int oldValue = registerDwordPatch(address);
        putDword(address, value);
        return oldValue;
    }

    public Vector<Patcher> createChildPatches() {
        return null;
    }

    private static native void performPatchInstallation(Patcher patcher);
    // todo it is actually do-nothing for now
    private static native void uninstallPatches(Patcher patcher);
}
