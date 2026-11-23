package com.dhj.actinium.mixin.resourcepack.color;

import com.dhj.actinium.render.CloudRenderer;
import com.dhj.actinium.resourcepack.color.ResourcePackColors;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CloudRenderer.class, remap = false)
abstract class MixinCloudRendererColors {
    @ModifyExpressionValue(method = "checkSettings", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/settings/GameSettings;clouds:I", opcode = Opcodes.GETFIELD, remap = true))
    private int actinium$cloudMode(int original) {
        return ResourcePackColors.cloudMode(original);
    }
}
