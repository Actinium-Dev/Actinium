package com.dhj.actinium.mixin.resourcepack.hd;

import com.dhj.actinium.resourcepack.hd.ResourcePackHd;
import com.dhj.actinium.resourcepack.hd.mixinterface.PackMipmappedTexture;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureMap;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(AbstractTexture.class)
abstract class MixinHdTextureFilter {
    @ModifyVariable(method = "setBlurMipmapDirect", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private boolean actinium$respectAtlasMipmaps(boolean mipmap) {
        return mipmap && (!((Object) this instanceof TextureMap atlas) || !ResourcePackHd.disablesAtlasMipmaps(atlas));
    }

    @ModifyConstant(method = "setBlurMipmapDirect", constant = @Constant(intValue = GL11.GL_NEAREST_MIPMAP_LINEAR))
    private int actinium$samplePackMipmaps(int vanilla) {
        return this instanceof PackMipmappedTexture texture && texture.actinium$getPackMipmapLevels() > 0
            ? GL11.GL_LINEAR_MIPMAP_LINEAR : vanilla;
    }
}
