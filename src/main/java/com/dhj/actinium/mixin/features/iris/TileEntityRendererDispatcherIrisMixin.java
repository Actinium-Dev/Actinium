package com.dhj.actinium.mixin.features.iris;

import com.gtnewhorizons.angelica.rendering.tesr.ShadowFastTesr;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(TileEntityRendererDispatcher.class)
public class TileEntityRendererDispatcherIrisMixin {
    @WrapMethod(method = "render(Lnet/minecraft/tileentity/TileEntity;DDDFIF)V")
    private void actinium$blockEntityId(TileEntity tile, double x, double y, double z, float partialTicks,
                                        int destroyStage, float alpha, Operation<Void> original) {
        ShadowFastTesr.withBlockEntity(tile, () -> original.call(tile, x, y, z, partialTicks, destroyStage, alpha));
    }
}
