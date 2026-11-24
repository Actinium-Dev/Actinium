package com.dhj.actinium.resourcepack.ctm;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BetterGrassTest {
    private static final BlockPos POS = BlockPos.ORIGIN;
    private static final EnumFacing SIDE = EnumFacing.EAST;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    void fancyGrassBakesTheUntintedBaseBeforeTheBiomeTintedSurface() {
        Properties properties = new Properties();
        properties.setProperty("grass.multilayer", "true");
        var atlas = new TestAtlas();
        var grass = BetterGrass.bake(BetterGrass.Settings.parse(properties), atlas);
        var state = Blocks.GRASS.getDefaultState();
        var input = List.of(new BakedQuad(new int[28], -1, SIDE, atlas.side, true, DefaultVertexFormats.BLOCK));

        assertSame(input, grass.transform(state, new BlockAccess(Map.of()), POS, SIDE, input));

        var neighbor = new BlockAccess(Map.of(POS.offset(SIDE).down(), state));
        var replaced = grass.transform(state, neighbor, POS, SIDE, input);
        assertSame(atlas.side, replaced.getFirst().getSprite());
        assertFalse(replaced.getFirst().hasTintIndex());
        assertSame(atlas.top, replaced.getLast().getSprite());
        assertSame(SIDE, replaced.getLast().getFace());
        assertTrue(replaced.getLast().hasTintIndex());
    }

    @Test
    void fancySnowFollowsAnAdjacentSnowLayer() {
        var atlas = new TestAtlas();
        var grass = BetterGrass.bake(BetterGrass.Settings.parse(new Properties()), atlas);
        var state = Blocks.GRASS.getDefaultState();
        var input = List.of(new BakedQuad(new int[28], -1, SIDE, atlas.side, true, DefaultVertexFormats.BLOCK));
        var snow = Blocks.SNOW_LAYER.getDefaultState();

        assertSame(input, grass.transform(state, new BlockAccess(Map.of(POS.up(), snow)), POS, SIDE, input));

        var neighbor = new BlockAccess(Map.of(POS.up(), snow, POS.offset(SIDE), snow));
        var surface = grass.transform(state, neighbor, POS, SIDE, input).getFirst();
        assertSame(atlas.snow, surface.getSprite());
        assertFalse(surface.hasTintIndex());
    }

    @Test
    void fastGrassPathRecognizesTheVanillaSideWithACustomSideConfigured() {
        Properties properties = new Properties();
        properties.setProperty("mode", "fast");
        properties.setProperty("texture.grass_path_side", "blocks/custom_path_side");
        var atlas = new TestAtlas();
        var grass = BetterGrass.bake(BetterGrass.Settings.parse(properties), atlas);
        var input = List.of(new BakedQuad(new int[28], -1, SIDE, atlas.pathSide, true, DefaultVertexFormats.BLOCK));
        var result = grass.transform(Blocks.GRASS_PATH.getDefaultState(), new BlockAccess(Map.of()), POS, SIDE, input);

        assertSame(atlas.pathTop, result.getFirst().getSprite());
    }

    private static final class TestAtlas extends TextureMap {
        private final TextureAtlasSprite top = new Sprite("grass_top");
        private final TextureAtlasSprite side = new Sprite("grass_side");
        private final TextureAtlasSprite snow = new Sprite("snow");
        private final TextureAtlasSprite pathTop = new Sprite("grass_path_top");
        private final TextureAtlasSprite pathSide = new Sprite("grass_path_side");
        private final Map<String, TextureAtlasSprite> sprites = Map.of(top.getIconName(), top, side.getIconName(), side,
                snow.getIconName(), snow, pathTop.getIconName(), pathTop, pathSide.getIconName(), pathSide,
                "minecraft:blocks/custom_path_side", new Sprite("custom_path_side"),
                "minecraft:blocks/mycelium_top", new Sprite("mycelium_top"),
                "minecraft:blocks/dirt_podzol_top", new Sprite("dirt_podzol_top"));

        private TestAtlas() {
            super("textures");
        }

        @Override
        public TextureAtlasSprite getAtlasSprite(String name) {
            return sprites.getOrDefault(name, getMissingSprite());
        }
    }

    private static final class Sprite extends TextureAtlasSprite {
        private Sprite(String name) {
            super("minecraft:blocks/" + name);
            setIconWidth(16);
            setIconHeight(16);
            initSprite(32, 32, 0, 0, false);
        }
    }

    private record BlockAccess(Map<BlockPos, IBlockState> states) implements IBlockAccess {
        @Override public IBlockState getBlockState(BlockPos pos) { return states.getOrDefault(pos, Blocks.AIR.getDefaultState()); }
        @Override public boolean isAirBlock(BlockPos pos) { return getBlockState(pos).getBlock() == Blocks.AIR; }
        @Override public TileEntity getTileEntity(BlockPos pos) { throw new UnsupportedOperationException(); }
        @Override public int getCombinedLight(BlockPos pos, int lightValue) { throw new UnsupportedOperationException(); }
        @Override public Biome getBiome(BlockPos pos) { throw new UnsupportedOperationException(); }
        @Override public int getStrongPower(BlockPos pos, EnumFacing direction) { throw new UnsupportedOperationException(); }
        @Override public WorldType getWorldType() { throw new UnsupportedOperationException(); }
        @Override public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean defaultValue) { throw new UnsupportedOperationException(); }
    }
}
