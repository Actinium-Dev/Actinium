package com.dhj.actinium.resourcepack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.AbstractResourcePack;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.LegacyV2Adapter;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FilterInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/** Enumerates resources without changing the resource manager's pack order or lookup policy. */
public final class ResourcePackIndex {
    private static final Logger LOGGER = LogManager.getLogger("Actinium Resource Packs");

    private final IResourceManager manager;
    private final List<IResourcePack> packs;
    private final Map<ResourceLocation, Path> external;
    private final Map<ResourceLocation, Integer> priorities;
    private final List<ResourceLocation> locations;

    static ResourcePackIndex load(IResourceManager manager, List<IResourcePack> packs) {
        var external = new ArrayList<Path>(2);
        var client = Minecraft.getMinecraft();
        boolean resourceLoader = Loader.isModLoaded("resourceloader");
        if (resourceLoader || Loader.isModLoaded("base")) {
            external.add(client.gameDir.toPath().resolve("resources"));
        }
        if (resourceLoader) {
            external.add(client.gameDir.toPath().resolve("oresources"));
        }
        return new ResourcePackIndex(manager, packs, external);
    }

    ResourcePackIndex(IResourceManager manager, List<IResourcePack> packs, List<Path> externalRoots) {
        this.manager = manager;
        this.packs = List.copyOf(packs);
        var priorities = new HashMap<ResourceLocation, Integer>();
        var external = new HashMap<ResourceLocation, Path>();
        var paths = new HashSet<Path>();
        for (int i = 0; i < packs.size(); i++) {
            IResourcePack source = packs.get(i);
            IResourcePack unwrapped = source instanceof LegacyV2Adapter adapter ? adapter.getUnadaptedPack() : source;
            if (!(unwrapped instanceof AbstractResourcePack filePack)) {
                continue;
            }
            int priority = i;
            var file = filePack.getResourcePackFile();
            if (file == null) {
                continue;
            }
            Path path = file.toPath().toAbsolutePath().normalize();
            paths.add(path);
            scanPack(source, path).forEach(id -> {
                if (source.resourceExists(id)) {
                    priorities.put(id, priority);
                }
            });
        }
        for (Path externalRoot : externalRoots) {
            Path path = externalRoot.toAbsolutePath().normalize();
            if (Files.isDirectory(path) && !paths.contains(path)) {
                scanDirectory(path, (id, file) -> {
                    if (id.getPath().endsWith(".png.mcmeta") || id.getPath().startsWith("ctm_logic/")
                            || id.getPath().equals("ctm.json")) {
                        external.put(id, file);
                        priorities.putIfAbsent(id, -1);
                    }
                });
            }
        }
        this.priorities = Map.copyOf(priorities);
        this.external = Map.copyOf(external);
        this.locations = priorities.keySet().stream()
                .sorted(Comparator.comparingInt(this::priority).reversed().thenComparing(ResourceLocation::toString))
                .toList();
    }

    /** The active Forge resource manager, including metadata serializers and bundled assets. */
    public IResourceManager manager() {
        return manager;
    }

    /** Finds unique paths ordered by descending active pack priority and ascending resource name. */
    public List<ResourceLocation> find(Predicate<ResourceLocation> include) {
        return locations.stream().filter(include).toList();
    }

    /** Opens the highest-priority copy of a resource. The caller owns the returned stream. */
    public InputStream open(ResourceLocation id) throws IOException {
        try {
            var resource = manager.getResource(id);
            return new FilterInputStream(resource.getInputStream()) {
                @Override
                public void close() throws IOException {
                    resource.close();
                }
            };
        } catch (FileNotFoundException ex) {
            Path file = external.get(id);
            if (file == null) {
                throw ex;
            }
            return Files.newInputStream(file);
        }
    }

    /** Reads the Java properties format used by MCPatcher and OptiFine. */
    public Properties readProperties(ResourceLocation id) throws IOException {
        try (InputStream input = open(id)) {
            var properties = new Properties();
            properties.load(input);
            return properties;
        }
    }

    /** Reports whether the active resource stack provides the requested file. */
    public boolean exists(ResourceLocation id) {
        return priority(id) >= 0 || external.containsKey(id);
    }

    /** Identifies the pack that supplies this resource for diagnostics. */
    public String packName(ResourceLocation id) {
        int priority = priority(id);
        if (priority >= 0) {
            return packs.get(priority).getPackName();
        }
        return external.containsKey(id) ? external.get(id).toString() : "unknown pack";
    }

    /** Returns the pack's Forge order, with the last loaded pack having the highest value. */
    public int priority(ResourceLocation id) {
        Integer indexed = priorities.get(id);
        int minimum = indexed == null ? -1 : indexed;
        for (int i = packs.size() - 1; i > minimum; i--) {
            if (packs.get(i).resourceExists(id)) {
                return i;
            }
        }
        return minimum;
    }

    /** Reads optional format settings from the Actinium client configuration. */
    public boolean enabled(String option) {
        return ResourcePackOptions.enabled(option);
    }

    private static Set<ResourceLocation> scanPack(IResourcePack source, Path path) {
        var ids = new HashSet<ResourceLocation>();
        if (Files.isDirectory(path)) {
            scanDirectory(path.resolve("assets"), (id, file) -> ids.add(id));
        } else if (Files.isRegularFile(path)) {
            try (var zip = new ZipFile(path.toFile())) {
                zip.stream().filter(entry -> !entry.isDirectory()).forEach(entry -> {
                    String name = entry.getName();
                    if (name.startsWith("assets/")) {
                        var id = location(name.substring("assets/".length()));
                        if (id != null) {
                            ids.add(id);
                        }
                    }
                });
            } catch (IOException ex) {
                LOGGER.warn("Failed to enumerate resource pack {} ({})", source.getPackName(), path, ex);
            }
        }
        return ids;
    }

    private static void scanDirectory(Path root, IndexedFileConsumer consumer) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile).forEach(file -> {
                var id = location(root.relativize(file).toString().replace('\\', '/'));
                if (id != null) {
                    consumer.accept(id, file);
                }
            });
        } catch (IOException | UncheckedIOException ex) {
            LOGGER.warn("Failed to enumerate resource directory {}", root, ex);
        }
    }

    private static ResourceLocation location(String path) {
        int separator = path.indexOf('/');
        if (separator <= 0 || separator == path.length() - 1) {
            return null;
        }
        String file = path.substring(separator + 1);
        if (file.startsWith("optifine/") || file.startsWith("mcpatcher/") || file.startsWith("ctm_logic/")
                || file.equals("ctm.json") || file.equals("color.properties") || file.endsWith(".png.mcmeta")
                || file.startsWith("textures/font/") && (file.endsWith(".png") || file.endsWith(".properties"))) {
            return new ResourceLocation(path.substring(0, separator), file);
        }
        return null;
    }

    @FunctionalInterface
    private interface IndexedFileConsumer {
        void accept(ResourceLocation id, Path file);
    }
}
