package com.dhj.actinium.mixin.resourcepack;

import com.dhj.actinium.resourcepack.BuiltinResourcePacks;
import net.minecraft.client.resources.ResourcePackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ResourcePackRepository.class)
abstract class MixinBuiltinResourcePacks {
    @Inject(method = "updateRepositoryEntriesAll", at = @At("HEAD"))
    private void actinium$installOptionalPacks(CallbackInfo ci) {
        BuiltinResourcePacks.install(((ResourcePackRepository) (Object) this).getDirResourcepacks().toPath());
    }
}
