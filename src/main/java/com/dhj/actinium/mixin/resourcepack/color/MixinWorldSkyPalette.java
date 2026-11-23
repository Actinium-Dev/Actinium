package com.dhj.actinium.mixin.resourcepack.color;

import com.dhj.actinium.resourcepack.color.ResourcePackColors;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(World.class)
abstract class MixinWorldSkyPalette {
    @ModifyReturnValue(method = "getSkyColor", at = @At("RETURN"))
    private Vec3d actinium$customSky(Vec3d original, Entity entity, float partialTicks) {
        return ResourcePackColors.atmosphere("sky", (World) (Object) this, entity, partialTicks, original);
    }
}
