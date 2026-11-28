package com.dhj.actinium.gui.svg;

import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** A resource-pack SVG whose texture follows the icon's physical display size. */
public interface SvgIcon {
    /** Creates a reusable icon without loading resources or allocating a GPU texture. */
    static SvgIcon create(ResourceLocation resource) {
        return new SvgIconImpl(resource);
    }

    /**
     * Prepares a texture on the render thread for positive physical pixel dimensions.
     * Returns null after a logged resource error; resource reload retries loading the SVG.
     */
    @Nullable ResourceLocation texture(int pixelWidth, int pixelHeight);
}
