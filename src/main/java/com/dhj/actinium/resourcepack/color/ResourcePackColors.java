package com.dhj.actinium.resourcepack.color;

import com.dhj.actinium.resourcepack.ResourcePackReload;
import com.dhj.actinium.runtime.ActiniumRuntime;
import net.minecraft.block.BlockPlanks;
import net.minecraft.block.BlockRedstoneWire;
import net.minecraft.block.BlockStem;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.particle.Particle;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.potion.PotionEffect;
import net.minecraft.potion.PotionUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ToIntBiFunction;

/** Client render lookups for the current custom-colors snapshot. */
public final class ResourcePackColors {
    private static ResourcePackReload.Handle<ColorSnapshot> handle;
    private static final ResourceLocation SWAMP = new ResourceLocation("swampland");
    private static final String[] MAP_NAMES = ("air grass sand cloth tnt ice iron foliage snow clay dirt stone water wood quartz "
            + "orange magenta light_blue yellow lime pink gray silver cyan purple blue brown green red black gold diamond lapis "
            + "emerald podzol netherrack white_terracotta orange_terracotta magenta_terracotta light_blue_terracotta "
            + "yellow_terracotta lime_terracotta pink_terracotta gray_terracotta silver_terracotta cyan_terracotta "
            + "purple_terracotta blue_terracotta brown_terracotta green_terracotta red_terracotta black_terracotta").split(" ");

    private ResourcePackColors() {}

    public static void register() {
        handle = ResourcePackReload.register(new ColorPackModule());
    }

    static ColorSnapshot snapshot() {
        var registered = handle;
        return registered == null ? null : registered.get();
    }

    public static int color(String key, int original) {
        var colors = snapshot();
        return colors == null ? original : colors.colors().getOrDefault(key, original);
    }

    public static int cloudMode(int original) {
        return original == 0 ? 0 : color("clouds", original);
    }

    public static int durability(ItemStack stack, int original) {
        var snapshot = snapshot();
        var palette = snapshot == null ? null : snapshot.palettes().get("durability");
        if (palette == null) return original;
        double damage = stack.getItem().getDurabilityForDisplay(stack);
        return palette.indexed((int) ((1 - damage) * (palette.size() - 1)));
    }

    public static int indexed(String key, int index, int original) {
        var colors = snapshot();
        var palette = colors == null ? null : colors.palettes().get(key);
        return palette == null ? original : palette.indexed(index);
    }

    public static int experience(float ticks, int original) {
        var colors = snapshot();
        var palette = colors == null ? null : colors.palettes().get("xporb");
        if (palette == null) return original;
        double phase = ticks * 2 * Math.PI / colors.xpPeriod();
        return palette.indexed((int) Math.round((Math.sin(phase) + 1) * 0.5 * (palette.size() - 1)));
    }

    public static int block(IBlockState state, IBlockAccess world, BlockPos pos, int tint) {
        var colors = snapshot();
        if (colors == null) return -1;
        var rule = matchingBlockRule(colors, state);
        if (rule != null) return sample(rule.palette(), world, pos);
        if (tint != 0) return -1;
        var block = state.getBlock();
        if (block == Blocks.WATERLILY) return colors.colors().getOrDefault("lilypad", -1);
        if (state.getMaterial() == Material.WATER) return water(colors, world, pos, -1);
        if (block == Blocks.REDSTONE_WIRE) {
            var palette = colors.palettes().get("redstone");
            return palette == null ? -1 : palette.indexed(state.getValue(BlockRedstoneWire.POWER));
        }
        if (block == Blocks.PUMPKIN_STEM || block == Blocks.MELON_STEM) {
            var palette = colors.palettes().get(block == Blocks.PUMPKIN_STEM ? "pumpkinstem" : "melonstem");
            if (palette == null) palette = colors.palettes().get("stem");
            return palette == null ? -1 : palette.indexed(state.getValue(BlockStem.AGE));
        }
        if (world == null || pos == null) return -1;
        if (block == Blocks.GRASS || block == Blocks.TALLGRASS || block == Blocks.DOUBLE_PLANT) {
            return vegetation(colors, "grass", "swampgrass", world, pos, Biome::getGrassColorAtPos);
        }
        if (block == Blocks.LEAVES || block == Blocks.LEAVES2 || block == Blocks.VINE) {
            String key = "foliage";
            if (block == Blocks.LEAVES) {
                BlockPlanks.EnumType type = BlockPlanks.EnumType.byMetadata(block.getMetaFromState(state) & 3);
                if (type == BlockPlanks.EnumType.SPRUCE) return sample(colors.palettes().get("pine"), world, pos);
                if (type == BlockPlanks.EnumType.BIRCH) return sample(colors.palettes().get("birch"), world, pos);
            }
            return vegetation(colors, key, "swampfoliage", world, pos, Biome::getFoliageColorAtPos);
        }
        return -1;
    }

