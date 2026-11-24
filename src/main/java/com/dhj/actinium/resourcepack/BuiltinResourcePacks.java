package com.dhj.actinium.resourcepack;

import com.dhj.actinium.runtime.ActiniumRuntime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Makes optional bundled packs visible to the vanilla pack selector. */
public final class BuiltinResourcePacks {
    private static final String[] PACKS = {"Actinium-Connected-Textures.zip", "Actinium-Glass-Pane-Culling-Fix.zip"};

    private BuiltinResourcePacks() {
    }

    public static void install(Path directory) {
        for (String name : PACKS) {
            Path destination = directory.resolve(name);
            if (Files.exists(destination)) {
                continue;
            }
            try (var stream = BuiltinResourcePacks.class.getResourceAsStream("/actinium-builtin-packs/" + name)) {
                if (stream == null) {
                    throw new IOException("Missing embedded pack " + name);
                }
                Files.createDirectories(directory);
                Path staged = Files.createTempFile(directory, "actinium-pack-", ".tmp");
                try {
                    try (var output = Files.newOutputStream(staged)) {
                        stream.transferTo(output);
                    }
                    Files.move(staged, destination);
                } finally {
                    Files.deleteIfExists(staged);
                }
            } catch (IOException ex) {
                ActiniumRuntime.logger().warn("Could not install optional resource pack {}", destination, ex);
            }
        }
    }
}
