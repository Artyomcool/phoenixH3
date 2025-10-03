package com.github.artyomcool.h3resprocessor

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory

class PhoenixForgeExtension {
    final DirectoryProperty externalDir
    final RegularFileProperty inputA
    final RegularFileProperty inputB
    final RegularFileProperty inputDll
    final RegularFileProperty outputFile
    final RegularFileProperty exeFile

    PhoenixForgeExtension(ObjectFactory objects) {
        this.externalDir = objects.directoryProperty()
        this.inputA = objects.fileProperty()
        this.inputB = objects.fileProperty()
        this.inputDll = objects.fileProperty()
        this.outputFile = objects.fileProperty()
        this.exeFile = objects.fileProperty()
    }
}
