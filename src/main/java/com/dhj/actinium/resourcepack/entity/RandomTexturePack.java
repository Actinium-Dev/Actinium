package com.dhj.actinium.resourcepack.entity;

import com.dhj.actinium.resourcepack.ResourcePackIndex;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

final class RandomTexturePack {
    private static final Logger LOGGER = LogManager.getLogger("Actinium/RandomEntities");
    private static final String LEGACY = "mcpatcher/mob/";
    private static final String MODERN = "optifine/random/";
    private static final List<String> LEGACY_OVERLAYS = List.of("cow/mooshroom_overlay", "snowman_overlay");
    private static final List<String> LAYERS = List.of("_tame", "_angry", "_collar", "_armor", "_eyes", "_exploding", "_shooting",
        "_fur", "_invulnerable", "_overlay");

    private record Source(String prefix, ResourceLocation texture) {}

    private record Variants(Map<Integer, ResourceLocation> textures, RandomTextureRules rules, ResourceLocation parent,
                            int priority) {}

    private final Map<ResourceLocation, Variants> materials;
    private final boolean usesCareer;

    private RandomTexturePack(Map<ResourceLocation, Variants> materials) {
        this.materials = Map.copyOf(materials);
        this.usesCareer = materials.values().stream().anyMatch(variant -> variant.rules.usesCareer());
    }

    boolean contains(ResourceLocation texture) { return materials.containsKey(texture); }
    boolean usesCareer() { return usesCareer; }

    ResourceLocation select(ResourceLocation texture, RandomTextureRules.Subject subject) {
        Variants variants = materials.get(texture);
        if (variants == null) return texture;
        return variants.textures.getOrDefault(variants.rules.select(subject), texture);
    }

    static RandomTexturePack load(ResourcePackIndex index) {
        Map<Source, TreeMap<Integer, ResourceLocation>> sources = new HashMap<>();
        Map<Source, ResourceLocation> properties = new HashMap<>();
        for (ResourceLocation id : index.find(location -> prefix(location.getPath()) != null)) {
            String path = id.getPath();
            String prefix = prefix(path);
            String relative = path.substring(prefix.length());
            boolean overlay = prefix.equals(LEGACY) && LEGACY_OVERLAYS.stream().anyMatch(relative::startsWith);
            String basePath = (overlay ? LEGACY : prefix.equals(LEGACY) ? "textures/entity/" : "textures/") + relative;
            if (path.endsWith(".properties")) {
                Source source = new Source(prefix, new ResourceLocation(id.getNamespace(), stripExtension(basePath) + ".png"));
                properties.put(source, id);
                if (index.exists(source.texture)) sources.computeIfAbsent(source, ignored -> new TreeMap<>());
            } else if (path.endsWith(".png")) {
                if (overlay && LEGACY_OVERLAYS.contains(stripExtension(relative))) {
                    sources.computeIfAbsent(new Source(prefix, id), ignored -> new TreeMap<>()).put(1, id);
                }
                addVariant(index, sources, prefix, id, stripExtension(basePath));
            }
        }
        Map<ResourceLocation, Variants> result = new HashMap<>();
        for (String prefix : List.of(LEGACY, MODERN)) {
            sources.forEach((source, textures) -> {
                if (!source.prefix.equals(prefix)) return;
                int priority = textures.values().stream().mapToInt(index::priority).max().orElse(-1);
                ResourceLocation file = properties.get(source);
                ResourceLocation parent = parent(source.texture);
                if (file != null) priority = Math.max(priority, index.priority(file));
                if (result.containsKey(source.texture) && result.get(source.texture).priority > priority) return;
                textures.put(1, source.texture);
                Properties config;
                try {
                    config = file == null ? null : index.readProperties(file);
                } catch (IOException | IllegalArgumentException e) {
                    LOGGER.warn("Cannot read {} in {}", file, index.packName(file), e);
                    return;
                }
                ResourceLocation ruleFile = file;
                var rules = RandomTextureRules.parse(config, textures.navigableKeySet(),
                    message -> LOGGER.warn("{} in {}: {}", ruleFile, index.packName(ruleFile), message));
                result.put(source.texture, new Variants(Map.copyOf(textures), rules, config == null ? parent : null, priority));
            });
        }
        Map<ResourceLocation, Variants> independent = Map.copyOf(result);
        result.replaceAll((texture, variants) -> variants.parent != null && independent.containsKey(variants.parent)
            ? new Variants(variants.textures, independent.get(variants.parent).rules, null, variants.priority) : variants);
        return new RandomTexturePack(result);
    }

    private static void addVariant(ResourcePackIndex index, Map<Source, TreeMap<Integer, ResourceLocation>> sources,
                                   String prefix, ResourceLocation id, String basePath) {
        int end = basePath.length();
        int start = end;
        while (start > 0 && Character.isDigit(basePath.charAt(start - 1))) start--;
        if (start == end) return;
        for (int split = start; split < end; split++) {
            int stemEnd = basePath.charAt(split - 1) == '.' ? split - 1 : split;
            ResourceLocation original = new ResourceLocation(id.getNamespace(), basePath.substring(0, stemEnd) + ".png");
            if (!index.exists(original)) continue;
            try {
                int number = Integer.parseInt(basePath.substring(split));
                if (number > 1) sources.computeIfAbsent(new Source(prefix, original), ignored -> new TreeMap<>()).put(number, id);
                return;
            } catch (NumberFormatException e) {
                LOGGER.warn("Invalid texture variant {} in {}", id, index.packName(id));
                return;
            }
        }
    }

    private static ResourceLocation parent(ResourceLocation texture) {
        String stem = stripExtension(texture.getPath());
        if (stem.startsWith(LEGACY)) {
            String relative = stem.substring(LEGACY.length());
            if (LEGACY_OVERLAYS.contains(relative)) {
                return new ResourceLocation(texture.getNamespace(), "textures/entity/" + relative.substring(0, relative.length() - 8) + ".png");
            }
        }
        for (String suffix : LAYERS) {
            if (stem.endsWith(suffix)) return new ResourceLocation(texture.getNamespace(), stem.substring(0, stem.length() - suffix.length()) + ".png");
        }
        return null;
    }

    private static String prefix(String path) {
        if (path.startsWith(LEGACY)) return LEGACY;
        if (path.startsWith(MODERN + "entity/") || path.startsWith(MODERN + "painting/")) return MODERN;
        return null;
    }

    private static String stripExtension(String path) { return path.substring(0, path.lastIndexOf('.')); }
}
