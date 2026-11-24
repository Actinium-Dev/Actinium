package com.dhj.actinium.resourcepack.ctm;

import java.io.IOException;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.annotation.Nullable;

import com.dhj.actinium.resourcepack.ResourcePackIndex;
import com.dhj.actinium.resourcepack.ctm.client.CtmRuleTypes;
import net.minecraft.block.BlockDirt;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BakedQuadRetextured;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.BlockPartFace;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.lwjgl.util.vector.Vector3f;

/** Better Grass options and sprites for a single atlas generation. */
final class BetterGrass {
    private static final Map<String, String> DEFAULT_TEXTURES = Map.of(
            "grass", "grass_top", "grass_side", "grass_side", "grass_path", "grass_path_top",
            "grass_path_side", "grass_path_side", "mycelium", "mycelium_top", "podzol", "dirt_podzol_top",
            "snow", "snow");
    private final Settings settings;
    private final Map<String, Map<EnumFacing, List<BakedQuad>>> cubeSides;
    private final TextureAtlasSprite pathTop;
    private final TextureAtlasSprite pathSide;
    private final TextureAtlasSprite vanillaPathSide;

    private BetterGrass(Settings settings, Map<String, TextureAtlasSprite> sprites, @Nullable TextureAtlasSprite vanillaPathSide) {
        this.settings = settings;
        pathTop = sprites.get("grass_path");
        pathSide = sprites.get("grass_path_side");
        this.vanillaPathSide = vanillaPathSide;
        Map<String, Map<EnumFacing, List<BakedQuad>>> sides = new HashMap<>();
        FaceBakery bakery = new FaceBakery();
        for (String key : List.of("grass", "mycelium", "podzol", "snow", "grass_path")) {
            TextureAtlasSprite sprite = sprites.get(key);
            if (sprite == null) continue;
            int tint = key.equals("grass") || key.equals("podzol") ? 0 : -1;
            TextureAtlasSprite base = key.equals("grass") && settings.multilayer ? sprites.get("grass_side") : null;
            Map<EnumFacing, List<BakedQuad>> faces = new EnumMap<>(EnumFacing.class);
            for (EnumFacing face : EnumFacing.Plane.HORIZONTAL) {
                faces.put(face, cubeSide(bakery, face, sprite, base, tint));
            }
            sides.put(key, Map.copyOf(faces));
        }
        cubeSides = Map.copyOf(sides);
    }

    @Nullable
    static Settings prepare(ResourcePackIndex index) {
        var files = index.find(id -> id.getNamespace().equals("minecraft")
                && (id.getPath().equals("optifine/bettergrass.properties") || id.getPath().equals("mcpatcher/bettergrass.properties")));
        var file = files.stream().max(Comparator.<ResourceLocation>comparingInt(index::priority)
                .thenComparing(id -> id.getPath().startsWith("optifine/"))).orElse(null);
        if (file == null) return null;
        try {
            Settings settings = Settings.parse(index.readProperties(file));
            return settings.mode == Mode.OFF || !settings.enabled.containsValue(true) ? null : settings;
        } catch (IOException | IllegalArgumentException ex) {
            CtmRuleTypes.LOGGER.warn("Invalid Better Grass resource {} in {}", file, index.packName(file), ex);
            return null;
        }
    }

    static BetterGrass bake(Settings settings, TextureMap atlas) {
        Map<String, TextureAtlasSprite> sprites = new HashMap<>();
        settings.textures.forEach((key, id) -> {
            var sprite = atlas.getAtlasSprite(id.toString());
            if (sprite != atlas.getMissingSprite()) {
                sprites.put(key, sprite);
            } else {
                CtmRuleTypes.LOGGER.warn("Missing Better Grass texture {} for {}", id, key);
                var defaultSprite = atlas.getAtlasSprite(defaultTexture(key).toString());
                if (defaultSprite != atlas.getMissingSprite()) sprites.put(key, defaultSprite);
            }
        });
        TextureAtlasSprite vanillaPathSide = atlas.getAtlasSprite(defaultTexture("grass_path_side").toString());
        return sprites.isEmpty() ? null : new BetterGrass(settings, sprites,
                vanillaPathSide == atlas.getMissingSprite() ? null : vanillaPathSide);
    }

