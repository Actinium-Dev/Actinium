package com.dhj.actinium.mixin.resourcepack.color;

import com.dhj.actinium.resourcepack.color.ResourcePackColors;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.FontRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FontRenderer.class)
abstract class MixinFontColors {
    @Unique private Object actinium$colorRevision;
    @Unique private int[] actinium$formatColors;

    @WrapOperation(method = {"renderStringAtPos", "getColorCode"}, at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/gui/FontRenderer;colorCode:[I", opcode = Opcodes.GETFIELD))
    private int[] actinium$formatColors(FontRenderer renderer, Operation<int[]> original) {
        Object current = ResourcePackColors.revision();
        if (current == null) return original.call(renderer);
        if (current != actinium$colorRevision) {
            actinium$formatColors = ResourcePackColors.fontColors(original.call(renderer));
            actinium$colorRevision = current;
        }
        return actinium$formatColors;
    }
}
