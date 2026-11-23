package com.dhj.actinium.mixin.resourcepack.item;

import com.dhj.actinium.resourcepack.item.AtlasEmissiveStitch;
import java.util.Map;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureMap.class)
abstract class MixinResourcePackItemAtlas {
    @Shadow @Final private Map<String, TextureAtlasSprite> mapRegisteredSprites;

    @Inject(method = "loadTextureAtlas", at = @At("HEAD"))
    private void actinium$registerEmissiveSprites(IResourceManager manager, CallbackInfo ci) {
        AtlasEmissiveStitch.register((TextureMap) (Object) this, manager, mapRegisteredSprites);
    }
}
