package com.dhj.actinium.mixin.resourcepack.color;

import com.dhj.actinium.resourcepack.color.ResourcePackColors;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.color.ItemColors;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemColors.class)
abstract class MixinItemColors {
    @ModifyReturnValue(method = "colorMultiplier", at = @At("RETURN"))
    private int actinium$customColors(int original, ItemStack stack, int tint) {
        return ResourcePackColors.item(stack, tint, original);
    }
}
