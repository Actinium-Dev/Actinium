package com.dhj.actinium.mixin.resourcepack.color;

import com.dhj.actinium.resourcepack.color.ResourcePackColors;
import net.minecraft.client.gui.GuiIngame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(GuiIngame.class)
abstract class MixinGuiColorText {
    @ModifyConstant(method = "renderExpBar", constant = @Constant(intValue = 8453920))
    private int actinium$experienceText(int original) {
        return ResourcePackColors.color("text.xpbar", original);
    }
}
