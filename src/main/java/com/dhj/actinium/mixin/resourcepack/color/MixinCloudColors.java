package com.dhj.actinium.mixin.resourcepack.color;

import com.dhj.actinium.resourcepack.color.ResourcePackColors;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.settings.GameSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameSettings.class)
abstract class MixinCloudColors {
    @ModifyReturnValue(method = "shouldRenderClouds", at = @At("RETURN"))
    private int actinium$cloudMode(int original) {
        return ResourcePackColors.cloudMode(original);
    }
}
