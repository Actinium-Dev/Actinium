package com.dhj.actinium.mixin.resourcepack.color;

import com.dhj.actinium.resourcepack.color.ResourcePackColors;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.entity.layers.LayerWolfCollar;
import net.minecraft.item.EnumDyeColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LayerWolfCollar.class)
abstract class MixinWolfColors {
    @WrapOperation(method = "doRenderLayer(Lnet/minecraft/entity/passive/EntityWolf;FFFFFFF)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/item/EnumDyeColor;getColorComponentValues()[F"))
    private float[] actinium$customCollar(EnumDyeColor dye, Operation<float[]> original) {
        return ResourcePackColors.dye("collar", dye, original.call(dye));
    }
}
