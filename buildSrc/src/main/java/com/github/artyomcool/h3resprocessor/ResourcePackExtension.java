package com.github.artyomcool.h3resprocessor;

import javax.inject.Inject;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.file.DirectoryProperty;

public abstract class ResourcePackExtension {
    private final DirectoryProperty sourceDir;
    private final DirectoryProperty outputDir;

    @Inject
    public ResourcePackExtension(Project project, ObjectFactory objects) {
        this.sourceDir = objects.directoryProperty()
                .convention(project.getLayout().getProjectDirectory().dir("src/main/raw"));
        this.outputDir = objects.directoryProperty()
                .convention(project.getLayout().getBuildDirectory().dir("generated/resources"));
    }

    public DirectoryProperty getSourceDir() { return sourceDir; }
    public DirectoryProperty getOutputDir() { return outputDir; }
}
