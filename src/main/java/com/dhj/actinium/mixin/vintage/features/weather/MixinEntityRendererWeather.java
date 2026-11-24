package com.dhj.actinium.mixin.vintage.features.weather;

import com.dhj.actinium.render.WeatherRenderer;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class MixinEntityRendererWeather {

    @Inject(method = "renderWorld(FJ)V", at = @At("HEAD"))
    private void actinium$beginWeatherFrame(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        WeatherRenderer.beginFrame();
    }

    @Inject(method = "renderRainSnow(F)V", at = @At("HEAD"), cancellable = true)
    private void actinium$renderRainSnow(float partialTicks, CallbackInfo ci) {
        if (WeatherRenderer.render(partialTicks)) {
            ci.cancel();
        }
    }
}
