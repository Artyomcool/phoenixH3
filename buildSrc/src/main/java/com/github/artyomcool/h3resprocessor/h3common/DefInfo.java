package com.github.artyomcool.h3resprocessor.h3common;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class DefInfo {

    public static final int[] SPEC_COLORS = new int[]{
            0xFF00FFFF,
            0xFFFF96FF,
            0xFFFF64FF,
            0xFFFF32FF,
            0xFFFF00FF,
            0xFFFFFF00,
            0xFFB400FF,
            0xFF00FF00,
    };

    public DefType type;
    public int fullWidth;
    public int fullHeight;
    public int[] palette;

    public final List<Group> groups = new ArrayList<>();

    private static boolean hasSpecialColors(IntBuffer pixels) {
        while (pixels.hasRemaining()) {
            for (int specColor : SPEC_COLORS) {
                if (pixels.get() == specColor) {
                    return true;
                }
            }
        }
        return false;
    }

    public int compressionForType() {
        for (Group group : groups) {
            for (Frame frame : group.frames) {
                if (type == DefType.DefAdventureObject || type == DefType.DefAdventureHero) {
                    return  3;
                } else if (type == DefType.DefGroundTile) {
                    return hasSpecialColors(frame.pixels.duplicate()) ? 2 : 0;
                } else {
                    return  1;
                }
            }
        }
        return 1;
    }

    public static class Group {
        public final DefInfo def;
        public int groupIndex;
        public String name;
        public final List<Frame> frames = new ArrayList<>();

        public Group(DefInfo def) {
            this.def = def;
        }
    }

    public static class Frame {
        public final int fullWidth;
        public final int fullHeight;
        public final IntBuffer pixels;
        public final String pixelsSha;

        public String name;
        /*
        type == 43 || type == 44 -> 3
        type == 45 && w == 32 && h == 32 -> hasSpecialColors ? 2 : 0
        type == 45 -> 3
        default -> 1
         */
        public int compression = 1;    // 0 - no compression (0 spec colors), 1 - large packs (8 spec colors), 2 - small packs (6 spec colors), 3 - small packs 32 (6 spec colors)

        public Frame(int fullWidth, int fullHeight, IntBuffer pixels) {
            this(fullWidth, fullHeight, pixels, sha256(pixels));
        }

        public Frame(int fullWidth, int fullHeight, IntBuffer pixels, String sha256) {
            this.fullWidth = fullWidth;
            this.fullHeight = fullHeight;
            this.pixels = pixels;
            this.pixelsSha = sha256;
        }

        private static String sha256(IntBuffer pixels) {
            ByteBuffer buffer = ByteBuffer.allocate(pixels.remaining() * 4);
            IntBuffer ib = buffer.asIntBuffer();
            ib.put(pixels.duplicate());
            MessageDigest digest;
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
            buffer.limit(ib.position() * 4);
            digest.update(buffer);
            return HexFormat.of().formatHex(digest.digest());
        }

        public int color(int x, int y) {
            return pixels.get(x + y * fullWidth);
        }
    }

    public static void putFrameName(ByteBuffer buffer, String n) {
        byte[] name = Arrays.copyOf(n.getBytes(), 12);
        buffer.put(name);
        buffer.put((byte) 0);
    }

    public static class PackedFrame {
        final Frame frame;
        final Box box;
        final int hashCode;

        public PackedFrame(Frame frame, Box box) {
            this.frame = frame;
            this.box = box;
            this.hashCode = calcHashCode();
        }

        private int calcHashCode() {
            int result = box.hashCode();
            result = 31 * result + frame.fullWidth;
            result = 31 * result + frame.fullHeight;
            result = 31 * result + frame.compression;
            result = 31 * result + frame.pixelsSha.hashCode();
            return result;
        }

        public int color(int x, int y) {
            return frame.color(x + box.x(), y + box.y());
        }

        public int[] scanline(int y) {
            int[] scanline = new int[box.width()];
            frame.pixels.get(box.x() + (y + box.y()) * frame.fullWidth, scanline);
            return scanline;
        }

        public int[] scanline(int y, int dx, int w) {
            int[] scanline = new int[w];
            if (y + box.y() >= frame.fullHeight) {
                Arrays.fill(scanline, SPEC_COLORS[0]);
                return scanline;
            }
            w = Math.min(w, frame.fullWidth - (box.x() + dx));
            frame.pixels.get(box.x() + dx + (y + box.y()) * frame.fullWidth, scanline, 0, w);
            for (int i = w; i < scanline.length; i++) {
                scanline[i] = SPEC_COLORS[0];
            }
            return scanline;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            PackedFrame that = (PackedFrame) o;

            if (hashCode != that.hashCode) return false;
            if (frame.fullWidth != that.frame.fullWidth) return false;
            if (frame.fullHeight != that.frame.fullHeight) return false;
            if (frame.compression != that.frame.compression) return false;
            if (!Objects.equals(box, that.box)) return false;
            return Objects.equals(frame.pixelsSha, that.frame.pixelsSha);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    public static class FrameInfo {
        public final PackedFrame packedFrame;
        public final List<Frame> frames = new ArrayList<>();
        public String name;

        public FrameInfo(PackedFrame packedFrame) {
            this.packedFrame = packedFrame;
        }
    }

    public static Box calculateTransparent(int w, int h, IntBuffer pixels) {
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;

        int transparent = SPEC_COLORS[0];

        pixels = pixels.duplicate();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int color = pixels.get();
                if (color != transparent) {
                    left = Math.min(x, left);
                    right = Math.max(x, right);
                    top = Math.min(y, top);
                    bottom = Math.max(y, bottom);
                }
            }
        }

        if (left == Integer.MAX_VALUE) {
            return new Box(0, 0, 1, 1);
        }

        return new Box(left, top, right - left + 1, bottom - top + 1);
    }
}
