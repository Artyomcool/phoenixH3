package com.github.artyomcool.h3resprocessor.h3common;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.*;

public class Def extends DefInfo {

    public Def ensurePalette() {
        if (palette != null) {
            return this;
        }

        Set<Integer> colors = new HashSet<>();
        for (DefInfo.Group group : groups) {
            for (DefInfo.Frame frame : group.frames) {
                IntBuffer pixels = frame.pixels.duplicate();
                while (pixels.hasRemaining()) {
                    colors.add(pixels.get());
                }
            }
        }

        int specialColors = switch (compressionForType()) {
            case 0 -> 0;
            case 1 -> 8;
            case 2, 3 -> 6;
            default -> throw new IllegalStateException("Unexpected value for: " + type);
        };

        for (int i = 0; i < specialColors; i++) {
            int specColor = DefInfo.SPEC_COLORS[i];
            colors.remove(specColor);
        }

        int reserved = type == DefType.DefCombatCreature ? 10 : specialColors;

        int colorsCount = colors.size() + reserved;
        if (colorsCount > 256) {
            System.err.println("Wrong colors count: " + colorsCount + "; correction will take place");

            Map<Integer, Integer> colorReplacements = new HashMap<>();
            while (colors.size() + reserved > 256) {
                ArrayList<Integer> cc = new ArrayList<>(colors);
                reducePaletteForOne(cc, colorReplacements);
                colors = new HashSet<>(cc);
            }

            List<Map.Entry<Integer, Integer>> replacements = new ArrayList<>(colorReplacements.entrySet());
            Map<Integer, Map.Entry<Integer, Integer>> replacementsMap = new HashMap<>();
            for (Map.Entry<Integer, Integer> replacement : replacements) {
                replacementsMap.put(replacement.getKey(), replacement);
            }

            for (Map.Entry<Integer, Integer> replacement : replacements) {
                Integer to = replacement.getValue();

                Map.Entry<Integer, Integer> rep = replacementsMap.get(to);
                while (rep != null) {
                    to = rep.getValue();
                    replacement.setValue(to);
                    rep = replacementsMap.get(to);
                }
            }
        }

        palette = toPalette(colors, specialColors, reserved);

        return this;
    }

    public Def replaceTransparentColors() {
        Set<Frame> frames = new HashSet<>();
        for (Group group : groups) {
            frames.addAll(group.frames);
        }

        for (Frame frame : frames) {
            for (int i = frame.fullWidth * frame.fullHeight - 1; i >= 0; i--) {
                int pixel = frame.pixels.get(i);
                if ((pixel & 0xFF000000) == 0) {
                    frame.pixels.put(i, SPEC_COLORS[0]);
                }
            }
        }
        return this;
    }

