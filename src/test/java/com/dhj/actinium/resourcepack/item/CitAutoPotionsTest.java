package com.dhj.actinium.resourcepack.item;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.init.PotionTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.potion.PotionUtils;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CitAutoPotionsTest {
    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    void matchesOneBottleFormAndAllBrewedVariantsOfItsEffect() {
        CitAutoPotions.Image drinkable = image("optifine", "normal/speed");
        CitAutoPotions.Image splash = image("optifine", "splash/movespeed");
        CitAutoPotions.Image lingering = image("optifine", "linger/movespeed");

        assertTrue(drinkable.matches(PotionUtils.addPotionToItemStack(new ItemStack(Items.POTIONITEM), PotionTypes.SWIFTNESS)));
        ItemStack splashStack = PotionUtils.addPotionToItemStack(new ItemStack(Items.SPLASH_POTION), PotionTypes.LONG_SWIFTNESS);
        assertTrue(splash.matches(splashStack));
        assertFalse(drinkable.matches(splashStack));
        assertTrue(lingering.matches(PotionUtils.addPotionToItemStack(new ItemStack(Items.LINGERING_POTION), PotionTypes.STRONG_SWIFTNESS)));
        assertFalse(lingering.matches(PotionUtils.addPotionToItemStack(new ItemStack(Items.LINGERING_POTION), PotionTypes.SLOWNESS)));
    }

    @Test
    void matchesEmptyBottleAndKeepsUnspecifiedPotionsUnchanged() {
        CitAutoPotions.Image empty = image("optifine", "normal/empty");
        CitAutoPotions.Image water = image("optifine", "normal/water");

        assertTrue(empty.matches(new ItemStack(Items.GLASS_BOTTLE)));
        assertFalse(empty.matches(new ItemStack(Items.POTIONITEM)));
        assertTrue(water.matches(PotionUtils.addPotionToItemStack(new ItemStack(Items.POTIONITEM), PotionTypes.WATER)));
        assertFalse(water.matches(new ItemStack(Items.POTIONITEM)));
        assertFalse(water.matches(PotionUtils.addPotionToItemStack(new ItemStack(Items.POTIONITEM), PotionTypes.MUNDANE)));
        assertNull(image("optifine", "splash/empty"));
        assertNull(image("optifine", "normal/movespeed_n"));
        assertNull(image("optifine", "normal/artless"));
        assertNull(image("mcpatcher", "linger/other"));
    }

    @Test
    void legacyDrinkableImagesMatchOnlyTheFirstCustomEffect() {
        CitAutoPotions.Image legacy = image("mcpatcher", "normal/blindness");
        ItemStack blindnessFirst = PotionUtils.appendEffects(new ItemStack(Items.POTIONITEM),
            List.of(new PotionEffect(MobEffects.BLINDNESS, 40), new PotionEffect(MobEffects.POISON, 40)));
        ItemStack blindnessSecond = PotionUtils.appendEffects(new ItemStack(Items.POTIONITEM),
            List.of(new PotionEffect(MobEffects.POISON, 40), new PotionEffect(MobEffects.BLINDNESS, 40)));

        assertTrue(legacy.matches(blindnessFirst));
        assertFalse(legacy.matches(blindnessSecond));
        assertNull(image("mcpatcher", "splash/blindness"));
        assertNull(image("optifine", "normal/blindness"));
    }

    @Test
    void explicitRuleWinsAndLegacyOtherOnlyCoversBasePotionsWithoutSpecificArt() {
        ResourceLocation explicitFile = new ResourceLocation("mcpatcher/cit/custom_water.properties");
        Properties properties = new Properties();
        properties.setProperty("nbt.display.Name", "Special water");
        CitRule explicit = new CitRule(explicitFile, 0, -100, CitRule.Type.ITEM, Set.of(Items.POTIONITEM),
            CitConditions.parse(properties), Map.of(), Map.of(), null, null);
        CitRule other = automatic("mcpatcher", "normal/other", 9);
        CitRule water = automatic("optifine", "normal/water", 5);
        CitSnapshot snapshot = new CitSnapshot(List.of(other, water, explicit).stream().sorted(CitModule.ORDER).toList(),
            Map.of(), Map.of(), CitGlint.Settings.parse(new Properties()), Set.of(), Map.of());
        ItemStack waterStack = PotionUtils.addPotionToItemStack(new ItemStack(Items.POTIONITEM), PotionTypes.WATER);

        assertSame(water, snapshot.first(CitRule.Type.ITEM, waterStack, CitConditions.Hand.ANY));
        waterStack.setStackDisplayName("Special water");
        assertSame(explicit, snapshot.first(CitRule.Type.ITEM, waterStack, CitConditions.Hand.ANY));
        assertSame(other, snapshot.first(CitRule.Type.ITEM,
            PotionUtils.addPotionToItemStack(new ItemStack(Items.POTIONITEM), PotionTypes.AWKWARD), CitConditions.Hand.ANY));
        assertNull(snapshot.first(CitRule.Type.ITEM,
            PotionUtils.addPotionToItemStack(new ItemStack(Items.POTIONITEM), PotionTypes.POISON), CitConditions.Hand.ANY));
    }

    @Test
    void g5ContentsUseVanillaTintAndLegacyFullImagesHaveNoTintedQuads() {
        TextureAtlasSprite bottle = new PixelSprite("test:bottle");
        TextureAtlasSprite overlay = new PixelSprite("test:overlay");
        assertTrue(image("optifine", "linger/water").hasTintedContents());
        assertTrue(image("mcpatcher", "linger/water").hasTintedContents());
        assertFalse(image("mcpatcher", "normal/water").hasTintedContents());
        assertFalse(image("optifine", "normal/empty").hasTintedContents());

        List<BakedQuad> layered = CitAutoPotions.bakeQuads(bottle, overlay);
        assertTrue(layered.stream().anyMatch(quad -> quad.getSprite() == overlay && quad.getTintIndex() == 0));
        assertTrue(layered.stream().anyMatch(quad -> quad.getSprite() == bottle && !quad.hasTintIndex()));

        List<BakedQuad> fullImage = CitAutoPotions.bakeQuads(bottle, null);
        assertFalse(fullImage.isEmpty());
        assertTrue(fullImage.stream().allMatch(quad -> quad.getSprite() == bottle && !quad.hasTintIndex()));
    }

    private static CitAutoPotions.Image image(String root, String name) {
        return CitAutoPotions.parse(path(root, name));
    }

    private static CitRule automatic(String root, String name, int priority) {
        ResourceLocation path = path(root, name);
        return CitRule.automaticPotion(path, priority, CitAutoPotions.parse(path));
    }

    private static ResourceLocation path(String root, String name) {
        return new ResourceLocation(root + "/cit/potion/" + name + ".png");
    }

    private static final class PixelSprite extends TextureAtlasSprite {
        PixelSprite(String name) {
            super(name);
            setIconWidth(1);
            setIconHeight(1);
            setFramesTextureData(List.<int[][]>of(new int[][] {{0xFFFFFFFF}}));
            initSprite(16, 16, 0, 0, false);
        }
    }
}
