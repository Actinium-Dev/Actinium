package com.dhj.actinium.compat.sodium;

import com.dhj.actinium.gui.ActiniumGameOptionPages;
import com.dhj.actinium.gui.DynamicLightsOptionPage;
import com.dhj.actinium.gui.GraphicsOptionPage;
import com.dhj.actinium.render.dynamiclights.DynamicLights;
import com.dhj.actinium.runtime.ActiniumRuntime;
import net.irisshaders.iris.compat.sodium.IrisConfigEntryPoint;
import dhj.embeddedt.embeddium.impl.gui.SodiumGameOptions;
import dhj.embeddedt.embeddium.impl.gui.options.CommonOptionPages;
import dhj.embeddedt.embeddium.api.options.structure.OptionPage;

import java.util.ArrayList;
import java.util.List;

/**
 * Actinium's own built-in option page collection: returns embeddium
 * {@link OptionPage} forms of the Actinium video option pages and the Iris
 * compatibility pages directly, without any Sodium config model conversion.
 */
public final class ActiniumOptionPages {
    private ActiniumOptionPages() {
    }

    /** Builds all built-in pages Actinium currently exposes (embeddium model). */
    public static List<OptionPage> builtInPages() {
        return builtInPages(ActiniumRuntime.options());
    }

    /** Builds the built-in pages exposed by the supplied configuration snapshot. */
    static List<OptionPage> builtInPages(SodiumGameOptions options) {
        List<OptionPage> pages = new ArrayList<>(List.of(
                ActiniumGameOptionPages.general(),
                ActiniumGameOptionPages.quality(),
                CommonOptionPages.performance(options),
                ActiniumGameOptionPages.advanced()));
        appendGraphicsAndDebug(pages, options);
        if (DynamicLights.isBuiltIn()) {
            pages.add(DynamicLightsOptionPage.create());
        }
        pages.addAll(new IrisConfigEntryPoint().createPages());
        return List.copyOf(pages);
    }

    static void appendGraphicsAndDebug(List<OptionPage> pages, SodiumGameOptions options) {
        pages.add(GraphicsOptionPage.create(options));
        if (options.enableDebugTab) {
            pages.add(ActiniumGameOptionPages.debug());
        }
    }
}
