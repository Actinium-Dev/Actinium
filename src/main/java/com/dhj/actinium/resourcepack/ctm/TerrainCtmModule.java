package com.dhj.actinium.resourcepack.ctm;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.dhj.actinium.resourcepack.ResourcePackIndex;
import com.dhj.actinium.resourcepack.ResourcePackReload;
import com.dhj.actinium.resourcepack.ctm.api.client.CtmLoader;
import com.dhj.actinium.resourcepack.ctm.api.client.CtmLoaderRegistry;
import com.dhj.actinium.resourcepack.ctm.api.client.CtmProperties;
import com.dhj.actinium.resourcepack.ctm.api.client.QuadProcessor;
import com.dhj.actinium.resourcepack.ctm.client.CtmRuleTypes;
import com.dhj.actinium.resourcepack.ctm.client.ctm.CtmCachingPredicates;
import com.dhj.actinium.resourcepack.ctm.client.ctm.CtmCustomLogic;
import com.dhj.actinium.resourcepack.ctm.client.ctm.CtmDefinition;
import com.dhj.actinium.resourcepack.ctm.client.ctm.CtmLogicBakery;
import com.dhj.actinium.resourcepack.ctm.client.ctm.CtmLogicDefinition;
import com.dhj.actinium.resourcepack.ctm.client.ctm.CtmMcmetaParser;
import com.dhj.actinium.resourcepack.ctm.client.ctm.CtmQuadProcessor;
import com.dhj.actinium.resourcepack.ctm.client.processor.simple.CtmSpriteProvider;
import com.dhj.actinium.resourcepack.ctm.client.processor.simple.SimpleQuadProcessor;
import com.dhj.actinium.resourcepack.ctm.client.properties.OrientedConnectingCtmProperties;
import com.dhj.actinium.resourcepack.ctm.client.properties.PropertiesParsingHelper;
import com.dhj.actinium.resourcepack.ctm.client.resource.RedirectedTextureAtlasSprite;
import com.dhj.actinium.resourcepack.ctm.client.resource.ResourceRedirectHandler;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;

final class TerrainCtmModule implements ResourcePackReload.Module<TerrainCtmModule.Prepared, TerrainPackRules.Snapshot> {
    @Override
    public String option() {
        return "terrain";
    }

    @Override
    public boolean requiresAtlas() {
        return true;
    }

    @Override
    public Prepared prepare(ResourcePackIndex index) {
        List<PropertyRule<?>> rules = new ArrayList<>();
        Set<ResourceLocation> sprites = new HashSet<>();
        Map<ResourceLocation, ResourceLocation> paths = new LinkedHashMap<>();
        boolean connected = index.enabled("connectedTextures");
        for (ResourceLocation id : connected ? index.find(TerrainCtmModule::isCtmProperties) : List.<ResourceLocation>of()) {
            String path = id.getPath();
            ResourceLocation key = new ResourceLocation(id.getNamespace(), path.substring(path.indexOf('/') + 1));
            paths.merge(key, id, (left, right) -> {
                int order = Integer.compare(index.priority(left), index.priority(right));
                if (order != 0) {
                    return order > 0 ? left : right;
                }
                return left.getPath().startsWith("optifine/") ? left : right;
            });
        }
        for (ResourceLocation id : paths.values()) {
            try {
                Properties values = index.readProperties(id);
                String method = values.getProperty("method", "ctm").trim();
                boolean expanded = method.equals("compact_expanded");
                if (expanded) {
                    values.putIfAbsent("tiles", "0-4");
                }
                CtmLoader<?> loader = CtmLoaderRegistry.get().getLoader(expanded ? "ctm_compact" : method);
                if (loader == null) {
                    throw new IllegalArgumentException("Unknown method: " + method);
                }
                PropertyRule<?> rule = parseRule(loader, values, id, index, method, expanded);
                if (rule != null) {
                    rules.add(rule);
                    if (!expanded) {
                        sprites.addAll(rule.properties.getSpriteDependencies());
                    }
                }
            } catch (Exception ex) {
                warn(index, id, ex);
            }
        }
        rules.sort((left, right) -> right.properties.compareTo(left.properties));
        return new Prepared(List.copyOf(rules), Set.copyOf(sprites),
                index.enabled("ctmModTextures") ? loadMetadata(index) : List.of(), connected ? loadLayers(index) : List.of(),
                index.enabled("ctmConnectInside"), connected ? BetterGrass.prepare(index) : null);
    }

    static boolean isCtmProperties(ResourceLocation id) {
        String path = id.getPath();
        return path.endsWith(".properties") && (path.startsWith("optifine/ctm/") || path.startsWith("mcpatcher/ctm/"));
    }

