package com.dhj.actinium.mixin.resourcepack.hd;

import com.dhj.actinium.resourcepack.hd.ResourcePackHd;
import com.gtnewhorizons.angelica.client.font.FontProviderMC;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(FontRenderer.class)
abstract class MixinHdFontRenderer {
    @Shadow @Final protected ResourceLocation locationFontTexture;
    @Shadow private boolean unicodeFlag;

    @ModifyArg(method = "readFontTexture", at = @At(value = "INVOKE", remap = false,
        target = "Lnet/minecraft/client/gui/FontRenderer;getResource(Lnet/minecraft/util/ResourceLocation;)Lnet/minecraft/client/resources/IResource;"))
    private ResourceLocation actinium$readPackFontDuringReload(ResourceLocation location) {
        return ResourcePackHd.fontTextureDuringReload(location);
    }

    @WrapMethod(method = "bindTexture")
    private void actinium$bindPackFont(ResourceLocation location, Operation<Void> original) {
        original.call(ResourcePackHd.fontTexture(location));
    }

    @WrapMethod(method = "getCharWidth")
    private int actinium$measurePackFont(char ch, Operation<Integer> original) {
        int width = original.call(ch);
        if (unicodeFlag || width < 0) return width;
        int index = ch == 160 ? 32 : FontProviderMC.get(false).lookupMcFontPosition(ch);
        return (int) ResourcePackHd.fontWidth(locationFontTexture, index, width);
    }

    @WrapMethod(method = "renderChar")
    private float actinium$renderPackSpace(char ch, boolean italic, Operation<Float> original) {
        if (!unicodeFlag && (ch == ' ' || ch == 160)) return ResourcePackHd.fontWidth(locationFontTexture, 32, 4);
        return original.call(ch, italic);
    }

    @ModifyVariable(method = "renderDefaultChar", at = @At("STORE"), ordinal = 0)
    private float actinium$packGlyphExtent(float original, int index) {
        float width = ResourcePackHd.fontWidth(locationFontTexture, index, Float.NaN);
        return Float.isNaN(width) ? original : Math.max(1, width - 0.01F);
    }

    @ModifyReturnValue(method = "renderDefaultChar", at = @At("RETURN"))
    private float actinium$packGlyphAdvance(float original, int index) {
        return ResourcePackHd.fontWidth(locationFontTexture, index, original);
    }

    @WrapMethod(method = "renderDefaultChar")
    private float actinium$renderPackGlyph(int index, boolean italic, Operation<Float> original) {
        if (!ResourcePackHd.fontBlend(locationFontTexture)) return original.call(index, italic);
        GLStateManager.pushState(GL11.GL_COLOR_BUFFER_BIT);
        try {
            GLStateManager.enableBlend();
            GLStateManager.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            return original.call(index, italic);
        } finally {
            GLStateManager.popState();
        }
    }
}
