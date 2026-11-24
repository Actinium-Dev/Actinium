package com.dhj.actinium.mixin.features.tesr;

import com.gtnewhorizons.angelica.glsm.DepthEcho;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleManager.class)
public abstract class MixinParticleManagerGlowReplay {
    @Inject(method = {"addEffect(Lnet/minecraft/client/particle/Particle;)V",
        "emitParticleAtEntity(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/EnumParticleTypes;)V",
        "emitParticleAtEntity(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/EnumParticleTypes;I)V"},
        at = @At("HEAD"), cancellable = true, require = 3)
    private void actinium$skipParticleEnqueue(CallbackInfo ci) {
        if (DepthEcho.replaying()) ci.cancel();
    }
}
