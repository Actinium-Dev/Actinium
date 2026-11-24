package com.dhj.actinium.resourcepack.entity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.NavigableSet;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Pattern;

final class RandomTextureRules {
    private static final Pattern RANGE = Pattern.compile("(-?\\d+)(?:-(-?\\d+))?");
    private static final Pattern RULE = Pattern.compile("(?:skins|textures)\\.(\\d+)");
    private static final List<String> VANILLA_PROFESSIONS = List.of("farmer", "librarian", "priest", "smith", "butcher", "nitwit");
    private static final List<List<String>> VANILLA_CAREERS = List.of(
        List.of("farmer", "fisherman", "shepherd", "fletcher"), List.of("librarian", "cartographer"), List.of("cleric"),
        List.of("armor", "weapon", "tool"), List.of("butcher", "leather"), List.of("nitwit"));

    record Subject(int seed, String biome, int height, String name, String profession, int career,
                   String color, float health, float maxHealth, long dayTime, int moon,
                   String weather, int size, boolean baby) {}

    private record Range(int min, int max) {
        boolean contains(double value) { return value >= min && value <= max; }
    }

    private record Profession(String id, List<Integer> careers) {
        boolean matches(Subject s) {
            return id.equals(s.profession) && (careers.isEmpty() || s.career > 0 && careers.contains(s.career));
        }
    }

    private record Choice(int[] textures, long[] weights, long total) {
        int select(int seed) {
            if (weights == null) return textures[Math.floorMod(seed, textures.length)];
            long value = Math.floorMod(seed, total);
            for (int i = 0; i < weights.length; i++) {
                if (value < weights[i]) return textures[i];
            }
            throw new IllegalStateException("Invalid cumulative texture weights");
        }
    }

    private record Rule(Choice choice, Set<String> biomes, boolean invertBiomes, List<Range> heights,
                        Pattern name, boolean invertName, List<Profession> professions, Set<String> colors,
                        List<Range> health, boolean percent, List<Range> times, List<Range> moons,
                        Set<String> weather, List<Range> sizes, Boolean baby) {
        boolean matches(Subject s) {
            if (!biomes.isEmpty() && (s.biome == null || biomes.contains(s.biome) == invertBiomes)) return false;
            if (!contains(heights, s.height)) return false;
            if (name != null && (s.name != null && name.matcher(s.name).matches()) == invertName) return false;
            if (!professions.isEmpty() && professions.stream().noneMatch(profession -> profession.matches(s))) return false;
            if (!colors.isEmpty() && (s.color == null || !colors.contains(s.color))) return false;
            if (!health.isEmpty() && (s.maxHealth <= 0 || !contains(health, percent ? (int) (s.health * 100 / s.maxHealth) : s.health))) return false;
            return contains(times, s.dayTime) && contains(moons, s.moon) && contains(sizes, s.size)
                && (weather.isEmpty() || weather.contains(s.weather)) && (baby == null || baby == s.baby);
        }

    }

    private final List<Rule> rules;
    private final Choice variants;

    private RandomTextureRules(List<Rule> rules, Choice variants) {
        this.rules = List.copyOf(rules);
        this.variants = variants;
    }