    /** Checks the published snapshot for an explicit colormap matching this block state. */
    public static boolean hasBlockColorRule(IBlockState state) {
        var colors = snapshot();
        return colors != null && matchingBlockRule(colors, state) != null;
    }

    private static BlockColorRule matchingBlockRule(ColorSnapshot colors, IBlockState state) {
        List<BlockColorRule> rules = colors.blocks().get(state.getBlock());
        if (rules != null) {
            for (var rule : rules) {
                if (rule.matches().test(state)) return rule;
            }
        }
        return null;
    }

    private static int vegetation(ColorSnapshot colors, String key, String swampKey, IBlockAccess world, BlockPos pos,
                                  ToIntBiFunction<Biome, BlockPos> vanilla) {
        var main = colors.palettes().get(key);
        var swamp = colors.palettes().get(swampKey);
        if (main == null && swamp == null) return -1;
        return blend(world, pos, ActiniumRuntime.options().quality.legacyBiomeBlendRadius, (biome, at) -> {
            var palette = swamp != null && SWAMP.equals(biome.getRegistryName()) ? swamp : main;
            return palette == null ? vanilla.applyAsInt(biome, at) : sample(palette, biome, at);
        });
    }

    public static int water(IBlockAccess world, BlockPos pos, int original) {
        var colors = snapshot();
        return colors == null ? original : water(colors, world, pos, original);
    }

    private static int water(ColorSnapshot colors, IBlockAccess world, BlockPos pos, int original) {
        int custom = sample(colors.palettes().get("water"), world, pos);
        return custom == -1 ? original : custom;
    }

    private static int sample(ColorPalette palette, IBlockAccess world, BlockPos pos) {
        if (palette == null) return -1;
        if (world == null || pos == null) return palette.defaultColor();
        return blend(world, pos, ActiniumRuntime.options().quality.legacyBiomeBlendRadius,
                (biome, at) -> sample(palette, biome, at));
    }

    private static int sample(ColorPalette palette, Biome biome, BlockPos pos) {
        return palette.sample(Biome.getIdForBiome(biome), biome.getDefaultTemperature(), biome.getRainfall(),
                pos.getX(), pos.getY(), pos.getZ());
    }

    static int blend(IBlockAccess world, BlockPos pos, int radius, ToIntBiFunction<Biome, BlockPos> sampler) {
        if (radius == 0) return sampler.applyAsInt(world.getBiome(pos), pos);
        var cursor = new BlockPos.MutableBlockPos();
        int red = 0, green = 0, blue = 0;
        for (int z = -radius; z <= radius; z++) {
            for (int x = -radius; x <= radius; x++) {
                cursor.setPos(pos.getX() + x, pos.getY(), pos.getZ() + z);
                int color = sampler.applyAsInt(world.getBiome(cursor), cursor);
                red += color >> 16 & 255;
                green += color >> 8 & 255;
                blue += color & 255;
            }
        }
        int area = (radius * 2 + 1) * (radius * 2 + 1);
        return red / area << 16 | green / area << 8 | blue / area;
    }

    public static Vec3d atmosphere(String kind, World world, Entity camera, float partialTicks, Vec3d original) {
        if (!world.isRemote) return original;
        int dimension = world.provider.getDimension();
        if (kind.equals("sky") && dimension == 1) return vector(color("sky.end", -1), original);
        if (kind.equals("fog") && dimension != 0) {
            if (dimension == -1 || dimension == 1) return vector(color(dimension == -1 ? "fog.nether" : "fog.end", -1), original);
            return original;
        }
        if (dimension != 0) return original;
        int rgb = environment(kind + '0', world, camera, partialTicks);
        if (rgb == -1) return original;
        Vec3d custom = vector(rgb, original);
        if (kind.equals("fog")) {
            return new Vec3d(custom.x * original.x / 0.753, custom.y * original.y / 0.8471, custom.z * original.z);
        }
        return new Vec3d(custom.x * original.x / 0.5, custom.y * original.y / 0.66275, custom.z * original.z);
    }

