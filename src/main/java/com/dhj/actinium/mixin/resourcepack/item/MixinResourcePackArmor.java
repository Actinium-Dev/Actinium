package com.dhj.actinium.mixin.resourcepack.item;

import com.dhj.actinium.resourcepack.item.ItemPackRendering;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.layers.LayerArmorBase;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LayerArmorBase.class)
abstract class MixinResourcePackArmor {
    @WrapOperation(method = "renderArmorLayer", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/renderer/entity/RenderLivingBase;bindTexture(Lnet/minecraft/util/ResourceLocation;)V"))
    private void actinium$armorTexture(RenderLivingBase<?> renderer, ResourceLocation texture, Operation<Void> original,
                                       @Local ItemStack stack) {
        original.call(renderer, ItemPackRendering.armor(stack, texture));
    }

    @WrapOperation(method = "renderArmorLayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;hasEffect()Z"))
    private boolean actinium$armorEffect(ItemStack stack, Operation<Boolean> original) {
        return ItemPackRendering.shouldDrawEquipmentGlint(stack, original.call(stack));
    }

    @WrapOperation(method = "renderArmorLayer", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/renderer/entity/layers/LayerArmorBase;renderEnchantedGlint(Lnet/minecraft/client/renderer/entity/RenderLivingBase;Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/client/model/ModelBase;FFFFFFF)V"))
    private void actinium$armorGlint(RenderLivingBase<?> renderer, EntityLivingBase entity, ModelBase model, float swing,
                                    float amount, float partial, float age, float yaw, float pitch, float scale,
                                    Operation<Void> original, @Local ItemStack stack) {
        if (!ItemPackRendering.drawEquipmentGlint(stack, () -> model.render(entity, swing, amount, age, yaw, pitch, scale))) {
            original.call(renderer, entity, model, swing, amount, partial, age, yaw, pitch, scale);
        }
    }
}
