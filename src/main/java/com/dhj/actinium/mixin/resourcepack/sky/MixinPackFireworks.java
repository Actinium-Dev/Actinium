package com.dhj.actinium.mixin.resourcepack.sky;

import com.dhj.actinium.resourcepack.sky.ResourcePackSky;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.ParticleRunSplitter;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFirework;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ParticleManager.class, priority = 850)
abstract class MixinPackFireworks {
    @WrapOperation(method = "renderParticles", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/particle/Particle;renderParticle(Lnet/minecraft/client/renderer/BufferBuilder;Lnet/minecraft/entity/Entity;FFFFFF)V"))
    private void actinium$blendFirework(Particle particle, BufferBuilder buffer, Entity viewer, float tick,
                                      float x, float z, float yz, float xy, float xz, Operation<Void> original) {
        if (!ResourcePackSky.hasFireworkBlend() || !(particle instanceof ParticleFirework.Spark || particle instanceof ParticleFirework.Overlay)) {
            original.call(particle, buffer, viewer, tick, x, z, yz, xy, xz);
            return;
        }
        int mode = buffer.getDrawMode();
        var format = buffer.getVertexFormat();
        ParticleRunSplitter.renderIndependent(particle, () -> {
            GLStateManager.pushState(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
            try {
                ResourcePackSky.applyFireworkBlend();
                buffer.begin(mode, format);
                original.call(particle, buffer, viewer, tick, x, z, yz, xy, xz);
                Tessellator.getInstance().draw();
            } finally {
                GLStateManager.popState();
            }
        });
    }
}
