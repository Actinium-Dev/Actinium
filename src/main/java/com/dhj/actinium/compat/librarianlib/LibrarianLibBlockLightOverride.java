package com.dhj.actinium.compat.librarianlib;

import com.dhj.actinium.mixin.mod.librarianlib.mixinterface.AccessorGlowingHandler;
import com.dhj.actinium.render.terrain.compile.light.BlockQuadLightOverride;
import com.teamwizardry.librarianlib.features.base.block.IGlowingBlock;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import java.util.Map;

final class LibrarianLibBlockLightOverride implements BlockQuadLightOverride {
    private final Map<Block, IGlowingBlock> registeredHandlers;
    private IGlowingBlock handler;
    private IBlockAccess world;
    private IBlockState state;
    private BlockPos pos;
    private int packedLight;

    LibrarianLibBlockLightOverride(Map<Block, IGlowingBlock> registeredHandlers) {
        this.registeredHandlers = registeredHandlers;
    }

    static BlockQuadLightOverride create() {
        return new LibrarianLibBlockLightOverride(AccessorGlowingHandler.actinium$getBlockHandlers());
    }

    @Override
    public boolean beginBlock(IBlockAccess world, IBlockState state, BlockPos pos) {
        Block block = state.getBlock();
        IGlowingBlock handler = block instanceof IGlowingBlock glowing ? glowing : this.registeredHandlers.get(block);
        if (handler == null) {
            return false;
        }

        this.packedLight = handler.packedGlowCoords(world, state, pos);
        this.handler = handler;
        this.world = world;
        this.state = state;
        this.pos = pos;
        return true;
    }

    @Override
    public int getLight(BakedQuad quad) {
        return this.handler.shouldGlow(this.world, quad, this.state, this.pos) ? this.packedLight : UNCHANGED;
    }

    @Override
    public void endBlock() {
        this.handler = null;
        this.world = null;
        this.state = null;
        this.pos = null;
    }
}
