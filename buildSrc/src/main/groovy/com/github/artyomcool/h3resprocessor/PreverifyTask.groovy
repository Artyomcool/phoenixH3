package com.github.artyomcool.h3resprocessor

import com.github.artyomcool.classflow.BaseClassFlow
import com.github.artyomcool.classflow.ByteArrayInOut
import com.github.artyomcool.classflow.StackMapFromStackMapTableTransformer
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*

import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@CacheableTask
abstract class PreverifyTask extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract RegularFileProperty getInputJar()

    @OutputFile
    abstract RegularFileProperty getOutputJar()

    @TaskAction
    void run() {
        def outFile = outputJar.asFile.get()
        outFile.parentFile.mkdirs()
        try (def zin = new ZipInputStream(inputJar.asFile.get().newInputStream());
             def zout = new ZipOutputStream(outFile.newOutputStream())) {
            ZipEntry e
            while ((e = zin.nextEntry) != null) {

                if (!e.isDirectory()) {
                    byte[] data = zin.readAllBytes()
                    if (e.name.endsWith('.class')) {
                        try {
                            data = transformClass(data)
                        } catch (RuntimeException re) {
                            throw new RuntimeException("${e.name}", re)
                        }
                    }
                    def entry = new ZipEntry(e.name)
                    entry.method = 0
                    entry.size = data.length
                    entry.crc = crc32(data)
                    zout.putNextEntry(entry)
                    zout.write(data)
                    zout.closeEntry()
                }

            }
        }
    }

    private static byte[] transformClass(byte[] classBytes) {
        def out = new ByteArrayInOut(classBytes)
        new StackMapFromStackMapTableTransformer().parse(out)
        return Arrays.copyOf(out.out, out.wpos())
    }

    private static long crc32(byte[] data) {
        CRC32 crc = new CRC32()
        crc.update(data)
        return crc.value
    }

}
