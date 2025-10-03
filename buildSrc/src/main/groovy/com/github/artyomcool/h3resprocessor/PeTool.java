package com.github.artyomcool.h3resprocessor;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;

public class PeTool {

    static final int IMAGE_DOS_SIGNATURE = 0x5A4D;      // 'MZ'
    static final int IMAGE_NT_SIGNATURE = 0x00004550;  // 'PE\0\0'
    static final int IMAGE_FILE_MACHINE_I386 = 0x014c;
    static final int IMAGE_DIRECTORY_ENTRY_BASERELOC = 5;

    static final int IMAGE_SCN_CNT_CODE = 0x00000020;

    static final int IMAGE_REL_BASED_HIGHLOW = 3;

    static class Import {
        String functionName;
        int offset;
    }

    static class Dll {
        Section sec;
        String name;
        List<Import> imports = new ArrayList<>();
    }

    static class Section {
        int virtualAddress;
        int virtualSize;
        int rawPtr;
        int rawSize;
        int characteristics;
        List<Dll> dlls = new ArrayList<>();

        boolean isCode() {
            return (characteristics & IMAGE_SCN_CNT_CODE) != 0;
        }
    }

    record Relocation(
            Section sectionToPatch,
            int offsetToPatch,
            Section addressedSection,
            int offsetFromAddressedSection
    ) {
    }

    static int u16(byte[] b, int off) {
        return ((b[off] & 0xFF)) | ((b[off + 1] & 0xFF) << 8);
    }

    static long u32(byte[] b, int off) {
        return ((long) (b[off] & 0xFF)) |
                ((long) (b[off + 1] & 0xFF) << 8) |
                ((long) (b[off + 2] & 0xFF) << 16) |
                ((long) (b[off + 3] & 0xFF) << 24);
    }

    static void u32(byte[] b, int off, int patch) {
        b[off] = (byte) (patch & 0xff);
        b[off + 1] = (byte) ((patch >>> 8) & 0xff);
        b[off + 2] = (byte) ((patch >>> 16) & 0xff);
        b[off + 3] = (byte) ((patch >>> 24) & 0xff);
    }

    static String readZ(byte[] b, int off) {
        int i = off;
        while (i < b.length && b[i] != 0) i++;
        return new String(b, off, i - off);
    }

    static String hex8(long v) {
        return String.format("0x%08X", v & 0xFFFFFFFFL);
    }

    static Section findSectionByRva(List<Section> secs, long rva) {
        for (Section s : secs) {
            long va = s.virtualAddress & 0xFFFFFFFFL;
            long vs = s.virtualSize & 0xFFFFFFFFL;
            long rs = s.rawSize & 0xFFFFFFFFL;
            long span = vs != 0 ? vs : rs;
            if (rva >= va && rva < va + span) return s;
        }
        return null;
    }

    static int rvaToFileOff(List<Section> secs, long rva) {
        Section s = findSectionByRva(secs, rva);
        return rvaToFileOff(rva, s);
    }

    private static int rvaToFileOff(long rva, Section s) {
        if (s == null) throw new IllegalStateException("RVA not in any section: " + hex8(rva));
        long off = (rva - (s.virtualAddress & 0xFFFFFFFFL)) + (s.rawPtr & 0xFFFFFFFFL);
        return (int) off;
    }

    static int index(byte[] bytes, byte[] c) {
        int r = -1;
        a:
        for (int i = 0; i < bytes.length; i++) {
            for (int j = 0; j < c.length; j++) {
                if (bytes[i + j] != c[j]) {
                    continue a;
                }
            }
            r = i;
        }
        return r;
    }

