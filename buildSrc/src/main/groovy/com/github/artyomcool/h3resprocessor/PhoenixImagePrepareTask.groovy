package com.github.artyomcool.h3resprocessor


import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*

@CacheableTask
abstract class PhoenixImagePrepareTask extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    abstract RegularFileProperty getJarFile()

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

        byte[] dll = inputDll.get().asFile.bytes
        byte[] loaderDll = inputLoaderDll.get().asFile.bytes
        byte[] jar = jarFile.get().asFile.bytes

        out.bytes = SavePatcher.createSharedPatch(dll, loaderDll, jar)
    }
}
