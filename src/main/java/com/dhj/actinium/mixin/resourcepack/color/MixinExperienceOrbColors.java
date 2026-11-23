package com.dhj.actinium.mixin.resourcepack.color;

import com.dhj.actinium.resourcepack.color.ResourcePackColors;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.entity.RenderXPOrb;
import net.minecraft.entity.item.EntityXPOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RenderXPOrb.class)
abstract class MixinExperienceOrbColors {
    @WrapOperation(method = "doRender(Lnet/minecraft/entity/item/EntityXPOrb;DDDFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/BufferBuilder;color(IIII)Lnet/minecraft/client/renderer/BufferBuilder;"))
    private BufferBuilder actinium$orbColor(BufferBuilder buffer, int r, int g, int b, int a, Operation<BufferBuilder> original,
                                            EntityXPOrb orb, double x, double y, double z, float yaw, float partialTicks) {
        int color = ResourcePackColors.experience(orb.xpColor + partialTicks, r << 16 | g << 8 | b);
        return original.call(buffer, color >> 16 & 255, color >> 8 & 255, color & 255, a);
    }
}
