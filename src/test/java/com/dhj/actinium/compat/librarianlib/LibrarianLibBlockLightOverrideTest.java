package com.dhj.actinium.compat.librarianlib;

import com.dhj.actinium.render.terrain.compile.light.BlockQuadLightOverride;
import com.teamwizardry.librarianlib.features.base.block.IGlowingBlock;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibrarianLibBlockLightOverrideTest {
    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    void blockHandlerTakesPrecedenceAndRelightsOnlyItsSelectedQuad() {
        var block = new GlowingBlock();
        var light = new LibrarianLibBlockLightOverride(Map.of(block, new Handler(0x00200010)));

        assertTrue(light.beginBlock(null, block.getDefaultState(), BlockPos.ORIGIN));
        assertEquals(0x00F000F0, light.getLight(quad(1)));
        assertEquals(BlockQuadLightOverride.UNCHANGED, light.getLight(quad(0)));
        light.endBlock();

        assertFalse(light.beginBlock(null, Blocks.STONE.getDefaultState(), BlockPos.ORIGIN));
    }

    @Test
    void readsReplacementRegistrationsAndAcceptsZeroLight() {
        Block block = Blocks.STONE;
        Map<Block, IGlowingBlock> handlers = new Reference2ObjectOpenHashMap<>();
        var light = new LibrarianLibBlockLightOverride(handlers);
        var glowQuad = quad(1);

        assertFalse(light.beginBlock(null, block.getDefaultState(), BlockPos.ORIGIN));

        handlers.put(block, new Handler(0x00200010));
        assertTrue(light.beginBlock(null, block.getDefaultState(), BlockPos.ORIGIN));
        assertEquals(0x00200010, light.getLight(glowQuad));
        light.endBlock();

        handlers.put(block, new Handler(0));
        assertTrue(light.beginBlock(null, block.getDefaultState(), BlockPos.ORIGIN));
        assertEquals(0, light.getLight(glowQuad));
        light.endBlock();
    }

    private static BakedQuad quad(int tint) {
        return new BakedQuad(new int[28], tint, EnumFacing.NORTH, null, false, DefaultVertexFormats.BLOCK);
    }

    private static final class GlowingBlock extends Block implements IGlowingBlock {
        GlowingBlock() {
            super(Material.ROCK);
        }

        @Override
        public boolean shouldGlow(IBlockAccess world, BakedQuad quad, IBlockState state, BlockPos pos) {
            return quad.getTintIndex() == 1;
        }

        @Override
        public int packedGlowCoords(IBlockAccess world, IBlockState state, BlockPos pos) {
            return 0x00F000F0;
        }
    }

    private record Handler(int light) implements IGlowingBlock {
        @Override
        public boolean shouldGlow(IBlockAccess world, BakedQuad quad, IBlockState state, BlockPos pos) {
            return true;
        }

        @Override
        public int packedGlowCoords(IBlockAccess world, IBlockState state, BlockPos pos) {
            return light;
        }
    }
}
