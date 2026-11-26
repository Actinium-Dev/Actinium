package com.dhj.actinium.render.dynamiclights.rules;

import git.jbredwards.fluidlogged_api.api.util.FluidloggedUtils;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fml.common.Loader;

/** Samples liquid at entity light sources, including stored fluids when Fluidlogged API is installed. */
public final class DynamicLightFluids {
    private static final boolean FLUIDLOGGED_LOADED = Loader.isModLoaded("fluidlogged_api");

    private DynamicLightFluids() {
    }

    public static boolean areEyesInLiquid(Entity entity) {
        return entity.world != null && isLiquid(entity.world, new BlockPos(entity.getPositionEyes(1.0F)));
    }

    public static boolean isBodyInLiquid(Entity entity) {
        return entity.world != null && (entity.isInWater() || entity.isInLava()
                || isLiquid(entity.world, entity.getPosition()));
    }

    public static boolean isLiquid(IBlockAccess access, BlockPos pos) {
        IBlockState state = FLUIDLOGGED_LOADED ? FluidloggedAccess.getFluidOrReal(access, pos) : access.getBlockState(pos);
        return state.getMaterial().isLiquid() || state.getBlock() instanceof IFluidBlock;
    }

    private static final class FluidloggedAccess {
        private static IBlockState getFluidOrReal(IBlockAccess access, BlockPos pos) {
            return FluidloggedUtils.getFluidOrReal(access, pos);
        }
    }
}
