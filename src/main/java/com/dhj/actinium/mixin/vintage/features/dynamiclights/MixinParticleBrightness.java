package com.dhj.actinium.mixin.vintage.features.dynamiclights;

import com.dhj.actinium.render.dynamiclights.DynamicLights;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.particle.Particle;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Particle.class)
public abstract class MixinParticleBrightness {
    @WrapOperation(
        method = "getBrightnessForRender(F)I",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getCombinedLight(Lnet/minecraft/util/math/BlockPos;I)I")
    )
    private int actinium$mergeParticleBrightness(World world, BlockPos pos, int minimum, Operation<Integer> original) {
        return DynamicLights.merge(world, pos, original.call(world, pos, minimum));
    }
}
