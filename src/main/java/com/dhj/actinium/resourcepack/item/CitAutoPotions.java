package com.dhj.actinium.resourcepack.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.vecmath.Matrix4f;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionType;
import net.minecraft.potion.PotionUtils;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.model.ItemLayerModel;
import org.apache.commons.lang3.tuple.Pair;

/** Discovers potion images and builds their item geometry against the current atlas. */
final class CitAutoPotions {
    private static final String MODERN_ROOT = "optifine/cit/potion/";
    private static final String LEGACY_ROOT = "mcpatcher/cit/potion/";
    private static final ResourceLocation CONTENTS_SPRITE = new ResourceLocation("items/potion_overlay");
    private static final Set<String> BASES = Set.of("water", "awkward", "mundane", "thick");
    private static final Map<String, String> FAMILIES = Map.ofEntries(
        Map.entry("regeneration", "regeneration"), Map.entry("movespeed", "swiftness"),
        Map.entry("speed", "swiftness"), Map.entry("fireresistance", "fire_resistance"),
        Map.entry("fire_resistance", "fire_resistance"), Map.entry("poison", "poison"),
        Map.entry("heal", "healing"), Map.entry("instant_health", "healing"),
        Map.entry("nightvision", "night_vision"), Map.entry("night_vision", "night_vision"),
        Map.entry("weakness", "weakness"), Map.entry("damageboost", "strength"),
        Map.entry("strength", "strength"), Map.entry("moveslowdown", "slowness"),
        Map.entry("slowness", "slowness"), Map.entry("leaping", "leaping"), Map.entry("harm", "harming"),
        Map.entry("instant_damage", "harming"), Map.entry("waterbreathing", "water_breathing"),
        Map.entry("water_breathing", "water_breathing"), Map.entry("invisibility", "invisibility"));

    private CitAutoPotions() {}

    static boolean isImagePath(ResourceLocation id) {
        String path = id.getPath();
        return path.endsWith(".png") && (path.startsWith(MODERN_ROOT) || path.startsWith(LEGACY_ROOT));
    }

    static Image parse(ResourceLocation id) {
        if (!isImagePath(id)) return null;
        String path = id.getPath();
        boolean modern = path.startsWith(MODERN_ROOT);
        String[] parts = path.substring((modern ? MODERN_ROOT : LEGACY_ROOT).length(), path.length() - 4).split("/", -1);
        if (parts.length != 2) return null;
        String name = parts[1];
        if (name.equals("empty")) return parts[0].equals("normal") ? new Image(Bottle.EMPTY, modern, null, null, false) : null;
        Bottle bottle = switch (parts[0]) {
            case "normal" -> Bottle.DRINKABLE;
            case "splash" -> Bottle.SPLASH;
            case "linger" -> Bottle.LINGERING;
            default -> null;
        };
        if (bottle == null) return null;
        if (BASES.contains(name)) return new Image(bottle, modern, name, null, false);
        if (!modern && name.equals("other") && bottle != Bottle.LINGERING) {
            return new Image(bottle, false, null, null, true);
        }

        String family = !modern && name.equals("jump") ? "leaping" : FAMILIES.get(name);
        Potion customEffect = !modern && bottle == Bottle.DRINKABLE ? legacyEffect(name) : null;
        return family == null && customEffect == null ? null : new Image(bottle, modern, family, customEffect, false);
    }

    private static Potion legacyEffect(String name) {
        for (Potion effect : Potion.REGISTRY) {
            ResourceLocation id = Potion.REGISTRY.getNameForObject(effect);
            String translationKey = effect.getName();
            if (id.getNamespace().equals("minecraft") && Potion.getIdFromPotion(effect) <= 23
                && translationKey.startsWith("effect.") && translationKey.substring(7).toLowerCase(Locale.ROOT).equals(name)) return effect;
        }
        return null;
    }

    private enum Bottle {
        DRINKABLE(Items.POTIONITEM, "bottle_drinkable"), SPLASH(Items.SPLASH_POTION, "bottle_splash"),
        LINGERING(Items.LINGERING_POTION, "bottle_lingering"), EMPTY(Items.GLASS_BOTTLE, "glass_bottle");

