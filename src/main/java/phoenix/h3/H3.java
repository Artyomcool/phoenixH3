package phoenix.h3;

import phoenix.h3.annotations.R;
import phoenix.h3.annotations.Upcall;
import phoenix.h3.game.GzFile;
import phoenix.h3.game.patch.*;
import phoenix.h3.game.stdlib.Memory;

import static phoenix.h3.annotations.R.EDX;

public class H3 {

    private PatchRepository patchRepository;

    public static void main(String[] args) {
        System.loadLibrary("phoenixH3");

        try {
            new H3().init();
        } catch (Throwable t) {
            t.printStackTrace();
        }
        notifyInitialized();
        throw new IllegalStateException("Should never got here!");
    }

    private void init() {
        new Patcher() {
            @Upcall(base = 0x407740)
            public void cleanup() {
                // well, we uninstall it on the native side, do we really need it?
                patchRepository.rollbackPatches();
                Memory.autoFree();
                System.exit(0);
            }

            @Upcall(base = 0x4bed98)
            public void writeHeaderOnSave(@R(EDX) int gzFile) {
                PatchInfo info = new PatchInfo();
                extractPatchInfo(info);
                GzFile.write(gzFile, info.headerStartAddress, info.headerSize);
                GzFile.write(gzFile, info.totalStartAddress, info.totalSize);
            }

            @Upcall(base = 0x4c01a2)
            public void afterGameCreated() {
                patchRepository.onGameCreated(false);
            }

            @Upcall(base = 0x4BF1F3)
            public void afterSaveLoaded() {
                patchRepository.onGameCreated(true);
            }
        }.installPatch();

        patchRepository = Init.start();
    }

    public static void dbg(Object... args) {
        StringBuffer buffer = new StringBuffer();
        for (Object arg : args) {
            buffer.append(arg);
        }
        System.out.println(buffer);
        System.out.flush();
    }

    public static native void notifyInitialized();
    public static native void exitFromUpcall(int depth);
    public static native int exitFromDowncall();
    public static native void extractPatchInfo(PatchInfo out);
    public static native void pauseJvmLoop();


    public static int depth = 0;
    public static void loop() {
        int d = depth;
        do {
            pauseJvmLoop();
        } while (d == depth);
    }
}
