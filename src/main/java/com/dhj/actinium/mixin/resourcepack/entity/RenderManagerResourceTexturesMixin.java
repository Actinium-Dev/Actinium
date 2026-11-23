package com.dhj.actinium.mixin.resourcepack.entity;

import com.dhj.actinium.resourcepack.entity.ResourcePackEntities;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(RenderManager.class)
public abstract class RenderManagerResourceTexturesMixin {
    @Shadow private boolean renderOutlines;

    @WrapMethod(method = "renderEntity(Lnet/minecraft/entity/Entity;DDDFFZ)V")
    private void actinium$entityTextures(Entity entity, double x, double y, double z, float yaw, float tick,
                                         boolean skipBounds, Operation<Void> original) {
        try (var scope = ResourcePackEntities.enter(entity, renderOutlines)) {
            original.call(entity, x, y, z, yaw, tick, skipBounds);
        }
    }

    @WrapMethod(method = "renderMultipass")
    private void actinium$multipassTextures(Entity entity, float tick, Operation<Void> original) {
        try (var scope = ResourcePackEntities.enter(entity, renderOutlines)) {
            original.call(entity, tick);
        }
    }
}
