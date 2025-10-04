package com.github.artyomcool.h3resprocessor

import java.nio.file.Files
import java.nio.file.Path

import static PeTool.LE
import static com.github.icedland.iced.x86.asm.AsmRegisters.*

class SavePatcher {

    static final int oTextBuffer = 0x697428
    static final int malloc = 0x617492
    static final int afterReadHeroNameInHeader = 0x4c56c7
    static final int mapSignature = 0x0BAD0BBE

    static byte[] createPatch(byte[] specialGameSave) {
        byte[] bytes = specialGameSave
        byte[] c = "<CODE_PATCH>".bytes

        byte[] trampoline = Assembler.assemble {
            def ca = delegate
            def call = { ca.'call'(it) }
            def staticBuffer = oTextBuffer + 128

            def start = createLabel()
        label start
            mov ebp, mem_ptr(esp, -4)

            // correcting SEH
            mov eax, mem_ptr(ebp, 12)
            db([0x83, 0xe8, 0x0c] as byte[]) //sub eax, 12
            xor ecx, ecx
            mov dword_ptr(ecx).fs(), eax

            mov esp, staticBuffer << 4
            shr esp, 4
            mov edi, (int) 0xfffff2e4
            add edi, ebp
            mov edi, mem_ptr(edi)   // GzFile
            mov esi, mem_ptr(edi)   // vtable

            push eax
            mov eax, esp            // let's read size directly in stack
            push 4
            push eax
            mov ecx, edi
            call dword_ptr(esi, 4)

            push dword_ptr(esp)    // duplicate size for GzFile.read

            mov ecx, malloc << 4
            shr ecx, 4
            call ecx
            mov dword_ptr(esp), eax // place for GzFile.read

            mov ecx, edi
            mov ebx, esi
            mov esi, eax
            call dword_ptr(ebx, 4)

            jmp esi

            nop()
            nop()
            jmp start
        }

        trampoline.eachByte {
            if (!it) {
                println("Trampoline:")
                for (def b in trampoline) {
                    printf("%02x  ", b & 0xff)
                }
                println()

                throw new IllegalArgumentException("Can't have 0 in first trampoline")
            }
        }
        byte[] phoenixMapSaveLoader = Assembler.assemble {
            def ca = delegate
            def call = { ca.'call'(it) }

            def afterPrologue = createLabel()
            // keep debugger happy
            nop()
            nop()
            nop()
            nop()
            nop()
            nop()
            nop()
            nop()

            // esi = start address
            // edi = GzFile
            jmp afterPrologue

            // functions
            def fixer = createLabel()
            def fixerJmpOut = createLabel()
            def fixerStdRetry = createLabel()
        label fixer
            // ebx GzFile
            // ebp -0x48 = std::string - hero name
            // [edx + 4] = c_str
            // [edx + 8] = size
            // ...
            // if (size <= 12) { jump out }
            cmp dword_ptr(ebp, -0x40), 12
            jbe fixerJmpOut

            // if (*(int*)str != mapSignature) { jump out }
            mov eax, dword_ptr(ebp, -0x44)
            cmp dword_ptr(eax), mapSignature
            jne fixerJmpOut

            // skip zeroes
            push 9
            push dword_ptr(ebp, -0x44)    // string with just read hacked hero name
            mov edx, mem_ptr(ebx)   // GzFile vtable
            mov ecx, ebx    // GzFile this
            call dword_ptr(edx, 4)

            // skip patch
            // read patch size
            push 4
            push dword_ptr(ebp, -0x44)    // string with just read hacked hero name
            mov edx, mem_ptr(ebx)   // GzFile vtable
            mov ecx, ebx    // GzFile this
            call dword_ptr(edx, 4)

            // read (skip) patch itself
            mov eax, dword_ptr(ebp, -0x44)
            push dword_ptr(eax) // size of patch
            push dword_ptr(ebp, -0x44)    // string with just read hacked hero name
            mov edx, mem_ptr(ebx)   // GzFile vtable
            mov ecx, ebx    // GzFile this
            call dword_ptr(edx, 4)

            // read load interceptor
            // read size
            push 4
            push dword_ptr(ebp, -0x44)    // string with just read hacked hero name
            mov edx, mem_ptr(ebx)   // GzFile vtable
            mov ecx, ebx    // GzFile this
            call dword_ptr(edx, 4)

            // read interceptor itself
            mov eax, dword_ptr(ebp, -0x44)
            push dword_ptr(eax) // size of interceptor
            push dword_ptr(ebp, -0x44)    // string with just read hacked hero name
            mov edx, mem_ptr(ebx)   // GzFile vtable
            mov ecx, ebx    // GzFile this
            call dword_ptr(edx, 4)

            mov eax, dword_ptr(ebp, -0x44)
            pushad()
            mov ecx, ebx
            mov edx, esp
            call eax    // call interceptor
            test eax, eax
            je fixerStdRetry

            // destroy string to free the memory
            mov eax, dword_ptr(ebp, -0x44)  // str memory
            dec eax
            push eax
            mov eax, 0x0060b0f0 // destroy
            call eax
            add esp, 4

            popad() // we expect it to be corrected by interceptor
            // todo recover esp
            ret() // we expect it to be corrected by interceptor

        label fixerStdRetry
            popad() // it might be changed by interceptor

            // destroy string to free the memory
            mov eax, dword_ptr(ebp, -0x44)  // str memory
            dec eax
            push eax
            mov eax, 0x0060b0f0 // destroy
            call eax
            add esp, 4

            mov ecx, dword_ptr(ebp, -0x20)  // header this
            mov eax, 0x45A480   // re-create header
            call eax
            mov ecx, dword_ptr(ebp, -0x20)  // header this
            mov eax, mem_ptr(ebp, -0xc)
            mov dword_ptr(0).fs(), eax
            mov eax, ebx
            pop edi
            pop esi
            pop ebx
            mov esp, ebp
            pop ebp
            mov mem_ptr(esp, 4), eax        // GzFile this
            mov eax, 0x4c52f0                     // restart
            jmp eax


        label fixerJmpOut
            // recover commands
            mov edx, mem_ptr(ebp, -0x44)
            cmp edx, edi
            mov edi, 0x0063a608
            mov ecx, 0x4c56d5
            jmp ecx   // jump out

        label afterPrologue

            // patching map header loader
            mov eax, esi
            add eax, { +fixer }
            mov byte_ptr(afterReadHeroNameInHeader), 0xBA   // MOV EDX
            mov dword_ptr(afterReadHeroNameInHeader + 1), eax    // offset (fixer)
            mov ax, 0xE2FF  // LE(jmp edx)
            mov word_ptr(afterReadHeroNameInHeader + 5), ax

            mov esp, ebp
            add esp, 12
            mov ecx, mem_ptr(esp, 0x196A30-0x197720)   // (dialog *this)
            mov ebx, ecx    // restore
            mov eax, ecx
            add eax, 0x1030
            mov mem_ptr(esp, 8), eax
            mov eax, 0x582e11   // re-start
            jmp eax    // jump out
            // verify ret address, it should be 57f99c
        }

        def patchedHeader = new Replacer(bytes).tap {
            replace2BytesPrefixed c, makePatch(size(-0xC, 7), 0xaa).tap {
                patchLE(it, 0, mapSignature)
                patch(it, -trampoline.length - 4, trampoline)
                patchLE(it, -4, 0x0060B016)
            }
        }.zero(9)
                .write4BytesPrefixed(phoenixMapSaveLoader)
                .write4BytesPrefixed(createLoader())
                .finish(0)

        return patchedHeader
    }

