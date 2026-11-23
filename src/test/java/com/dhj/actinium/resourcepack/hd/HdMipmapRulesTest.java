package com.dhj.actinium.resourcepack.hd;

import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class HdMipmapRulesTest {
    @Test
    void pngOverridesTakePrecedenceOverDirectoryDefaults() {
        var rules = rules(Map.of("/textures/gui/menu.png", " TRUE ",
            "example:textures/entity/pig.png", "false", "scratch", "false"));

        assertTrue(rules.textureEnabled(new ResourceLocation("textures/gui/menu.png")));
        assertFalse(rules.textureEnabled(new ResourceLocation("textures/gui/other.png")));
        assertFalse(rules.textureEnabled(new ResourceLocation("example:textures/entity/pig.png")));
        assertTrue(rules.textureEnabled(new ResourceLocation("textures/entity/pig.png")));
        assertFalse(rules.textureEnabled(new ResourceLocation("textures/items/stick.png")));
        assertFalse(rules.textureEnabled(new ResourceLocation("mcpatcher/sky/world0/clouds.png")));
        assertFalse(rules.textureEnabled(new ResourceLocation("optifine/cit/sword.png")));
        assertFalse(rules.textureEnabled(new ResourceLocation("title/mojang.png")));
    }

    @Test
    void resolvesTheSharedAtlasBeforeLegacyTerrainAndIgnoresNonPngKeys() {
        assertTrue(rules(Map.of("terrain", "false", "items.png", "false")).atlasEnabled());
        assertFalse(rules(Map.of("terrain.png", "false")).atlasEnabled());
        assertTrue(rules(Map.of("terrain.png", "false", "textures/atlas/blocks.png", "true")).atlasEnabled());
    }

    @Test
    void reportsInvalidValuesWithoutReplacingDefaults() {
        var invalid = new ArrayList<String>();
        var properties = new Properties();
        properties.setProperty("textures/entity/pig.png", "maybe");
        var rules = HdMipmapRules.parse(properties, (key, value) -> invalid.add(key + "=" + value));

        assertEquals(List.of("textures/entity/pig.png=maybe"), invalid);
        assertTrue(rules.textureEnabled(new ResourceLocation("textures/entity/pig.png")));
    }

    @Test
    void limitsRectangularMipChainsByDimensionsAndSettings() {
        assertEquals(2, HdMipmapRules.imageLevels(12, 20, 4));
        assertEquals(0, HdMipmapRules.imageLevels(15, 20, 4));
        assertEquals(0, HdMipmapRules.imageLevels(12, 20, 0));
    }

    @Test
    void generatesAllLevelsOfANonSquareTexture() {
        int[] pixels = new int[12 * 20];
        Arrays.fill(pixels, 0xffffffff);
        var chain = HdMipmapTextures.generateMipmaps(pixels, 12, 2);

        assertEquals(List.of(240, 60, 15), Arrays.stream(chain).map(level -> level.length).toList());
        assertTrue(Arrays.stream(chain[2]).allMatch(pixel -> (pixel >>> 24) == 255));
    }

    @Test
    void preservesOpaqueEdgesWithoutMixingColorsOfTransparentTexels() {
        int[] pixels = {0xff40a020, 0x00ff0000, 0x00ffffff, 0x000000ff};

        assertEquals(pixels[0], HdMipmapTextures.generateMipmaps(pixels, 2, 1)[1][0]);
    }

    private static HdMipmapRules rules(Map<String, String> entries) {
        var properties = new Properties();
        properties.putAll(entries);
        return HdMipmapRules.parse(properties, (key, value) -> fail("Unexpected invalid rule: " + key + "=" + value));
    }
}
