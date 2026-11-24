package com.dhj.actinium.resourcepack.color;

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/** Associates an immutable block state selector with its palette. */
record BlockColorRule(Predicate<IBlockState> matches, ColorPalette palette) {
    record Selector(Block block, Predicate<IBlockState> matches) {}

    static Selector parse(String token, Function<ResourceLocation, Block> lookup) {
        String[] parts = token.split(":", -1);
        int fields = parts.length > 1 && !parts[1].contains("=") && !parts[1].matches("[0-9,-]+") ? 2 : 1;
        String name = fields == 2 ? parts[0] + ':' + parts[1] : parts[0];
        Block block = name.matches("\\d+") ? Block.getBlockById(Integer.parseInt(name)) : lookup.apply(new ResourceLocation(name));
        if (block == null) throw new IllegalArgumentException("Unknown block: " + name);
        List<Predicate<IBlockState>> predicates = new ArrayList<>();
        for (int i = fields; i < parts.length; i++) {
            String part = parts[i];
            if (part.contains("=")) {
                String[] pair = part.split("=", 2);
                IProperty<?> property = block.getBlockState().getProperty(pair[0]);
                if (property == null) throw new IllegalArgumentException("Unknown state property: " + pair[0]);
                predicates.add(propertyPredicate(property, pair[1]));
            } else {
                int mask = 0;
                for (String range : part.split(",", -1)) {
                    String[] ends = range.split("-", -1);
                    int start = Integer.parseInt(ends[0]);
                    int end = ends.length == 2 ? Integer.parseInt(ends[1]) : start;
                    if (ends.length > 2 || start < 0 || end > 15 || start > end) {
                        throw new IllegalArgumentException("Invalid metadata range: " + range);
                    }
                    for (int meta = start; meta <= end; meta++) mask |= 1 << meta;
                }
                int allowed = mask;
                predicates.add(state -> (allowed & (1 << block.getMetaFromState(state))) != 0);
            }
        }
        List<Predicate<IBlockState>> filters = List.copyOf(predicates);
        return new Selector(block, state -> {
            for (var filter : filters) if (!filter.test(state)) return false;
            return true;
        });
    }

    private static <T extends Comparable<T>> Predicate<IBlockState> propertyPredicate(IProperty<T> property, String input) {
        var allowed = new HashSet<T>();
        for (String value : input.split(",", -1)) {
            var parsed = property.parseValue(value);
            if (!parsed.isPresent()) throw new IllegalArgumentException("Invalid " + property.getName() + " value: " + value);
            allowed.add(parsed.get());
        }
        var values = Set.copyOf(allowed);
        return state -> values.contains(state.getValue(property));
    }
}
