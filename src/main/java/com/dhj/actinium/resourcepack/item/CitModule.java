package com.dhj.actinium.resourcepack.item;

import com.dhj.actinium.resourcepack.ResourcePackIndex;
import com.dhj.actinium.resourcepack.ResourcePackReload;
import java.io.IOException;
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
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class CitModule implements ResourcePackReload.Module<CitModule.Prepared, CitSnapshot> {
    private static final Logger LOGGER = LogManager.getLogger("Actinium/CIT");
    static final Comparator<CitRule> ORDER = Comparator.comparingInt(CitRule::selectionGroup)
        .thenComparing(Comparator.comparingInt(CitRule::weight).reversed())
        .thenComparing(Comparator.comparingInt(CitRule::priority).reversed())
        .thenComparing(rule -> rule.potionImage() != null && !rule.potionImage().modern())
        .thenComparing(rule -> rule.source().toString());

    static final class Prepared {
        final List<CitRule> rules;
        final CitGlint.Settings glintSettings;
        final Map<ResourceLocation, IModel> unbaked = new LinkedHashMap<>();
        final Map<ResourceLocation, IBakedModel> baked = new HashMap<>();
        final Set<Item> variantItems;
        Map<IBakedModel, ModelResourceLocation> baseLocations = Map.of();
        TextureMap atlas;

        Prepared(List<CitRule> rules, CitGlint.Settings glintSettings) {
            this.rules = rules;
            this.glintSettings = glintSettings;
            this.variantItems = CitModelState.variantItems(rules);
        }
    }

    @Override public String option() { return "cit"; }

    @Override public boolean requiresAtlas() { return true; }

    @Override
    public Prepared prepare(ResourcePackIndex index) {
        List<CitRule> rules = new ArrayList<>();
        for (ResourceLocation path : index.find(id -> id.getPath().endsWith(".properties")
            && (id.getPath().startsWith("optifine/cit/") || id.getPath().startsWith("mcpatcher/cit/")))) {
            if (path.getPath().startsWith("mcpatcher/")) {
                ResourceLocation modern = new ResourceLocation(path.getNamespace(), "optifine/" + path.getPath().substring(10));
                if (index.exists(modern) && index.priority(modern) >= index.priority(path)) continue;
            }
            try {
                rules.add(parse(index, path, index.readProperties(path)));
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("Invalid CIT rule {} in {}: {}", path, index.packName(path), e.getMessage());
            }
        }
        for (ResourceLocation path : index.find(CitAutoPotions::isImagePath)) {
            CitAutoPotions.Image image = CitAutoPotions.parse(path);
            if (image != null) rules.add(CitRule.automaticPotion(path, index.priority(path), image));
        }
        rules.sort(ORDER);
        return new Prepared(List.copyOf(rules), glintSettings(index));
    }

    private static CitGlint.Settings glintSettings(ResourcePackIndex index) {
        ResourceLocation modern = new ResourceLocation("optifine/cit.properties");
        ResourceLocation legacy = new ResourceLocation("mcpatcher/cit.properties");
        ResourceLocation path = index.exists(modern) && (!index.exists(legacy) || index.priority(modern) >= index.priority(legacy)) ? modern : legacy;
        if (index.exists(path)) {
            try {
                return CitGlint.Settings.parse(index.readProperties(path));
            } catch (IOException | IllegalArgumentException e) {
                LOGGER.warn("Invalid CIT settings {} in {}", path, index.packName(path), e);
            }
        }
        return CitGlint.Settings.parse(new Properties());
    }

    private static CitRule parse(ResourcePackIndex index, ResourceLocation path, Properties p) {
        CitRule.Type type = CitRule.Type.valueOf(p.getProperty("type", "item").trim().toUpperCase(Locale.ROOT));
        Set<Item> items = new HashSet<>();
        String itemFilter = p.getProperty("items", p.getProperty("matchItems", "")).trim();
        for (String token : itemFilter.split("\\s+")) {
            if (token.isEmpty()) continue;
            if (token.matches("[0-9]+-[0-9]+")) {
                CitConditions.Ranges range = CitConditions.Ranges.parse(token);
                for (Item item : Item.REGISTRY) if (range.contains(Item.getIdFromItem(item))) items.add(item);
            } else {
                Item item = token.matches("[0-9]+") ? Item.getItemById(Integer.parseInt(token)) : Item.getByNameOrId(token);
                if (item == null) throw new IllegalArgumentException("Unknown item: " + token);
                items.add(item);
            }
        }
        String stem = path.getPath().substring(path.getPath().lastIndexOf('/') + 1, path.getPath().length() - 11);
        if (items.isEmpty() && type != CitRule.Type.ENCHANTMENT && type != CitRule.Type.ELYTRA) {
            Item item = itemFilter.isEmpty() ? Item.getByNameOrId(stem) : null;
            if (item == null) throw new IllegalArgumentException("No matched items");
            items.add(item);
        }
        Map<String, ResourceLocation> textures = assets(index, path, p, "texture", ".png");
        Map<String, ResourceLocation> models = type == CitRule.Type.ITEM ? assets(index, path, p, "model", ".json") : Map.of();
        if (textures.isEmpty() && models.isEmpty()) {
            ResourceLocation implicit = CitPaths.file(path, stem, ".png");
            if (index.exists(implicit)) textures = Map.of("", implicit);
        }
        if (textures.isEmpty() && models.isEmpty()) throw new IllegalArgumentException("No CIT textures or models");
        CitGlint glint = type == CitRule.Type.ENCHANTMENT ? CitGlint.parse(p, textures.get("")) : null;
        return new CitRule(path, index.priority(path), Integer.parseInt(p.getProperty("weight", "0").trim()), type,
            Set.copyOf(items), CitConditions.parse(p), textures, models, glint, null);
    }

    private static Map<String, ResourceLocation> assets(ResourcePackIndex index, ResourceLocation rule, Properties properties,
                                                       String property, String extension) {
        Map<String, ResourceLocation> result = new HashMap<>();
        for (String name : properties.stringPropertyNames()) {
            if (!name.equals(property) && !name.startsWith(property + ".")) continue;
            ResourceLocation file = CitPaths.file(rule, properties.getProperty(name), extension);
            if (!index.exists(file)) throw new IllegalArgumentException("Missing " + file);
            result.put(name.equals(property) ? "" : name.substring(property.length() + 1), file);
        }
        if (property.equals("texture") && !result.containsKey("")) {
            String alias = properties.getProperty("tile", properties.getProperty("source"));
            if (alias != null) {
                ResourceLocation file = CitPaths.file(rule, alias, extension);
                if (!index.exists(file)) throw new IllegalArgumentException("Missing " + file);
                result.put("", file);
            }
        }
        return Map.copyOf(result);
    }

    @Override
    public void registerSprites(Prepared prepared, TextureMap atlas) {
        prepared.atlas = atlas;
        for (CitRule rule : prepared.rules) {
            if (rule.type() != CitRule.Type.ITEM) continue;
            if (rule.potionImage() != null) {
                register(atlas, rule.source());
                CitAutoPotions.registerContents(rule.potionImage(), atlas);
                continue;
            }
            rule.textures().values().forEach(file -> register(atlas, file));
            for (ResourceLocation file : rule.models().values()) {
                if (prepared.unbaked.containsKey(file)) continue;
                try {
                    IModel model = ModelLoaderRegistry.getModel(CitPaths.modelId(file));
                    prepared.unbaked.put(file, model);
                    for (ResourceLocation texture : model.getTextures()) {
                        ResourceLocation source = CitAtlasSprite.source(texture);
                        if (source == null) atlas.registerSprite(texture);
                        else register(atlas, source);
                    }
                } catch (Exception e) {
                    LOGGER.warn("Cannot load CIT model {} from rule {}", file, rule.source(), e);
                }
            }
        }
    }

    private static void register(TextureMap atlas, ResourceLocation file) {
        atlas.setTextureEntry(new CitAtlasSprite(CitAtlasSprite.id(file), file, atlas));
    }

    @Override
    public void modelBake(Prepared prepared, ModelBakeEvent event) {
        if (!prepared.variantItems.isEmpty()) {
            prepared.baseLocations = CitModelState.baseLocations(event.getModelRegistry(), event.getModelManager().getMissingModel());
        }
        prepared.unbaked.forEach((file, model) -> {
            try {
                prepared.baked.put(file, model.bake(model.getDefaultState(), DefaultVertexFormats.ITEM,
                    texture -> prepared.atlas.getAtlasSprite(texture.toString())));
            } catch (RuntimeException e) {
                LOGGER.warn("Cannot bake CIT model {}", file, e);
            }
        });
        for (CitRule rule : prepared.rules) {
            CitAutoPotions.Image image = rule.potionImage();
            if (image == null) continue;
            ResourceLocation file = rule.source();
            try {
                prepared.baked.put(file, CitAutoPotions.bake(image, file, prepared.atlas, event));
            } catch (RuntimeException e) {
                LOGGER.warn("Cannot bake automatic CIT potion model {}", file, e);
            }
        }
    }

    @Override
    public CitSnapshot finish(Prepared prepared, ResourcePackIndex index, TextureMap atlas) {
        Map<ResourceLocation, TextureAtlasSprite> sprites = new HashMap<>();
        for (CitRule rule : prepared.rules) {
            if (rule.type() != CitRule.Type.ITEM) continue;
            for (ResourceLocation file : rule.textures().values()) {
                TextureAtlasSprite sprite = atlas.getAtlasSprite(CitAtlasSprite.id(file).toString());
                if (sprite != atlas.getMissingSprite()) sprites.put(file, sprite);
            }
        }
        List<CitRule> available = prepared.rules.stream()
            .filter(rule -> rule.potionImage() == null || prepared.baked.containsKey(rule.source())).toList();
        return new CitSnapshot(available, Map.copyOf(prepared.baked), Map.copyOf(sprites), prepared.glintSettings,
            prepared.variantItems, prepared.baseLocations);
    }
}