    static RandomTextureRules parse(Properties properties, NavigableSet<Integer> available, Consumer<String> warn) {
        if (properties == null) return new RandomTextureRules(List.of(), choice(available.stream().mapToInt(Integer::intValue).toArray(), null));
        var ids = new TreeSet<Integer>();
        for (String key : properties.stringPropertyNames()) {
            var matcher = RULE.matcher(key);
            if (matcher.matches()) {
                try {
                    ids.add(Integer.parseInt(matcher.group(1)));
                } catch (NumberFormatException e) {
                    warn.accept("Invalid rule number: " + key);
                }
            }
        }
        List<Rule> rules = new ArrayList<>();
        for (int id : ids) {
            try {
                String suffix = "." + id;
                String skins = properties.getProperty("textures" + suffix, properties.getProperty("skins" + suffix)).trim();
                int[] choices = switch (skins) {
                    case "*", "all", "any" -> available.stream().mapToInt(Integer::intValue).toArray();
                    default -> indices(skins, available);
                };
                String heights = properties.getProperty("heights" + suffix);
                List<Range> height = ranges(heights);
                if (heights == null && (properties.containsKey("minHeight" + suffix) || properties.containsKey("maxHeight" + suffix))) {
                    height = List.of(new Range(Integer.parseInt(properties.getProperty("minHeight" + suffix, "0").trim()),
                        Integer.parseInt(properties.getProperty("maxHeight" + suffix, "255").trim())));
                }
                String biomes = properties.getProperty("biomes" + suffix, "").trim();
                String name = properties.getProperty("name" + suffix);
                boolean invertName = name != null && name.startsWith("!");
                String health = properties.getProperty("health" + suffix, "");
                String baby = properties.getProperty("baby" + suffix);
                if (baby != null && !baby.equals("true") && !baby.equals("false")) throw new IllegalArgumentException("Invalid baby condition");
                rules.add(new Rule(choice(choices, properties.getProperty("weights" + suffix)),
                    biomeNames(biomes.startsWith("!") ? biomes.substring(1) : biomes), biomes.startsWith("!"), height,
                    name == null ? null : namePattern(invertName ? name.substring(1) : name), invertName,
                    professions(properties.getProperty("professions" + suffix, "")), words(properties.getProperty("collarColors" + suffix,
                    properties.getProperty("colors" + suffix, ""))), ranges(health.replace("%", "")), health.contains("%"),
                    ranges(properties.getProperty("dayTime" + suffix)), ranges(properties.getProperty("moonPhase" + suffix)),
                    words(properties.getProperty("weather" + suffix, "")), ranges(properties.getProperty("sizes" + suffix)),
                    baby == null ? null : Boolean.valueOf(baby)));
            } catch (IllegalArgumentException e) {
                warn.accept("Invalid rule " + id + ": " + e.getMessage());
            }
        }
        return new RandomTextureRules(rules, null);
    }

    int select(Subject subject) {
        for (Rule rule : rules) {
            if (rule.matches(subject)) return rule.choice.select(subject.seed);
        }
        return variants == null ? 1 : variants.select(subject.seed);
    }

    boolean usesCareer() {
        return rules.stream().anyMatch(rule -> rule.professions.stream().anyMatch(p -> !p.careers.isEmpty()));
    }

    private static List<Profession> professions(String value) {
        List<Profession> result = new ArrayList<>();
        for (String token : words(value)) {
            String entry = token.startsWith("minecraft:") ? token.substring("minecraft:".length()) : token;
            String[] parts = entry.split(":", -1);
            String name = parts[0].equals("blacksmith") ? "smith" : parts[0];
            int id = name.matches("[0-9]+") ? Integer.parseInt(name) : VANILLA_PROFESSIONS.indexOf(name);
            if (id >= 0 && id < VANILLA_PROFESSIONS.size() && parts.length <= 2) {
                List<Integer> careers = new ArrayList<>();
                if (parts.length == 2) {
                    for (String career : parts[1].split(",", -1)) {
                        int careerId = career.matches("[0-9]+") ? Integer.parseInt(career) : VANILLA_CAREERS.get(id).indexOf(career) + 1;
                        if (careerId <= 0) throw new IllegalArgumentException("Unknown villager career: " + token);
                        careers.add(careerId);
                    }
                }
                result.add(new Profession("minecraft:" + VANILLA_PROFESSIONS.get(id), List.copyOf(careers)));
            } else if (parts.length >= 2 && parts.length <= 3 && parts[0].matches("[a-z0-9_.-]+")
                    && parts[1].matches("[a-z0-9_./-]+")) {
                List<Integer> careers = parts.length == 2 ? List.of() : Arrays.stream(parts[2].split(",", -1))
                    .map(Integer::parseInt).toList();
                if (careers.stream().anyMatch(career -> career <= 0)) throw new IllegalArgumentException("Invalid career ID: " + token);
                result.add(new Profession(parts[0] + ':' + parts[1], careers));
            } else {
                throw new IllegalArgumentException("Unknown villager profession: " + token);
            }
        }
        return List.copyOf(result);
    }