    public byte[] pack(String defName) {
        ByteBuffer buffer = ByteBuffer.allocate(10 * 1024 * 1024).order(ByteOrder.LITTLE_ENDIAN);

        List<Group> groups = new ArrayList<>(this.groups);
        groups.removeIf(g -> g.frames.isEmpty());

        int groupCount = groups.size();
        buffer
                .putInt(type.type)
                .putInt(fullWidth)
                .putInt(fullHeight)
                .putInt(groupCount);

        for (int c : palette) {
            int alpha = c >>> 24;

            if (alpha == 0) {
                c = SPEC_COLORS[0];
            } else if (alpha != 0xff) {
                throw new IllegalArgumentException("Palette has translucent color: #" + Integer.toHexString(c));
            }
            buffer.put((byte) (c >> 16));
            buffer.put((byte) (c >> 8));
            buffer.put((byte) c);
        }

        Map<Integer, Byte> paletteMap = new HashMap<>();
        for (int i = 0; i < palette.length; i++) {
            paletteMap.putIfAbsent(palette[i], (byte) i);
        }

        Map<Frame, Integer> offsetToPutOffset = new HashMap<>();
        Map<Frame, FrameInfo> links = getLinks(this, defName);

        for (Group group : groups) {
            int groupIndex = group.groupIndex;
            int framesCount = group.frames.size();
            int unk1 = 0;
            int unk2 = 0;

            buffer
                    .putInt(groupIndex)
                    .putInt(framesCount)
                    .putInt(unk1)
                    .putInt(unk2);

            for (Frame f : group.frames) {
                putFrameName(buffer, links.get(f).name);
            }
            for (Frame f : group.frames) {
                offsetToPutOffset.put(f, buffer.position());
                buffer.putInt(0);   // offset
            }
        }

        for (FrameInfo frameInfo : links.values()) {
            for (Frame frame : frameInfo.frames) {
                buffer.putInt(offsetToPutOffset.get(frame), buffer.position());
            }
            PackedFrame packedFrame = frameInfo.packedFrame;

            int offsetOfSize = buffer.position();
            System.out.println("Offset of size: " + Integer.toHexString(offsetOfSize));
            buffer.putInt(0);   // size
            buffer.putInt(packedFrame.frame.compression);
            buffer.putInt(packedFrame.frame.fullWidth);
            buffer.putInt(packedFrame.frame.fullHeight);

            if (packedFrame.frame.compression == 3) {
                int x = packedFrame.box.x() / 32 * 32;
                int w = packedFrame.box.width() + (packedFrame.box.x() - x);
                if (w % 32 != 0) {
                    w = (w / 32 + 1) * 32;
                }
                packedFrame = new PackedFrame(packedFrame.frame, new Box(x, packedFrame.box.y(), w, packedFrame.box.height()));
            }
            buffer.putInt(packedFrame.box.width());
            buffer.putInt(packedFrame.box.height());
            buffer.putInt(packedFrame.box.x());
            buffer.putInt(packedFrame.box.y());

            switch (packedFrame.frame.compression) {
                case 0 -> {
                    for (int y = 0; y < packedFrame.box.height(); y++) {
                        for (int x = 0; x < packedFrame.box.width(); x++) {
                            buffer.put(paletteMap.get(packedFrame.color(x, y)));
                        }
                    }
                }
                case 1 -> {
                    int offsetsPos = buffer.position();

                    buffer.position(offsetsPos + packedFrame.box.height() * 4);
                    for (int y = 0; y < packedFrame.box.height(); y++) {
                        int[] scanline = packedFrame.scanline(y);
                        int offset = buffer.position();
                        for (int x = 0; x < packedFrame.box.width(); ) {
                            int color = scanline[x];
                            int index = paletteMap.get(color) & 0xff;
                            if (index < 8) {
                                int count = 1;
                                int xx = x + 1;
                                while (xx < packedFrame.box.width() && count < 256 && scanline[xx] == color) {
                                    count++;
                                    xx++;
                                }
                                buffer.put((byte) index);
                                buffer.put((byte) (count - 1));
                                x = xx;
                            } else {
                                int count = 1;
                                int xx = x;
                                while (xx + 1 < packedFrame.box.width() && count < 256 && (paletteMap.get(scanline[xx + 1]) & 0xff) >= 8) {
                                    count++;
                                    xx++;
                                }

                                buffer.put((byte) 0xff);
                                buffer.put((byte) (count - 1));
                                while (x <= xx) {
                                    buffer.put(paletteMap.get(scanline[x]));
                                    x++;
                                }
                            }
                        }
                        buffer.putInt(offsetsPos + y * 4, offset - offsetsPos);
                    }
                }
                case 2 -> {
                    int offsetsPos = buffer.position();

                    buffer.position(offsetsPos + packedFrame.box.height() * 2);
                    for (int y = 0; y < packedFrame.box.height(); y++) {
                        int[] scanline = packedFrame.scanline(y);
                        int offset = buffer.position();
                        for (int x = 0; x < packedFrame.box.width(); ) {
                            int color = scanline[x];
                            int index = paletteMap.get(color) & 0xff;
                            if (index < 6) {
                                int count = 1;
                                int xx = x + 1;
                                while (xx < packedFrame.box.width() && count < 32 && scanline[xx] == color) {
                                    count++;
                                    xx++;
                                }
                                buffer.put((byte) (index << 5 | (count - 1)));
                                x = xx;
                            } else {
                                int count = 1;
                                int xx = x;
                                while (xx + 1 < packedFrame.box.width() && count < 32 && (paletteMap.get(scanline[xx + 1]) & 0xff) >= 6) {
                                    count++;
                                    xx++;
                                }

                                buffer.put((byte) (7 << 5 | (count - 1)));
                                while (x <= xx) {
                                    buffer.put(paletteMap.get(scanline[x++]));
                                }
                            }
                        }
                        buffer.putShort(offsetsPos + y * 2, (short) (offset - offsetsPos));
                    }
                }
                case 3 -> {
                    int blocksCount = packedFrame.box.width() * packedFrame.box.height() / 32;
                    int offsetsPos = buffer.position();

                    buffer.position(offsetsPos + blocksCount * 2);

                    int i = 0;
                    for (int y = 0; y < packedFrame.box.height(); y++) {
                        for (int xw = 0; xw < packedFrame.box.width(); xw += 32) {
                            int[] scanline = new int[32];
                            System.arraycopy(packedFrame.scanline(y, xw, 32), 0, scanline, 0, 32);
                            int offset = buffer.position();
                            for (int x = 0; x < scanline.length; ) {
                                int color = scanline[x];
                                int index = paletteMap.get(color) & 0xff;
                                int count = 0;
                                int xx = x + 1;
                                if (index < 6) {
                                    while (xx < scanline.length && scanline[xx] == color) {
                                        count++;
                                        xx++;
                                    }
                                    buffer.put((byte) (index << 5 | count));
                                    x = xx;
                                } else {
                                    while (xx < scanline.length && (paletteMap.get(scanline[xx]) & 0xff) >= 6) {
                                        count++;
                                        xx++;
                                    }

                                    buffer.put((byte) (7 << 5 | count));
                                    while (x < xx) {
                                        buffer.put(paletteMap.get(scanline[x++]));
                                    }
                                }
                            }
                            buffer.putShort(offsetsPos + (i++ * 2), (short) (offset - offsetsPos));
                        }
                    }
                }
            }

            int size = buffer.position() - offsetOfSize;
            System.out.println("Put size: " + Integer.toHexString(size) + " at " + Integer.toHexString(offsetOfSize));
            buffer.putInt(offsetOfSize, size);
            System.out.println("Size " + Integer.toHexString(buffer.getInt(offsetOfSize)));
        }
        return Arrays.copyOf(buffer.array(), buffer.position());
    }

