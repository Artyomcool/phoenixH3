package com.github.artyomcool.h3resprocessor

import com.github.artyomcool.h3resprocessor.h3common.HMap
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
    abstract RegularFileProperty getMap()

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

        byte[] game = new GZIPInputStream(map.get().asFile.newInputStream()).bytes
        byte[] dll = inputDll.get().asFile.bytes
        byte[] loaderDll = inputLoaderDll.get().asFile.bytes
        byte[] jar = jarFile.get().asFile.bytes

        def stream = new HMap.HeroStream(game)
        def header = HMap.MapHeader.read(stream)

        ByteArrayOutputStream specialGame = new ByteArrayOutputStream()
        specialGame.write(stream.in, 0, stream.p)
        specialGame.write(new byte[18]) // artefacts
        specialGame.write(new byte[9]) // spells
        specialGame.write(new byte[4]) // skills
        specialGame.write(new byte[4])  // rumors (count = 0)
        specialGame.write(new byte[156])  // hero options (enabled = 0)
        specialGame.write(new byte[7 * header.size * header.size * (header.twoLevel + 1)]) // cells
        specialGame.write(new byte[]{1,0,0,0})   // templates_count = 1
        specialGame.write(new byte[]{"AVXPrsn0.def\0".length(),0,0,0})
        specialGame.write("AVXPrsn0.def\0".getBytes())
        specialGame.write(new byte[6])  // passability
        specialGame.write(new byte[6])  // actions
        specialGame.write(new byte[2])  // landscape
        specialGame.write(new byte[2])  // land_edit_groups
        specialGame.write(new byte[] { 0x3E, 0, 0, 0 }) // prison
        specialGame.write(new byte[4])  // object number
        specialGame.write(new byte[1])  // object group
        specialGame.write(new byte[1])  // overlay
        specialGame.write(new byte[16])  // junk

        specialGame.write(new byte[]{1,0,0,0})  // objects_count = 1
        specialGame.write(new byte[12]) // coords, template, junk
        specialGame.write(new byte[4])  // hero id
        specialGame.write(new byte[1])  // color
        specialGame.write(new byte[1])  // hero
        specialGame.write(new byte[]{1})  // has name

        def ba = new ByteArrayOutputStream(1024)
        def r = new GZIPOutputStream(ba)
        r.write(SavePatcher.createNewMap(specialGame.toByteArray(), game, dll, loaderDll, jar))
        r.close()

        out.bytes = ba.toByteArray()
    }
}
