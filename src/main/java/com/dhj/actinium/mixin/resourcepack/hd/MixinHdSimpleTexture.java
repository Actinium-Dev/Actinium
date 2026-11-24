package com.dhj.actinium.mixin.resourcepack.hd;

import com.dhj.actinium.resourcepack.hd.HdMipmapTextures;
import com.dhj.actinium.resourcepack.hd.HdMipmapTextures.AnimatedImage;
import com.dhj.actinium.resourcepack.hd.ResourcePackHd;
import com.dhj.actinium.resourcepack.hd.mixinterface.PackMipmappedTexture;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfo;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfoCache;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.image.BufferedImage;

@Mixin(SimpleTexture.class)
abstract class MixinHdSimpleTexture extends AbstractTexture implements PackMipmappedTexture {
    @Shadow @Final protected ResourceLocation textureLocation;
    @Unique private TextureInfo actinium$packMipmaps;
    @Unique private AnimatedImage actinium$animatedImage;

    @Inject(method = "loadTexture", at = @At("HEAD"))
    private void actinium$clearAnimationPixels(CallbackInfo ci) {
        actinium$animatedImage = null;
    }

    @WrapOperation(method = "loadTexture", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/renderer/texture/TextureUtil;uploadTextureImageAllocate(ILjava/awt/image/BufferedImage;ZZ)I"))
    private int actinium$uploadPackMipmaps(int id, BufferedImage image, boolean blur, boolean clamp, Operation<Integer> original) {
        boolean wasMipped = actinium$packMipmaps != null;
        actinium$packMipmaps = null;
        int levels = ResourcePackHd.standaloneMipmapLevels(textureLocation, image.getWidth(), image.getHeight());
        if (levels == 0) {
            int uploaded = original.call(id, image, blur, clamp);
            if (wasMipped) setBlurMipmapDirect(blur, false);
            return uploaded;
        }
        actinium$animatedImage = HdMipmapTextures.uploadImage(id, image, blur, clamp, levels,
            ResourcePackHd.hasAnimationTarget(textureLocation));
        actinium$packMipmaps = TextureInfoCache.INSTANCE.peekInfo(id);
        setBlurMipmapDirect(blur, true);
        return id;
    }

    @Override
    public int actinium$getPackMipmapLevels() {
        return actinium$ownsPackMipmaps() ? actinium$packMipmaps.getMaxLevel() : 0;
    }

    @Override
    public AnimatedImage actinium$getAnimatedImage() {
        return actinium$animatedImage != null && actinium$ownsPackMipmaps() ? actinium$animatedImage : null;
    }

    @Unique
    private boolean actinium$ownsPackMipmaps() {
        return actinium$packMipmaps != null && TextureInfoCache.INSTANCE.peekInfo(glTextureId) == actinium$packMipmaps;
    }
}
