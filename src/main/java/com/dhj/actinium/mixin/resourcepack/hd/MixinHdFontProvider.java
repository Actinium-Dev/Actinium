package com.dhj.actinium.mixin.resourcepack.hd;

import com.dhj.actinium.resourcepack.hd.ResourcePackHd;
import com.gtnewhorizons.angelica.client.font.FontProviderMC;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = FontProviderMC.class, remap = false)
abstract class MixinHdFontProvider {
    @Shadow ResourceLocation locationFontTexture;
    @Shadow public abstract int lookupMcFontPosition(char ch);

    @ModifyReturnValue(method = "getTexture", at = @At("RETURN"))
    private ResourceLocation actinium$packFontTexture(ResourceLocation original) {
        return ResourcePackHd.fontTexture(original);
    }

    @ModifyReturnValue(method = "getXAdvance", at = @At("RETURN"))
    private float actinium$packAdvance(float original, char ch) {
        return ResourcePackHd.fontWidth(locationFontTexture, lookupMcFontPosition(ch), original);
    }

    @ModifyReturnValue(method = "getGlyphW", at = @At("RETURN"))
    private float actinium$packGlyphSize(float original, char ch) {
        return ResourcePackHd.fontWidth(locationFontTexture, lookupMcFontPosition(ch), original + 0.01F) - 0.01F;
    }

    @ModifyReturnValue(method = "getUSize", at = @At("RETURN"))
    private float actinium$packGlyphUSize(float original, char ch) {
        return Math.max(0, (ResourcePackHd.fontWidth(locationFontTexture, lookupMcFontPosition(ch), original * 128 + 1.01F) - 1.01F) / 128);
    }
}
