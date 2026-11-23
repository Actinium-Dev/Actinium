package com.dhj.actinium.mixin.resourcepack.color;

import com.dhj.actinium.resourcepack.color.ResourcePackColors;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.entity.layers.LayerArmorBase;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LayerArmorBase.class)
abstract class MixinArmorColors {
    @WrapOperation(method = "renderArmorLayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/item/ItemArmor;getColor(Lnet/minecraft/item/ItemStack;)I"))
    private int actinium$customArmor(ItemArmor armor, ItemStack stack, Operation<Integer> original) {
        return ResourcePackColors.armor(armor, stack, original.call(armor, stack));
    }
}