    private static int[] toPalette(Set<Integer> colors, int specialColors, int reserved) {
        int[] palette = new int[256];
        int i = 0;
        for (; i < specialColors; i++) {
            palette[i] = DefInfo.SPEC_COLORS[i];
        }
        for (; i < reserved; i++) {
            palette[i] = 0xff000000;
        }
        TreeSet<Integer> tree = new TreeSet<>((c1, c2) -> {
            int r1 = c1 >>> 16 & 0xff;
            int r2 = c2 >>> 16 & 0xff;

            int g1 = c1 >>> 8 & 0xff;
            int g2 = c2 >>> 8 & 0xff;

            int b1 = c1 & 0xff;
            int b2 = c2 & 0xff;

            int y1 = r1 * 3 + b1 + g1 * 4;
            int y2 = r2 * 3 + b2 + g2 * 4;

            int compare = Integer.compare(y1, y2);
            return compare == 0 ? Integer.compare(c1, c2) : compare;
        });
        tree.addAll(colors);
        for (Integer c : tree) {
            palette[i++] = c;
        }
        while (i < 256) {
            palette[i++] = 0xFF000000;
        }
        return palette;
    }

    public static int colorDifferenceForCompare(int c1, int c2) {
        int a1 = (c1 >>> 24) & 0xff;
        int a2 = (c2 >>> 24) & 0xff;
        int r1 = (c1 >>> 16) & 0xff;
        int r2 = (c2 >>> 16) & 0xff;
        int g1 = (c1 >>> 8) & 0xff;
        int g2 = (c2 >>> 8) & 0xff;
        int b1 = c1 & 0xff;
        int b2 = c2 & 0xff;

        return diff2(a1, a2) + diff2(r1, r2) + diff2(g1, g2) + diff2(b1, b2);
    }

