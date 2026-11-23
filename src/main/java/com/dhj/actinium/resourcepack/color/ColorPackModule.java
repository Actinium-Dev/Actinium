package com.dhj.actinium.resourcepack.color;

import com.dhj.actinium.resourcepack.ResourcePackIndex;
import com.dhj.actinium.resourcepack.ResourcePackReload;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Loads color resources without changing any game registries or texture objects. */
final class ColorPackModule implements ResourcePackReload.Module<ColorSnapshot, ColorSnapshot> {
    private static final Logger LOGGER = LogManager.getLogger("Actinium/CustomColors");
    private static final Pattern LIGHTMAP = Pattern.compile("(?:optifine|mcpatcher)/lightmap/world(-?\\d+)\\.png");
    private static final Pattern BLOCK_MAP = Pattern.compile("(?:optifine|mcpatcher)/colormap/(?:blocks|custom)/.+\\.properties");
    private static final String[] PALETTES = {"grass", "foliage", "swampgrass", "swampfoliage", "pine", "birch",
            "water", "underwater", "underlava", "fog0", "sky0", "redstone", "stem", "pumpkinstem", "melonstem",
            "lavadrop", "myceliumparticle", "xporb", "durability"};

    @Override
    public String option() {
        return "customColors";
    }

    @Override
    public ColorSnapshot prepare(ResourcePackIndex index) {
        Map<String, Integer> colors = new HashMap<>();
        Map<String, ColorPalette> palettes = new HashMap<>();
        Map<Block, List<BlockColorRule>> rules = new HashMap<>();
        Map<Integer, LightmapPalette.Weather> lightmaps = new HashMap<>();
        var config = choose(index, "color.properties");
        var properties = read(index, config);
        String defaultFormat = properties.getProperty("palette.format", "vanilla");
        float xpPeriod = 628;
        for (String key : new TreeSet<>(properties.stringPropertyNames())) {
            if (key.startsWith("palette.block.")) continue;
            try {
                if (key.equals("xporb.time")) {
                    float value = Float.parseFloat(properties.getProperty(key));
                    if (!Float.isFinite(value) || value <= 0) throw new IllegalArgumentException("Expected a positive period");
                    xpPeriod = value;
                } else if (isColor(key)) {
                    colors.put(key, parseRgb(properties.getProperty(key)));
                } else if (key.equals("clouds")) {
                    colors.put(key, switch (properties.getProperty(key).trim()) {
                        case "none", "off" -> 0;
                        case "fast" -> 1;
                        case "fancy" -> 2;
                        default -> throw new IllegalArgumentException("Expected fast, fancy, or none");
                    });
                }
            } catch (IllegalArgumentException e) {
                warn(index, config, key, e);
            }
        }

        for (String name : PALETTES) {
            var file = choose(index, "colormap/" + name + ".png");
            if (file == null && name.equals("water")) file = choose(index, "colormap/watercolorx.png");
            if (file == null) continue;
            var definition = replaceExtension(file, ".properties");
            try {
                palettes.put(name, palette(index, definition, read(index, definition), defaultFormat));
            } catch (IOException | IllegalArgumentException e) {
                warn(index, file, name, e);
            }
        }

        var files = new ArrayList<ResourceLocation>();
        index.find(id -> BLOCK_MAP.matcher(id.getPath()).matches()).forEach(files::add);
        files.sort(Comparator.comparingInt((ResourceLocation id) -> index.priority(id)).reversed()
                .thenComparing(id -> !id.getPath().startsWith("optifine/"))
                .thenComparing(ResourceLocation::toString));
        for (ResourceLocation file : files) {
            var definition = read(index, file);
            String path = file.getPath();
            String blocks = definition.getProperty("blocks", file.getNamespace() + ':' + path.substring(path.lastIndexOf('/') + 1, path.length() - 11));
            loadRules(index, file, definition, defaultFormat, blocks, rules);
        }
        for (String key : new TreeSet<>(properties.stringPropertyNames())) {
            if (!key.startsWith("palette.block.")) continue;
            var definition = new Properties();
            definition.setProperty("source", key.substring("palette.block.".length()));
            loadRules(index, config, definition, defaultFormat, properties.getProperty(key), rules);
        }

        var dimensions = new TreeSet<Integer>();
        index.find(id -> id.getNamespace().equals("minecraft") && LIGHTMAP.matcher(id.getPath()).matches()).forEach(id -> {
            var matcher = LIGHTMAP.matcher(id.getPath());
            if (matcher.matches()) {
                try {
                    dimensions.add(Integer.parseInt(matcher.group(1)));
                } catch (NumberFormatException e) {
                    warn(index, id, "invalid dimension", e);
                }
            }
        });
        for (int dimension : dimensions) {
            String path = "lightmap/world" + dimension;
            var clear = lightmap(index, choose(index, path + ".png"));
            if (clear != null) {
                lightmaps.put(dimension, new LightmapPalette.Weather(clear,
                        lightmap(index, choose(index, path + "_rain.png")),
                        lightmap(index, choose(index, path + "_thunder.png"))));
            }
        }
        return new ColorSnapshot(colors, palettes, rules, lightmaps, xpPeriod);
    }

