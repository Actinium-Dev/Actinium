package com.dhj.actinium.mixin.resourcepack;

import com.dhj.actinium.resourcepack.ctm.TerrainPackRules;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.block.BlockPane;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockPane.class)
abstract class MixinStainedPaneCulling {
    @ModifyReturnValue(method = "shouldSideBeRendered", at = @At("RETURN"))
    private boolean actinium$preserveDyeSeam(boolean original, IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return original || TerrainPackRules.renderStainedPaneSeam(state, world, pos, side);
    }
}
