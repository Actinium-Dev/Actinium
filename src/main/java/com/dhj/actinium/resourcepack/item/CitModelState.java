package com.dhj.actinium.resourcepack.item;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.vecmath.Matrix4f;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.IRegistry;
import org.apache.commons.lang3.tuple.Pair;

/** Carries Forge's selected model location to the CIT selection before perspective is applied. */
public final class CitModelState {
    private static final ThreadLocal<Lookup> LOOKUP = new ThreadLocal<>();

    private CitModelState() {}

    /** Tracks only model lookups made by this render item's override resolution. */
    public static IBakedModel resolve(ItemOverrideList overrides, IBakedModel base, ItemStack stack,
                                     Supplier<IBakedModel> resolver) {
        CitSnapshot snapshot = ResourcePackItemFeatures.cit();
        if (snapshot == null || !snapshot.variantItems().contains(stack.getItem())) return resolver.get();
        return resolve(overrides, base, stack, snapshot.baseLocations(), resolver);
    }

    static IBakedModel resolve(ItemOverrideList overrides, IBakedModel base, ItemStack stack,
                               Map<IBakedModel, ModelResourceLocation> baseLocations, Supplier<IBakedModel> resolver) {
        Lookup previous = LOOKUP.get();
        Lookup lookup = new Lookup(overrides, stack);
        LOOKUP.set(lookup);
        try {
            IBakedModel result = resolver.get();
            ModelResourceLocation location = null;
            if (lookup.location != null) {
                if (lookup.selected == result) location = lookup.location;
            } else if (result == base) {
                location = baseLocations.get(base);
            }
            return location == null ? result : new LocatedModel(result, location);
        } finally {
            if (previous == null) LOOKUP.remove();
            else LOOKUP.set(previous);
        }
    }

    /** Records the location used by the original Forge lookup without re-evaluating its predicates. */
    public static void record(ItemOverrideList overrides, ItemStack stack, ModelResourceLocation location, IBakedModel selected) {
        Lookup lookup = LOOKUP.get();
        if (lookup != null && lookup.overrides == overrides && lookup.stack == stack) {
            lookup.location = location;
            lookup.selected = selected;
        }
    }

    record Selection(IBakedModel model, boolean replaced) {}

    static Selection select(CitRule rule, IBakedModel model, Map<ResourceLocation, IBakedModel> baked) {
        IBakedModel original = model;
        if (model instanceof LocatedModel located) {
            original = located.delegate;
            if (rule != null) {
                ResourceLocation variant = variant(rule.models(), located.location);
                if (variant != null) {
                    IBakedModel replacement = baked.get(variant);
                    if (replacement != null) return new Selection(replacement, true);
                }
            }
        }
        ResourceLocation fallback = rule == null ? null : rule.models().get("");
        IBakedModel replacement = fallback == null ? null : baked.get(fallback);
        return replacement == null ? new Selection(original, false) : new Selection(replacement, true);
    }

    private static ResourceLocation variant(Map<String, ResourceLocation> replacements, ModelResourceLocation location) {
        String path = location.getPath();
        String namespace = location.getNamespace();
        String itemPath = path.startsWith("item/") ? path.substring(5) : path;
        ResourceLocation replacement = replacements.get(namespace + ':' + path);
        if (replacement == null) replacement = replacements.get(namespace + ':' + itemPath);
        if (replacement == null) replacement = replacements.get(path);
        if (replacement == null) replacement = replacements.get(itemPath);
        if (replacement == null && namespace.equals("minecraft")) {
            String standby = switch (itemPath) {
                case "bow" -> "bow_standby";
                case "fishing_rod" -> "fishing_rod_uncast";
                default -> null;
            };
            if (standby != null) {
                replacement = replacements.get(namespace + ':' + standby);
                if (replacement == null) replacement = replacements.get(standby);
            }
        }
        return replacement;
    }

    static Set<Item> variantItems(List<CitRule> rules) {
        var items = new HashSet<Item>();
        for (CitRule rule : rules) {
            if (rule.models().keySet().stream().anyMatch(key -> !key.isEmpty())) items.addAll(rule.items());
        }
        return Set.copyOf(items);
    }

    static Map<IBakedModel, ModelResourceLocation> baseLocations(IRegistry<ModelResourceLocation, IBakedModel> registry,
                                                                 IBakedModel missing) {
        Map<IBakedModel, ModelResourceLocation> locations = new IdentityHashMap<>();
        Set<IBakedModel> ambiguous = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ModelResourceLocation location : registry.getKeys()) {
            if (!location.getVariant().equals("inventory")) continue;
            IBakedModel model = registry.getObject(location);
            if (model != null && model != missing && locations.put(model, location) != null) ambiguous.add(model);
        }
        ambiguous.forEach(locations::remove);
        return Collections.unmodifiableMap(locations);
    }

    private static final class Lookup {
        final ItemOverrideList overrides;
        final ItemStack stack;
        ModelResourceLocation location;
        IBakedModel selected;

        Lookup(ItemOverrideList overrides, ItemStack stack) {
            this.overrides = overrides;
            this.stack = stack;
        }
    }

    private record LocatedModel(IBakedModel delegate, ModelResourceLocation location) implements IBakedModel {
        @Override public List<BakedQuad> getQuads(IBlockState state, EnumFacing face, long seed) { return delegate.getQuads(state, face, seed); }
        @Override public boolean isAmbientOcclusion() { return delegate.isAmbientOcclusion(); }
        @Override public boolean isAmbientOcclusion(IBlockState state) { return delegate.isAmbientOcclusion(state); }
        @Override public boolean isGui3d() { return delegate.isGui3d(); }
        @Override public boolean isBuiltInRenderer() { return delegate.isBuiltInRenderer(); }
        @Override public TextureAtlasSprite getParticleTexture() { return delegate.getParticleTexture(); }
        @Override public ItemCameraTransforms getItemCameraTransforms() { return delegate.getItemCameraTransforms(); }
        @Override public ItemOverrideList getOverrides() { return delegate.getOverrides(); }
        @Override public Pair<? extends IBakedModel, Matrix4f> handlePerspective(TransformType type) {
            return delegate.handlePerspective(type);
        }
    }
}
