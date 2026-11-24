package dhj.embeddedt.embeddium.impl.gui;

import dhj.embeddedt.embeddium.impl.gui.framework.TextComponent;
import dhj.embeddedt.embeddium.impl.gui.options.TextProvider;

/** Controls sorting and layer compositing for translucent geometry. */
public enum TranslucencyMode implements TextProvider {
    OFF("options.off"),
    FAST("options.graphics.fast"),
    FANCY("options.graphics.fancy");

    private final TextComponent name;

    TranslucencyMode(String translationKey) {
        this.name = TextComponent.translatable(translationKey);
    }

    @Override
    public TextComponent getLocalizedName() {
        return this.name;
    }

    public boolean sorts() {
        return this != OFF;
    }

    public boolean composites() {
        return this == FANCY;
    }
}