        final Item item;
        final ModelResourceLocation model;

        Bottle(Item item, String model) {
            this.item = item;
            this.model = new ModelResourceLocation(model, "inventory");
        }
    }

    record Image(Bottle bottle, boolean modern, String family, Potion customEffect, boolean other) {
        Item item() {
            return bottle.item;
        }

        int selectionGroup() {
            return other ? 2 : 1;
        }

        boolean hasTintedContents() {
            // MCPatcher's drinkable and splash images include the liquid. G5 keeps the contents layer.
            return bottle == Bottle.LINGERING || modern && bottle != Bottle.EMPTY;
        }

        boolean matches(ItemStack stack) {
            if (stack.isEmpty() || stack.getItem() != bottle.item) return false;
            if (bottle == Bottle.EMPTY) return true;
            PotionType potion = PotionUtils.getPotionFromItem(stack);
            ResourceLocation id = PotionType.REGISTRY.getNameForObject(potion);
            if (id != null && id.getNamespace().equals("minecraft")) {
                String base = potion.getNamePrefixed("");
                if ((family != null && family.equals(base)) || (other && BASES.contains(base))) return true;
            }
            NBTTagCompound tag = stack.getTagCompound();
            if (customEffect == null || tag == null) return false;
            NBTTagList effects = tag.getTagList("CustomPotionEffects", 10);
            if (effects.tagCount() == 0) return false;
            NBTTagCompound first = effects.getCompoundTagAt(0);
            return first.hasKey("Id", 99) && (first.getByte("Id") & 0xFF) == Potion.getIdFromPotion(customEffect);
        }
    }

    static IBakedModel bake(Image image, ResourceLocation file, TextureMap atlas, ModelBakeEvent event) {
        TextureAtlasSprite bottle = atlas.getAtlasSprite(CitAtlasSprite.id(file).toString());
        if (bottle == atlas.getMissingSprite()) throw new IllegalArgumentException("Missing potion image sprite");
        IBakedModel base = event.getModelRegistry().getObject(image.bottle().model);
        if (base == null || base == event.getModelManager().getMissingModel()) {
            throw new IllegalArgumentException("Missing base item model " + image.bottle().model);
        }

        TextureAtlasSprite contents = null;
        if (image.hasTintedContents()) {
            contents = atlas.getAtlasSprite(CONTENTS_SPRITE.toString());
            if (contents == atlas.getMissingSprite()) throw new IllegalArgumentException("Missing vanilla potion contents sprite");
        }
        return new PotionModel(base, bottle, bakeQuads(bottle, contents));
    }

    static void registerContents(Image image, TextureMap atlas) {
        if (image.hasTintedContents()) atlas.registerSprite(CONTENTS_SPRITE);
    }

    static List<BakedQuad> bakeQuads(TextureAtlasSprite bottle, TextureAtlasSprite contents) {
        List<BakedQuad> quads = new ArrayList<>();
        if (contents != null) quads.addAll(ItemLayerModel.getQuadsForSprite(0, contents, DefaultVertexFormats.ITEM, Optional.empty()));
        quads.addAll(ItemLayerModel.getQuadsForSprite(-1, bottle, DefaultVertexFormats.ITEM, Optional.empty()));
        return List.copyOf(quads);
    }

    private record PotionModel(IBakedModel base, TextureAtlasSprite sprite, List<BakedQuad> quads) implements IBakedModel {
        @Override public List<BakedQuad> getQuads(IBlockState state, EnumFacing face, long seed) {
            return face == null ? quads : List.of();
        }
        @Override public boolean isAmbientOcclusion() { return base.isAmbientOcclusion(); }
        @Override public boolean isGui3d() { return false; }
        @Override public boolean isBuiltInRenderer() { return false; }
        @Override public TextureAtlasSprite getParticleTexture() { return sprite; }
        @Override public ItemOverrideList getOverrides() { return ItemOverrideList.NONE; }
        @Override public Pair<? extends IBakedModel, Matrix4f> handlePerspective(TransformType type) {
            return Pair.of(this, base.handlePerspective(type).getRight());
        }
    }
}