    @Nullable
    private static <T extends CtmProperties> PropertyRule<T> parseRule(CtmLoader<T> loader, Properties values,
            ResourceLocation id, ResourcePackIndex index, String method, boolean expanded) throws IOException {
        T properties = loader.getPropertiesFactory().createProperties(values, id, index.packName(id),
                index.priority(id), index.manager(), method);
        if (properties == null) {
            return null;
        }
        ExpandedCompactTiles tiles = expanded
                ? ExpandedCompactTiles.read(index, id, ((OrientedConnectingCtmProperties) properties).getSpriteIds()) : null;
        return new PropertyRule<>(loader, properties, id, tiles);
    }

    @Override
    public void registerSprites(Prepared prepared, TextureMap atlas) {
        for (PropertyRule<?> rule : prepared.rules) {
            if (rule.expanded != null) {
                rule.expanded.register(atlas);
            }
        }
        if (prepared.grass != null) {
            prepared.grass.registerSprites(atlas);
        }
        for (ResourceLocation id : prepared.sprites) {
            if (id.getPath().startsWith(ResourceRedirectHandler.SPRITE_PATH_START)) {
                atlas.setTextureEntry(new RedirectedTextureAtlasSprite(id));
            } else {
                atlas.registerSprite(id);
            }
        }
        for (CtmDefinition definition : prepared.metadata) {
            definition.getSpriteDependencies().forEach(atlas::registerSprite);
        }
    }

    @Override
    public TerrainPackRules.Snapshot finish(Prepared prepared, ResourcePackIndex index, TextureMap atlas) {
        Function<ResourceLocation, TextureAtlasSprite> spriteGetter = id -> atlas.getAtlasSprite(id.toString());
        List<TerrainPackRules.Rule> rules = new ArrayList<>();
        for (PropertyRule<?> rule : prepared.rules) {
            rules.add(rule.bake(spriteGetter));
        }
        for (CtmDefinition definition : prepared.metadata) {
            if (definition.getSpriteDependencies().stream().anyMatch(id -> spriteGetter.apply(id) == atlas.getMissingSprite())) {
                CtmRuleTypes.LOGGER.warn("Missing atlas sprite for CTM metadata {}", definition.getResourceId());
                continue;
            }
            var light = definition.hasLight() ? new TerrainPackRules.Light(definition.getBlocklight(), definition.getSkylight()) : null;
            boolean bloomFallback = definition.requestsBloom() && definition.hasEmissiveFallback();
            if (bloomFallback && definition.getLayer() == null) {
                light = new TerrainPackRules.Light(15, 15);
            }
            rules.add(new TerrainPackRules.Rule(new CtmQuadProcessor.Factory(prepared.connectInside).createProcessor(definition, spriteGetter),
                    new CtmCachingPredicates.Factory<>().createPredicates(definition, spriteGetter),
                    definition.getLayer(), light, bloomFallback));
        }
        var grass = prepared.grass == null ? null : BetterGrass.bake(prepared.grass, atlas);
        return rules.isEmpty() && prepared.layers.isEmpty() && grass == null ? null :
                new TerrainPackRules.Snapshot(rules, prepared.layers, grass).withLegacyPaneModelOverrides(index);
    }

    private static List<CtmDefinition> loadMetadata(ResourcePackIndex index) {
        if (Loader.isModLoaded("ctm") || Loader.isModLoaded("ctm_vintage")) {
            return List.of();
        }
        Map<String, CtmCustomLogic> logics = loadLogics(index);
        List<CtmDefinition> definitions = new ArrayList<>();
        for (ResourceLocation id : index.find(path -> path.getPath().startsWith("textures/") && path.getPath().endsWith(".png.mcmeta"))) {
            String path = id.getPath();
            if (!index.exists(new ResourceLocation(id.getNamespace(), path.substring(0, path.length() - 7)))) {
                continue;
            }
            ResourceLocation sprite = new ResourceLocation(id.getNamespace(), path.substring(9, path.length() - 11));
            try {
                CtmDefinition definition = resolveMetadata(index, sprite, id, logics, new HashSet<>());
                if (definition != null) {
                    definitions.add(definition);
                }
            } catch (Exception ex) {
                warn(index, id, ex);
            }
        }
        definitions.sort(null);
        return List.copyOf(definitions);
    }

    @Nullable
    private static CtmDefinition resolveMetadata(ResourcePackIndex index, ResourceLocation source, ResourceLocation metadata,
            Map<String, CtmCustomLogic> logics, Set<ResourceLocation> seen) throws IOException {
        JsonObject ctm = readCtm(index, metadata);
        if (ctm == null) {
            return null;
        }
        String pack = index.packName(metadata);
        int priority = index.priority(metadata);
        ResourceLocation current = source;
        while (ctm.has("proxy")) {
            if (!seen.add(current)) {
                throw new IllegalArgumentException("CTM proxy cycle at " + current);
            }
            current = new ResourceLocation(ctm.get("proxy").getAsString());
            ResourceLocation texture = new ResourceLocation(current.getNamespace(), "textures/" + current.getPath() + ".png");
            if (!index.exists(texture)) {
                throw new IllegalArgumentException("Missing CTM proxy texture " + texture);
            }
            ctm = readCtm(index, new ResourceLocation(texture.getNamespace(), texture.getPath() + ".mcmeta"));
            if (ctm == null) {
                ctm = new JsonObject();
                ctm.addProperty("ctm_version", 1);
            }
        }
        CtmDefinition result = CtmMcmetaParser.parse(source, ctm, pack, priority, logics);
        if (result != null && !current.equals(source)) {
            CtmMcmetaParser.overrideBaseTexture(result, current);
        }
        return result;
    }

