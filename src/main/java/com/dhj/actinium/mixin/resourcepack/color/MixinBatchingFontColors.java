package com.dhj.actinium.mixin.resourcepack.color;

import com.dhj.actinium.resourcepack.color.ResourcePackColors;
import com.gtnewhorizons.angelica.client.font.BatchingFontRenderer;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = BatchingFontRenderer.class, remap = false)
abstract class MixinBatchingFontColors {
    @Unique private Object actinium$colorRevision;
    @Unique private int[] actinium$formatColors;

    @ModifyExpressionValue(method = "drawString", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
            target = "Lcom/gtnewhorizons/angelica/client/font/BatchingFontRenderer;colorCode:[I"))
    private int[] actinium$packFormatColors(int[] original) {
        Object revision = ResourcePackColors.revision();
        if (revision == null) return original;
        if (revision != actinium$colorRevision) {
            actinium$formatColors = ResourcePackColors.fontColors(original);
            actinium$colorRevision = revision;
        }
        return actinium$formatColors;
    }
}
