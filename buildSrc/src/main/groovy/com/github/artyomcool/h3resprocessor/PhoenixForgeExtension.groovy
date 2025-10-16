package com.github.artyomcool.h3resprocessor

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory

import javax.inject.Inject

class PhoenixForgeExtension {
    final DirectoryProperty externalDir
    final RegularFileProperty map
    final RegularFileProperty inputDll
    final RegularFileProperty inputLoaderDll
    final RegularFileProperty outputFile
    final RegularFileProperty exeFile
    final DirectoryProperty raw;
    final DirectoryProperty resGen;

    @Inject
    PhoenixForgeExtension(Project project, ObjectFactory objects) {
        this.externalDir = objects.directoryProperty()
        this.map = objects.fileProperty()
        this.inputDll = objects.fileProperty()
        this.inputLoaderDll = objects.fileProperty()
        this.outputFile = objects.fileProperty()
        this.exeFile = objects.fileProperty()
        this.raw = objects.directoryProperty()
                .convention(project.getLayout().getProjectDirectory().dir("src/main/raw"));
        this.resGen = objects.directoryProperty()
                .convention(project.getLayout().getBuildDirectory().dir("generated/resources"));
    }
}
