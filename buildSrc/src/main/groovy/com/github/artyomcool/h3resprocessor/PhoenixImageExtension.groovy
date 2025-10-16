package com.github.artyomcool.h3resprocessor

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory

import javax.inject.Inject

class PhoenixImageExtension {
    final RegularFileProperty inputDll
    final RegularFileProperty inputLoaderDll
    final RegularFileProperty inputExe
    final RegularFileProperty outputExe
    final DirectoryProperty resGen

    @Inject
    PhoenixImageExtension(Project project, ObjectFactory objects) {
        this.inputDll = objects.fileProperty()
        this.inputLoaderDll = objects.fileProperty()
        this.inputExe = objects.fileProperty()
        this.outputExe = objects.fileProperty()
                .convention(project.getLayout().getBuildDirectory().file("exe/phoenix.exe"));
        this.resGen = objects.directoryProperty()
                .convention(project.getLayout().getBuildDirectory().dir("generated/resources"));
    }
}
