package com.dhj.actinium.mixin.features.tesr;

import com.gtnewhorizons.angelica.glsm.DepthEcho;
import net.minecraft.client.audio.SoundHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundHandler.class)
public abstract class MixinSoundHandlerGlowReplay {
    @Inject(method = {"playSound(Lnet/minecraft/client/audio/ISound;)V", "playDelayedSound(Lnet/minecraft/client/audio/ISound;I)V"},
        at = @At("HEAD"), cancellable = true, require = 2)
    private void actinium$skipSoundEnqueue(CallbackInfo ci) {
        if (DepthEcho.replaying()) ci.cancel();
    }
}
