package com.dhj.actinium.render.terrain.compile.light;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/** Worker-local override for the lightmap value of an emitted block quad. */
public interface BlockQuadLightOverride {
    int UNCHANGED = -1;

    boolean beginBlock(IBlockAccess world, IBlockState state, BlockPos pos);

    int getLight(BakedQuad quad);

    void endBlock();
}