    public static Vec3d submerged(String key, World world, Entity camera, float partialTicks, Vec3d original) {
        return vector(environment(key, world, camera, partialTicks), original);
    }

    private static int environment(String key, World world, Entity camera, float partialTicks) {
        var colors = snapshot();
        if (colors == null || camera == null) return -1;
        var palette = colors.palettes().get(key);
        if (palette == null) return -1;
        Vec3d eye = camera.getPositionEyes(partialTicks);
        return sample(palette, world, new BlockPos(eye.x, eye.y, eye.z));
    }

    private static Vec3d vector(int rgb, Vec3d original) {
        return rgb == -1 ? original : new Vec3d((rgb >> 16 & 255) / 255.0, (rgb >> 8 & 255) / 255.0, (rgb & 255) / 255.0);
    }

    public static void lightmap(World world, int[] pixels, float partialTicks, float torch, float vision, float gamma) {
        var colors = snapshot();
        var palette = colors == null ? null : colors.lightmaps().get(world.provider.getDimension());
        if (palette == null) return;
        float sun = world.getLastLightningBolt() > 0 ? 1 : (world.getSunBrightness(1) - 0.2f) * (7f / 6);
        palette.render(pixels, sun, torch + 0.5f, vision, gamma, world.getRainStrength(partialTicks), world.getThunderStrength(partialTicks));
    }

    public static int item(ItemStack stack, int tint, int original) {
        var colors = snapshot();
        if (colors == null) return original;
        var item = stack.getItem();
        if (item == Items.SPAWN_EGG && (tint == 0 || tint == 1)) {
            var entity = ItemMonsterPlacer.getNamedIdFrom(stack);
            if (entity != null) {
                String prefix = tint == 0 ? "egg.shell." : "egg.spots.";
                return colors.colors().getOrDefault(prefix + entity, colors.colors().getOrDefault(prefix + entity.getPath(), original));
            }
        }
        if (tint == 0 && (item == Items.POTIONITEM || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION || item == Items.TIPPED_ARROW)) {
            var tag = stack.getTagCompound();
            if (tag != null && tag.hasKey("CustomPotionColor", 99)) return original;
            return potion(PotionUtils.getEffectsFromStack(stack), original);
        }
        if (tint == 0 && item instanceof ItemArmor armor) return armor(armor, stack, original);
        return original;
    }

    public static int armor(ItemArmor armor, ItemStack stack, int original) {
        return armor.getArmorMaterial() == ItemArmor.ArmorMaterial.LEATHER && !armor.hasColor(stack)
                ? color("armor.default", original) : original;
    }

