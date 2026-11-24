package com.gtnewhorizons.angelica.api.weather;

import net.minecraft.util.ResourceLocation;

/** Receives ordered texture layers and the columns drawn in them. */
public interface WeatherColumnSink {
    /** Starts a texture layer with a power-of-two scroll period between 1 and 1024 ticks. */
    void beginLayer(ResourceLocation texture, int scrollPeriod, float uDriftScale, float vDriftScale, float fadeScale);

    /** Adds a column in world coordinates with packed lightmap coordinates, UV animation and color. */
    void column(int x, int z, float bottom, float top, float uBase, float uDrift, float vBase, float vDrift,
                int brightness, int scrollOffset, float scrollScale, float r, float g, float b, float a);
}
