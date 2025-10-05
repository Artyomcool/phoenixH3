package phoenix.h3.game.patch;

import phoenix.h3.game.Def;

import java.util.Hashtable;

public class OwnResourceCache {

    private final Hashtable<String, Integer> cache = new Hashtable<>();

    private int[][] patchedFrames = new int[0][];

    public int customDef(String name) {
        Integer address = cache.get(name);
        if (address != null) {
            return address;
        }

        int result = Def.loadFromJar(name);
        cache.put(name, result);
        return result;
    }

    public void register(String name, int resourcePtr) {
        cache.put(name, resourcePtr);
    }

    public void patchFrame(int defToPatch, int group, int frameToPatch, int defReplacer) {
        if (patchedFrames.length < frameToPatch) {
            int[][] newPatchedFrames = new int[frameToPatch + 16][];
            System.arraycopy(patchedFrames, 0, newPatchedFrames, 0, patchedFrames.length);
            patchedFrames = newPatchedFrames;
        }

        int[] patchedFrame = patchedFrames[frameToPatch];
        if (patchedFrame == null) {
            patchedFrames[frameToPatch] = patchedFrame = new int[24];
        }

        int i;
        for (i = 0; i < patchedFrame.length; i += 3) {
            if ((patchedFrame[i] == 0 || patchedFrame[i] == defToPatch)
                    && (patchedFrame[i + 1] == 0 || patchedFrame[i + 1] == group)) {
                patchedFrame[i] = defToPatch;
                patchedFrame[i + 1] = group;
                patchedFrame[i + 2] = defReplacer;
                return;
            }
        }

        int[] newPatchedFrame = new int[patchedFrame.length * 2];
        System.arraycopy(patchedFrame, 0, newPatchedFrame, 0, patchedFrame.length);
        patchedFrames[frameToPatch] = newPatchedFrame;

        newPatchedFrame[i] = defToPatch;
        newPatchedFrame[i + 1] = group;
        newPatchedFrame[i + 2] = defReplacer;
    }

    public int findPatchedFrame(int def, int groupIndex, int frameIndex) {
        if (patchedFrames.length <= frameIndex) {
            return 0;
        }

        int[] patchedFrame = patchedFrames[frameIndex];
        if (patchedFrame == null) {
            return 0;
        }

        for (int i = 0; i < patchedFrame.length; i+=3) {
            if (patchedFrame[i] == def && patchedFrame[i + 1] == groupIndex) {
                return patchedFrame[i + 2];
            }
        }
        return 0;
    }

    public int findResource(String name) {
        Integer result = this.cache.get(name);
        return result == null ? 0 : result;
    }
}
