package com.dhj.actinium.mixin.resourcepack.item;

import com.dhj.actinium.resourcepack.item.CitModelState;
import com.dhj.actinium.resourcepack.item.ItemPackRendering;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHandSide;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = RenderItem.class, priority = 1100)
abstract class MixinResourcePackRenderItem {
    @Shadow private void renderModel(IBakedModel model, int color) { throw new AssertionError(); }

    @WrapMethod(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;Z)V")
    private void actinium$heldHand(ItemStack stack, EntityLivingBase entity, TransformType transform, boolean left,
                                  Operation<Void> original) {
        ItemPackRendering.withPerspective(transform, entity == null ? EnumHandSide.RIGHT : entity.getPrimaryHand(),
            () -> original.call(stack, entity, transform, left));
    }

    @WrapMethod(method = "renderItemModelIntoGUI")
    private void actinium$guiHand(ItemStack stack, int x, int y, IBakedModel model, Operation<Void> original) {
        ItemPackRendering.withPerspective(TransformType.GUI, EnumHandSide.RIGHT, () -> original.call(stack, x, y, model));
    }

    @WrapMethod(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;)V")
    private void actinium$itemPerspective(ItemStack stack, TransformType transform, Operation<Void> original) {
        ItemPackRendering.withPerspective(transform, EnumHandSide.RIGHT, () -> original.call(stack, transform));
    }

    @WrapOperation(method = {"renderItemModel", "renderItemModelIntoGUI"}, at = @At(value = "INVOKE", target =
        "Lnet/minecraftforge/client/ForgeHooksClient;handleCameraTransforms(Lnet/minecraft/client/renderer/block/model/IBakedModel;Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;Z)Lnet/minecraft/client/renderer/block/model/IBakedModel;", remap = false))
    private IBakedModel actinium$selectPerspectiveModel(IBakedModel model, TransformType transform, boolean left,
                                                       Operation<IBakedModel> original, @Local(argsOnly = true) ItemStack stack) {
        return original.call(ItemPackRendering.selectModel(stack, model), transform, left);
    }

    @WrapOperation(method = "getItemModelWithOverrides", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/renderer/block/model/ItemOverrideList;handleItemState(Lnet/minecraft/client/renderer/block/model/IBakedModel;Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/EntityLivingBase;)Lnet/minecraft/client/renderer/block/model/IBakedModel;", remap = false))
    private IBakedModel actinium$resolveModelState(ItemOverrideList overrides, IBakedModel base, ItemStack stack,
                                                  World world, EntityLivingBase entity, Operation<IBakedModel> original) {
        return CitModelState.resolve(overrides, base, stack, () -> original.call(overrides, base, stack, world, entity));
    }

    @WrapOperation(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/IBakedModel;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderItem;renderModel(Lnet/minecraft/client/renderer/block/model/IBakedModel;Lnet/minecraft/item/ItemStack;)V"))
    private void actinium$renderPackQuads(RenderItem renderer, IBakedModel model, ItemStack stack, Operation<Void> original) {
        IBakedModel selected = ItemPackRendering.drawModel(stack, ItemPackRendering.selectModelForDraw(stack, model));
        original.call(renderer, selected, stack);
        ItemPackRendering.drawEmissive(renderer, stack, selected);
    }

    @WrapOperation(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/IBakedModel;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;hasEffect()Z"))
    private boolean actinium$hasPackGlint(ItemStack stack, Operation<Boolean> original) {
        return ItemPackRendering.shouldDrawGlint(stack, original.call(stack));
    }

    @WrapOperation(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/IBakedModel;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderItem;renderEffect(Lnet/minecraft/client/renderer/block/model/IBakedModel;)V"))
    private void actinium$renderPackGlint(RenderItem renderer, IBakedModel model, Operation<Void> original,
                                         @Local(argsOnly = true) ItemStack stack) {
        IBakedModel selected = ItemPackRendering.drawModel(stack, ItemPackRendering.selectModelForDraw(stack, model));
        if (!ItemPackRendering.drawGlint(stack, () -> renderModel(selected, -1))) original.call(renderer, selected);
    }
}
