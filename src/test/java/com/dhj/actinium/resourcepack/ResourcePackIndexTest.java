package com.dhj.actinium.resourcepack;

import net.minecraft.client.resources.AbstractResourcePack;
import net.minecraft.client.resources.FileResourcePack;
import net.minecraft.client.resources.FolderResourcePack;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import net.minecraft.client.resources.data.MetadataSerializer;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ResourcePackIndexTest {
    @TempDir Path directory;

    @Test
    void opensTheWinningPackAndRescansAfterRemoval() throws Exception {
        Path directoryPack = directory.resolve("local");
        var local = directoryPack.resolve("assets/minecraft/optifine/ctm");
        Files.createDirectories(local);
        Files.writeString(local.resolve("shared.properties"), "method=horizontal\n");
        Files.writeString(local.resolve("only_local.properties"), "method=repeat\n");

        Path serverPack = directory.resolve("server-pack-cache-file");
        try (var zip = new ZipOutputStream(Files.newOutputStream(serverPack))) {
            entry(zip, "assets/minecraft/optifine/ctm/shared.properties", "method=random\n");
            entry(zip, "assets/minecraft/optifine/ctm/only_server.properties", "method=overlay\n");
        }

        var manager = new SimpleReloadableResourceManager(new MetadataSerializer());
        var folder = new FolderResourcePack(directoryPack.toFile());
        var shared = new ResourceLocation("optifine/ctm/shared.properties");
        var onlyServer = new ResourceLocation("optifine/ctm/only_server.properties");
        var onlyLocal = new ResourceLocation("optifine/ctm/only_local.properties");
        try (var server = new FileResourcePack(serverPack.toFile())) {
            var packs = List.of(folder, server);
            packs.forEach(manager::reloadResourcePack);
            var combined = new ResourcePackIndex(manager, List.copyOf(packs), List.of());
            assertEquals(List.of(onlyServer, shared, onlyLocal), combined.find(id -> id.getPath().endsWith(".properties")));
            assertEquals(server.getPackName(), combined.packName(shared));
            assertEquals("random", combined.readProperties(shared).getProperty("method"));
        }

        var nextManager = new SimpleReloadableResourceManager(new MetadataSerializer());
        nextManager.reloadResourcePack(folder);
        var afterRemoval = new ResourcePackIndex(nextManager, List.of(folder), List.of());
        assertFalse(afterRemoval.exists(onlyServer));
        assertEquals("horizontal", afterRemoval.readProperties(shared).getProperty("method"));
    }

    @Test
    void filelessPackCanOverrideAnIndexedRule() throws Exception {
        var rule = new ResourceLocation("optifine/ctm/shared.properties");
        var target = directory.resolve("assets/minecraft").resolve(rule.getPath());
        Files.createDirectories(target.getParent());
        Files.writeString(target, "method=horizontal\n");

        var folder = new FolderResourcePack(directory.toFile());
        var generated = new GeneratedPack("assets/minecraft/" + rule.getPath());
        var manager = new SimpleReloadableResourceManager(new MetadataSerializer());
        manager.reloadResourcePack(folder);
        manager.reloadResourcePack(generated);
        var index = new ResourcePackIndex(manager, List.of(folder, generated), List.of());

        assertEquals(List.of(rule), index.find(id -> id.getPath().endsWith(".properties")));
        assertEquals(generated.getPackName(), index.packName(rule));
        assertEquals("random", index.readProperties(rule).getProperty("method"));
    }

    @Test
    void discoversFontPropertiesBesideTheVanillaTexturePath() throws Exception {
        var font = new ResourceLocation("textures/font/ascii.properties");
        var target = directory.resolve("assets/minecraft").resolve(font.getPath());
        Files.createDirectories(target.getParent());
        Files.writeString(target, "width.65=5.5\n");

        var pack = new FolderResourcePack(directory.toFile());
        var manager = new SimpleReloadableResourceManager(new MetadataSerializer());
        manager.reloadResourcePack(pack);
        var index = new ResourcePackIndex(manager, List.of(pack), List.of());

        assertEquals(List.of(font), index.find(id -> id.getPath().startsWith("textures/font/")));
        assertEquals("5.5", index.readProperties(font).getProperty("width.65"));
    }

    private static void entry(ZipOutputStream zip, String path, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content.getBytes(StandardCharsets.US_ASCII));
        zip.closeEntry();
    }

    private static final class GeneratedPack extends AbstractResourcePack {
        private final String resource;

        private GeneratedPack(String resource) {
            super(null);
            this.resource = resource;
        }

        @Override
        public Set<String> getResourceDomains() {
            return Set.of("minecraft");
        }

        @Override
        public String getPackName() {
            return "generated";
        }

        @Override
        protected boolean hasResourceName(String name) {
            return resource.equals(name);
        }

        @Override
        protected InputStream getInputStreamByName(String name) throws FileNotFoundException {
            if (!hasResourceName(name)) {
                throw new FileNotFoundException(name);
            }
            return new ByteArrayInputStream("method=random\n".getBytes(StandardCharsets.US_ASCII));
        }
    }
}
