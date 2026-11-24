package com.dhj.actinium.mixin.resourcepack.color;

import com.dhj.actinium.resourcepack.color.ResourcePackColors;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.texture.LayeredColorMaskTexture;
import net.minecraft.item.EnumDyeColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LayeredColorMaskTexture.class)
abstract class MixinBannerDyeColors {
    @WrapOperation(method = "loadTexture", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/item/EnumDyeColor;getColorValue()I"))
    private int actinium$customDye(EnumDyeColor dye, Operation<Integer> original) {
        return ResourcePackColors.layeredDyeRgb(dye, original.call(dye));
    }
}
