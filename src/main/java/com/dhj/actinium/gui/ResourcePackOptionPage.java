package com.dhj.actinium.gui;

import com.google.common.collect.ImmutableList;
import dhj.embeddedt.embeddium.api.options.OptionIdentifier;
import dhj.embeddedt.embeddium.api.options.control.TickBoxControl;
import dhj.embeddedt.embeddium.api.options.structure.OptionFlag;
import dhj.embeddedt.embeddium.api.options.structure.OptionGroup;
import dhj.embeddedt.embeddium.api.options.structure.OptionImpl;
import dhj.embeddedt.embeddium.api.options.structure.OptionPage;
import dhj.embeddedt.embeddium.impl.gui.SodiumGameOptions;
import dhj.embeddedt.embeddium.impl.gui.SodiumGameOptions.ResourcePackSettings;
import dhj.embeddedt.embeddium.impl.gui.framework.TextComponent;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

/** Exposes resource pack rule families through the standard asset reload action. */
public final class ResourcePackOptionPage {
    private ResourcePackOptionPage() {
    }

    public static OptionPage create(SodiumGameOptions options) {
        var group = OptionGroup.createBuilder()
                .setId(OptionIdentifier.create("actinium", "resource_pack_features"))
                .add(toggle(options, "connected_textures", true, s -> s.connectedTextures, (s, v) -> s.connectedTextures = v))
                .add(toggle(options, "ctm_mod_textures", true, s -> s.ctmModTextures, (s, v) -> s.ctmModTextures = v))
                .add(toggle(options, "ctm_connect_inside", false, s -> s.ctmConnectInside, (s, v) -> s.ctmConnectInside = v))
                .add(toggle(options, "emissive", true, s -> s.emissive, (s, v) -> s.emissive = v))
                .add(toggle(options, "cit", true, s -> s.cit, (s, v) -> s.cit = v))
                .add(toggle(options, "custom_colors", true, s -> s.customColors, (s, v) -> s.customColors = v))
                .add(toggle(options, "random_entities", true, s -> s.randomEntities, (s, v) -> s.randomEntities = v))
                .add(toggle(options, "custom_sky", true, s -> s.customSky, (s, v) -> s.customSky = v))
                .add(toggle(options, "extended_textures", true, s -> s.extendedTextures, (s, v) -> s.extendedTextures = v))
                .build();
        return new OptionPage(OptionIdentifier.create("actinium", "resource_packs"),
                TextComponent.translatable("options.actinium.resource_packs"), ImmutableList.of(group));
    }

    private static OptionImpl<SodiumGameOptions, Boolean> toggle(SodiumGameOptions options, String id,
            boolean defaultValue, Predicate<ResourcePackSettings> get, BiConsumer<ResourcePackSettings, Boolean> set) {
        String key = "options.actinium.resource_packs." + id;
        return OptionImpl.createBuilder(boolean.class, options)
                .setId(OptionIdentifier.create("actinium", "packs_" + id, boolean.class))
                .setName(TextComponent.translatable(key))
                .setTooltip(TextComponent.translatable(key + ".tooltip"))
                .setBinding((opts, value) -> set.accept(opts.resourcePacks, value), opts -> get.test(opts.resourcePacks))
                .setControl(TickBoxControl::new)
                .setFlags(OptionFlag.REQUIRES_ASSET_RELOAD)
                .setDefaultValue(defaultValue)
                .build();
    }
}
