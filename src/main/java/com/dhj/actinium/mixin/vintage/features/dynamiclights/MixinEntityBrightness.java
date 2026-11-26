/*
 * Copyright © 2020 LambdAurora <email@lambdaurora.dev>
 *
 * This file is part of SodiumDynamicLights.
 *
 * Licensed under the MIT License. For more information,
 * see the LICENSE file.
 */

package com.dhj.actinium.mixin.vintage.features.dynamiclights;

import com.dhj.actinium.render.dynamiclights.DynamicLights;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public abstract class MixinEntityBrightness {
    @ModifyReturnValue(method = "getBrightnessForRender()I", at = @At("RETURN"))
    private int actinium$mergeEntityBrightness(int packed) {
        return DynamicLights.entityBrightness((Entity) (Object) this, packed);
    }
}
