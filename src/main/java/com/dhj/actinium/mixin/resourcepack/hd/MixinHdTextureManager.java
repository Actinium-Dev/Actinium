package com.dhj.actinium.mixin.resourcepack.hd;

import com.dhj.actinium.resourcepack.hd.ResourcePackHd;
import net.minecraft.client.renderer.texture.TextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureManager.class)
abstract class MixinHdTextureManager {
    @Inject(method = "tick", at = @At("TAIL"))
    private void actinium$tickPackTextures(CallbackInfo ci) {
        ResourcePackHd.tick((TextureManager) (Object) this);
    }
}
