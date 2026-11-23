package com.dhj.actinium.resourcepack.item;

import java.util.Properties;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.init.PotionTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.potion.PotionUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CitConditionsTest {
    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    void matchesWildcardListChildrenAndColorNumbers() {
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagCompound display = new NBTTagCompound();
        NBTTagList lore = new NBTTagList();
        lore.appendTag(new NBTTagString("Forged under Moonlight"));
        display.setTag("Lore", lore);
        display.setInteger("color", 0xAA7733);
        tag.setTag("display", display);

        assertTrue(CitConditions.NbtMatch.parse("display.Lore.*", "ipattern:*moonlight").test(tag));
        assertTrue(CitConditions.NbtMatch.parse("display.color", "#aa7733").test(tag));
        assertFalse(CitConditions.NbtMatch.parse("display.Lore.0", "!pattern:Forged*").test(tag));
    }

    @Test
    void requiresOneEnchantmentToSatisfyBothIdAndLevel() {
        Properties props = new Properties();
        props.setProperty("enchantmentIDs", "sharpness");
        props.setProperty("enchantmentLevels", "3-");
        props.setProperty("hand", "off");
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        stack.addEnchantment(Enchantments.SHARPNESS, 1);
        stack.addEnchantment(Enchantments.UNBREAKING, 3);
        CitConditions rule = CitConditions.parse(props);

        assertFalse(rule.matches(stack, CitConditions.Hand.OFF));
        stack.addEnchantment(Enchantments.SHARPNESS, 3);
        assertTrue(rule.matches(stack, CitConditions.Hand.OFF));
        assertFalse(rule.matches(stack, CitConditions.Hand.MAIN));
    }

    @Test
    void matchesLegacySplashDamageAndSeparatesLongFromStrong() {
        Properties props = new Properties();
        props.setProperty("damage", "16418");
        props.setProperty("damageMask", "16447");
        CitConditions rule = CitConditions.parse(props);
        assertTrue(rule.matches(PotionUtils.addPotionToItemStack(new ItemStack(Items.SPLASH_POTION), PotionTypes.STRONG_SWIFTNESS), CitConditions.Hand.ANY));
        assertFalse(rule.matches(PotionUtils.addPotionToItemStack(new ItemStack(Items.SPLASH_POTION), PotionTypes.LONG_SWIFTNESS), CitConditions.Hand.ANY));
        assertFalse(rule.matches(PotionUtils.addPotionToItemStack(new ItemStack(Items.LINGERING_POTION), PotionTypes.STRONG_SWIFTNESS), CitConditions.Hand.ANY));
    }

    @Test
    void unknownPotionDoesNotMatchDamageZero() {
        Properties props = new Properties();
        props.setProperty("damage", "0");
        props.setProperty("damageMask", "15");
        CitConditions rule = CitConditions.parse(props);
        assertTrue(rule.matches(PotionUtils.addPotionToItemStack(new ItemStack(Items.POTIONITEM), PotionTypes.WATER), CitConditions.Hand.ANY));
        ItemStack luck = new ItemStack(Items.POTIONITEM);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Potion", "minecraft:luck");
        luck.setTagCompound(tag);
        assertFalse(rule.matches(luck, CitConditions.Hand.ANY));
        assertTrue(CitConditions.parse(new Properties()).matches(luck, CitConditions.Hand.ANY));
    }

    @Test
    void combinesNamedAndNumericEnchantmentRangesForStoredEnchantments() {
        Properties props = new Properties();
        props.setProperty("enchantmentIDs", "16-18 minecraft:unbreaking 80-");
        props.setProperty("enchantmentLevels", "3-");
        CitConditions rule = CitConditions.parse(props);
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        NBTTagList entries = new NBTTagList();
        NBTTagCompound entry = new NBTTagCompound();
        entries.appendTag(entry);
        book.setTagInfo("StoredEnchantments", entries);
        entry.setShort("lvl", (short) 3);
        entry.setShort("id", (short) 19);
        assertFalse(rule.matches(book, CitConditions.Hand.ANY));
        entry.setShort("id", (short) 17);
        assertTrue(rule.matches(book, CitConditions.Hand.ANY));
        entry.setShort("id", (short) 34);
        assertTrue(rule.matches(book, CitConditions.Hand.ANY));
        entry.setShort("id", (short) 82);
        assertTrue(rule.matches(book, CitConditions.Hand.ANY));
    }
}
