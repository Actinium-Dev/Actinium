package com.dhj.actinium.mixin.resourcepack.entity;

import com.dhj.actinium.resourcepack.entity.LegacyEntityOverlays;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.entity.layers.LayerMooshroomMushroom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LayerMooshroomMushroom.class)
abstract class MixinLegacyMooshroomOverlay {
    @WrapOperation(method = "doRenderLayer(Lnet/minecraft/entity/passive/EntityMooshroom;FFFFFFF)V", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/client/renderer/BlockRendererDispatcher;renderBlockBrightness(Lnet/minecraft/block/state/IBlockState;F)V"))
    private void actinium$firstMushroom(BlockRendererDispatcher renderer, IBlockState state, float brightness, Operation<Void> original) {
        if (!LegacyEntityOverlays.drawMushroom(0)) original.call(renderer, state, brightness);
    }

    @WrapOperation(method = "doRenderLayer(Lnet/minecraft/entity/passive/EntityMooshroom;FFFFFFF)V", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/client/renderer/BlockRendererDispatcher;renderBlockBrightness(Lnet/minecraft/block/state/IBlockState;F)V"))
    private void actinium$secondMushroom(BlockRendererDispatcher renderer, IBlockState state, float brightness, Operation<Void> original) {
        if (!LegacyEntityOverlays.drawMushroom(1)) original.call(renderer, state, brightness);
    }

    @WrapOperation(method = "doRenderLayer(Lnet/minecraft/entity/passive/EntityMooshroom;FFFFFFF)V", at = @At(value = "INVOKE", ordinal = 2,
            target = "Lnet/minecraft/client/renderer/BlockRendererDispatcher;renderBlockBrightness(Lnet/minecraft/block/state/IBlockState;F)V"))
    private void actinium$thirdMushroom(BlockRendererDispatcher renderer, IBlockState state, float brightness, Operation<Void> original) {
        if (!LegacyEntityOverlays.drawMushroom(2)) original.call(renderer, state, brightness);
    }
}
