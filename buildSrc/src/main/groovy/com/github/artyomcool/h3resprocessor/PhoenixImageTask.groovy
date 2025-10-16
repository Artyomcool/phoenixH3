package com.github.artyomcool.h3resprocessor

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import java.nio.file.Files

abstract class PhoenixImageTask extends DefaultTask {

    @InputFile
    File inputExe

    @OutputFile
    File outputExe

    @Input
    String sectionName = ".java_00"

    @InputFile
    File payloadFile

    @TaskAction
    void run() {
        byte[] data = Files.readAllBytes(payloadFile.toPath())
        byte[] file = Files.readAllBytes(inputExe.toPath())

        final int DOS_E_LFANEW = 0x3C
        final int PE_SIGNATURE = 0x00004550
        final int SIZEOF_COFF_HEADER = 20, SIZEOF_SECTION_HEADER = 40
        final int COFF_NumberOfSections = 2, COFF_SizeOfOptionalHdr = 16

        final int OPT_Magic = 0, OPT_SectionAlignment = 32, OPT_FileAlignment = 36, OPT_SizeOfImage = 56
        final int OPT32_Magic = 0x10B, OPT64_Magic = 0x20B

        final int SH_Name=0, SH_VirtualSize=8, SH_VirtualAddress=12, SH_SizeOfRawData=16,
                  SH_PointerToRawData=20, SH_PointerToRelocs=24, SH_PointerToLinenums=28,
                  SH_NumberOfRelocs=32, SH_NumberOfLinenums=34, SH_Characteristics=36

        final int IMAGE_SCN_CNT_INITIALIZED_DATA = 0x00000040
        final int IMAGE_SCN_MEM_READ = 0x40000000
        final int CHAR = IMAGE_SCN_CNT_INITIALIZED_DATA | IMAGE_SCN_MEM_READ

        int peOff = readU32(file, DOS_E_LFANEW)
        if (peOff <= 0 || peOff + 4 > file.length) throw new GradleException("Bad e_lfanew")
        if (readU32(file, peOff) != PE_SIGNATURE) throw new GradleException("Not a PE")

        int coff = peOff + 4
        int nSections = readU16(file, coff + COFF_NumberOfSections)
        int sizeOpt   = readU16(file, coff + COFF_SizeOfOptionalHdr)

        int opt = coff + SIZEOF_COFF_HEADER
        int magic = readU16(file, opt + OPT_Magic)
        if (magic != OPT32_Magic && magic != OPT64_Magic) throw new GradleException("Unknown OPT magic")

        int sectionAlignment = readU32(file, opt + OPT_SectionAlignment)
        int fileAlignment    = readU32(file, opt + OPT_FileAlignment)

        int sectTable = opt + sizeOpt
        int newShOff  = sectTable + nSections * SIZEOF_SECTION_HEADER

        int minPtrRaw = Integer.MAX_VALUE
        int lastVAEnd = 0
        int lastRawEnd = 0

        for (int i = 0; i < nSections; i++) {
            int sh = sectTable + i * SIZEOF_SECTION_HEADER
            int va      = readU32(file, sh + SH_VirtualAddress)
            int vsize   = readU32(file, sh + SH_VirtualSize)
            int rawPtr  = readU32(file, sh + SH_PointerToRawData)
            int rawSize = readU32(file, sh + SH_SizeOfRawData)

            if (rawPtr != 0 && rawPtr < minPtrRaw) minPtrRaw = rawPtr

            int vaEnd  = alignUp(va + vsize, sectionAlignment)
            if (vaEnd  > lastVAEnd)  lastVAEnd  = vaEnd

            int rawEnd = alignUp(rawPtr + rawSize, fileAlignment)
            if (rawEnd > lastRawEnd) lastRawEnd = rawEnd
        }
        if (minPtrRaw == Integer.MAX_VALUE) minPtrRaw = file.length

        if (newShOff + SIZEOF_SECTION_HEADER > minPtrRaw)
            throw new GradleException("No space for a new section header (no slack)")

        int newVA       = alignUp(lastVAEnd, sectionAlignment)
        int newRawPtr   = alignUp(Math.max(lastRawEnd, file.length), fileAlignment)
        int newVirtSize = data.length
        int newRawSize  = alignUp(data.length, fileAlignment)

        int newLen = Math.max(file.length, newRawPtr + newRawSize)
        byte[] out = Arrays.copyOf(file, newLen)

        writeName(out, newShOff + SH_Name, fitName(sectionName))
        writeU32(out, newShOff + SH_VirtualSize,      newVirtSize)
        writeU32(out, newShOff + SH_VirtualAddress,   newVA)
        writeU32(out, newShOff + SH_SizeOfRawData,    newRawSize)
        writeU32(out, newShOff + SH_PointerToRawData, newRawPtr)
        writeU32(out, newShOff + SH_PointerToRelocs,  0)
        writeU32(out, newShOff + SH_PointerToLinenums,0)
        writeU16(out, newShOff + SH_NumberOfRelocs,   0)
        writeU16(out, newShOff + SH_NumberOfLinenums, 0)
        writeU32(out, newShOff + SH_Characteristics,  CHAR)

        writeU16(out, coff + COFF_NumberOfSections, nSections + 1)
        writeU32(out, opt + OPT_SizeOfImage, alignUp(newVA + newVirtSize, sectionAlignment))

        System.arraycopy(data, 0, out, newRawPtr, data.length)

        Files.write(outputExe.toPath(), out)
    }

    // ---- helpers ----
    static int alignUp(int v, int a) { int r = v % a; return r == 0 ? v : v + (a - r) }

    static void writeName(byte[] b, int off, byte[] name8) {
        for (int i = 0; i < 8; i++) b[off + i] = (byte) ((i < name8.length) ? name8[i] : 0)
    }
    static byte[] fitName(String name) {
        byte[] n = name.getBytes('US-ASCII')
        byte[] out = new byte[8]
        System.arraycopy(n, 0, out, 0, Math.min(8, n.length))
        return out
    }

    static int readU16(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8)
    }
    static int readU32(byte[] b, int off) {
        return (b[off] & 0xFF)
                | ((b[off + 1] & 0xFF) << 8)
                | ((b[off + 2] & 0xFF) << 16)
                | ((b[off + 3] & 0xFF) << 24)
    }
    static void writeU16(byte[] b, int off, int v) {
        b[off] = (byte)(v)
        b[off + 1] = (byte)(v >>> 8)
    }
    static void writeU32(byte[] b, int off, int v) {
        b[off] = (byte)(v)
        b[off + 1] = (byte)(v >>> 8)
        b[off + 2] = (byte)(v >>> 16)
        b[off + 3] = (byte)(v >>> 24)
    }
}