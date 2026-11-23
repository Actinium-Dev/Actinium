package com.dhj.actinium.resourcepack.item;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

record CitSnapshot(List<CitRule> rules, Map<ResourceLocation, IBakedModel> models,
                   Map<ResourceLocation, TextureAtlasSprite> sprites, CitGlint.Settings glintSettings,
                   Set<Item> variantItems, Map<IBakedModel, ModelResourceLocation> baseLocations) {
    boolean vanillaGlint() {
        return glintSettings.vanilla();
    }

    CitRule first(CitRule.Type type, ItemStack stack, CitConditions.Hand hand) {
        for (CitRule rule : rules) if (rule.type() == type && rule.matches(stack, hand)) return rule;
        return null;
    }

    List<CitGlint> glints(ItemStack stack, CitConditions.Hand hand) {
        return rules.stream().filter(rule -> rule.type() == CitRule.Type.ENCHANTMENT && rule.matches(stack, hand)
                && (rule.conditions().hasEnchantmentFilter() || stack.hasEffect()))
            .map(CitRule::glint).collect(Collectors.toMap(CitGlint::layer, effect -> effect, (first, ignored) -> first,
                TreeMap::new)).descendingMap().values().stream().limit(glintSettings.cap()).toList().reversed();
    }
}
