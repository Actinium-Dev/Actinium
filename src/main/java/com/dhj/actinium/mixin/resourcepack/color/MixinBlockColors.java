package com.dhj.actinium.mixin.resourcepack.color;

import com.dhj.actinium.resourcepack.color.ResourcePackColors;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.color.BlockColors;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockColors.class)
abstract class MixinBlockColors {
    @ModifyReturnValue(method = "colorMultiplier", at = @At("RETURN"))
    private int actinium$customColors(int original, IBlockState state, IBlockAccess world, BlockPos pos, int tint) {
        int color = ResourcePackColors.block(state, world, pos, tint);
        return color == -1 ? original : color;
    }
}
