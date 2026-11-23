package com.dhj.actinium.resourcepack.item;

import com.dhj.actinium.resourcepack.ResourcePackReload;

/** Registers the item and block-atlas resource pack modules. */
public final class ResourcePackItemFeatures {
    private static ResourcePackReload.Handle<AtlasEmissive> emissive;
    private static ResourcePackReload.Handle<CitSnapshot> cit;

    private ResourcePackItemFeatures() {}

    public static void register() {
        emissive = ResourcePackReload.register(new AtlasEmissiveModule());
        cit = ResourcePackReload.register(new CitModule());
    }

    /** Captures the current atlas mapper, or null when emissive textures are disabled. */
    public static AtlasEmissive atlasEmissive() {
        return emissive == null ? null : emissive.get();
    }

    static CitSnapshot cit() {
        return cit == null ? null : cit.get();
    }
}
