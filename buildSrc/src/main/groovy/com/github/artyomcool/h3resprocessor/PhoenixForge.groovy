package com.github.artyomcool.h3resprocessor

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Jar

class PhoenixForge implements Plugin<Project> {
    @Override
    void apply(Project project) {
        def ext = project.extensions.create('phoenixForge', PhoenixForgeExtension, project.objects)

        def forge = project.tasks.register('phoenixForge', PhoenixForgeTask) { task ->
            task.group = 'build'
            task.description = 'Pack Dll and JAR into H3 map'

            def jarTask = project.tasks.named('jar', Jar)
            task.dependsOn(jarTask)
            task.jarFile.set(jarTask.flatMap { it.archiveFile })
            task.inputA.set(ext.inputA)
            task.inputB.set(ext.inputB)
            task.inputDll.set(ext.inputDll)
            task.inputLoaderDll.set(ext.inputLoaderDll)
            task.outputFile.set(ext.outputFile)
        }

        project.tasks.register('phoenixRun', PhoenixRunTask) { task ->
            task.group = 'build'
            task.description = 'Runs H3 after phoenixForge.'
            task.dependsOn(forge)
            task.executable.set(ext.exeFile)
        }
    }
}
