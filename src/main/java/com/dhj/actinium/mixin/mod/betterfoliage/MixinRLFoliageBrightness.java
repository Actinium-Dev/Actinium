package com.dhj.actinium.mixin.mod.betterfoliage;

import betterfoliage.render.BlockContext;
import betterfoliage.render.ModelRenderer;
import com.dhj.actinium.render.dynamiclights.DynamicLights;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.BufferBuilder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ModelRenderer.class, remap = false)
public abstract class MixinRLFoliageBrightness {
    @Shadow @Final public BlockContext BLOCK_CONTEXT;

    @WrapOperation(
        method = "render",
        at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/BufferBuilder;lightmap(II)Lnet/minecraft/client/renderer/BufferBuilder;"),
            @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/BufferBuilder;func_187314_a(II)Lnet/minecraft/client/renderer/BufferBuilder;")
        },
        remap = false
    )
    private BufferBuilder actinium$mergeFoliageBrightness(
        BufferBuilder buffer, int sky, int block, Operation<BufferBuilder> original
    ) {
        int packed = DynamicLights.merge(this.BLOCK_CONTEXT.getWorld(), this.BLOCK_CONTEXT.getPos(),
            (sky << 16) | (block & 0xFFFF));
        return original.call(buffer, sky, packed & 0xFFFF);
    }
}
