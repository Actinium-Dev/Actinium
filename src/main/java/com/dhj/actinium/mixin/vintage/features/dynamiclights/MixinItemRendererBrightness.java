package com.dhj.actinium.mixin.vintage.features.dynamiclights;

import com.dhj.actinium.render.dynamiclights.DynamicLights;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemRenderer.class)
public abstract class MixinItemRendererBrightness {
    @Shadow @Final private Minecraft mc;
    @Shadow private ItemStack itemStackMainHand;
    @Shadow private ItemStack itemStackOffHand;

    @ModifyExpressionValue(
        method = "setLightmap()V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/WorldClient;getCombinedLight(Lnet/minecraft/util/math/BlockPos;I)I")
    )
    private int actinium$mergeHandBrightness(int packed) {
        return DynamicLights.handBrightness(this.mc.player, this.itemStackMainHand, this.itemStackOffHand, packed);
    }
}
