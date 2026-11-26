package com.dhj.actinium.mixin.mod.galacticraftplayerapi;

import com.dhj.actinium.render.dynamiclights.DynamicLights;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "micdoodle8.mods.galacticraft.core.entities.player.GCPlayerBaseSP", remap = false)
public abstract class MixinGalacticraftPlayerBaseBrightness {
    @WrapOperation(
        method = "getBrightnessForRender()I",
        at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getCombinedLight(Lnet/minecraft/util/math/BlockPos;I)I"),
            @At(value = "INVOKE", target = "Lnet/minecraft/world/World;func_175626_b(Lnet/minecraft/util/math/BlockPos;I)I")
        },
        remap = false
    )
    private int actinium$mergePlayerBaseBrightness(World world, BlockPos pos, int minimum, Operation<Integer> original) {
        return DynamicLights.merge(world, pos, original.call(world, pos, minimum));
    }
}
