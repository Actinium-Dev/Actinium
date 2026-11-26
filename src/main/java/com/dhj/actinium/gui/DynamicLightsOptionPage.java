package com.dhj.actinium.gui;

import com.dhj.actinium.render.dynamiclights.DynamicLights;
import com.dhj.actinium.render.dynamiclights.DynamicLightsSettings;
import com.dhj.actinium.render.dynamiclights.DynamicLightsSettings.ExplosiveMode;
import com.dhj.actinium.render.dynamiclights.DynamicLightsSettings.Mode;
import com.google.common.collect.ImmutableList;
import dhj.embeddedt.embeddium.api.options.OptionIdentifier;
import dhj.embeddedt.embeddium.api.options.control.ControlValueFormatter;
import dhj.embeddedt.embeddium.api.options.control.CyclingControl;
import dhj.embeddedt.embeddium.api.options.control.SliderControl;
import dhj.embeddedt.embeddium.api.options.control.TickBoxControl;
import dhj.embeddedt.embeddium.api.options.structure.OptionGroup;
import dhj.embeddedt.embeddium.api.options.structure.OptionImpl;
import dhj.embeddedt.embeddium.api.options.structure.OptionPage;
import dhj.embeddedt.embeddium.api.options.structure.OptionStorage;
import dhj.embeddedt.embeddium.impl.gui.framework.TextComponent;
import dhj.embeddedt.embeddium.impl.gui.framework.TextFormattingStyle;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Builds the built-in dynamic lights tab with its own configuration storage. */
public final class DynamicLightsOptionPage {
    private static final String MOD_ID = "actinium";
    private static final String PREFIX = "actinium.dynamic_lights.";

    private DynamicLightsOptionPage() {
    }

    public static OptionPage create() {
        OptionStorage<DynamicLightsSettings> storage = new OptionStorage<>() {
            @Override
            public DynamicLightsSettings getData() {
                return DynamicLights.settings();
            }

            @Override
            public void save() {
                getData().save();
                DynamicLights.settingsChanged();
            }
        };

        OptionGroup sources = OptionGroup.createBuilder()
                .setId(id("sources"))
                .add(option("mode", Mode.class, storage)
                        .setControl(option -> new CyclingControl<>(option, Mode.class,
                                Arrays.stream(Mode.values()).map(DynamicLightsOptionPage::modeName).toArray(TextComponent[]::new)))
                        .setBinding((settings, value) -> settings.mode = value, settings -> settings.mode)
                        .build())
                .add(toggle("self", storage, (settings, value) -> settings.selfLightSource = value, settings -> settings.selfLightSource))
                .add(toggle("entities", storage, (settings, value) -> settings.entitiesLightSource = value, settings -> settings.entitiesLightSource))
                .add(toggle("blockentities", storage, (settings, value) -> settings.blockEntitiesLightSource = value, settings -> settings.blockEntitiesLightSource))
                .add(toggle("underwater", storage, (settings, value) -> settings.waterSensitiveCheck = value, settings -> settings.waterSensitiveCheck))
                .add(explosive("tnt", storage, (settings, value) -> settings.tntLightingMode = value, settings -> settings.tntLightingMode))
                .add(explosive("creeper", storage, (settings, value) -> settings.creeperLightingMode = value, settings -> settings.creeperLightingMode))
                .build();

        OptionGroup scheduling = OptionGroup.createBuilder()
                .setId(id("scheduling"))
                .add(toggle("adaptive_ticking", storage, (settings, value) -> settings.adaptiveTicking = value, settings -> settings.adaptiveTicking))
                .add(slider("slow_distance", storage, 16, 64, 8, "blocks",
                        (settings, value) -> settings.slowDistance = value, settings -> settings.slowDistance))
                .add(slider("slower_distance", storage, 32, 128, 16, "blocks",
                        (settings, value) -> settings.slowerDistance = value, settings -> settings.slowerDistance))
                .add(slider("background_distance", storage, 64, 256, 16, "blocks",
                        (settings, value) -> settings.backgroundDistance = value, settings -> settings.backgroundDistance))
                .add(toggle("frustum_culling", storage, (settings, value) -> settings.frustumCulling = value, settings -> settings.frustumCulling))
                .add(slider("max_wait_ticks", storage, 20, 200, 20, "ticks",
                        (settings, value) -> settings.maxWaitTicks = value, settings -> settings.maxWaitTicks))
                .build();

        return new OptionPage(id("page"), TextComponent.translatable(PREFIX + "page"), ImmutableList.of(sources, scheduling));
    }

