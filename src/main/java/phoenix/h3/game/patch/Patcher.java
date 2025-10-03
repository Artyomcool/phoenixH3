package phoenix.h3.game.patch;

import java.util.Vector;

public abstract class Patcher {

    public final Class<? extends Patcher>[] dependencies;

    @SafeVarargs
    public Patcher(Class<? extends Patcher>... dependencies) {
        this.dependencies = dependencies;
    }

    public void installPatch() {
        performPatchInstallation(this);
    }

    public void onGameCreated() {
    }

    private static native void performPatchInstallation(Patcher patcher);
    private static native void uninstallPatches(Patcher patcher);

    public Vector<Patcher> createChildPatches() {
        return null;
    }
}