    private static int diff2(int a, int b) {
        return (a - b) * (a - b);
    }

    private void reducePaletteForOne(List<Integer> colors, Map<Integer, Integer> colorReplacements) {
        int diff = Integer.MAX_VALUE;
        int gc1 = 0;
        int gc2 = 0;

        for (int i = 0; i < colors.size(); i++) {
            int c1 = colors.get(i);
            for (int j = i + 1; j < colors.size(); j++) {
                int c2 = colors.get(j);
                int d = colorDifferenceForCompare(c1, c2);
                if (d < diff) {
                    gc1 = c1;
                    gc2 = c2;
                    diff = d;
                }
            }
        }

        int a = (((gc1 >>> 24) & 0xff) + ((gc2 >>> 24) & 0xff)) >>> 1;
        int r = (((gc1 >>> 16) & 0xff) + ((gc2 >>> 16) & 0xff)) >>> 1;
        int g = (((gc1 >>> 8) & 0xff) + ((gc2 >>> 8) & 0xff)) >>> 1;
        int b = ((gc1 & 0xff) + (gc2 & 0xff)) >>> 1;

        colors.remove((Integer) gc1);
        colors.remove((Integer) gc2);
        int gc = a << 24 | r << 16 | g << 8 | b;
        for (int specColor : DefInfo.SPEC_COLORS) {
            if (specColor == gc) {
                gc |= 0x0100;
                if (specColor == gc) {
                    gc &= ~0x0100;
                }
                break;
            }
        }
        colors.add(gc);
        if (gc1 != gc) {
            colorReplacements.put(gc1, gc);
        }
        if (gc2 != gc) {
            colorReplacements.put(gc2, gc);
        }
    }

    private static Map<Frame, FrameInfo> getLinks(DefInfo def, String defName) {
        Map<String, Box> shaToBox = new HashMap<>();
        Map<PackedFrame, FrameInfo> frameInfoMap = new LinkedHashMap<>();
        Map<Frame, Group> frameToGroup = new HashMap<>();
        for (Group group : def.groups) {
            for (Frame frame : group.frames) {
                frameToGroup.put(frame, group);
                Box box = shaToBox.computeIfAbsent(
                        frame.pixelsSha,
                        s -> calculateTransparent(frame.fullWidth, frame.fullHeight, frame.pixels)
                );
                FrameInfo info = frameInfoMap.computeIfAbsent(
                        new PackedFrame(frame, box),
                        FrameInfo::new
                );
                info.frames.add(frame);
            }
        }

        int sharedFramesCount = 0;
        Map<Frame, FrameInfo> result = new HashMap<>();
        for (FrameInfo value : frameInfoMap.values()) {
            String prefix = defName.substring(0, Math.min(8, defName.length()));
            if (value.frames.size() > 1) {
                value.name = String.format("%s_%03d", prefix, sharedFramesCount++);
            } else {
                Frame frame = value.frames.get(0);
                char group = (char) ('A' + frameToGroup.get(frame).groupIndex);
                value.name = String.format("%s%s%03d", prefix, group + "", frameToGroup.get(frame).frames.indexOf(frame));
            }
            for (Frame frame : value.frames) {
                result.put(frame, value);
            }
        }

        if (result.size() == 1) {
            String name = result.keySet().iterator().next().name;
            if (name != null) {
                result.values().iterator().next().name = name;
            }
        }

        return result;
    }

    public Def fixupCompression() {
        int compression = compressionForType();
        for (DefInfo.Group group : groups) {
            for (DefInfo.Frame frame : group.frames) {
                frame.compression = compression;
            }
        }
        return this;
    }
}
