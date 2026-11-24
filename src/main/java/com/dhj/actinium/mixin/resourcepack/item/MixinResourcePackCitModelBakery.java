package com.dhj.actinium.mixin.resourcepack.item;

import com.dhj.actinium.resourcepack.item.CitPaths;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.io.Reader;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelBlock;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ModelBakery.class)
abstract class MixinResourcePackCitModelBakery {
    @WrapOperation(method = "loadModel", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/renderer/block/model/ModelBakery;getModelLocation(Lnet/minecraft/util/ResourceLocation;)Lnet/minecraft/util/ResourceLocation;"))
    private ResourceLocation actinium$resolveCitModel(ModelBakery bakery, ResourceLocation model, Operation<ResourceLocation> original) {
        ResourceLocation file = CitPaths.modelFile(model);
        return file == null ? original.call(bakery, model) : file;
    }

    @WrapOperation(method = "loadModel", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/renderer/block/model/ModelBlock;deserialize(Ljava/io/Reader;)Lnet/minecraft/client/renderer/block/model/ModelBlock;"))
    private ModelBlock actinium$resolveCitModelTextures(Reader reader, Operation<ModelBlock> original,
                                                        @Local(argsOnly = true) ResourceLocation model) {
        ModelBlock result = original.call(reader);
        CitPaths.rewriteModelTextures(model, result);
        return result;
    }
}
