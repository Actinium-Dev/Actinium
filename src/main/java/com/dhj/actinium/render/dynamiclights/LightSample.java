/*
 * Copyright © 2020 LambdAurora <email@lambdaurora.dev>
 *
 * This file is part of SodiumDynamicLights.
 *
 * Licensed under the MIT License. For more information,
 * see the LICENSE file.
 */

package com.dhj.actinium.render.dynamiclights;

import com.dhj.actinium.render.dynamiclights.LightTracker.SectionRebuild;

/** The immutable position and luminance captured from a single emitting source. */
record LightSample(double x, double y, double z, int luminance) {
    private static final double RADIUS = 7.75;
    private static final double RADIUS_SQUARED = RADIUS * RADIUS;

    int levelAt(double sampleX, double sampleY, double sampleZ) {
        double dx = sampleX - x;
        double dy = sampleY - y;
        double dz = sampleZ - z;
        double squaredDistance = dx * dx + dy * dy + dz * dz;
        if (squaredDistance >= RADIUS_SQUARED) {
            return 0;
        }
        return (int) ((1.0 - Math.sqrt(squaredDistance) / RADIUS) * luminance);
    }

    void forEachSection(int padding, SectionRebuild visit) {
        int minX = minBlock(x, padding) >> 4;
        int minY = minBlock(y, padding) >> 4;
        int minZ = minBlock(z, padding) >> 4;
        int maxX = maxBlock(x, padding) >> 4;
        int maxY = maxBlock(y, padding) >> 4;
        int maxZ = maxBlock(z, padding) >> 4;
        for (int sx = minX; sx <= maxX; sx++) {
            for (int sy = minY; sy <= maxY; sy++) {
                for (int sz = minZ; sz <= maxZ; sz++) {
                    visit.rebuild(sx, sy, sz);
                }
            }
        }
    }

    private static int minBlock(double center, int padding) {
        return (int) Math.ceil(center - RADIUS - 0.5) - padding;
    }

    private static int maxBlock(double center, int padding) {
        return (int) Math.floor(center + RADIUS - 0.5) + padding;
    }
}
