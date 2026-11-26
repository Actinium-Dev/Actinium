package com.dhj.actinium.mixin.mod.ccl;

import codechicken.lib.lighting.LightMatrix;
import com.dhj.actinium.render.dynamiclights.DynamicLights;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = LightMatrix.class, remap = false)
public abstract class MixinLightMatrixBrightness {
    @WrapOperation(
        method = "sample(I)V",
        at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/world/IBlockAccess;getCombinedLight(Lnet/minecraft/util/math/BlockPos;I)I"),
            @At(value = "INVOKE", target = "Lnet/minecraft/world/IBlockAccess;func_175626_b(Lnet/minecraft/util/math/BlockPos;I)I")
        },
        remap = false
    )
    private int actinium$mergeSampleBrightness(IBlockAccess access, BlockPos pos, int minimum, Operation<Integer> original) {
        return DynamicLights.merge(access, pos, original.call(access, pos, minimum));
    }
}
