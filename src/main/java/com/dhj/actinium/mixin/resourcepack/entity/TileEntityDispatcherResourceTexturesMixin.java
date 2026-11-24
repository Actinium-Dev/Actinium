package com.dhj.actinium.mixin.resourcepack.entity;

import com.dhj.actinium.resourcepack.entity.ResourcePackEntities;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(TileEntityRendererDispatcher.class)
public abstract class TileEntityDispatcherResourceTexturesMixin {
    @WrapMethod(method = "render(Lnet/minecraft/tileentity/TileEntity;DDDFIF)V")
    private void actinium$blockEntityTextures(TileEntity tile, double x, double y, double z, float tick,
                                              int destroyStage, float alpha, Operation<Void> original) {
        try (var scope = ResourcePackEntities.enter(tile, destroyStage)) {
            original.call(tile, x, y, z, tick, destroyStage, alpha);
        }
    }
}
