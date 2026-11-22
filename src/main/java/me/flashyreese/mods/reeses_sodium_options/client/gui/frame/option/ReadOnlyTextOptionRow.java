package me.flashyreese.mods.reeses_sodium_options.client.gui.frame.option;

import com.dhj.actinium.gui.rso.compat.Component;
import com.dhj.actinium.gui.rso.compat.GuiGraphicsExtractor;
import dhj.embeddedt.embeddium.api.options.control.ReadOnlyTextControl;
import me.flashyreese.mods.reeses_sodium_options.client.gui.layout.LayoutBounds;
import me.flashyreese.mods.reeses_sodium_options.client.gui.option.RsoOption;
import me.flashyreese.mods.reeses_sodium_options.client.gui.state.OptionStateStore;
import me.flashyreese.mods.reeses_sodium_options.client.gui.theme.GuiTheme;

final class ReadOnlyTextOptionRow extends AbstractOptionRow {
    private final ReadOnlyTextControl control;
    private Component visibleText = Component.empty();

    ReadOnlyTextOptionRow(LayoutBounds dim, GuiTheme theme, OptionStateStore optionStateStore, RsoOption option, ReadOnlyTextControl control) {
        super(dim, theme, optionStateStore, option);
        this.control = control;
    }

    @Override
    protected void prepareRender(int mouseX, int mouseY, float delta) {
        Component text = this.narrationValue();
        int width = Math.min(this.control.getMaxWidth(), this.getWidth() / 2);
        this.visibleText = this.font.width(text) <= width
                ? text
                : Component.literal(this.truncateTextToFit(text.getString(), width));
    }

    @Override
    protected int controlContentWidth() {
        return this.font.width(this.visibleText);
    }

    @Override
    protected void renderControl(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float delta) {
        this.drawString(guiGraphics, this.visibleText, this.rightAlignedControlX(this.controlContentWidth()),
                this.centeredTextY(), 0xFFFFFFFF);
    }

    @Override
    protected boolean activateControl() {
        return false;
    }

    @Override
    protected Component narrationValue() {
        return Component.fromEmbeddium(this.control.getText());
    }

    @Override
    protected boolean optionShowsControl() {
        return false;
    }
}