    private static List<Range> ranges(String value) {
        if (value == null || value.isBlank()) return List.of();
        List<Range> ranges = new ArrayList<>();
        for (String token : value.trim().split("[ ,]+")) {
            var matcher = RANGE.matcher(token);
            if (!matcher.matches()) throw new IllegalArgumentException("Invalid range: " + token);
            int min = Integer.parseInt(matcher.group(1));
            int max = matcher.group(2) == null ? min : Integer.parseInt(matcher.group(2));
            if (min > max) throw new IllegalArgumentException("Reversed range: " + token);
            ranges.add(new Range(min, max));
        }
        return List.copyOf(ranges);
    }

    private static boolean contains(List<Range> ranges, double value) {
        if (ranges.isEmpty()) return true;
        for (Range range : ranges) if (range.contains(value)) return true;
        return false;
    }

    private static int[] indices(String value, NavigableSet<Integer> available) {
        List<Integer> result = new ArrayList<>();
        for (Range range : ranges(value)) {
            var present = available.subSet(range.min, true, range.max, true);
            if (present.size() != (long) range.max - range.min + 1) throw new IllegalArgumentException("Missing texture in " + range);
            result.addAll(present);
        }
        return result.stream().mapToInt(Integer::intValue).toArray();
    }

    private static Choice choice(int[] textures, String weights) {
        if (textures.length == 0) throw new IllegalArgumentException("Empty texture list");
        if (weights == null || weights.isBlank()) return new Choice(textures, null, 0);
        String[] tokens = weights.trim().split("[ ,]+");
        long[] cumulative = new long[textures.length];
        long sum = 0;
        int supplied = Math.min(tokens.length, textures.length);
        for (int i = 0; i < textures.length; i++) {
            long weight = i < supplied ? Integer.parseInt(tokens[i]) : cumulative[supplied - 1] / supplied;
            if (weight < 0) throw new IllegalArgumentException("Negative weight");
            cumulative[i] = sum += weight;
        }
        return sum == 0 ? new Choice(textures, null, 0) : new Choice(textures, cumulative, sum);
    }

    private static Set<String> words(String value) {
        if (value.isBlank()) return Set.of();
        return Set.copyOf(Arrays.asList(value.trim().toLowerCase(Locale.ROOT).split("\\s+")));
    }

    static String biomeName(String name) {
        String normalized = name.toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "");
        return normalized.contains(":") ? normalized : "minecraft:" + normalized;
    }

    private static Set<String> biomeNames(String value) {
        var names = new TreeSet<String>();
        for (String word : words(value)) names.add(biomeName(word));
        return Set.copyOf(names);
    }

    private static Pattern namePattern(String value) {
        int colon = value.indexOf(':');
        if (colon < 0) return Pattern.compile(Pattern.quote(value));
        String pattern = value.substring(colon + 1);
        return switch (value.substring(0, colon)) {
            case "regex" -> Pattern.compile(pattern);
            case "iregex" -> Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            case "pattern", "ipattern" -> {
                StringBuilder regex = new StringBuilder();
                for (int i = 0; i < pattern.length(); i++) {
                    char ch = pattern.charAt(i);
                    regex.append(ch == '*' ? ".*" : ch == '?' ? "." : Pattern.quote(String.valueOf(ch)));
                }
                yield Pattern.compile(regex.toString(), value.startsWith("i") ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0);
            }
            default -> Pattern.compile(Pattern.quote(value));
        };
    }
}
