package com.dhj.actinium.mixin.resourcepack.color;

import com.dhj.actinium.resourcepack.color.ResourcePackColors;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.entity.layers.LayerSheepWool;
import net.minecraft.item.EnumDyeColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LayerSheepWool.class)
abstract class MixinSheepColors {
    @WrapOperation(method = "doRenderLayer(Lnet/minecraft/entity/passive/EntitySheep;FFFFFFF)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/passive/EntitySheep;getDyeRgb(Lnet/minecraft/item/EnumDyeColor;)[F"))
    private float[] actinium$customWool(EnumDyeColor dye, Operation<float[]> original) {
        return ResourcePackColors.dye("sheep", dye, original.call(dye));
    }
}
