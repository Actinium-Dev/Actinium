package com.dhj.actinium.gui;

import com.dhj.actinium.runtime.ActiniumRuntime;
import dhj.embeddedt.embeddium.api.options.OptionIdentifier;
import dhj.embeddedt.embeddium.api.options.control.ExternalButtonControl;
import dhj.embeddedt.embeddium.api.options.structure.Option;
import dhj.embeddedt.embeddium.api.options.structure.OptionGroup;
import dhj.embeddedt.embeddium.api.options.structure.OptionPage;
import dhj.embeddedt.embeddium.api.options.structure.StandardOptions;
import dhj.embeddedt.embeddium.impl.gui.SodiumGameOptions;
import dhj.embeddedt.embeddium.impl.render.chunk.MultiDrawMode;
import dhj.embeddedt.embeddium.impl.render.culling.GpuCullingMode;
import me.flashyreese.mods.reeses_sodium_options.client.gui.option.RsoOption;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Builds the real ADVANCED page so every option id it registers is checked against the
 * {@link OptionIdentifier} registry. A group id that repeats a member option path makes that
 * registry reject the second type, which aborts the page build instead of the page appearing.
 */
class ActiniumGameOptionPagesAdvancedTest {
    @Test
    void resetUsesModesThatTheDeviceCanRun() {
        SodiumGameOptions config = new SodiumGameOptions();
        config.advanced.multiDrawMode = MultiDrawMode.INDIVIDUAL;
        OptionPage page = ActiniumGameOptionPages.advanced(config, () -> false, () -> false, () -> false);
        RsoOption draw = row(page, StandardOptions.Option.MULTIDRAW_MODE);
        RsoOption cull = row(page, OptionIdentifier.create("actinium", "gpu_culling_mode", GpuCullingMode.class));

        draw.resetToDefault();
        cull.resetToDefault();

        assertEquals(MultiDrawMode.DIRECT, draw.getPendingValue());
        assertTrue(draw.isValueAllowed(draw.getPendingValue()));
        assertFalse(draw.isValueAllowed(MultiDrawMode.INDIRECT));
        assertFalse(cull.isEnabled());
        assertEquals(GpuCullingMode.CPU_ONLY, cull.getDefaultValue());
        assertEquals(GpuCullingMode.CPU_ONLY, cull.getPendingValue());
    }

    @Test
    void applyingDirectDrawAlsoSavesThePendingCullingChoice() {
        SodiumGameOptions config = new SodiumGameOptions();
        config.advanced.multiDrawMode = MultiDrawMode.INDIRECT;
        config.performance.gpuCullingMode = GpuCullingMode.COMPUTE;
        OptionPage page = ActiniumGameOptionPages.advanced(config, () -> true,
                () -> config.advanced.multiDrawMode == MultiDrawMode.INDIRECT, () -> true);
        RsoOption draw = row(page, StandardOptions.Option.MULTIDRAW_MODE);
        RsoOption cull = row(page, OptionIdentifier.create("actinium", "gpu_culling_mode", GpuCullingMode.class));

        cull.modifyValue(GpuCullingMode.CPU_ONLY);
        draw.modifyValue(MultiDrawMode.DIRECT);
        page.getOptions().stream().filter(Option::hasChanged).forEach(Option::applyChanges);

        assertFalse(cull.isEnabled());
        assertEquals(MultiDrawMode.DIRECT, config.advanced.multiDrawMode);
        assertEquals(GpuCullingMode.CPU_ONLY, config.performance.gpuCullingMode);
    }

    private static RsoOption row(OptionPage page, OptionIdentifier<?> id) {
        return page.getOptions().stream().filter(option -> option.getId().equals(id))
                .map(RsoOption::new).findFirst().orElseThrow();
    }

    @Test
    void entityBatchingGroupUsesItsOwnIdentifier() {
        OptionGroup group = entityBatchingGroup(ActiniumGameOptionPages.advanced());

        assertEquals(ActiniumRuntime.MODID, group.getId().getModId());

        for (Option<?> option : group.getOptions()) {
            assertNotEquals(option.getId().getPath(), group.getId().getPath(),
                    "group id repeats the option path " + option.getId());
        }
    }

    @Test
    void shadowReplayExclusionEditorIsInTheEntityBatchingGroup() {
        OptionGroup group = entityBatchingGroup(ActiniumGameOptionPages.advanced());

        assertTrue(group.getOptions().stream().map(Option::getControl).anyMatch(ExternalButtonControl.class::isInstance),
                "the entity batching group must expose the exclusion list editor");
    }

    private static OptionGroup entityBatchingGroup(OptionPage page) {
        OptionIdentifier<Boolean> batching = OptionIdentifier.create(ActiniumRuntime.MODID, "entity_batching", boolean.class);

        return page.getGroups().stream()
                .filter(group -> group.getOptions().stream().anyMatch(option -> batching.equals(option.getId())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("advanced page has no entity batching group"));
    }
}
