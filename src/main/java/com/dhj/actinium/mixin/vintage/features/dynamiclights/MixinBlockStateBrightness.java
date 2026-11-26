package com.dhj.actinium.mixin.vintage.features.dynamiclights;

import com.dhj.actinium.render.dynamiclights.DynamicLights;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockStateContainer.StateImplementation.class)
public abstract class MixinBlockStateBrightness {
    @ModifyReturnValue(method = "getPackedLightmapCoords", at = @At("RETURN"))
    private int actinium$mergeBlockLight(int packed, IBlockAccess access, BlockPos pos) {
        return DynamicLights.blockBrightness(access, pos, packed);
    }
}
