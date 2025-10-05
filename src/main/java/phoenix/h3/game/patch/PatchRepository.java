package phoenix.h3.game.patch;

import java.util.Hashtable;
import java.util.Vector;

public class PatchRepository {

    private final Vector<Patcher<?>> registered;

    private PatchRepository(Vector<Patcher<?>> registered) {
        this.registered = registered;
    }

    public static PatchRepository install(Patcher<?>... patchers) {
        Vector<Patcher<?>> collectedPatchers = new Vector<>(patchers.length);
        Hashtable<Class<?>, Boolean> classes = new Hashtable<>();
        for (Patcher<?> patcher : patchers) {
            addRecursive(collectedPatchers, patcher);
        }

        for (int i = 0, collectedPatchersSize = collectedPatchers.size(); i < collectedPatchersSize; i++) {
            Patcher<?> patcher = collectedPatchers.get(i);
            classes.put(patcher.getClass(), Boolean.TRUE);
        }

        StringBuffer notFound = new StringBuffer();
        for (int i = 0, collectedPatchersSize = collectedPatchers.size(); i < collectedPatchersSize; i++) {
            Patcher<?> patcher = collectedPatchers.get(i);
            for (Class<? extends Patcher<?>> dependency : patcher.dependencies) {
                if (!classes.containsKey(dependency)) {
                    if (notFound.length() == 0) {
                        notFound.append("Not found patch dependencies:\n");
                    }
                    notFound.append(patcher.getClass().getName())
                            .append(" requires ")
                            .append(dependency.getName())
                            .append("\n");
                }
            }
        }
        if (notFound.length() > 0) {
            notFound.setLength(notFound.length() - 1);
            throw new IllegalArgumentException(notFound.toString());
        }

        PatchRepository repository = new PatchRepository(collectedPatchers);
        for (int i = 0, collectedPatchersSize = collectedPatchers.size(); i < collectedPatchersSize; i++) {
            Patcher<?> patcher = collectedPatchers.get(i);
            patcher.installPatch();
        }
        return repository;
    }

    private static void addRecursive(Vector<Patcher<?>> out, Patcher<?> patcher) {
        out.add(patcher);
        Vector<Patcher<?>> children = patcher.createChildPatches();
        if (children != null) {
            for (int i = 0; i < children.size(); i++) {
                addRecursive(out, children.get(i));
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void onStart(Object savedData) {
        Object[] allData = (Object[]) savedData;
        for (int i = 0, registeredSize = registered.size(); i < registeredSize; i++) {
            Patcher patcher = registered.get(i);
            Object data = null;
            if (allData != null) {
                String className = (String) allData[i * 2];
                String patchClassName = patcher.getClass().getName();
                if (!patchClassName.equals(className)) {
                    throw new IllegalStateException(new StringBuffer("Wrong patches order: expected ").append(patchClassName).append(", got ").append(className).toString());
                }

                data = allData[i * 2 + 1];
            }
            patcher.onStart(data);
        }
    }

    public Object savePatches() {
        Object[] allData = new Object[registered.size() * 2];
        for (int i = 0, registeredSize = registered.size(); i < registeredSize; i++) {
            Patcher<?> patcher = registered.get(i);
            allData[i * 2] = patcher.getClass().getName();
            allData[i * 2 + 1] = patcher.beforeSave();
        }
        return allData;
    }

    public void rollbackPatches() {
        for (int i = registered.size() - 1; i >= 0; i--) {
            registered.get(i).uninstall();
        }
    }
}
