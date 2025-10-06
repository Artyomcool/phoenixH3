package com.github.artyomcool.h3resprocessor

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory

class PhoenixForgeExtension {
    final DirectoryProperty externalDir
    final RegularFileProperty map
    final RegularFileProperty inputDll
    final RegularFileProperty inputLoaderDll
    final RegularFileProperty outputFile
    final RegularFileProperty exeFile

    PhoenixForgeExtension(ObjectFactory objects) {
        this.externalDir = objects.directoryProperty()
        this.map = objects.fileProperty()
        this.inputDll = objects.fileProperty()
        this.inputLoaderDll = objects.fileProperty()
        this.outputFile = objects.fileProperty()
        this.exeFile = objects.fileProperty()
    }
}
