package com.dhj.actinium.resourcepack.hd;

import com.dhj.actinium.resourcepack.ResourcePackIndex;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiConsumer;

/** Resolves MCPatcher mipmap switches for PNG resources and the shared block atlas. */
record HdMipmapRules(Map<ResourceLocation, Boolean> overrides) {
    private static final ResourceLocation LEGACY_FILE = new ResourceLocation("mcpatcher/mipmap.properties");
    private static final ResourceLocation OPTIFINE_FILE = new ResourceLocation("optifine/mipmap.properties");
    private static final ResourceLocation LEGACY_TERRAIN = new ResourceLocation("terrain.png");
    private static final Set<String> UNFILTERED_VANILLA = Set.of("colormap", "environment", "font", "gui", "map", "misc");
    private static final Set<String> UNFILTERED_PACK = Set.of("colormap", "cit", "dial", "font", "lightmap", "sky");
    private static final Set<String> UNFILTERED_LEGACY = Set.of("achievement", "environment", "font", "gui", "misc", "terrain", "title");

    static HdMipmapRules load(ResourcePackIndex index, Logger logger) {
        ResourceLocation file;
        if (index.exists(OPTIFINE_FILE) && index.priority(OPTIFINE_FILE) >= index.priority(LEGACY_FILE)) {
            file = OPTIFINE_FILE;
        } else if (index.exists(LEGACY_FILE)) {
            file = LEGACY_FILE;
        } else {
            return null;
        }
        try {
            return parse(index.readProperties(file), (key, value) ->
                logger.warn("Invalid mipmap rule {}={} in {} from {}", key, value, file, index.packName(file)));
        } catch (IOException | IllegalArgumentException e) {
            logger.warn("Cannot read {} from {}", file, index.packName(file), e);
            return null;
        }
    }

    static HdMipmapRules parse(Properties properties, BiConsumer<String, String> invalidRule) {
        var overrides = new HashMap<ResourceLocation, Boolean>();
        for (String key : properties.stringPropertyNames().stream().sorted().toList()) {
            String path = key.trim();
            if (!path.endsWith(".png")) continue;
            String value = properties.getProperty(key).trim().toLowerCase(Locale.ROOT);
            if (!value.equals("true") && !value.equals("false")) {
                invalidRule.accept(key, value);
                continue;
            }
            overrides.put(new ResourceLocation(path.replaceFirst("^/+|(?<=:)/+", "")), Boolean.valueOf(value));
        }
        return new HdMipmapRules(Map.copyOf(overrides));
    }

    boolean atlasEnabled() {
        return overrides.getOrDefault(TextureMap.LOCATION_BLOCKS_TEXTURE, overrides.getOrDefault(LEGACY_TERRAIN, true));
    }

    boolean textureEnabled(ResourceLocation texture) {
        Boolean override = overrides.get(texture);
        if (override != null) return override;
        String path = texture.getPath();
        if (!path.endsWith(".png") || path.contains("item") || path.startsWith("%") || path.startsWith("##")) return false;
        return !matchesDirectory(path, "textures/", UNFILTERED_VANILLA)
            && !matchesDirectory(path, "mcpatcher/", UNFILTERED_PACK)
            && !matchesDirectory(path, "optifine/", UNFILTERED_PACK)
            && !matchesDirectory(path, "", UNFILTERED_LEGACY);
    }

    private static boolean matchesDirectory(String path, String prefix, Set<String> directories) {
        if (!path.startsWith(prefix)) return false;
        int end = path.indexOf('/', prefix.length());
        return end >= 0 && directories.contains(path.substring(prefix.length(), end));
    }

    /** Counts the complete reductions allowed by both image dimensions and the mipmap setting. */
    static int imageLevels(int width, int height, int limit) {
        return Math.min(Integer.numberOfTrailingZeros(width | height), limit);
    }
}
