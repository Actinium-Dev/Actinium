package com.dhj.actinium.mixin.resourcepack;

import com.dhj.actinium.render.terrain.compile.pipeline.VanillaPackModel;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockModelRenderer.class)
abstract class MixinPackQuadBrightness {
    @WrapOperation(method = {"renderQuadsFlat", "renderQuadsSmooth"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/BufferBuilder;putBrightness4(IIII)V"))
    private void actinium$preserveBakedMinimum(BufferBuilder buffer, int a, int b, int c, int d,
            Operation<Void> original, @Local BakedQuad quad) {
        VanillaPackModel.writeBrightness(quad, a, b, c, d, (w, x, y, z) -> original.call(buffer, w, x, y, z));
    }
}
