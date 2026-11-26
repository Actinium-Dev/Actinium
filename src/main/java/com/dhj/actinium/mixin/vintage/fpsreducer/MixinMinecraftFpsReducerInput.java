package com.dhj.actinium.mixin.vintage.fpsreducer;

import com.dhj.actinium.render.fps.FpsReducer;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public class MixinMinecraftFpsReducerInput {

    @ModifyExpressionValue(
            method = "runTickKeyboard",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Keyboard;next()Z", remap = false)
    )
    private boolean actinium$onKeyboardNext(boolean hasEvent) {
        if (hasEvent && Keyboard.getEventKeyState() && !Keyboard.isRepeatEvent()) {
            FpsReducer.onInput();
        }
        return hasEvent;
    }

    @ModifyExpressionValue(
            method = "runTickMouse",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Mouse;next()Z", remap = false)
    )
    private boolean actinium$onMouseNext(boolean hasEvent) {
        if (hasEvent) FpsReducer.onInput();
        return hasEvent;
    }
}
