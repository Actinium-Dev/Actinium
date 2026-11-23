package com.dhj.actinium.mixin.resourcepack.entity;

import com.dhj.actinium.resourcepack.entity.ResourcePackEntities;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(TextureManager.class)
public abstract class TextureManagerResourceTexturesMixin {
    @WrapMethod(method = "bindTexture")
    private void actinium$selectMaterial(ResourceLocation original, Operation<Void> bind) {
        ResourceLocation selected = ResourcePackEntities.select(original);
        bind.call(selected);
        ResourcePackEntities.bound((TextureManager) (Object) this, selected);
    }
}
