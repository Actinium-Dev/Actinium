package com.dhj.actinium.resourcepack.item;

import java.util.Map;
import net.minecraft.init.Items;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Converts the 1.12.2 Potion tag to the damage bits used by G5's explicit CIT filters. */
final class CitPotionDamage {
    private static final int EFFECT = 0x2000;
    private static final int STRONG = 0x20;
    private static final int LONG = 0x40;
    private static final int SPLASH = 0x4000;
    private static final String VANILLA = "minecraft:";
    private static final Map<String, Integer> EFFECTS = Map.ofEntries(
        Map.entry("regeneration", 1), Map.entry("swiftness", 2), Map.entry("fire_resistance", 3),
        Map.entry("poison", 4), Map.entry("healing", 5), Map.entry("night_vision", 6), Map.entry("weakness", 8),
        Map.entry("strength", 9), Map.entry("slowness", 10), Map.entry("leaping", 11), Map.entry("harming", 12),
        Map.entry("water_breathing", 13), Map.entry("invisibility", 14));

    private CitPotionDamage() {}

    static int of(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemPotion)) return stack.getItemDamage();
        NBTTagCompound tag = stack.getTagCompound();
        String id = tag == null ? "" : tag.getString("Potion");
        if (id.isEmpty()) return 0;
        if (!id.startsWith(VANILLA)) return -1;

        String name = id.substring(VANILLA.length());
        int damage = switch (name) {
            case "water" -> 0;
            case "awkward" -> 0x10;
            case "thick" -> 0x20;
            case "mundane" -> 0x40;
            default -> effectDamage(name);
        };
        return damage >= 0 && stack.getItem() == Items.SPLASH_POTION ? damage | SPLASH : damage;
    }

    private static int effectDamage(String name) {
        int variant = 0;
        if (name.startsWith("strong_")) {
            variant = STRONG;
            name = name.substring("strong_".length());
        } else if (name.startsWith("long_")) {
            variant = LONG;
            name = name.substring("long_".length());
        }
        Integer effect = EFFECTS.get(name);
        return effect == null ? -1 : EFFECT | variant | effect;
    }
}
