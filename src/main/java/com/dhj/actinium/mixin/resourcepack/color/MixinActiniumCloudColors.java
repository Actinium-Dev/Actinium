package com.dhj.actinium.mixin.resourcepack.color;

import com.dhj.actinium.render.CloudRenderSettings;
import com.dhj.actinium.resourcepack.color.ResourcePackColors;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CloudRenderSettings.class, remap = false)
abstract class MixinActiniumCloudColors {
    @ModifyReturnValue(method = "effectiveCloudMode", at = @At("RETURN"))
    private static int actinium$cloudMode(int original) {
        return ResourcePackColors.cloudMode(original);
    }
}
