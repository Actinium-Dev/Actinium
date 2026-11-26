/*
 * Copyright © 2020 LambdAurora <email@lambdaurora.dev>
 *
 * This file is part of LambDynamicLights.
 *
 * Licensed under the MIT License. For more information,
 * see the LICENSE file.
 */

package com.dhj.actinium.render.dynamiclights.rules;

import com.dhj.actinium.api.dynamiclights.ItemLightSource;
import com.dhj.actinium.api.dynamiclights.ItemLightSource.StaticItemLightSource;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Loads the ten legacy item light resources and resolves block light from item states. */
public final class ItemLightDefinitions {
    private static final Logger LOGGER = LogManager.getLogger(ItemLightDefinitions.class);
    private static final String NAMESPACE = "celeritasdynamiclights";
    private static final List<String> BUILTIN_DEFINITIONS = List.of(
            "blaze_powder", "blaze_rod", "fire_charge", "glowstone_dust", "lava_bucket",
            "nether_star", "prismarine_crystals", "redstone_torch", "spectral_arrow", "torch");

    private ItemLightDefinitions() {
    }

    public static Map<Item, ItemLightSource> load(IResourceManager resources) {
        Map<Item, ItemLightSource> definitions = new HashMap<>();
        for (String name : BUILTIN_DEFINITIONS) {
            ResourceLocation id = new ResourceLocation(NAMESPACE, "dynamiclights/item/" + name);
            ResourceLocation path = new ResourceLocation(NAMESPACE, id.getPath() + ".json");
            try (IResource resource = resources.getResource(path)) {
                var reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
                ItemLightSource source = parse(id, new JsonParser().parse(reader).getAsJsonObject());
                ItemLightSource previous = definitions.putIfAbsent(source.item(), source);
                if (previous != null) {
                    LOGGER.warn("Ignoring duplicate item light {} for {}; already defined by {}",
                            id, source.item().getRegistryName(), previous.id());
                }
            } catch (IOException | JsonParseException | IllegalStateException | IllegalArgumentException | ArithmeticException e) {
                LOGGER.warn("Failed to load dynamic item light {}", path, e);
            }
        }
        return Map.copyOf(definitions);
    }

    private static ItemLightSource parse(ResourceLocation id, JsonObject json) {
        ResourceLocation itemId = new ResourceLocation(JsonUtils.getString(json, "item"));
        Item item = ForgeRegistries.ITEMS.getValue(itemId);
        if (item == null || item == Items.AIR) {
            throw new JsonSyntaxException("Unknown item: " + itemId);
        }

        boolean waterSensitive = JsonUtils.getBoolean(json, "water_sensitive", false);
        if (json.get("luminance") instanceof JsonPrimitive luminance) {
            if (luminance.isNumber()) {
                return new StaticItemLightSource(id, item, luminance.getAsBigDecimal().intValueExact(), waterSensitive);
            }
            if (luminance.isString()) {
                String value = luminance.getAsString();
                if (value.equals("block")) {
                    if (item instanceof ItemBlock blockItem) {
                        return new BlockItemLightSource(id, item, blockItem.getBlock(), waterSensitive);
                    }
                    throw new JsonSyntaxException("The block luminance rule requires an ItemBlock: " + itemId);
                }

                ResourceLocation blockId = new ResourceLocation(value);
                Block block = ForgeRegistries.BLOCKS.getValue(blockId);
                if (block != null && block != Blocks.AIR) {
                    return new BlockItemLightSource(id, item, block, waterSensitive);
                }
                throw new JsonSyntaxException("Unknown light-emitting block: " + blockId);
            }
        }
        throw new JsonSyntaxException("Expected luminance to be an integer or block identifier");
    }

    public static int blockLuminance(ItemStack stack, Block block) {
        // ItemPiston uses placement metadata 7, which has no corresponding block facing.
        IBlockState state = block.getDefaultState();
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null && tag.hasKey("BlockStateTag", NBT.TAG_COMPOUND)) {
            NBTTagCompound properties = tag.getCompoundTag("BlockStateTag");
            for (IProperty<?> property : state.getPropertyKeys()) {
                if (properties.hasKey(property.getName(), NBT.TAG_STRING)) {
                    state = applyProperty(state, property, properties.getString(property.getName()));
                }
            }
        }
        return state.getLightValue();
    }

    private static <T extends Comparable<T>> IBlockState applyProperty(IBlockState state, IProperty<T> property, String text) {
        var parsed = property.parseValue(text);
        if (parsed.isPresent()) {
            return state.withProperty(property, parsed.get());
        }
        for (T value : property.getAllowedValues()) {
            if (value.toString().equalsIgnoreCase(text)) {
                return state.withProperty(property, value);
            }
        }
        return state;
    }

    private static final class BlockItemLightSource extends ItemLightSource {
        private final Block block;

        private BlockItemLightSource(ResourceLocation id, Item item, Block block, boolean waterSensitive) {
            super(id, item, waterSensitive);
            this.block = block;
        }

        @Override
        public int getLuminance(ItemStack stack) {
            return blockLuminance(stack, block);
        }
    }
}
