package com.dhj.actinium.mixin.vintage.features.textures;

import com.dhj.actinium.render.terrain.sprite.SpriteUtil;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraftforge.client.model.pipeline.LightUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LightUtil.class, remap = false)
public abstract class MixinLightUtil {
    @Inject(method = "renderQuadColor", at = @At("HEAD"))
    private static void actinium$markRenderedSprite(BufferBuilder buffer, BakedQuad quad, int color, CallbackInfo ci) {
        TextureAtlasSprite sprite = quad.getSprite();
        if (sprite != null) {
            SpriteUtil.markSpriteActive(sprite);
        }
    }
}
