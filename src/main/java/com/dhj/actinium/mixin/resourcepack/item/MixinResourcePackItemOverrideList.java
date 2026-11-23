package com.dhj.actinium.mixin.resourcepack.item;

import com.dhj.actinium.resourcepack.item.CitModelState;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.block.model.ModelManager;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemOverrideList.class)
abstract class MixinResourcePackItemOverrideList {
    @WrapOperation(method = "handleItemState", remap = false, at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/renderer/block/model/ModelManager;getModel(Lnet/minecraft/client/renderer/block/model/ModelResourceLocation;)Lnet/minecraft/client/renderer/block/model/IBakedModel;", remap = true))
    private IBakedModel actinium$recordOverrideModel(ModelManager manager, ModelResourceLocation location,
                                                      Operation<IBakedModel> original, @Local(argsOnly = true) ItemStack stack) {
        IBakedModel model = original.call(manager, location);
        CitModelState.record((ItemOverrideList) (Object) this, stack, location, model);
        return model;
    }
}
