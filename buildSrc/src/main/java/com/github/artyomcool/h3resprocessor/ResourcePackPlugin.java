package com.github.artyomcool.h3resprocessor;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;

public class ResourcePackPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        ResourcePackExtension ext = project.getExtensions()
                .create("resourcePack", ResourcePackExtension.class, project, project.getObjects());

        Provider<Directory> fromDir = ext.getSourceDir();
        Provider<Directory> toDir = ext.getOutputDir();

        TaskProvider<ResourcePackTask> prepare = project.getTasks().register("prepareResourcePack", ResourcePackTask.class, t -> {
            t.setGroup("build");
            t.setDescription("Builds resource pack");
            t.getFromDir().set(fromDir);
            t.getToDir().set(toDir);
        });

        SourceSetContainer sourceSets = (SourceSetContainer) project.getExtensions().getByName("sourceSets");
        sourceSets.named("main", ss -> ss.getResources().srcDir(toDir));
        project.getTasks().named("processResources", task -> task.dependsOn(prepare));
    }
}
