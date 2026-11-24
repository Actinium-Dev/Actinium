package com.dhj.actinium.mixin.resourcepack.entity;

import com.dhj.actinium.resourcepack.entity.LegacyEntityLines;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderFish;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.projectile.EntityFishHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderFish.class)
public abstract class MixinLegacyFishingLine extends Render<EntityFishHook> {
    protected MixinLegacyFishingLine(RenderManager manager) {
        super(manager);
    }

    @Inject(method = "doRender(Lnet/minecraft/entity/projectile/EntityFishHook;DDDFF)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/GlStateManager;disableTexture2D()V"), cancellable = true)
    private void actinium$drawFishingLine(EntityFishHook hook, double x, double y, double z, float yaw, float ticks,
            CallbackInfo ci, @Local(index = 49) double dx, @Local(index = 51) double dy, @Local(index = 53) double dz) {
        if (LegacyEntityLines.drawFishingLine(x, y, z, dx, dy, dz)) {
            super.doRender(hook, x, y, z, yaw, ticks);
            ci.cancel();
        }
    }
}