    private static OptionIdentifier<Void> id(String name) {
        return OptionIdentifier.create(MOD_ID, "dynamic_lights_" + name);
    }

    private static <T> OptionImpl.Builder<DynamicLightsSettings, T> option(String name, Class<T> type,
                                                                           OptionStorage<DynamicLightsSettings> storage) {
        return OptionImpl.createBuilder(type, storage)
                .setId(OptionIdentifier.create(MOD_ID, "dynamic_lights_" + name, type))
                .setName(TextComponent.translatable(PREFIX + name))
                .setTooltip(TextComponent.translatable(PREFIX + name + ".desc"));
    }

    private static OptionImpl<DynamicLightsSettings, Boolean> toggle(String name, OptionStorage<DynamicLightsSettings> storage,
                                                                     BiConsumer<DynamicLightsSettings, Boolean> setter,
                                                                     Function<DynamicLightsSettings, Boolean> getter) {
        return option(name, Boolean.class, storage).setControl(TickBoxControl::new).setBinding(setter, getter).build();
    }

    private static OptionImpl<DynamicLightsSettings, Integer> slider(String name, OptionStorage<DynamicLightsSettings> storage,
                                                                     int min, int max, int step, String unit,
                                                                     BiConsumer<DynamicLightsSettings, Integer> setter,
                                                                     Function<DynamicLightsSettings, Integer> getter) {
        return option(name, Integer.class, storage)
                .setControl(option -> new SliderControl(option, min, max, step, ControlValueFormatter.translateVariable(PREFIX + unit)))
                .setBinding(setter, getter)
                .build();
    }

    private static OptionImpl<DynamicLightsSettings, ExplosiveMode> explosive(String name, OptionStorage<DynamicLightsSettings> storage,
                                                                             BiConsumer<DynamicLightsSettings, ExplosiveMode> setter,
                                                                             Function<DynamicLightsSettings, ExplosiveMode> getter) {
        TextComponent[] names = Arrays.stream(ExplosiveMode.values()).map(DynamicLightsOptionPage::explosiveName).toArray(TextComponent[]::new);
        return option(name, ExplosiveMode.class, storage)
                .setTooltip(TextComponent.translatable(PREFIX + name + ".desc", (Object[]) names))
                .setControl(option -> new CyclingControl<>(option, ExplosiveMode.class, names))
                .setBinding(setter, getter)
                .build();
    }

    private static TextComponent modeName(Mode mode) {
        return valueName(mode, switch (mode) {
            case OFF -> TextFormattingStyle.RED;
            case SLOW -> TextFormattingStyle.YELLOW;
            case FAST -> TextFormattingStyle.GOLD;
            case REALTIME -> TextFormattingStyle.GREEN;
        });
    }

    private static TextComponent explosiveName(ExplosiveMode mode) {
        return valueName(mode, switch (mode) {
            case OFF -> TextFormattingStyle.RED;
            case SIMPLE -> TextFormattingStyle.YELLOW;
            case FANCY -> TextFormattingStyle.GREEN;
        });
    }

    private static TextComponent valueName(Enum<?> mode, TextFormattingStyle style) {
        return TextComponent.translatable(PREFIX + "value." + mode.name().toLowerCase(Locale.ROOT)).withStyle(style);
    }
}
