package com.dhj.actinium.mixin.features.tesr;

import com.gtnewhorizons.angelica.rendering.tesr.ShadowFastTesr;
import com.gtnewhorizons.angelica.rendering.tesr.TesrBrackets;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.coderbot.iris.gl.MatrixStack;
import net.coderbot.iris.pipeline.ShadowRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Hands the shadow pass block entities to the shadow batch bracket. */
@Mixin(ShadowRenderer.class)
public abstract class MixinShadowRendererTesr {

    @WrapMethod(method = "renderTileEntities")
    private void actinium$batchShadowTesr(Object bufferSource, MatrixStack modelView, double cameraX, double cameraY,
                                          double cameraZ, float partialTicks, boolean hasEntityFrustum,
                                          boolean lightsOnly, Operation<Void> original) {
        TesrBrackets.shadowBlockEntities(modelView.peek().getModel(), cameraX, cameraY, cameraZ,
            () -> original.call(bufferSource, modelView, cameraX, cameraY, cameraZ, partialTicks, hasEntityFrustum,
                lightsOnly));
    }

    @WrapOperation(method = "renderTileEntity", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/tileentity/TileEntityRendererDispatcher;render(Lnet/minecraft/tileentity/TileEntity;DDDF)V"))
    private void actinium$dispatchShadow(TileEntityRendererDispatcher dispatcher, TileEntity tile,
                                         double x, double y, double z, float partialTicks, Operation<Void> original) {
        ShadowFastTesr.dispatch(() -> original.call(dispatcher, tile, x, y, z, partialTicks));
    }
}
