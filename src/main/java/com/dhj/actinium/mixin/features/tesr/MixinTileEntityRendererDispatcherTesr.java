package com.dhj.actinium.mixin.features.tesr;

import com.dhj.actinium.debug.WorldRenderInventory;
import com.gtnewhorizons.angelica.rendering.tesr.TesrBrackets;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Hands every tile entity renderer call to the TESR batch brackets. */
@Mixin(TileEntityRendererDispatcher.class)
public abstract class MixinTileEntityRendererDispatcherTesr {

    @WrapOperation(
        method = "render(Lnet/minecraft/tileentity/TileEntity;DDDFIF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer;render(Lnet/minecraft/tileentity/TileEntity;DDDFIF)V"
        )
    )
    private void actinium$renderTesrBatched(TileEntitySpecialRenderer<TileEntity> renderer, TileEntity te,
                                            double x, double y, double z, float partialTicks, int destroyStage,
                                            float alpha, Operation<Void> original) {
        WorldRenderInventory.recordBlockEntity(te, renderer);
        TesrBrackets.renderTileEntity(renderer, te, x, y, z, partialTicks, destroyStage, alpha,
            () -> original.call(renderer, te, x, y, z, partialTicks, destroyStage, alpha));
    }

    @WrapOperation(
        method = "render(Lnet/minecraft/tileentity/TileEntity;DDDFIF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer;renderTileEntityFast(Lnet/minecraft/tileentity/TileEntity;DDDFIFLnet/minecraft/client/renderer/BufferBuilder;)V", remap = false)
    )
    private void actinium$recordFastTesr(TileEntitySpecialRenderer<TileEntity> renderer, TileEntity te,
                                        double x, double y, double z, float partialTicks, int destroyStage,
                                        float alpha, BufferBuilder buffer, Operation<Void> original) {
        WorldRenderInventory.recordBlockEntity(te, renderer);
        original.call(renderer, te, x, y, z, partialTicks, destroyStage, alpha, buffer);
    }
}
