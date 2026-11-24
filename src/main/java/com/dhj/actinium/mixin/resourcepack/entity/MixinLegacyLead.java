package com.dhj.actinium.mixin.resourcepack.entity;

import com.dhj.actinium.resourcepack.entity.LegacyEntityLines;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.EntityLiving;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderLiving.class)
public abstract class MixinLegacyLead {
    @Inject(method = "renderLeash(Lnet/minecraft/entity/EntityLiving;DDDFF)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/GlStateManager;disableTexture2D()V"), cancellable = true)
    private void actinium$drawLead(EntityLiving entity, double x, double y, double z, float yaw, float ticks,
            CallbackInfo ci, @Local(index = 39) double dx, @Local(index = 41) double dy, @Local(index = 43) double dz) {
        if (LegacyEntityLines.drawLead(x, y, z, dx, dy, dz)) ci.cancel();
    }
}
