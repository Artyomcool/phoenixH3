package com.github.artyomcool.h3resprocessor

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar

class PhoenixForgePlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        def ext = project.extensions.create('phoenixForge', PhoenixForgeExtension, project.objects)
        def img = project.extensions.create('phoenixImage', PhoenixImageExtension, project.objects)

        if (project == project.rootProject) {
            def preverify = project.tasks.register('preverify', PreverifyTask) { task ->
                def jar = project.tasks.named('jar', Jar)
                task.inputJar.set(jar.flatMap { it.archiveFile })
                task.outputJar.convention(project.layout.file(
                        inputJar.map { rf ->
                            def inFile = rf.asFile
                            def name = inFile.name
                            def base = name.endsWith(".jar") ? name[0..-5] : name
                            new File(inFile.parentFile, "${base}-preverified.jar")
                        }
                ))
                task.dependsOn(jar)
            }

            def forge = project.tasks.register('phoenixForge', PhoenixForgeTask) { task ->
                task.group = 'build'
                task.description = 'Pack Dll and JAR into H3 map'

                task.dependsOn(preverify)
                task.jarFile.set(preverify.flatMap { it.outputJar })
                task.map.set(ext.map)
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

            TaskProvider<ResourcePackTask> prepare = project.getTasks()
                    .register("prepareResourcePack", ResourcePackTask, t -> {
                        t.setGroup("build");
                        t.setDescription("Builds resource pack");
                        t.getFromDir().set(ext.raw);
                        t.getToDir().set(ext.resGen);
                    })

            SourceSetContainer sourceSets = (SourceSetContainer) project.getExtensions().getByName("sourceSets");
            sourceSets.named("main", ss -> ss.getResources().srcDir(ext.resGen));
            project.tasks.named("processResources", task -> task.dependsOn(prepare));
        }

        // fixme
        if (project != project.rootProject) {
            def imgPrepareTask =
                    project.tasks.register("phoenixImagePrepare", PhoenixImagePrepareTask, t -> {
                        t.setGroup("build")
                        t.setDescription("Prepare patch data for phoenix image")
                        t.inputDll.set(img.inputDll)
                        t.inputLoaderDll.set(img.inputLoaderDll)

                        def jarTask = project.rootProject.tasks.named('preverify', PreverifyTask)
                        t.dependsOn(jarTask)
                        t.jarFile.set(jarTask.flatMap { it.outputJar })

                        t.outputFile.set(img.resGen.file("patch.dat"))
                    })

            project.tasks.register("phoenixImage", PhoenixImageTask, t -> {
                t.setGroup("build")
                t.setDescription("Build phoenix image")
                t.inputExe = img.inputExe.get().asFile

                def jarTask = project.tasks.named('jar', Jar)
                t.dependsOn(jarTask)
                t.payloadFile = jarTask.flatMap { it.archiveFile }.get().asFile
                t.outputExe = img.outputExe.get().asFile
            })

            SourceSetContainer sourceSets = (SourceSetContainer) project.getExtensions().getByName("sourceSets")
            sourceSets.named("main", ss -> ss.getResources().srcDir(img.resGen))
            project.tasks.named("processResources", task -> task.dependsOn(imgPrepareTask))
        }
    }
}
