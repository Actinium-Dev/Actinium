package com.dhj.actinium.mixin.resourcepack.color;

import com.dhj.actinium.resourcepack.color.ResourcePackColors;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleManager.class)
abstract class MixinParticleColors {
    @Shadow protected World world;

    @Inject(method = "spawnEffectParticle", at = @At("RETURN"))
    private void actinium$resourcePackParticle(int id, double x, double y, double z, double vx, double vy, double vz,
                                               int[] parameters, CallbackInfoReturnable<Particle> cir) {
        Particle particle = cir.getReturnValue();
        if (particle != null) ResourcePackColors.particle(particle, id, world, x, y, z);
    }
}
