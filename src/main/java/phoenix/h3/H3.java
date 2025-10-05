package phoenix.h3;

import phoenix.h3.annotations.R;
import phoenix.h3.annotations.Upcall;
import phoenix.h3.game.GzFile;
import phoenix.h3.game.patch.*;
import phoenix.h3.game.stdlib.Memory;

import java.io.IOException;

import static phoenix.h3.annotations.R.*;
import static phoenix.h3.game.stdlib.Memory.*;

public class H3 {

    private PatchRepository patchRepository;

    public static void main(String[] args) {
        System.loadLibrary("phoenixH3");

        new H3().init();
        notifyInitialized();
        throw new IllegalStateException("Should never got here!");
    }

    private void init() {
        new Patcher.Stateless() {
            Object savedPatches;

            @Upcall(base = 0x407740)
            public void cleanup() {
                // todo well, we uninstall it on the native side, do we really need it?
                // todo moreover, it does literally nothing for now)
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
                patchRepository.onStart(null);
            }

            @Upcall(base = 0x4BF1F3)
            public void afterSaveLoaded() {
                patchRepository.onStart(savedPatches);
            }

            @Upcall(base = 0x4BE1DD)
            void onSaveExtras(@R(ESI) int gzFile) throws IOException {
                Object data = patchRepository.savePatches();
                // todo too many memory moves)
                byte[] serialized = Serializer.serialize(data);
                int tmpMemory = malloc(serialized.length + 4);
                putDword(tmpMemory, serialized.length);
                putArray(tmpMemory + 4, serialized, 0, serialized.length);
                GzFile.write(gzFile, tmpMemory, serialized.length + 4);
                free(tmpMemory);
            }

            @Upcall(base = 0x4BCD09)
            void onLoadExtras(@R(EBX) int gzFile) throws IOException {
                int size = GzFile.readDword(gzFile);
                byte[] data = new byte[size];
                GzFile.readFully(gzFile, data);
                //noinspection unchecked
                savedPatches = Serializer.deserialize(data);
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
    public static native void extractPatchInfo(PatchInfo out);


    // all of that "magic" is used for "cooperative" switch between upcall/downcall state
    // it is very similar to modern stackfull coroutines, except it neither "stackfull" nor "coroutine")))
    public static native void pauseJvmLoop();
    public static native void exitFromUpcall(int depth);
    public static native int exitFromDowncall();
    public static int depth = 0;
    public static void loop() {
        int d = depth;
        do {
            pauseJvmLoop();
        } while (d == depth);
    }
}
