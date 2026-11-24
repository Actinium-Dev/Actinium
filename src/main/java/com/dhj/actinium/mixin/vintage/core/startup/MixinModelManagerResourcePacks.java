package com.dhj.actinium.mixin.vintage.core.startup;

import com.dhj.actinium.resourcepack.ResourcePackReload;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.block.model.ModelManager;
import net.minecraft.client.resources.IResourceManager;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ModelManager.class)
public class MixinModelManagerResourcePacks {
    @WrapMethod(method = "onResourceManagerReload")
    private void actinium$bakeWithPackRules(IResourceManager resources, Operation<Void> original) {
        ResourcePackReload.bakeModels(resources, () -> original.call(resources));
    }
}