    @Nullable
    private static JsonObject readCtm(ResourcePackIndex index, ResourceLocation id) throws IOException {
        if (!index.exists(id)) {
            return null;
        }
        JsonObject root = readJson(index, id);
        return root.has(CtmMcmetaParser.SECTION_NAME) ? root.getAsJsonObject(CtmMcmetaParser.SECTION_NAME) : null;
    }

    private static Map<String, CtmCustomLogic> loadLogics(ResourcePackIndex index) {
        Map<String, CtmCustomLogic> logics = new HashMap<>();
        for (ResourceLocation manifest : index.find(id -> id.getPath().equals("ctm.json"))) {
            try {
                JsonObject json = readJson(index, manifest);
                if (json.has("logics")) {
                    for (var entry : json.getAsJsonArray("logics")) {
                        String name = entry.getAsString();
                        ResourceLocation id = new ResourceLocation(manifest.getNamespace(), "ctm_logic/" + name + ".json");
                        try {
                            logics.put((id.getNamespace() + ":" + name).toLowerCase(Locale.ROOT),
                                    CtmLogicBakery.bake(CtmLogicDefinition.fromJson(readJson(index, id))));
                        } catch (Exception ex) {
                            warn(index, id, ex);
                        }
                    }
                }
            } catch (Exception ex) {
                warn(index, manifest, ex);
            }
        }
        return logics;
    }

    private static JsonObject readJson(ResourcePackIndex index, ResourceLocation id) throws IOException {
        try (var input = index.open(id); var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static List<LayerRule> loadLayers(ResourcePackIndex index) {
        List<LayerRule> layers = new ArrayList<>();
        List<ResourceLocation> files = new ArrayList<>();
        for (ResourceLocation id : index.find(id -> id.getPath().equals("optifine/block.properties")
                || id.getPath().equals("mcpatcher/block.properties"))) {
            files.add(id);
        }
        files.sort(Comparator.<ResourceLocation>comparingInt(index::priority).reversed()
                .thenComparingInt(id -> id.getPath().startsWith("optifine/") ? 0 : 1)
                .thenComparing(ResourceLocation::toString));
        for (ResourceLocation id : files) {
            try {
                Properties values = index.readProperties(id);
                for (BlockRenderLayer layer : List.of(BlockRenderLayer.SOLID, BlockRenderLayer.CUTOUT,
                        BlockRenderLayer.CUTOUT_MIPPED, BlockRenderLayer.TRANSLUCENT)) {
                    String key = "layer." + layer.name().toLowerCase(Locale.ROOT);
                    var predicate = PropertiesParsingHelper.parseBlockStates(values, key, id, index.packName(id));
                    if (predicate != null) {
                        layers.add(new LayerRule(predicate, layer));
                    }
                }
            } catch (Exception ex) {
                warn(index, id, ex);
            }
        }
        return List.copyOf(layers);
    }

    private static void warn(ResourcePackIndex index, ResourceLocation id, Exception exception) {
        CtmRuleTypes.LOGGER.warn("Invalid CTM resource {} in {}", id, index.packName(id), exception);
    }

    record Prepared(List<PropertyRule<?>> rules, Set<ResourceLocation> sprites, List<CtmDefinition> metadata,
            List<LayerRule> layers, boolean connectInside, @Nullable BetterGrass.Settings grass) {
    }

    record LayerRule(Predicate<IBlockState> matches, BlockRenderLayer layer) {
    }

    private record PropertyRule<T extends CtmProperties>(CtmLoader<T> loader, T properties, ResourceLocation id,
            @Nullable ExpandedCompactTiles expanded) {
        TerrainPackRules.Rule bake(Function<ResourceLocation, TextureAtlasSprite> sprites) {
            QuadProcessor processor = expanded == null ? loader.getProcessorFactory().createProcessor(properties, sprites)
                    : new SimpleQuadProcessor.Factory<>(new CtmSpriteProvider.Factory())
                            .createProcessor((OrientedConnectingCtmProperties) properties, expanded.sprites(sprites));
            return new TerrainPackRules.Rule(processor,
                    loader.getPredicatesFactory().createPredicates(properties, sprites), null, null, false,
                    id.getPath().startsWith("mcpatcher/"));
        }
    }
}