    static byte[] createNewMap(byte[] specialNewMap, byte[] realNewMap, byte[] dll, byte[] jar) {
        byte[] c = "<CODE_PATCH>".bytes

        byte[] trampoline = Assembler.assemble {
            def ca = delegate
            def call = { ca.'call'(it) }
            def staticBuffer = oTextBuffer + 128

            nop()
            nop()
            nop()
            nop()
            nop()
            nop()
            def start = createLabel()
        label start
            mov ebp, mem_ptr(esp, -4)

            mov esp, staticBuffer
            mov edi, dword_ptr(ebp, 0x196610 - 0x1965B4) // GzFile
            mov esi, mem_ptr(edi)   // vtable

            push eax
            mov eax, esp            // let's read size directly in stack
            push 4
            push eax
            mov ecx, edi
            call dword_ptr(esi, 4)

            push dword_ptr(esp)    // duplicate size for GzFile.read

            mov ecx, malloc
            call ecx
            mov dword_ptr(esp), eax // place for GzFile.read

            mov ecx, edi
            mov ebx, eax
            call dword_ptr(esi, 4)

            mov esp, ebp
//            // correcting SEH
//            mov eax, mem_ptr(ebp, 12)
//            db([0x83, 0xe8, 0x0c] as byte[]) //sub eax, 12
//            xor ecx, ecx
//            mov dword_ptr(ecx).fs(), eax

            jmp ebx
            nop()
            nop()
            nop()
            nop()
            jmp start
        }

        byte[] secondTrampoline = Assembler.assemble {
            def ca = delegate
            def call = { ca.'call'(it) }
            nop()
            nop()
            nop()
            nop()

            push eax
            mov eax, esp            // let's read size directly in stack
            push 4
            push eax
            mov ecx, edi
            call dword_ptr(esi, 4)

            push dword_ptr(esp)    // duplicate size for GzFile.read

            mov ecx, malloc
            call ecx
            mov dword_ptr(esp), eax // place for GzFile.read

            mov ecx, edi
            mov ebx, eax
            call dword_ptr(esi, 4)

            mov ecx, edi
            mov edx, esp
            not edx
            call ebx    // call interceptor

            mov ebp, esp
            add ebp, 0x196608 - 0x1965B4

            def loop = createLabel()
        label loop
            mov eax, dword_ptr(ebp, 4)
            cmp eax, 0x4C295D   // start of loading new game return address
            mov ebp, dword_ptr(ebp)

            jne loop

            mov eax, ebp
            sub eax, 0xC
            mov dword_ptr(0).fs(), eax
            mov ebx, dword_ptr(eax, -0x7C)

            mov ecx, ebx
            add ecx, 0x1F86C
            mov esi, 0x45A480   // re-create header
            call esi

            mov ecx, ebx
            mov esi, 0x4BEE60   // clear game manager's map
            call esi

            mov eax, ebp
            sub eax, 0xC

            mov esi, dword_ptr(eax, -0x80)
            mov edi, dword_ptr(eax, -0x84)
            mov esp, eax
            mov ecx, ebx
            mov eax, 0x4C2128
            jmp eax

        }

        def stream = Thread.currentThread().contextClassLoader.getResourceAsStream("special.GM1")
        if (stream == null) {
            throw new IllegalStateException("Can't load special.GM1")
        }
        byte[] bytes = new GZIPInputStream(stream).bytes
        byte[] patchedHeader = createPatch(bytes)

        new Replacer(specialNewMap).tap {
            replace4BytesPrefixed c, new byte[0xcc + 0x10].tap {
                Arrays.fill(it, (byte)0xAA)
                patch(it, -trampoline.length - 4-0x10, trampoline)
                patchLE(it, -4-0x10, 0x0060B016)
            }
        }
                .write4BytesPrefixed(secondTrampoline)
                .write4BytesPrefixed(createLoader())
                .write(createDllPatch(patchedHeader, dll, jar))
                .write(realNewMap).finish(0)
    }

