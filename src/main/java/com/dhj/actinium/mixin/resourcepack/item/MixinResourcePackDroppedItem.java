package com.dhj.actinium.mixin.resourcepack.item;

import com.dhj.actinium.resourcepack.item.ItemPackRendering;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.client.renderer.entity.RenderEntityItem;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHandSide;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RenderEntityItem.class)
abstract class MixinResourcePackDroppedItem {
    @WrapMethod(method = "doRender(Lnet/minecraft/entity/item/EntityItem;DDDFF)V")
    private void actinium$groundPerspective(EntityItem entity, double x, double y, double z, float yaw, float partial,
                                             Operation<Void> original) {
        ItemPackRendering.withPerspective(TransformType.GROUND, EnumHandSide.RIGHT,
            () -> original.call(entity, x, y, z, yaw, partial));
    }

    @WrapOperation(method = "doRender(Lnet/minecraft/entity/item/EntityItem;DDDFF)V", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/renderer/RenderItem;getItemModelWithOverrides(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/EntityLivingBase;)Lnet/minecraft/client/renderer/block/model/IBakedModel;"))
    private IBakedModel actinium$groundModel(RenderItem renderer, ItemStack stack, World world, EntityLivingBase entity,
                                            Operation<IBakedModel> original) {
        return ItemPackRendering.selectModel(stack, original.call(renderer, stack, world, entity));
    }
}
