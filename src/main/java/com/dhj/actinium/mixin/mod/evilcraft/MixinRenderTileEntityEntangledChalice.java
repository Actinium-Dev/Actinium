package com.dhj.actinium.mixin.mod.evilcraft;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraftforge.fluids.FluidStack;
import org.cyclops.cyclopscore.fluid.SingleUseTank;
import org.cyclops.evilcraft.client.render.tileentity.RenderTileEntityEntangledChalice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = RenderTileEntityEntangledChalice.class, remap = false)
public abstract class MixinRenderTileEntityEntangledChalice {
    @WrapOperation(
        method = "render(Lorg/cyclops/evilcraft/tileentity/TileEntangledChalice;DDDFIF)V",
        at = @At(value = "INVOKE", target = "Lorg/cyclops/cyclopscore/fluid/SingleUseTank;getFluid()Lnet/minecraftforge/fluids/FluidStack;"),
        require = 3,
        allow = 3,
        remap = false
    )
    private FluidStack actinium$readFluidOnce(SingleUseTank tank, Operation<FluidStack> original,
                                            @Share("fluidSnapshot") LocalRef<FluidStack> snapshot) {
        FluidStack fluid = snapshot.get();
        if (fluid == null) {
            // The renderer exits after the first read if the tank is empty.
            fluid = original.call(tank);
            snapshot.set(fluid);
        }
        return fluid;
    }

    @WrapOperation(
        method = "renderFluid",
        at = @At(value = "INVOKE", target = "Lorg/cyclops/cyclopscore/fluid/SingleUseTank;getFluid()Lnet/minecraftforge/fluids/FluidStack;"),
        remap = false
    )
    private FluidStack actinium$useRenderedFluidForTexture(SingleUseTank tank, Operation<FluidStack> original,
                                                          @Local(argsOnly = true) FluidStack fluid) {
        return fluid;
    }
}