    private String replacement(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing face) {
        if (face == null || face.getAxis() == EnumFacing.Axis.Y) return null;
        String key = kind(state);
        if (world == null || settings.mode == Mode.OFF) return null;
        if (key == null) {
            return state.getBlock() == Blocks.DIRT && settings.enabled.get("grass_path")
                    && world.getBlockState(pos.up()).getBlock() == Blocks.GRASS_PATH
                    && world.getBlockState(pos.offset(face)).getBlock() == Blocks.GRASS_PATH ? "path_underlay" : null;
        }
        boolean snowy = !key.equals("grass_path") && isSnow(world.getBlockState(pos.up()));
        if (!settings.enabled.get(snowy ? key + ".snow" : key)) return null;
        if (settings.mode == Mode.FANCY) {
            BlockPos adjacent = pos.offset(face);
            if (snowy) {
                if (world.getBlockState(adjacent).getBlock() != Blocks.SNOW_LAYER) return null;
            } else {
                if (!key.equals(kind(world.getBlockState(adjacent.down())))) return null;
            }
        }
        return snowy ? "snow" : key;
    }

    List<BakedQuad> transform(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing face, List<BakedQuad> quads) {
        String key = replacement(state, world, pos, face);
        if (key == null) return quads;
        if (key.equals("grass_path")) {
            if (pathTop == null || quads.stream().noneMatch(this::isPathSide)) return quads;
            return quads.stream().map(quad -> isPathSide(quad) ? new BakedQuadRetextured(quad, pathTop) : quad).toList();
        }
        var sides = cubeSides.get(key.equals("path_underlay") ? "grass_path" : key);
        return sides == null ? quads : sides.get(face);
    }

    private boolean isPathSide(BakedQuad quad) {
        var sprite = quad.getSprite();
        return sprite != null && (sprite == pathSide || sprite == vanillaPathSide) && quad.getFormat().hasUvOffset(0);
    }

    private static List<BakedQuad> cubeSide(FaceBakery bakery, EnumFacing face, TextureAtlasSprite sprite,
            @Nullable TextureAtlasSprite base, int tint) {
        BakedQuad surface = bakeSide(bakery, face, sprite, tint);
        return base == null ? List.of(surface) : List.of(bakeSide(bakery, face, base, -1), surface);
    }

    private static BakedQuad bakeSide(FaceBakery bakery, EnumFacing face, TextureAtlasSprite sprite, int tint) {
        BlockPartFace part = new BlockPartFace(face, tint, sprite.getIconName(), new BlockFaceUV(new float[]{0, 0, 16, 16}, 0));
        return bakery.makeBakedQuad(new Vector3f(0, 0, 0), new Vector3f(16, 16, 16), part, sprite,
                face, ModelRotation.X0_Y0, null, false, true);
    }

    private static String kind(IBlockState state) {
        var block = state.getBlock();
        if (block == Blocks.GRASS) return "grass";
        if (block == Blocks.GRASS_PATH) return "grass_path";
        if (block == Blocks.MYCELIUM) return "mycelium";
        if (block == Blocks.DIRT && state.getValue(BlockDirt.VARIANT) == BlockDirt.DirtType.PODZOL) return "podzol";
        return null;
    }

    private static boolean isSnow(IBlockState state) {
        return state.getBlock() == Blocks.SNOW_LAYER || state.getBlock() == Blocks.SNOW;
    }

    private static ResourceLocation defaultTexture(String key) {
        return new ResourceLocation("minecraft", "blocks/" + DEFAULT_TEXTURES.get(key));
    }

    enum Mode { OFF, FAST, FANCY }

    record Settings(Map<String, ResourceLocation> textures, Map<String, Boolean> enabled, boolean multilayer, Mode mode) {
        static Settings parse(Properties properties) {
            Map<String, ResourceLocation> textures = new HashMap<>();
            DEFAULT_TEXTURES.forEach((key, ignored) -> {
                String path = properties.getProperty("texture." + key);
                textures.put(key, path == null || path.isBlank() ? defaultTexture(key) : new ResourceLocation(path.trim()));
            });
            Map<String, Boolean> enabled = new HashMap<>();
            for (String key : List.of("grass", "grass_path", "mycelium", "podzol", "grass.snow", "mycelium.snow", "podzol.snow")) {
                enabled.put(key, Boolean.parseBoolean(properties.getProperty(key, "true").trim()));
            }
            String mode = properties.getProperty("mode", "fancy").trim();
            return new Settings(Map.copyOf(textures), Map.copyOf(enabled),
                    Boolean.parseBoolean(properties.getProperty("grass.multilayer", "false").trim()),
                    mode.equalsIgnoreCase("off") ? Mode.OFF : mode.equalsIgnoreCase("fast") ? Mode.FAST : Mode.FANCY);
        }

        void registerSprites(TextureMap atlas) {
            textures.values().forEach(atlas::registerSprite);
            textures.keySet().stream().map(BetterGrass::defaultTexture).forEach(atlas::registerSprite);
        }
    }
}