    static byte[] createDllPatch(byte[] patchedHeader, byte[] dll, byte[] jar) {
        byte[] endMarker = new byte[0]
        List<byte[]> data = [
                PeTool.pack(dll, "SodSuperPatchEntryPoint", false),
                jar,
                patchedHeader,
                endMarker
        ]

        ByteArrayOutputStream bb = new ByteArrayOutputStream(4096)
        DataOutputStream out = new DataOutputStream(bb)
        out.writeInt(LE(data.size()))
        int offset = data.size() * 4 + 4
        for (def d in data) {
            out.writeInt(LE(offset))
            offset += d.length
        }
        for (def d in data) {
            out.write(d)
        }
        out.flush()

        byte[] dllAndFiles = bb.toByteArray()
        bb.reset()
        out.writeInt(LE(dllAndFiles.length))
        out.write(dllAndFiles)
        out.write()
        out.flush()

        return bb.toByteArray()
    }

    static byte[] createLoader() {
        return PeTool.pack(Files.readAllBytes(Path.of("C:\\git\\PhoenixH3\\src\\main\\cpp\\dll_loader.dll")), null, false)
    }

    static class Replacer {
        final byte[] original
        int pos = 0
        ByteArrayOutputStream out = new ByteArrayOutputStream()

        Replacer(byte[] bytes) {
            original = bytes
        }

