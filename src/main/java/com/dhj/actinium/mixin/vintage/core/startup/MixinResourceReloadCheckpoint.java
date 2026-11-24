package com.dhj.actinium.mixin.vintage.core.startup;

import com.dhj.actinium.resourcepack.ResourcePackReload;
import com.dhj.actinium.startup.LoadingCheckpoints;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(SimpleReloadableResourceManager.class)
public class MixinResourceReloadCheckpoint {
    @WrapMethod(method = "reloadResources")
    private void actinium$reloadPackRules(List<IResourcePack> packs, Operation<Void> original) {
        ResourcePackReload.reload((SimpleReloadableResourceManager) (Object) this, packs, () -> original.call(packs));
    }

    @Inject(method = "notifyReloadListeners", at = @At("HEAD"))
    private void actinium$preparePackRules(CallbackInfo ci) {
        ResourcePackReload.beforeListeners((SimpleReloadableResourceManager) (Object) this);
    }

    @Inject(method = "reloadResources", at = @At("RETURN"))
    private void actinium$finishResourceUploads(CallbackInfo ci) {
        LoadingCheckpoints.post(true);
    }
}