    @Override
    public ColorSnapshot finish(ColorSnapshot prepared, ResourcePackIndex index, TextureMap atlas) {
        return prepared;
    }

    private static boolean isColor(String key) {
        return key.equals("lilypad") || key.matches("(?:particle|fog|sky|potion|egg|map|sheep|armor|collar|dye|text)\\..+");
    }

    static int parseRgb(String value) {
        String rgb = value.trim();
        if (rgb.startsWith("#")) rgb = rgb.substring(1);
        if (!rgb.matches("[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Expected six hexadecimal RGB digits");
        return Integer.parseInt(rgb, 16);
    }

    private static void loadRules(ResourcePackIndex index, ResourceLocation file, Properties props, String format,
                                  String selectors, Map<Block, List<BlockColorRule>> rules) {
        ColorPalette palette;
        try {
            palette = palette(index, file, props, format);
        } catch (IOException | IllegalArgumentException e) {
            warn(index, file, "invalid block palette", e);
            return;
        }
        for (String token : selectors.trim().split("\\s+")) {
            try {
                BlockColorRule.Selector selector = BlockColorRule.parse(token, ForgeRegistries.BLOCKS::getValue);
                rules.computeIfAbsent(selector.block(), ignored -> new ArrayList<>())
                        .add(new BlockColorRule(selector.matches(), palette));
            } catch (IllegalArgumentException e) {
                warn(index, file, "invalid block selector " + token, e);
            }
        }
    }

    private static ColorPalette palette(ResourcePackIndex index, ResourceLocation definition, Properties props,
                                        String defaultFormat) throws IOException {
        var format = ColorPalette.Format.valueOf(props.getProperty("format", defaultFormat).trim().toUpperCase(Locale.ROOT));
        String color = props.getProperty("color");
        int defaultColor = color == null ? -1 : parseRgb(color);
        if (format == ColorPalette.Format.FIXED) {
            return new ColorPalette(format, null, defaultColor, 0, 0);
        }
        ResourceLocation image = source(definition, props.getProperty("source"));
        int variance = Integer.parseInt(props.getProperty("yVariance", "0").trim());
        if (variance < 0) throw new IllegalArgumentException("yVariance must be nonnegative");
        return new ColorPalette(format, image(index, image), defaultColor, variance,
                Integer.parseInt(props.getProperty("yOffset", "0").trim()));
    }

    static ResourceLocation source(ResourceLocation definition, String source) {
        if (source == null) return replaceExtension(definition, ".png");
        String path = source.trim();
        if (!path.endsWith(".png")) path += ".png";
        if (path.contains(":")) return new ResourceLocation(path);
        String file = definition.getPath();
        int slash = file.lastIndexOf('/');
        if (path.startsWith("~/")) path = file.substring(0, file.indexOf('/') + 1) + path.substring(2);
        else if (path.startsWith("/")) path = path.substring(1);
        else if (path.startsWith("assets/minecraft/")) path = path.substring("assets/minecraft/".length());
        else if (!path.startsWith("textures/") && !path.startsWith("mcpatcher/") && !path.startsWith("optifine/")) {
            path = file.substring(0, slash + 1) + (path.startsWith("./") ? path.substring(2) : path);
        }
        return new ResourceLocation(definition.getNamespace(), path);
    }

    private static ResourceLocation replaceExtension(ResourceLocation id, String extension) {
        String path = id.getPath();
        return new ResourceLocation(id.getNamespace(), path.substring(0, path.lastIndexOf('.')) + extension);
    }

    private static ResourceLocation choose(ResourcePackIndex index, String suffix) {
        var modern = new ResourceLocation("optifine/" + suffix);
        var legacy = new ResourceLocation("mcpatcher/" + suffix);
        if (!index.exists(modern)) return index.exists(legacy) ? legacy : null;
        return index.exists(legacy) && index.priority(legacy) > index.priority(modern) ? legacy : modern;
    }

    private static Properties read(ResourcePackIndex index, ResourceLocation file) {
        if (file == null || !index.exists(file)) return new Properties();
        try {
            return index.readProperties(file);
        } catch (IOException | IllegalArgumentException e) {
            warn(index, file, "invalid properties", e);
            return new Properties();
        }
    }

    private static BufferedImage image(ResourcePackIndex index, ResourceLocation id) throws IOException {
        try (var stream = index.open(id)) {
            var image = ImageIO.read(stream);
            if (image == null) throw new IOException("Invalid PNG: " + id);
            return image;
        }
    }

    private static LightmapPalette lightmap(ResourcePackIndex index, ResourceLocation id) {
        if (id == null) return null;
        try {
            return new LightmapPalette(image(index, id));
        } catch (IOException | IllegalArgumentException e) {
            warn(index, id, "invalid lightmap", e);
            return null;
        }
    }

    private static void warn(ResourcePackIndex index, ResourceLocation id, String context, Exception error) {
        LOGGER.warn("{} ({}) {}: {}", id, index.packName(id), context, error.getMessage());
    }
}
