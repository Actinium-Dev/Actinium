package com.gtnewhorizons.angelica.api.weather;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.EntityLivingBase;

/** Supplies instanced weather columns from a dimension's Forge weather renderer. */
public interface WeatherColumnProvider {
    /** Returns whether the renderer should draw vanilla rain and snow before the custom layers. */
    boolean rendersVanillaWeather();

    /** Updates the provider for this frame and returns the tick counter used for texture animation. */
    int beginFrame(WorldClient world, EntityLivingBase view, float partialTicks);

    /** Changes whenever the layers or columns produced by {@link #collectColumns} change. */
    int columnRevision();

    /** Returns the finite positive distance, in blocks, used to scale the custom columns' fade. */
    float fadeRadius();

    /** Emits texture layers and their columns. The sink is only valid during this call. */
    void collectColumns(WeatherColumnSink sink);
}
