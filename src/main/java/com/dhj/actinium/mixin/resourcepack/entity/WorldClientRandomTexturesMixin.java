package com.dhj.actinium.mixin.resourcepack.entity;

import com.dhj.actinium.resourcepack.entity.ResourcePackEntities;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldClient.class)
public abstract class WorldClientRandomTexturesMixin {
    @Inject(method = "onEntityAdded", at = @At("RETURN"))
    private void actinium$rememberOrigin(Entity entity, CallbackInfo ci) {
        ResourcePackEntities.entityAdded(entity);
    }
}
