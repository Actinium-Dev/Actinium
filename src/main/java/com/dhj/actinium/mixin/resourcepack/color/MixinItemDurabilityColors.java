package com.dhj.actinium.mixin.resourcepack.color;

import com.dhj.actinium.resourcepack.color.ResourcePackColors;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RenderItem.class)
abstract class MixinItemDurabilityColors {
    @WrapOperation(method = "renderItemOverlayIntoGUI", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/item/Item;getRGBDurabilityForDisplay(Lnet/minecraft/item/ItemStack;)I", remap = false))
    private int actinium$durabilityColor(Item item, ItemStack stack, Operation<Integer> original) {
        return ResourcePackColors.durability(stack, original.call(item, stack));
    }
}
