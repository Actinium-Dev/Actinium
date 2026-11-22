package com.dhj.actinium.compat.sodium;

import dhj.embeddedt.embeddium.api.options.OptionIdentifier;
import dhj.embeddedt.embeddium.api.options.structure.OptionPage;
import dhj.embeddedt.embeddium.api.options.structure.StandardOptions;
import dhj.embeddedt.embeddium.impl.gui.SodiumGameOptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActiniumOptionPagesTest {
    @Test
    void debugPageIsDroppedAndOffByDefault() {
        SodiumGameOptions options = new SodiumGameOptions();

        assertEquals(List.of(StandardOptions.Pages.GRAPHICS), appendedPageIds(options));
    }

    @Test
    void debugPageIsKeptWhileEnabled() {
        SodiumGameOptions options = new SodiumGameOptions();
        options.enableDebugTab = true;

        assertEquals(List.of(StandardOptions.Pages.GRAPHICS, StandardOptions.Pages.DEBUG),
                appendedPageIds(options));
    }

    private static List<OptionIdentifier<Void>> appendedPageIds(SodiumGameOptions options) {
        List<OptionPage> pages = new ArrayList<>();
        ActiniumOptionPages.appendGraphicsAndDebug(pages, options);
        return pages.stream().map(OptionPage::getId).toList();
    }
}
