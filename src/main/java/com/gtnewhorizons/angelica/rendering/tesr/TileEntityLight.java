package com.gtnewhorizons.angelica.rendering.tesr;

import com.dhj.actinium.render.dynamiclights.DynamicLights;
import com.dhj.actinium.render.dynamiclights.DynamicLightSampler;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;

/** Combines the dispatcher's block-light floor with the neighbour light expected by shader packs. */
public final class TileEntityLight {

    private TileEntityLight() {}

    public static int packedLight(World world, BlockPos pos, int combined, int minimum, boolean shaders,
                                  DynamicLightSampler lights) {
        int base = shaders ? shaderLight(world, pos, combined) : combined;
        return lights.merge(pos, DynamicLights.withMinimum(base, minimum));
    }

    private static int shaderLight(World world, BlockPos pos, int combined) {
        final IBlockState state = world.getBlockState(pos);
        final Block block = state.getBlock();
        if (!block.getUseNeighborBrightness(state) || block.getLightOpacity(state) != 0) {
            return combined;
        }
        final int sky = world.provider.hasSkyLight() ? world.getLightFor(EnumSkyBlock.SKY, pos) : 0;
        final int blockLight = Math.max(world.getLightFor(EnumSkyBlock.BLOCK, pos), state.getLightValue());
        return sky << 20 | blockLight << 4;
    }
}
