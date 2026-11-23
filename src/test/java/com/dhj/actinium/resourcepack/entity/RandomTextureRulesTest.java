package com.dhj.actinium.resourcepack.entity;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class RandomTextureRulesTest {
    @Test
    void legacyRulesUseSpawnBiomeAndHeightInNumericOrder() throws IOException {
        var rules = RandomTextureRules.parse(props("""
            skins.10=4
            skins.2=2
            biomes.2=Forest
            minHeight.2=64
            maxHeight.2=90
            """), textures(), message -> fail(message));
        assertEquals(2, rules.select(subject(99, "minecraft:forest", 65, null, 20, "clear")));
        assertEquals(4, rules.select(subject(99, "minecraft:forest", 91, null, 20, "clear")));
        assertEquals(4, rules.select(subject(99, "minecraft:plains", 65, null, 20, "clear")));
    }

    @Test
    void sameIdentityChangesOnlyWhenLiveConditionsChange() throws IOException {
        var rules = RandomTextureRules.parse(props("""
            textures.1=2 4
            weights.1=0 12
            name.1=ipattern:Ash*
            health.1=0-50%
            weather.1=rain thunder
            """), textures(), message -> fail(message));
        assertEquals(4, rules.select(subject(19, "minecraft:forest", 70, "ashwolf", 10, "rain")));
        assertEquals(1, rules.select(subject(19, "minecraft:forest", 70, "ashwolf", 10, "clear")));
        assertEquals(1, rules.select(subject(19, "minecraft:forest", 70, "ashwolf", 11, "rain")));
    }

    @Test
    void reportsInvalidRulesAndStillUsesLaterValidRules() throws IOException {
        var diagnostics = new ArrayList<String>();
        var rules = RandomTextureRules.parse(props("textures.1=2-4\ntextures.3=4\n"), textures(), diagnostics::add);
        assertEquals(4, rules.select(subject(0, "minecraft:forest", 70, null, 20, "clear")));
        assertEquals(1, diagnostics.size());
        assertTrue(diagnostics.getFirst().contains("Missing texture"));
    }

    @Test
    void implicitVariantsUseOnlyAvailableIndices() {
        var rules = RandomTextureRules.parse(null, textures(), message -> fail(message));
        assertEquals(1, rules.select(subject(0, "minecraft:forest", 64, null, 20, "clear")));
        assertEquals(4, rules.select(subject(2, "minecraft:forest", 64, null, 20, "clear")));
        assertEquals(4, rules.select(subject(2, "minecraft:plains", 70, "Wolf", 10, "rain")));
    }

    private static TreeSet<Integer> textures() { return new TreeSet<>(List.of(1, 2, 4)); }

    @Test
    void careerNamesAndNumbersSelectTheSameVillagerTexture() throws IOException {
        var named = RandomTextureRules.parse(props("textures.1=2\nprofessions.1=farmer:fisherman,shepherd blacksmith:armor\n"),
            textures(), message -> fail(message));
        var numeric = RandomTextureRules.parse(props("textures.1=2\nprofessions.1=0:2,3 3:1\n"), textures(), message -> fail(message));
        for (var rules : List.of(named, numeric)) {
            assertEquals(2, rules.select(villager("farmer", 2)));
            assertEquals(2, rules.select(villager("farmer", 3)));
            assertEquals(2, rules.select(villager("smith", 1)));
            assertEquals(1, rules.select(villager("farmer", 1)));
            assertEquals(1, rules.select(villager("librarian", 2)));
        }
    }

    @Test
    void unknownCareerSkipsQualifiedRuleButStillMatchesProfession() throws IOException {
        var rules = RandomTextureRules.parse(props("""
            textures.1=2
            professions.1=farmer:fisherman
            textures.2=4
            professions.2=farmer
            """), textures(), message -> fail(message));
        assertEquals(4, rules.select(villager("farmer", 0)));
        assertEquals(2, rules.select(villager("farmer", 2)));
        assertEquals(1, rules.select(villager("librarian", 0)));
    }

    private static RandomTextureRules.Subject villager(String profession, int career) {
        return new RandomTextureRules.Subject(0, "minecraft:plains", 70, null, "minecraft:" + profession, career,
            null, 20, 20, 0, 0, "clear", -1, false);
    }

    private static Properties props(String content) throws IOException {
        var result = new Properties();
        result.load(new StringReader(content));
        return result;
    }

    private static RandomTextureRules.Subject subject(int seed, String biome, int y, String name, float health, String weather) {
        return new RandomTextureRules.Subject(seed, biome, y, name, null, -1, null, health, 20, 0, 0, weather, -1, false);
    }
}
