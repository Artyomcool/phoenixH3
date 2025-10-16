package com.github.artyomcool.h3resprocessor;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;

public abstract class ResourcePackTask extends DefaultTask {
    private final DirectoryProperty fromDir = getProject().getObjects().directoryProperty();
    private final DirectoryProperty toDir = getProject().getObjects().directoryProperty();

    @InputDirectory
    public DirectoryProperty getFromDir() {
        return fromDir;
    }

    @OutputDirectory
    public DirectoryProperty getToDir() {
        return toDir;
    }

    @TaskAction
    public void run() throws IOException {
        File fromFile = fromDir.get().getAsFile();
        File toFile = toDir.get().getAsFile();
        if (!toFile.exists() && !toFile.mkdirs()) {
            throw new IOException("Can't make dirs " + toFile);
        }
        new ResourcePacker(fromFile.toPath(), toFile.toPath()).process();
    }
}
