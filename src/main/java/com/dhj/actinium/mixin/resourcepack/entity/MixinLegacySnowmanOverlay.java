package com.dhj.actinium.mixin.resourcepack.entity;

import com.dhj.actinium.resourcepack.entity.LegacyEntityOverlays;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.client.renderer.entity.layers.LayerSnowmanHead;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LayerSnowmanHead.class)
abstract class MixinLegacySnowmanOverlay {
    @WrapOperation(method = "doRenderLayer(Lnet/minecraft/entity/monster/EntitySnowman;FFFFFFF)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;)V"))
    private void actinium$snowmanHead(ItemRenderer renderer, EntityLivingBase snowman, ItemStack stack,
            TransformType transform, Operation<Void> original) {
        if (!LegacyEntityOverlays.drawSnowmanHead()) original.call(renderer, snowman, stack, transform);
    }
}
