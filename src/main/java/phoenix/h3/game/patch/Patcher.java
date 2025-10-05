package phoenix.h3.game.patch;

import java.util.Vector;

import static phoenix.h3.H3.dbg;
import static phoenix.h3.game.stdlib.Memory.*;

public abstract class Patcher<T> {

    public static abstract class Stateless extends Patcher<Vector<Void>> {
        @Override
        protected Vector<Void> createInitialSaveData() {
            return new Vector<>();
        }

        @Override
        protected void onNewGameStarted(Vector<Void> voids) {
            onGameStarted();
        }

        @Override
        protected void onSaveGameLoaded(Vector<Void> voids) {
            onGameStarted();
        }

        protected void onGameStarted() {

        }
    }

    private static int NEXT_ID = 0;

    public final Class<? extends Patcher<?>>[] dependencies;
    public final int id = NEXT_ID++;

    protected T savedData;

    @SafeVarargs
    public Patcher(Class<? extends Patcher<?>>... dependencies) {
        this.dependencies = dependencies;
    }

    public void installPatch() {
        performPatchInstallation(this);
    }

    public void uninstall() {
        uninstallPatches(this);
    }

    protected abstract T createInitialSaveData();

    public T beforeSave() {
        return savedData;
    }

    public void onStart(T savedData) {
        if (savedData == null) {
            onNewGameStarted(this.savedData = createInitialSaveData());
        } else {
            onSaveGameLoaded(this.savedData = savedData);
        }
    }

    protected void onNewGameStarted(T t) {
    }

    protected void onSaveGameLoaded(T t) {
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

    public Vector<Patcher<?>> createChildPatches() {
        return null;
    }

    private static native void performPatchInstallation(Patcher<?> patcher);
    // todo it is actually do-nothing for now
    private static native void uninstallPatches(Patcher<?> patcher);
}
