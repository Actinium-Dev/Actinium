package dhj.embeddedt.embeddium.api.options.control;

import dhj.embeddedt.embeddium.api.options.structure.Option;
import dhj.embeddedt.embeddium.impl.gui.framework.DrawContext;
import dhj.embeddedt.embeddium.impl.gui.framework.TextComponent;
import dhj.embeddedt.embeddium.impl.util.Dim2i;

import java.util.Objects;
import java.util.function.Supplier;

/** Displays live status text for a value-free option. */
public final class ReadOnlyTextControl implements Control<Void> {
    private static final int MAX_WIDTH = 120;

    private final Option<Void> option;
    private final Supplier<TextComponent> text;

    public ReadOnlyTextControl(Option<Void> option, Supplier<TextComponent> text) {
        this.option = Objects.requireNonNull(option);
        this.text = Objects.requireNonNull(text);
    }

    @Override
    public Option<Void> getOption() {
        return this.option;
    }

    /** Resolves the displayed value against the current status. */
    public TextComponent getText() {
        return this.text.get();
    }

    @Override
    public ControlElement<Void> createElement(Dim2i dim) {
        return new ControlElement<>(this.option, dim) {
            @Override
            public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
                super.render(drawContext, mouseX, mouseY, delta);

                TextComponent value = getText();
                int width = drawContext.getStringWidth(value);
                int availableWidth = Math.min(MAX_WIDTH, this.dim.width() / 2);
                if (width > availableWidth) {
                    String ellipsis = "...";
                    String shortened = drawContext.substrByWidth(drawContext.extractString(value),
                            Math.max(0, availableWidth - drawContext.getStringWidth(ellipsis)));
                    value = TextComponent.literal(shortened + ellipsis);
                    width = drawContext.getStringWidth(value);
                }
                drawContext.drawString(value, this.dim.getLimitX() - width - 6,
                        this.dim.getCenterY() - 4, 0xFFFFFFFF);
            }
        };
    }

    @Override
    public int getMaxWidth() {
        return MAX_WIDTH;
    }
}
