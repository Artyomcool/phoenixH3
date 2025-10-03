package phoenix.h3.game;

import phoenix.h3.annotations.Downcall;
import phoenix.h3.annotations.Thiscall;
import phoenix.h3.game.common.ByteArrayReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Hashtable;

import static phoenix.h3.H3.dbg;
import static phoenix.h3.game.stdlib.Memory.*;

public class Def extends Resource {

    public static final int SIZE = 0x56;

    public static final int OFFSET_GROUPS_ARRAY = 0x1C;
    public static final int OFFSET_GROUPS_EXIST_ARRAY = 0x2C;
    public static final int OFFSET_PALETTE_RGB565 = 0x20;
    public static final int OFFSET_PALETTE_RGB888 = 0x24;

    public static int createNew(int name, int type, int width, int height) {
        int malloc = malloc(SIZE);
        create(malloc, name, type, width, height);
        return malloc;
    }

    @Thiscall
    @Downcall(0x47B240)
    public static native void create(int where, int name, int type, int width, int height);

    @Thiscall
    @Downcall(0x55C9C0)
    public static native int getByName(int name);

    public static int loadFromJar(String defName) {
        InputStream stream = Def.class.getResourceAsStream(new StringBuffer("/").append(defName).toString());
        if (stream == null) {
            throw new IllegalArgumentException(new StringBuffer(defName).append(" not found").toString());
        }
        try {
            return loadDefFromResourceStream(defName, stream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static int loadDefFromResourceStream(String name, InputStream s) throws IOException {
        byte[] buf = new byte[s.available()];
        int pos = 0;
        while (true) {
            if (s.read(buf, pos, buf.length - pos) == -1) {
                break;
            }
        }

        s.close();
        ByteArrayReader stream = new ByteArrayReader(buf);

        int type = stream.nextIntLE();
        int width = stream.nextIntLE();
        int height = stream.nextIntLE();
        int groupsCount = stream.nextIntLE();

        int tmpBuffer = SHARED_TMP_NATIVE_BUFFER;
        putCstr(tmpBuffer, name);
        int def = Def.createNew(tmpBuffer, type, width, height);
        putDword(def + OFFSET_REF_COUNT, Integer.MAX_VALUE / 2); // "infinite" ref count

        int groupsArray = dwordAt(def + OFFSET_GROUPS_ARRAY);
        int groupsExistArray = dwordAt(def + OFFSET_GROUPS_EXIST_ARRAY);

        int palette888 = PaletteRGB888.allocate();
        putDword(def + OFFSET_PALETTE_RGB888, palette888);

        // let's make colors in place!
        int paletteSize = 256 * 3;
        putArray(palette888 + PaletteRGB888.OFFSET_COLORS, stream.buf, stream.pos, paletteSize);
        stream.pos += paletteSize;

        PaletteRGB888.createFromColors(palette888, palette888 + PaletteRGB888.OFFSET_COLORS);    // will copy on the same place))

        int enabledContrast = byteAt(0x69E600);
        if (enabledContrast != 0) {
            PaletteRGB888.adjustHsv(palette888, -1f, -1f, 1.5f, 1.2f);
        }

        dbg("Before PaletteRGB565.createNewFromPalette888");
        int palette16 = PaletteRGB565.createNewFromPalette888(palette888);
        dbg("After PaletteRGB565.createNewFromPalette888");
        putDword(def + OFFSET_PALETTE_RGB565, palette16);

        Hashtable<Integer, Integer> loadedFrames = new Hashtable<>();
        for (int i = 0; i < groupsCount; i++) { // groups already allocated by type
            dbg("Groups loop ", i);
            int groupIndex = stream.nextIntLE();
            int framesCount = stream.nextIntLE();
            stream.pos += 8; // skip unknown 8 bytes

            int group = Group.create(framesCount);

            putDword(groupsArray + groupIndex * 4, group);
            putDword(groupsExistArray + groupIndex * 4, 1);

            int framesArray = malloc(4 * framesCount);
            putDword(group + Group.OFFSET_FRAMES, framesArray);

            for (int j = 0; j < framesCount; j++) {
                dbg("Frames loop ", j);
                int nameBytesPos = stream.pos;
                stream.pos += 13;
                int frameOffset = stream.nextIntLE();
                Integer frame = loadedFrames.get(frameOffset);
                if (frame == null) {
                    int oldPos = stream.pos;
                    stream.pos = frameOffset;

                    int size = stream.nextIntLE();
                    int compressionType = stream.nextIntLE();
                    int fullWidth = stream.nextIntLE();
                    int fullHeight = stream.nextIntLE();
                    int w = stream.nextIntLE();
                    int h = stream.nextIntLE();
                    int offsetX = stream.nextIntLE();
                    int offsetY = stream.nextIntLE();

                    dbg("load frame ", Integer.toHexString(frameOffset), " ", Integer.toHexString(size), " ", compressionType, " ", fullWidth, " ", fullHeight, " ", w, " ", h, " ", offsetX, " ", offsetY);

                    int data = malloc(size);
                    putArray(data, buf, stream.pos, size);
                    putArray(tmpBuffer, buf, nameBytesPos, 13);
                    frame = Def.Frame.createNew(
                            tmpBuffer,
                            fullWidth,
                            fullHeight,
                            data,
                            size,
                            compressionType,
                            w,
                            h,
                            offsetX,
                            offsetY
                    );
                    putDword(frame + Frame.OFFSET_REF_COUNT, Integer.MAX_VALUE / 2); // "infinite" ref count

                    loadedFrames.put(frameOffset, frame);
                    stream.pos = oldPos;
                }

                putDword(framesArray + 4 * i, frame);
            }
        }
        dbg("Def loaded");
        return def;
    }

    public static void patchArtifactsDef(int artifactDef, int oldArtifactsCount, int totalArtifactsInGame, int[] singleFrameDefs) {
        int prevGroupArray = dwordAt(artifactDef + 0x1C);
        int prevGroup = dwordAt(prevGroupArray);

        int framesCountAddress = prevGroup + 0;
        int framesMaxCountAddress = prevGroup + 4;
        int framesAddress = prevGroup + 8;

        putDword(framesCountAddress, totalArtifactsInGame);
        putDword(framesMaxCountAddress, totalArtifactsInGame);

        int prevFrameArray = dwordAt(framesAddress);

        int newFrameArray = malloc(totalArtifactsInGame * 4);
        memcpy(newFrameArray, prevFrameArray, oldArtifactsCount * 4);
        putDword(framesAddress, newFrameArray);

        int nextFrameAddress = newFrameArray + oldArtifactsCount * 4;
        for (int i = 0; i < singleFrameDefs.length; i++) {
            int groups = dwordAt(singleFrameDefs[i] + Def.OFFSET_GROUPS_ARRAY);
            int group = dwordAt(groups);
            int frames = dwordAt(group + Group.OFFSET_FRAMES);
            int frame = dwordAt(frames);
            putDword(nextFrameAddress + i * 4, frame);
        }
    }

    public static class Group {
        public static final int SIZE = 0x0C;
        public static final int OFFSET_FRAMES_COUNT = 0;
        public static final int OFFSET_MAX_FRAMES_COUNT = 4;
        public static final int OFFSET_FRAMES = 8;

        public static int allocate() {
            return malloc(SIZE);
        }

        public static int create(int frames) {
            int result = malloc(SIZE);
            putDword(result + OFFSET_FRAMES_COUNT, frames);
            putDword(result + OFFSET_MAX_FRAMES_COUNT, frames);
            return result;
        }
    }

    public static class Frame extends Resource {
        public static final int SIZE = 0x48;

        public static int allocate() {
            return malloc(SIZE);
        }

        public static int createNew(
                int frameName,
                int fullWidth,
                int fullHeight,
                int data,
                int size,
                int compressionType,
                int w,
                int h,
                int dx,
                int dy
        ) {
            int self = allocate();
            create(self, frameName, fullWidth, fullHeight, data, size, compressionType, w, h, dx, dy);
            return self;
        }

        @Thiscall
        @Downcall(0x47BC80)
        public static native void create(
                int where,
                int frameName,
                int fullWidth,
                int fullHeight,
                int data,
                int size,
                int compressionType,
                int w,
                int h,
                int dx,
                int dy
        );

    }

}
