package com.dhj.actinium.mixin.resourcepack.color;

import com.dhj.actinium.resourcepack.color.ResourcePackColors;
import net.minecraft.block.material.Material;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleDrip;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleDrip.class)
abstract class MixinDripColors extends Particle {
    @Shadow @Final private Material materialType;
    @Shadow private int bobTimer;

    protected MixinDripColors(World world, double x, double y, double z) {
        super(world, x, y, z);
    }

    @Inject(method = "onUpdate", at = @At("RETURN"))
    private void actinium$updateDripColor(CallbackInfo ci) {
        ResourcePackColors.drip(this, materialType, 40 - bobTimer, world, posX, posY, posZ);
    }
}
