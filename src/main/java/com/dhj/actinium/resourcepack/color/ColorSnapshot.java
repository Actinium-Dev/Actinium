package com.dhj.actinium.resourcepack.color;

import net.minecraft.block.Block;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Published color data; all lookups can be performed by chunk workers. */
record ColorSnapshot(Map<String, Integer> colors, Map<String, ColorPalette> palettes,
                     Map<Block, List<BlockColorRule>> blocks, Map<Integer, LightmapPalette.Weather> lightmaps,
                     float xpPeriod) {
    ColorSnapshot {
        colors = Map.copyOf(colors);
        palettes = Map.copyOf(palettes);
        blocks = blocks.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> List.copyOf(e.getValue())));
        lightmaps = Map.copyOf(lightmaps);
    }
}