    public static float[] dye(String group, EnumDyeColor dye, float[] original) {
        int rgb = dyeRgb(group, dye, -1);
        return rgb == -1 ? original : new float[] {(rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f};
    }

    public static int dyeRgb(String group, EnumDyeColor dye, int original) {
        return dyeRgb(snapshot(), group, dye, original);
    }

    /** Reads the current reload's dye color when the texture manager rebuilds layered textures. */
    public static int layeredDyeRgb(EnumDyeColor dye, int original) {
        var registered = handle;
        return dyeRgb(registered == null ? null : registered.getDuringReload(), "dye", dye, original);
    }

    private static int dyeRgb(ColorSnapshot snapshot, String group, EnumDyeColor dye, int original) {
        if (snapshot == null) return original;
        var colors = snapshot.colors();
        return colors.getOrDefault(group + '.' + dye.getTranslationKey(),
                colors.getOrDefault(group + '.' + dye.getName(), original));
    }

    public static Object revision() {
        return snapshot();
    }

    public static int map(int index, int shade, int original) {
        if (index <= 0 || index >= MAP_NAMES.length) return original;
        String name = MAP_NAMES[index];
        int rgb = color("map." + name, -1);
        if (rgb == -1 && index == 8) rgb = color("map.white", -1);
        if (rgb == -1 && index == 15) rgb = color("map.adobe", -1);
        if (rgb == -1 && index == 17) rgb = color("map.lightBlue", -1);
        if (rgb == -1) return original;
        int brightness = switch (shade) { case 0 -> 180; case 1 -> 220; case 3 -> 135; default -> 255; };
        return 0xff000000 | ((rgb >> 16 & 255) * brightness / 255) << 16
                | ((rgb >> 8 & 255) * brightness / 255) << 8 | (rgb & 255) * brightness / 255;
    }

    public static int[] fontColors(int[] original) {
        var snapshot = snapshot();
        if (snapshot == null) return original;
        int[] colors = original;
        for (int i = 0; i < original.length; i++) {
            Integer rgb = snapshot.colors().get("text.code." + i);
            if (rgb == null) continue;
            if (colors == original) colors = original.clone();
            colors[i] = rgb;
        }
        return colors;
    }

    public static void particle(Particle particle, int id, World world, double x, double y, double z) {
        var snapshot = snapshot();
        if (snapshot == null) return;
        var type = EnumParticleTypes.getParticleFromId(id);
        if (type == null) return;
        switch (type) {
            case WATER_BUBBLE, WATER_SPLASH, WATER_WAKE, WATER_DROP, DRIP_WATER -> waterParticle(snapshot, particle, world, x, y, z);
            case SUSPENDED -> {
                var palette = snapshot.palettes().get("underwater");
                if (palette != null) particleRgb(particle, sample(palette, world, new BlockPos(x, y, z)), 1);
            }
            case REDSTONE -> {
                var palette = snapshot.palettes().get("redstone");
                if (palette != null) {
                    var state = world.getBlockState(new BlockPos(x, y, z));
                    int power = state.getBlock() == Blocks.REDSTONE_WIRE ? state.getValue(BlockRedstoneWire.POWER) : 15;
                    particleRgb(particle, palette.indexed(power), 1);
                }
            }
            case PORTAL -> {
                int rgb = snapshot.colors().getOrDefault("particle.portal", -1);
                if (rgb != -1) particleRgb(particle, rgb, particle.getBlueColorF());
            }
            case TOWN_AURA -> {
                var palette = snapshot.palettes().get("myceliumparticle");
                if (palette != null) particleRgb(particle, palette.indexed(ThreadLocalRandom.current().nextInt(palette.size())), 1);
            }
            case DRIP_LAVA -> drip(particle, Material.LAVA, 0, world, x, y, z);
            default -> { }
        }
    }

    public static void drip(Particle particle, Material material, int age, World world, double x, double y, double z) {
        var snapshot = snapshot();
        if (snapshot == null) return;
        if (material == Material.WATER) {
            waterParticle(snapshot, particle, world, x, y, z);
        } else if (material == Material.LAVA) {
            var palette = snapshot.palettes().get("lavadrop");
            if (palette != null) particleRgb(particle, palette.indexed(age), 1);
        }
    }

    private static void waterParticle(ColorSnapshot snapshot, Particle particle, World world, double x, double y, double z) {
        var palette = snapshot.palettes().get("water");
        int base = snapshot.colors().getOrDefault("particle.water", -1);
        if (palette == null && base == -1) return;
        int tint = palette == null ? 0xffffff : sample(palette, world, new BlockPos(x, y, z));
        if (base == -1) base = 0xffffff;
        particle.setRBGColorF((base >> 16 & 255) * (tint >> 16 & 255) / 65025f,
                (base >> 8 & 255) * (tint >> 8 & 255) / 65025f, (base & 255) * (tint & 255) / 65025f);
    }

    private static void particleRgb(Particle particle, int color, float scale) {
        particle.setRBGColorF((color >> 16 & 255) * scale / 255, (color >> 8 & 255) * scale / 255, (color & 255) * scale / 255);
    }

    static int potion(Collection<PotionEffect> effects, int original) {
        var snapshot = snapshot();
        if (snapshot == null) return original;
        var colors = snapshot.colors();
        if (effects.isEmpty()) return colors.getOrDefault("potion.water", original);
        long r = 0, g = 0, b = 0, weight = 0;
        boolean changed = false;
        for (var effect : effects) {
            if (!effect.doesShowParticles()) continue;
            var potion = effect.getPotion();
            String key = potion.getName();
            Integer color = colors.get("potion." + (key.startsWith("effect.") ? key.substring(7) : key));
            if (color != null) changed = true;
            else color = potion.getLiquidColor();
            int count = effect.getAmplifier() + 1;
            r += (color >> 16 & 255) * (long) count;
            g += (color >> 8 & 255) * (long) count;
            b += (color & 255) * (long) count;
            weight += count;
        }
        return changed && weight > 0 ? (int) (r / weight) << 16 | (int) (g / weight) << 8 | (int) (b / weight) : original;
    }
}
