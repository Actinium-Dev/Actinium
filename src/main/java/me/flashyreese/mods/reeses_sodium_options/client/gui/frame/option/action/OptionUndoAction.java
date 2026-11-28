package me.flashyreese.mods.reeses_sodium_options.client.gui.frame.option.action;

import com.dhj.actinium.gui.svg.SvgIcon;
import me.flashyreese.mods.reeses_sodium_options.client.config.ReeseSodiumOptionsConfig;
import me.flashyreese.mods.reeses_sodium_options.client.gui.option.RsoOption;
import net.minecraft.util.ResourceLocation;

import java.util.Objects;

public final class OptionUndoAction {
    static final SvgIcon ICON = SvgIcon.create(new ResourceLocation("reeses-sodium-options", "textures/gui/undo_to_unmodified.svg"));

    public static boolean isVisible(RsoOption option) {
        return ReeseSodiumOptionsConfig.config().isUndoButtonOverlay()
                && (ReeseSodiumOptionsConfig.config().isAlwaysShowActionButtons() || canUndo(option));
    }

    public static boolean isActive(RsoOption option) {
        return ReeseSodiumOptionsConfig.config().isUndoButtonOverlay()
                && canUndo(option);
    }

    public static boolean canUndo(RsoOption option) {
        return option.isEnabled()
                && option.hasChanged()
                && !Objects.equals(option.getPendingValue(), option.getAppliedValue());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void undoChanges(RsoOption option) {
        option.modifyValue(option.getAppliedValue());
    }

    public static void normalizeEquivalentChange(RsoOption option) {
        if (option.hasChanged() && Objects.equals(option.getPendingValue(), option.getAppliedValue())) {
            undoChanges(option);
        }
    }

}
