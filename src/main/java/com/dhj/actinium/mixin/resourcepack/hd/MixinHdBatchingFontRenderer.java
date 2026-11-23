package com.dhj.actinium.mixin.resourcepack.hd;

import com.dhj.actinium.resourcepack.hd.ResourcePackHd;
import com.gtnewhorizons.angelica.client.font.BatchingFontRenderer;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = BatchingFontRenderer.class, remap = false)
abstract class MixinHdBatchingFontRenderer {
    @Shadow @Final protected ResourceLocation locationFontTexture;
    @Shadow protected FontRenderer underlying;

    @ModifyReturnValue(method = "getWhitespaceScale", at = @At("RETURN"))
    private float actinium$packWhitespace(float scale) {
        return underlying.getUnicodeFlag() ? scale : scale * ResourcePackHd.fontWidth(locationFontTexture, 32, 4) / 4;
    }
}
