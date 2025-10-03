package com.github.artyomcool.h3resprocessor;

import com.github.artyomcool.h3resprocessor.h3common.Def;
import com.github.artyomcool.h3resprocessor.h3common.DefInfo;
import com.github.artyomcool.h3resprocessor.h3common.DefType;
import com.github.artyomcool.h3resprocessor.h3common.Png;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class ResourcePacker {

    private final Path inputRoot;
    private final Path outputRoot;

    public ResourcePacker(Path inputRoot, Path outputRoot) {
        this.inputRoot = inputRoot;
        this.outputRoot = outputRoot;
    }

    public void process() {
        processDefs(inputRoot.resolve("def"));
        processSimpleDefs(inputRoot.resolve("simple_def"));
    }

    private void processDefs(Path root) {
        forEachPath(root, Files::isDirectory, defRoot -> {
            Properties cfg = loadProperties(defRoot);

            Def def = new Def();
            def.type = DefType.of(cfg.getProperty("type"));
            def.fullWidth = Integer.parseInt(cfg.getProperty("width"));
            def.fullHeight = Integer.parseInt(cfg.getProperty("height"));
            forEachPath(defRoot, Files::isDirectory, groupRoot -> {
                DefInfo.Group group = new DefInfo.Group(def);
                group.name = groupRoot.getFileName().toString();
                group.groupIndex = def.groups.size();
                def.groups.add(group);

                forEachPath(
                        groupRoot,
                        p -> p.getFileName().toString().toLowerCase().endsWith(".png"),
                        pngFile -> group.frames.add(Png.load(ByteBuffer.wrap(Files.readAllBytes(pngFile))))
                );
            });

            byte[] packed = pack(def, defRoot.getFileName().toString());
            Files.write(outputRoot.resolve(defRoot.getFileName()), packed);
        });
    }

    private void processSimpleDefs(Path root) {
        forEachPath(
                root,
                p -> p.getFileName().toString().toLowerCase().endsWith(".png"),
                pngFile -> {
                    DefInfo.Frame frame = Png.load(ByteBuffer.wrap(Files.readAllBytes(pngFile)));

                    String fileName = pngFile.getFileName().toString();
                    int dollar = fileName.indexOf('$');
                    String defName = fileName.substring(0, dollar);
                    String type = fileName.substring(dollar + 1, fileName.lastIndexOf('.'));
                    System.out.println("Def: " + defName + "/" + type);

                    frame.name = defName.replace('.', '!');

                    Def def = new Def();
                    def.type = DefType.of(type);
                    def.fullWidth = frame.fullWidth;
                    def.fullHeight = frame.fullHeight;

                    DefInfo.Group group = new DefInfo.Group(def);
                    group.name = "common";
                    group.groupIndex = def.groups.size();
                    def.groups.add(group);
                    group.frames.add(frame);
                    byte[] packed = pack(def, defName);
                    Files.write(outputRoot.resolve(defName), packed);
                }
        );
    }

    private static byte[] pack(Def def, String defName) {
        return def.fixupCompression().ensurePalette().replaceTransparentColors().pack(defName);
    }

    private static @NotNull Properties loadProperties(Path defRoot) throws IOException {
        Properties cfg = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(defRoot.resolve("cfg.properties"))) {
            cfg.load(reader);
        }
        return cfg;
    }

    private static void forEachPath(Path root, Predicate<Path> filter, IORethrowConsumer<Path> consumer) {
        try (Stream<Path> list = Files.list(root).filter(filter)) {
            list.forEach(path -> {
                try {
                    consumer.accept(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private interface IORethrowConsumer<E> {
        void accept(E e) throws IOException;
    }

}