    public static byte[] pack(byte[] dll, String entryName, boolean withImports) {
        boolean withEntry = entryName != null;
        // --- DOS header ---
        int e_magic = u16(dll, 0);
        if (e_magic != IMAGE_DOS_SIGNATURE) {
            throw new IllegalStateException("not MZ");
        }
        int e_lfanew = (int) u32(dll, 0x3C);

        // --- NT headers ---
        int ntSig = (int) u32(dll, e_lfanew);
        if (ntSig != IMAGE_NT_SIGNATURE) {
            throw new IllegalStateException("not PE");
        }

        int fileHeaderOff = e_lfanew + 4;
        int Machine = u16(dll, fileHeaderOff);
        int NumberOfSections = u16(dll, fileHeaderOff + 2);
        int SizeOfOptionalHeader = u16(dll, fileHeaderOff + 16);
        if (Machine != IMAGE_FILE_MACHINE_I386) {
            throw new IllegalStateException("not i386");
        }

        int optionalOff = fileHeaderOff + 20;
        int imageBase = (int) u32(dll, optionalOff + 28);

        int dataDirOff = optionalOff + 96;
        long exportDirRva = u32(dll, dataDirOff);
        long importDirRva = u32(dll, dataDirOff + 8);
        long relocDirRva = u32(dll, dataDirOff + 8 * IMAGE_DIRECTORY_ENTRY_BASERELOC);
        long relocDirSz = u32(dll, dataDirOff + 8 * IMAGE_DIRECTORY_ENTRY_BASERELOC + 4);

        // --- Section table ---
        int impOff = -1;
        int secTableOff = optionalOff + SizeOfOptionalHeader;
        List<Section> secs = new ArrayList<>();
        for (int i = 0; i < NumberOfSections; i++) {
            int off = secTableOff + i * 40;

            Section s = new Section();
            s.virtualSize = (int) u32(dll, off + 8);
            s.virtualAddress = (int) u32(dll, off + 12);
            s.rawSize = (int) u32(dll, off + 16);
            s.rawPtr = (int) u32(dll, off + 20);
            s.characteristics = (int) u32(dll, off + 36);
            secs.add(s);

            if (impOff == -1) {
                if (importDirRva >= s.virtualAddress && importDirRva < s.virtualAddress + s.virtualSize) {
                    impOff = (int) (s.rawPtr + (importDirRva - s.virtualAddress));
                }
            }
        }

        if (withImports) {
            System.out.println("Imports:");
            while (true) {
                long origFirstThunk = u32(dll, impOff);
                long timeDateStamp = u32(dll, impOff + 4);
                long forwarderChain = u32(dll, impOff + 8);
                long nameRVA = u32(dll, impOff + 12);
                long firstThunk = u32(dll, impOff + 16);
                long iatRVA = firstThunk;

                if (origFirstThunk == 0 && nameRVA == 0 && firstThunk == 0) break;

                Section sec = findSectionByRva(secs, nameRVA);
                String dllName = readAsciiString(dll, rvaToFileOff(nameRVA, sec));
                Dll d = new Dll();
                d.sec = sec;
                d.name = dllName;
                sec.dlls.add(d);
                System.out.println("  From DLL: " + dllName);

                long thunkRVA = origFirstThunk != 0 ? origFirstThunk : firstThunk;
                int thunkOff = rvaToFileOff(thunkRVA, sec);

                int idx = 0;
                while (true) {
                    long entry = (u32(dll, thunkOff) & 0xFFFFFFFFL);
                    if (entry == 0) break;

                    if ((entry & 0x80000000L) == 0) {
                        int hintNameRVA = (int) entry;
                        int hnOff = rvaToFileOff(hintNameRVA, sec);
                        long thisIatRVA = (iatRVA & 0xFFFFFFFFL) + (long)idx * 4;
                        String funcName = readAsciiString(dll, hnOff + 2);
                        Import im = new Import();
                        im.functionName = funcName;
                        im.offset = (int)thisIatRVA;
                        d.imports.add(im);
                        System.out.printf("    %-40s @ IAT_RVA 0x%X%n", funcName, (int)thisIatRVA);
                    } else {
                        System.out.println("    Ordinal: " + (entry & 0xFFFF));
                    }

                    thunkOff += 4;
                    idx++;
                }

                impOff += 20;
            }
        }

        Set<Section> usedSections = new HashSet<>();
        Map<Section, List<Relocation>> relocations = new HashMap<>();

        if (relocDirRva != 0 && relocDirSz != 0) {
            int reloOff = rvaToFileOff(secs, relocDirRva);
            int end = reloOff + (int) relocDirSz;
            int p = reloOff;
            while (p + 8 <= end) { // IMAGE_BASE_RELOCATION
                long pageRva = u32(dll, p);
                long sizeOfBlock = u32(dll, p + 4);
                if (sizeOfBlock < 8) break;
                int entries = (int) ((sizeOfBlock - 8) / 2);
                int entOff = p + 8;
                for (int i = 0; i < entries; i++) {
                    int e = u16(dll, entOff + i * 2);
                    int type = (e >>> 12) & 0xF;
                    int ofs = e & 0x0FFF;
                    if (type == IMAGE_REL_BASED_HIGHLOW) {
                        long rva = (pageRva + ofs) & 0xFFFFFFFFL;

                        Section s = findSectionByRva(secs, rva);
                        int loc = rvaToFileOff(rva, s);
                        int orig = (int) u32(dll, loc);

                        Section addressedSection = findSectionByRva(secs, orig - imageBase);
                        if (addressedSection == null) {
                            // wtf
                            continue;
                        }
                        usedSections.add(addressedSection);
                        relocations.computeIfAbsent(s, k -> new ArrayList<>()).add(
                                new Relocation(
                                        s,
                                        (int) rva - s.virtualAddress,
                                        addressedSection,
                                        orig - imageBase - addressedSection.virtualAddress
                                )
                        );
                    }
                }
                p += (int) sizeOfBlock;
            }
        }

        List<Section> sections = new ArrayList<>();
        for (Section s : secs) {
            if (usedSections.contains(s) || s.isCode()) {
                sections.add(s);
            }
        }

        int relocationsCount = 0;
        for (Section s : sections) {
            relocationsCount += relocations.getOrDefault(s, List.of()).size();
        }

        Map<Section, Integer> sectionOffsets = new HashMap<>();
        int currentOffset = 0;
        for (Section s : sections) {
            sectionOffsets.put(s, currentOffset);
            currentOffset += s.rawPtr == 0 ? s.virtualSize : Math.min(s.rawSize, s.virtualSize);
        }

        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            if (withEntry) {
                out.writeInt(LE(relocationsCount));
                for (Section s : sections) {
                    for (Relocation r : relocations.getOrDefault(s, List.of())) {
                        u32(
                                dll,
                                r.sectionToPatch.rawPtr + r.offsetToPatch,
                                sectionOffsets.get(r.addressedSection) + r.offsetFromAddressedSection
                        );
                        out.writeInt(LE(sectionOffsets.get(r.sectionToPatch) + r.offsetToPatch));
                    }
                }
            }


            int entryOffset = -1;
            String prevName = null;
            if (withEntry) {
                int eoff = rvaToFileOff(secs, exportDirRva);
                long NumberOfNames = u32(dll, eoff + 24);
                long AddressOfFunctions = u32(dll, eoff + 28);
                long AddressOfNames = u32(dll, eoff + 32);
                long AddressOfOrdinals = u32(dll, eoff + 36);

                for (int i = 0; i < (int) NumberOfNames; i++) {
                    long nameRva = u32(dll, rvaToFileOff(secs, AddressOfNames + i * 4L));
                    String name = readZ(dll, rvaToFileOff(secs, nameRva));
                    int ord = u16(dll, rvaToFileOff(secs, AddressOfOrdinals + i * 2L));
                    int funcRva = (int) u32(dll, rvaToFileOff(secs, AddressOfFunctions + ord * 4L));
                    if (name.contains(entryName)) {
                        if (entryOffset != -1) {
                            throw new IllegalStateException("Several entry functions found: " + name + "/" + prevName);
                        }
                        prevName = name;
                        Section s = findSectionByRva(secs, funcRva);
                        entryOffset = funcRva - s.virtualAddress + sectionOffsets.get(s);
                    }
                }

                if (entryOffset == -1) {
                    throw new IllegalStateException("No entry function found");
                }
            }

            if (withEntry) {
                out.writeInt(LE(entryOffset));
            }
            for (Section s : sections) {
                if (s.rawPtr != 0) {
                    out.write(dll, s.rawPtr, Math.min(s.rawSize, s.virtualSize));
                } else {
                    out.write(new byte[s.virtualSize]);
                }
            }

            /*if (withImports) {
                LinkedHashMap<String, Integer> offsets = new LinkedHashMap<>();
                List<Dll> dlls = new ArrayList<>();
                int stringsOffset = 0;
                for (Section s : sections) {
                    for (Dll d : s.dlls) {
                        dlls.add(d);
                        if (offsets.putIfAbsent(d.name, stringsOffset) == null) {
                            stringsOffset += (d.name.length() + 2);
                        }
                        for (Import im : d.imports) {
                            if (offsets.putIfAbsent(im.functionName, stringsOffset) == null) {
                                stringsOffset += (im.functionName.length() + 2);
                            }
                        }
                    }
                }

                out.writeInt(LE(stringsOffset));
                for (String s : offsets.keySet()) {
                    out.writeShort(LE(s.length()) >>> 16);
                    out.write(s.getBytes());
                }

                out.writeInt(LE(dlls.size()));
                for (Dll d : dlls) {
                    out.writeInt(LE(offsets.get(d.name)));
                    out.writeInt(LE(d.imports.size()));
                    for (Import im : d.imports) {
                        out.writeInt(LE(offsets.get(im.functionName)));
                        Section s = d.sec;
                        int off = im.offset - s.virtualAddress + sectionOffsets.get(s);
                        out.writeInt(LE(off));
                    }
                }
            }*/

            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

private static String readAsciiString(byte[] buf, int off) {
    StringBuilder sb = new StringBuilder();
    for (int i = off; i < buf.length; i++) {
        byte b = buf[i];
        if (b == 0) break;
        sb.append((char) b);
    }
    return sb.toString();
}

    static int LE(int i) {
        return Integer.reverseBytes(i);
    }
}