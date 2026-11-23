package com.dhj.actinium.resourcepack.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTPrimitive;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;

record CitConditions(Ranges damage, boolean percent, int mask, Ranges count, Ranges enchantments,
                     Ranges levels, List<NbtMatch> nbt, Hand hand) {
    private static final Pattern NUMERIC_ENCHANTMENT = Pattern.compile("[0-9]+(?:-[0-9]*)?|-[0-9]+");

    enum Hand { ANY, MAIN, OFF }

    static CitConditions parse(Properties properties) {
        String damage = properties.getProperty("damage", "");
        List<Span> enchantments = new ArrayList<>();
        for (String token : properties.getProperty("enchantmentIDs", "").trim().split("[\\s,]+")) {
            if (token.isEmpty()) continue;
            if (NUMERIC_ENCHANTMENT.matcher(token).matches()) {
                int dash = token.indexOf('-');
                int min = dash < 0 ? Integer.parseInt(token) : dash == 0 ? 0 : Integer.parseInt(token.substring(0, dash));
                int max = dash < 0 ? min : dash == token.length() - 1 ? 0xFFFF : Integer.parseInt(token.substring(dash + 1));
                enchantments.add(new Span(Math.min(min, max), Math.max(min, max)));
            } else {
                Enchantment enchantment = Enchantment.REGISTRY.getObject(new ResourceLocation(token));
                if (enchantment == null) throw new IllegalArgumentException("Unknown enchantment: " + token);
                int id = Enchantment.getEnchantmentID(enchantment);
                enchantments.add(new Span(id, id));
            }
        }
        List<NbtMatch> nbt = properties.stringPropertyNames().stream().filter(key -> key.startsWith("nbt.")).sorted()
            .map(key -> NbtMatch.parse(key.substring(4), properties.getProperty(key))).toList();
        return new CitConditions(Ranges.parse(damage.replace("%", "")), damage.contains("%"),
            Integer.decode(properties.getProperty("damageMask", "-1").trim()), Ranges.parse(properties.getProperty("stackSize", "")),
            new Ranges(List.copyOf(enchantments)), Ranges.parse(properties.getProperty("enchantmentLevels", "")), nbt,
            Hand.valueOf(properties.getProperty("hand", "any").trim().toUpperCase(Locale.ROOT)));
    }

    boolean matches(ItemStack stack, Hand activeHand) {
        if (hand != Hand.ANY && hand != activeHand || !count.contains(stack.getCount())) return false;
        if (!damage.spans().isEmpty()) {
            int value = CitPotionDamage.of(stack);
            if (value < 0) return false;
            if (mask != 0) value &= mask;
            if (percent) value = (int) (100.0 * value / stack.getMaxDamage());
            if (!damage.contains(value)) return false;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (hasEnchantmentFilter()) {
            if (tag == null || !(matchesEnchantments(tag.getTagList("ench", 10))
                || matchesEnchantments(tag.getTagList("StoredEnchantments", 10)))) return false;
        }
        for (NbtMatch match : nbt) if (!match.test(tag)) return false;
        return true;
    }

    boolean hasEnchantmentFilter() {
        return !enchantments.spans().isEmpty() || !levels.spans().isEmpty();
    }

    private boolean matchesEnchantments(NBTTagList entries) {
        for (int i = 0; i < entries.tagCount(); i++) {
            NBTTagCompound entry = entries.getCompoundTagAt(i);
            if (enchantments.contains(entry.getShort("id")) && levels.contains(entry.getShort("lvl"))) return true;
        }
        return false;
    }

    record Ranges(List<Span> spans) {
        static Ranges parse(String source) {
            List<Span> ranges = new ArrayList<>();
            for (String token : source.trim().split("[ ,]+")) {
                if (token.isEmpty()) continue;
                int dash = token.indexOf('-');
                if (dash < 0) {
                    int value = Integer.parseInt(token);
                    ranges.add(new Span(value, value));
                } else {
                    if (token.equals("-")) throw new IllegalArgumentException("Empty range");
                    int min = dash == 0 ? 0 : Integer.parseInt(token.substring(0, dash));
                    int max = dash == token.length() - 1 ? Integer.MAX_VALUE : Integer.parseInt(token.substring(dash + 1));
                    if (min > max) throw new IllegalArgumentException("Inverted range: " + token);
                    ranges.add(new Span(min, max));
                }
            }
            return new Ranges(List.copyOf(ranges));
        }

        boolean contains(int value) {
            return spans.isEmpty() || spans.stream().anyMatch(span -> value >= span.min && value <= span.max);
        }
    }

    record Span(int min, int max) {}

    record NbtMatch(String[] path, Predicate<String> value, boolean negated) {
        static NbtMatch parse(String path, String value) {
            boolean negated = value.startsWith("!");
            String text = negated ? value.substring(1) : value;
            int colon = text.indexOf(':');
            String mode = colon < 0 ? "" : text.substring(0, colon);
            String expression = colon < 0 ? text : text.substring(colon + 1);
            Predicate<String> predicate = switch (mode) {
                case "regex" -> Pattern.compile(expression).asMatchPredicate();
                case "iregex" -> Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).asMatchPredicate();
                case "pattern" -> glob(expression, 0);
                case "ipattern" -> glob(expression, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                default -> text::equals;
            };
            return new NbtMatch(path.split("\\.", -1), predicate, negated);
        }

        private static Predicate<String> glob(String text, int flags) {
            StringBuilder regex = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '*') regex.append(".*");
                else if (c == '?') regex.append('.');
                else regex.append(Pattern.quote(String.valueOf(c)));
            }
            return Pattern.compile(regex.toString(), flags | Pattern.DOTALL).asMatchPredicate();
        }

        boolean test(NBTBase tag) {
            return matches(tag, 0) != negated;
        }

        private boolean matches(NBTBase tag, int part) {
            if (tag == null) return false;
            if (part == path.length) {
                if (tag instanceof NBTTagString string) return value.test(string.getString());
                if (tag instanceof NBTPrimitive number) {
                    String numeric = switch (number.getId()) {
                        case 1, 2, 3, 4 -> Long.toString(number.getLong());
                        case 5 -> Float.toString(number.getFloat());
                        default -> Double.toString(number.getDouble());
                    };
                    return value.test(numeric) || number.getId() == 3 && value.test(String.format(Locale.ROOT, "#%06x", number.getInt()));
                }
                return value.test(tag.toString());
            }
            String key = path[part];
            if (tag instanceof NBTTagCompound compound) {
                if (key.equals("*")) return compound.getKeySet().stream().anyMatch(child -> matches(compound.getTag(child), part + 1));
                return matches(compound.getTag(key), part + 1);
            }
            if (tag instanceof NBTTagList list) {
                if (key.equals("count") && part + 1 == path.length) return value.test(Integer.toString(list.tagCount()));
                if (key.equals("*")) {
                    for (int i = 0; i < list.tagCount(); i++) if (matches(list.get(i), part + 1)) return true;
                } else if (key.matches("[0-9]{1,9}")) {
                    int index = Integer.parseInt(key);
                    return index < list.tagCount() && matches(list.get(index), part + 1);
                }
            }
            return false;
        }
    }
}
