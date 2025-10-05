package com.github.artyomcool.h3resprocessor

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*

import java.util.zip.GZIPOutputStream

@CacheableTask
abstract class PhoenixForgeTask extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    abstract RegularFileProperty getJarFile()

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract RegularFileProperty getInputA()

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract RegularFileProperty getInputB()

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract RegularFileProperty getInputDll()

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract RegularFileProperty getInputLoaderDll()

    @OutputFile
    abstract RegularFileProperty getOutputFile()

    @TaskAction
    void run() {
        def out = outputFile.get().asFile
        out.parentFile.mkdirs()

        byte[] specialGame = new GZIPInputStream(inputA.get().asFile.newInputStream()).bytes
        byte[] game = new GZIPInputStream(inputB.get().asFile.newInputStream()).bytes
        byte[] dll = inputDll.get().asFile.bytes
        byte[] loaderDll = inputLoaderDll.get().asFile.bytes
        byte[] jar = jarFile.get().asFile.bytes

        def ba = new ByteArrayOutputStream(1024)
        def r = new GZIPOutputStream(ba)
        r.write(SavePatcher.createNewMap(specialGame, game, dll, loaderDll, jar))
        r.close()

        out.bytes = ba.toByteArray()
    }
}
