package com.dhj.actinium.mixin.resourcepack.hd;

import com.dhj.actinium.resourcepack.hd.ResourcePackHd;
import net.minecraft.client.renderer.texture.TextureMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureMap.class)
abstract class MixinHdTextureMap {
    @Shadow private int mipmapLevels;
    @Unique private int actinium$requestedMipmaps = -1;

    @Inject(method = "setMipmapLevels", at = @At("HEAD"))
    private void actinium$updateRequestedMipmaps(int levels, CallbackInfo ci) {
        actinium$requestedMipmaps = -1;
    }

    @Inject(method = {"loadSprites", "loadTextureAtlas"}, at = @At("HEAD"))
    private void actinium$selectAtlasMipmaps(CallbackInfo ci) {
        if (ResourcePackHd.disablesAtlasMipmaps((TextureMap) (Object) this)) {
            if (actinium$requestedMipmaps < 0) actinium$requestedMipmaps = mipmapLevels;
            mipmapLevels = 0;
        } else if (actinium$requestedMipmaps >= 0) {
            mipmapLevels = actinium$requestedMipmaps;
            actinium$requestedMipmaps = -1;
        }
    }
}
