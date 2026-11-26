package com.dhj.actinium.mixin.features.tesr;

import com.dhj.actinium.render.dynamiclights.DynamicLights;
import com.gtnewhorizons.angelica.rendering.tesr.TesrLightCache;
import com.gtnewhorizons.angelica.rendering.tesr.TileEntityLight;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Other render mods may raise the minimum light argument before this lookup. */
@Mixin(value = TileEntityRendererDispatcher.class, priority = 900)
public abstract class MixinTileEntityRendererDispatcherTesrLight {

    @WrapOperation(
        method = "render(Lnet/minecraft/tileentity/TileEntity;FI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getCombinedLight(Lnet/minecraft/util/math/BlockPos;I)I")
    )
    private int actinium$tesrLight(World world, BlockPos pos, int minimum, Operation<Integer> original) {
        int combined = TesrLightCache.get(world, pos, minimum, () -> original.call(world, pos, minimum));
        return TileEntityLight.packedLight(world, pos, combined, minimum, IrisApi.getInstance().isShaderPackInUse(),
                DynamicLights.snapshot(world));
    }

    @Inject(method = "setWorld", at = @At("HEAD"))
    private void actinium$dropTesrLightmapCache(World world, CallbackInfo ci) {
        TesrLightCache.clear();
    }
}
