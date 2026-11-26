package com.gtnewhorizons.angelica.rendering.tesr;

import com.dhj.actinium.render.dynamiclights.LightSnapshot;
import com.dhj.actinium.render.dynamiclights.LightTracker;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Bootstrap;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.DimensionType;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.storage.SaveHandlerMP;
import net.minecraft.world.storage.WorldInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TileEntityLightTest {

    private static final BlockPos POS = new BlockPos(3, 72, -8);

    @BeforeAll
    static void bootstrapRegistries() {
        Bootstrap.register();
    }

    @Test
    void mergesTheDynamicLevelIntoTheCallersLightWithoutNeighbourBrightness() {
        final LightWorld world = new LightWorld(new LightBlock(false, 0, 0));

        assertEquals(15 << 20 | 12 << 4, packedLight(world, 15 << 20 | 3 << 4, 12));
    }

    @Test
    void mergesTheDynamicLevelIntoTheCallersLightForOpaqueBlocks() {
        final LightWorld world = new LightWorld(new LightBlock(true, 255, 0));

        assertEquals(15 << 20 | 12 << 4, packedLight(world, 15 << 20 | 3 << 4, 12));
    }

    @Test
    void mergesTheDynamicLevelIntoTheRecomputedLight() {
        final LightWorld world = new LightWorld(new LightBlock(true, 0, 6)).withLight(11, 2);

        assertEquals(11 << 20 | 12 << 4, packedLight(world, 0, 12));
    }

    @Test
    void keepsTheRecomputedBlockLightAboveTheDynamicLevel() {
        final LightWorld world = new LightWorld(new LightBlock(true, 0, 6)).withLight(11, 9);

        assertEquals(11 << 20 | 9 << 4, packedLight(world, 0, 4));
    }

    @Test
    void preservesAnExternalModsMinimumAcrossTheShaderNeighbourCorrection() {
        final LightWorld world = new LightWorld(new LightBlock(true, 0, 6)).withLight(11, 2);
        assertEquals(11 << 20 | 13 << 4,
                TileEntityLight.packedLight(world, POS, 15 << 20 | 13 << 4, 13, true, LightSnapshot.EMPTY));
    }

    @Test
    void mergesIntoTheCachedLightWithoutShaders() {
        final LightWorld world = new LightWorld(new LightBlock(true, 0, 6)).withLight(11, 2);
        int cached = 7 << 20 | 3 << 4;
        assertEquals(7 << 20 | 12 << 4, TileEntityLight.packedLight(world, POS, cached, 0, false, snapshot(12)));
    }

    private static int packedLight(LightWorld world, int callerLight, int luminance) {
        return TileEntityLight.packedLight(world, POS, callerLight, 0, true, snapshot(luminance));
    }

    private static LightSnapshot snapshot(int luminance) {
        LightTracker tracker = new LightTracker();
        tracker.beginTick(0, 0, 0, 0, 0, 1, 1, false, 32, 64, 128, true);
        tracker.visit(POS, 0, POS.getX() + 0.5, POS.getY() + 0.5, POS.getZ() + 0.5, pos -> luminance);
        return tracker.endTick();
    }

    /** Block state data the light path reads: neighbour brightness, opacity and emitted light. */
    private static final class LightBlock extends Block {

        private final boolean neighbourBrightness;
        private final int lightOpacity;
        private final int lightValue;

        private LightBlock(boolean neighbourBrightness, int lightOpacity, int lightValue) {
            super(Material.ROCK);
            this.neighbourBrightness = neighbourBrightness;
            this.lightOpacity = lightOpacity;
            this.lightValue = lightValue;
        }

        @Override
        public boolean getUseNeighborBrightness(IBlockState state) {
            return neighbourBrightness;
        }

        @Override
        public int getLightOpacity(IBlockState state) {
            return lightOpacity;
        }

        @Override
        public int getLightValue(IBlockState state) {
            return lightValue;
        }
    }

    /** Fixture world that answers the two light lookups the path makes with fixed values. */
    private static final class LightWorld extends World {

        private final IBlockState state;
        private int skyLight;
        private int blockLight;

        private LightWorld(Block block) {
            super(
                new SaveHandlerMP(),
                new WorldInfo(
                    new WorldSettings(0L, GameType.SURVIVAL, false, false, WorldType.DEFAULT),
                    "TileEntityLightTest"
                ),
                new SkyLightProvider(),
                new Profiler(),
                false
            );
            this.state = block.getDefaultState();
            this.provider.setWorld(this);
        }

        private LightWorld withLight(int sky, int block) {
            this.skyLight = sky;
            this.blockLight = block;
            return this;
        }

        @Override
        public IBlockState getBlockState(BlockPos pos) {
            return state;
        }

        @Override
        public int getLightFor(EnumSkyBlock type, BlockPos pos) {
            return type == EnumSkyBlock.SKY ? skyLight : blockLight;
        }

        @Override
        protected IChunkProvider createChunkProvider() {
            return null;
        }

        @Override
        protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) {
            return true;
        }
    }

    private static final class SkyLightProvider extends WorldProvider {

        @Override
        public IChunkGenerator createChunkGenerator() {
            return null;
        }

        @Override
        public DimensionType getDimensionType() {
            return DimensionType.OVERWORLD;
        }
    }
}
