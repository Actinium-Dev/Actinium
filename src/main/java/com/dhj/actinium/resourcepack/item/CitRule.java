package com.dhj.actinium.resourcepack.item;

import java.util.Map;
import java.util.Set;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

record CitRule(ResourceLocation source, int priority, int weight, Type type, Set<Item> items,
               CitConditions conditions, Map<String, ResourceLocation> textures, Map<String, ResourceLocation> models,
               CitGlint glint, CitAutoPotions.Image potionImage) {
    enum Type { ITEM, ARMOR, ELYTRA, ENCHANTMENT }

    static CitRule automaticPotion(ResourceLocation file, int priority, CitAutoPotions.Image image) {
        return new CitRule(file, priority, 0, Type.ITEM, Set.of(image.item()), null, Map.of(), Map.of("", file), null, image);
    }

    boolean matches(ItemStack stack, CitConditions.Hand hand) {
        return !stack.isEmpty() && (items.isEmpty() || items.contains(stack.getItem()))
            && (potionImage == null ? conditions.matches(stack, hand) : potionImage.matches(stack));
    }

    int selectionGroup() {
        return potionImage == null ? 0 : potionImage.selectionGroup();
    }
}
