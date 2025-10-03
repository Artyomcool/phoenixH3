package com.github.artyomcool.h3resprocessor

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*

abstract class PhoenixRunTask extends DefaultTask {

    @InputFile
    abstract RegularFileProperty getExecutable()

    @TaskAction
    void runExec() {
        def exe = executable.get().asFile
        if (!exe.exists()) {
            throw new GradleException("Executable not found: ${exe}")
        }
        def pb = new ProcessBuilder([exe.absolutePath] as List<String>)
        pb.directory(exe.parentFile)
        pb.inheritIO()
        def p = pb.start()
        try {
            int code = p.waitFor()
            if (code != 0) {
                throw new GradleException("Process exited with code ${code}")
            }
        } finally {
            p.destroyForcibly()
        }
    }
}
