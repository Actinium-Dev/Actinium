package com.dhj.actinium.mixin.resourcepack.sky;

import com.dhj.actinium.resourcepack.sky.ResourcePackSky;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderGlobal.class, priority = 900)
abstract class MixinResourcePackSky {
    @Inject(method = "renderSky(FI)V", at = @At(value = "INVOKE", ordinal = 0,
        target = "Lnet/minecraft/client/renderer/GlStateManager;enableTexture2D()V", shift = At.Shift.AFTER))
    private void actinium$drawPackSky(float partialTicks, int pass, CallbackInfo ci) {
        ResourcePackSky.render(partialTicks);
    }

    @ModifyExpressionValue(method = "renderSky(FI)V", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/renderer/RenderGlobal;SUN_TEXTURES:Lnet/minecraft/util/ResourceLocation;"))
    private ResourceLocation actinium$replaceSun(ResourceLocation original) {
        return ResourcePackSky.celestialTexture(original, false);
    }

    @ModifyExpressionValue(method = "renderSky(FI)V", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/renderer/RenderGlobal;MOON_PHASES_TEXTURES:Lnet/minecraft/util/ResourceLocation;"))
    private ResourceLocation actinium$replaceMoon(ResourceLocation original) {
        return ResourcePackSky.celestialTexture(original, true);
    }

    @WrapOperation(method = "renderSky(FI)V", at = @At(value = "INVOKE", ordinal = 1,
        target = "Lnet/minecraft/client/renderer/Tessellator;draw()V"))
    private void actinium$blendSun(Tessellator tessellator, Operation<Void> original) {
        ResourcePackSky.drawCelestial(false, () -> original.call(tessellator));
    }

    @WrapOperation(method = "renderSky(FI)V", at = @At(value = "INVOKE", ordinal = 2,
        target = "Lnet/minecraft/client/renderer/Tessellator;draw()V"))
    private void actinium$blendMoon(Tessellator tessellator, Operation<Void> original) {
        ResourcePackSky.drawCelestial(true, () -> original.call(tessellator));
    }
}