        def firstIndexOf(byte[] bytes, byte[] c, int i) {
            a:
            for (; i < bytes.length; i++) {
                for (int j = 0; j < c.length; j++) {
                    if (bytes[i + j] != c[j]) {
                        continue a
                    }
                }
                return i
            }
            return -1
        }

        Replacer replace2BytesPrefixed(byte[] scanline, byte[] data) {
            int found = firstIndexOf(original, scanline, pos)
            if (found == -1) {
                throw new IllegalStateException()
            }

            int prefixPos = found - 2
            int size = (original[prefixPos] & 0xff) | ((original[prefixPos + 1] & 0xff) << 8)

            out.write(original, pos, prefixPos - pos)
            out.write(data.length & 0xff)
            out.write(data.length >>> 8 & 0xff)
            out.write(data)

            pos = found + size
            return this
        }

        Replacer replace4BytesPrefixed(byte[] scanline, byte[] data) {
            int found = firstIndexOf(original, scanline, pos)
            if (found == -1) {
                throw new IllegalStateException()
            }

            int prefixPos = found - 4
            int size = (original[prefixPos] & 0xff) |
                    ((original[prefixPos + 1] & 0xff) << 8) |
                    ((original[prefixPos + 2] & 0xff) << 16) |
                    ((original[prefixPos + 3] & 0xff) << 24)

            out.write(original, pos, prefixPos - pos)
            out.write(data.length & 0xff)
            out.write((data.length >>> 8) & 0xff)
            out.write((data.length >>> 16) & 0xff)
            out.write((data.length >>> 24) & 0xff)
            out.write(data)

            pos = found + size
            return this
        }

        Replacer write4BytesPrefixed(byte[] data) {
            out.write(data.length & 0xff)
            out.write((data.length >>> 8) & 0xff)
            out.write((data.length >>> 16) & 0xff)
            out.write((data.length) >>> 24 & 0xff)
            out.write(data)
            return this
        }

        Replacer write(byte[] data) {
            out.write(data)
            return this
        }

        Replacer writeSentinels(int W) {
            out.write((W) >>> 24 & 0xff)
            out.write((W >>> 16) & 0xff)
            out.write((W >>> 8) & 0xff)
            out.write(W & 0xff)
            return this
        }

        Replacer zero(int amount) {
            amount.times {
                out.write(0)
            }
            return this
        }

        Replacer skip(int amount) {
            out.write(original, pos, amount)
            pos += amount
            return this
        }

        byte[] finish(int limit) {
            out.write(original, pos, Math.min(limit, original.length - pos))
            return out.toByteArray()
        }
    }


    static def makePatch(int size, int fill) {
        byte[] patch = new byte[size]
        if (fill <= 0xff) {
            Arrays.fill(patch, (byte) fill)
            return patch
        }

        if (fill == 0x100) {
            byte v = 1
            for (int p = 0; p < size; p++) {
                patch[p] = v++
                if (v == (byte) 0) v++
            }
            return patch
        }

        if (fill == 0x1000) {
            byte v1 = 1
            byte v2 = 1
            for (int p = size - 1; p >= 0;) {
                patch[p--] = v1++
                if (p < 0) break
                patch[p--] = v2
                if (v1 == (byte) 0) {
                    v1++
                    v2++
                }
            }
        }
        return patch
    }

    static def size(int diffFromRetPosEnd, int index) {
        return 0x4F0 + diffFromRetPosEnd - index * 0x44
    }

    static def patchLE(byte[] bytes, int pos, int value) {
        if (pos < 0) {
            pos += bytes.length
        }
        for (int i = pos; i < pos + 4; i++) {
            bytes[i] = (byte) (value & 0xff)
            value >>>= 8;
        }
    }

    static def patchBE(byte[] bytes, int pos, int value) {
        if (pos < 0) {
            pos += bytes.length
        }
        for (int i = pos; i < pos + 4; i++) {
            bytes[i] = (byte) (value >>> 24)
            value <<= 8;
        }
    }

    static def patch(byte[] bytes, int pos, byte[] p) {
        if (pos < 0) {
            pos += bytes.length
        }
        for (def b in p) {
            bytes[pos++] = b
        }
    }
}
