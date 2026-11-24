package com.dhj.actinium.resourcepack.ctm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.dhj.actinium.resourcepack.ResourcePackIndex;
import com.dhj.actinium.resourcepack.ResourcePackReload;
import com.dhj.actinium.resourcepack.ctm.api.client.CachingPredicates;
import com.dhj.actinium.resourcepack.ctm.api.client.QuadProcessor;
import com.dhj.actinium.resourcepack.ctm.client.CtmRuleTypes;
import com.dhj.actinium.resourcepack.ctm.client.model.OverlayBakedQuad;
import com.dhj.actinium.resourcepack.ctm.impl.client.ProcessingContextImpl;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;

/** Applies a single resource generation to the baked quads of a terrain build. */
public final class TerrainPackRules {
    private static final int MAX_PASSES = 3;
    private static volatile ResourcePackReload.Handle<Snapshot> handle;

    private TerrainPackRules() {
    }

    public static synchronized void register() {
        if (handle == null) {
            CtmRuleTypes.registerLoaders();
            handle = ResourcePackReload.register(new TerrainCtmModule());
        }
    }

    @Nullable
    public static Snapshot snapshot() {
        var registered = handle;
        return registered == null ? null : registered.get();
    }

    public static BlockRenderLayer targetLayer(@Nullable Snapshot snapshot, IBlockState state, BlockRenderLayer fallback) {
        if (snapshot != null) {
            for (var entry : snapshot.layers) {
                if (entry.matches().test(state)) {
                    return entry.layer();
                }
            }
        }
        return fallback;
    }

    /** Returns null when the caller can submit the input list directly to targetLayer. */
    @Nullable
    public static List<OutputQuad> transform(@Nullable Snapshot snapshot, IBlockState state, IBlockAccess world,
            BlockPos pos, BlockRenderLayer sourceLayer, BlockRenderLayer targetLayer, @Nullable EnumFacing side,
            long seed, List<BakedQuad> quads) {
        return transform(snapshot, state, world, pos, sourceLayer, targetLayer, side, seed, quads, null);
    }

    /** Applies the same prepared pane replacement to each face group of this block. */
    @Nullable
    public static List<OutputQuad> transform(@Nullable Snapshot snapshot, IBlockState state, IBlockAccess world,
            BlockPos pos, BlockRenderLayer sourceLayer, BlockRenderLayer targetLayer, @Nullable EnumFacing side,
            long seed, List<BakedQuad> quads, @Nullable PaneReplacement pane) {
        if (snapshot == null || snapshot.rules.isEmpty() && snapshot.grass == null || quads.isEmpty()) {
            return null;
        }
        if (pane != null && side != null) return List.of();
        BlockRenderLayer previous = MinecraftForgeClient.getRenderLayer();
        ForgeHooksClient.setRenderLayer(sourceLayer);
        try {
            return transformQuads(snapshot, state, world, pos, targetLayer, side, seed, quads, pane);
        } finally {
            ForgeHooksClient.setRenderLayer(previous);
        }
    }

    /** Verifies all face groups and resolves legacy CTM before the renderer submits any group. */
    @Nullable
    public static PaneReplacement prepareLegacyPane(@Nullable Snapshot snapshot, IBlockState state, IBlockAccess world,
            BlockPos pos, BlockRenderLayer sourceLayer, long seed, Function<EnumFacing, List<BakedQuad>> faces) {
        if (!hasLegacyPaneRules(snapshot, state)) return null;
        BlockRenderLayer previous = MinecraftForgeClient.getRenderLayer();
        ForgeHooksClient.setRenderLayer(sourceLayer);
        try {
            var model = LegacyGlassPane.identify(state, snapshot.paneModelOverrides, faces.apply(null), faces);
            if (model == null) return null;
            var context = new ProcessingContextImpl();
            boolean legacyTile = false;
            for (BakedQuad probe : model.probes()) {
                TextureAtlasSprite sprite = probe.getSprite();
                context.reset();
                for (Rule rule : snapshot.rules) {
                    if (!rule.matches(state, sprite, 0)) continue;
                    var generated = context.getExtraQuads();
                    generated.clear();
                    var action = rule.processor.processQuad(probe, sprite, world, pos, state, state, seed, 0, context);
                    if (action == QuadProcessor.ProcessingResult.NEXT_PROCESSOR) continue;
                    if (action == QuadProcessor.ProcessingResult.DISCARD) return null;
                    if (!generated.isEmpty()) {
                        if (!rule.legacyPane) return null;
                        legacyTile |= generated.stream().anyMatch(quad -> quad.getSprite() != sprite);
                    }
                    break;
                }
            }
            return legacyTile ? new PaneReplacement(model.mesh(state, world, pos), model.edgeSprite()) : null;
        } finally {
            ForgeHooksClient.setRenderLayer(previous);
        }
    }

    /** Reports whether the caller needs to collect the complete model for a possible pane replacement. */
    public static boolean hasLegacyPaneRules(@Nullable Snapshot snapshot, IBlockState state) {
        return snapshot != null && snapshot.hasLegacyPaneRules && LegacyGlassPane.isVanillaPane(state);
    }

    /** Keeps the culled cap groups visible across a vertical join between different dyes. */
    public static boolean renderStainedPaneSeam(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return side.getAxis() == EnumFacing.Axis.Y && state.getBlock() == Blocks.STAINED_GLASS_PANE
                && LegacyGlassPane.separatesDyes(state, world.getBlockState(pos.offset(side)));
    }

    /** Block-local mesh shared by the general list and the culled face lists. */
    public static final class PaneReplacement {
        private final List<BakedQuad> quads;
        private final TextureAtlasSprite edgeSprite;

        private PaneReplacement(List<BakedQuad> quads, TextureAtlasSprite edgeSprite) {
            this.quads = quads;
            this.edgeSprite = edgeSprite;
        }
    }

    @Nullable
    static List<OutputQuad> transformQuads(Snapshot snapshot, IBlockState state, IBlockAccess world,
            BlockPos pos, BlockRenderLayer target, long seed, List<BakedQuad> quads) {
        return transformQuads(snapshot, state, world, pos, target, null, seed, quads, null);
    }

    @Nullable
    private static List<OutputQuad> transformQuads(Snapshot snapshot, IBlockState state, IBlockAccess world,
            BlockPos pos, BlockRenderLayer target, @Nullable EnumFacing side, long seed, List<BakedQuad> quads,
            @Nullable PaneReplacement pane) {
        List<OutputQuad> output = new ArrayList<>(quads.size());
        ProcessingContextImpl context = new ProcessingContextImpl();
        List<BakedQuad> prepared = pane != null ? pane.quads
                : snapshot.grass == null ? quads : snapshot.grass.transform(state, world, pos, side, quads);
        for (BakedQuad quad : prepared) {
            apply(snapshot, quad, state, world, pos, target, seed, context, output,
                    pane != null && quad.getSprite() == pane.edgeSprite);
        }
        if (output.size() == quads.size()) {
            boolean unchanged = true;
            for (int i = 0; i < output.size(); i++) {
                OutputQuad result = output.get(i);
                if (result.quad != quads.get(i) || result.layer != target || result.appended || result.light != null) {
                    unchanged = false;
                    break;
                }
            }
            if (unchanged) {
                return null;
            }
        }
        return output;
    }

    // A rule emitting only overlays can coexist with the first matching replacement.
    private static void apply(Snapshot snapshot, BakedQuad input, IBlockState state, IBlockAccess world, BlockPos pos,
            BlockRenderLayer target, long seed, ProcessingContextImpl context, List<OutputQuad> output, boolean paneRim) {
        BakedQuad current = input;
        List<OutputQuad> overlays = null;
        Set<Rule> used = null;
        for (int pass = 0; pass < MAX_PASSES; pass++) {
            TextureAtlasSprite sprite = current.getSprite();
            BakedQuad next = null;
            context.reset();
            for (Rule rule : snapshot.rules) {
                if (sprite == null || paneRim && rule.legacyPane || used != null && used.contains(rule)) {
                    continue;
                }
                if (!rule.matches(state, sprite, pass)) {
                    continue;
                }
                List<BakedQuad> generated = context.getExtraQuads();
                generated.clear();
                var action = rule.processor.processQuad(current, sprite, world, pos, state, state, seed, pass, context);
                if (action == QuadProcessor.ProcessingResult.NEXT_PROCESSOR) {
                    if (!generated.isEmpty()) {
                        if (overlays == null) {
                            overlays = new ArrayList<>();
                            used = Collections.newSetFromMap(new IdentityHashMap<>());
                        }
                        used.add(rule);
                        for (BakedQuad quad : generated) {
                            overlays.add(result(quad, target, true, null));
                        }
                    }
                    continue;
                }
                if (action == QuadProcessor.ProcessingResult.DISCARD) {
                    return;
                }
                if (action == QuadProcessor.ProcessingResult.NEXT_PASS && pass + 1 < MAX_PASSES
                        && generated.size() == 1 && generated.getFirst().getSprite() != sprite) {
                    next = generated.getFirst();
                    break;
                }
                BlockRenderLayer layer = rule.layer != null ? rule.layer : target;
                for (BakedQuad quad : generated.isEmpty() ? List.of(current) : generated) {
                    output.add(result(quad, layer, false, rule.light));
                    if (rule.emissiveFallback && layer != target) {
                        output.add(result(quad, target, true, new Light(15, 15)));
                    }
                }
                if (overlays != null) {
                    output.addAll(overlays);
                }
                return;
            }
            if (next == null) {
                output.add(result(current, target, false, null));
                if (overlays != null) {
                    output.addAll(overlays);
                }
                return;
            }
            current = next;
        }
    }

    private static OutputQuad result(BakedQuad quad, BlockRenderLayer layer, boolean appended, @Nullable Light light) {
        if (quad instanceof OverlayBakedQuad overlay) {
            return new OutputQuad(quad, overlay.getRenderLayer(), true, overlay.tintState(), quad.getTintIndex(), light);
        }
        return new OutputQuad(quad, layer, appended, null, quad.getTintIndex(), light);
    }

    /** A generated quad and its submission parameters. A null tintState uses the source state. */
    public record OutputQuad(BakedQuad quad, BlockRenderLayer layer, boolean appended,
            @Nullable IBlockState tintState, int tintIndex, @Nullable Light light) {
    }

    /** Minimum block and sky light in vanilla's 0-15 range. */
    public record Light(int block, int sky) {
    }

    public static final class Snapshot {
        private final List<Rule> rules;
        private final List<TerrainCtmModule.LayerRule> layers;
        private final BetterGrass grass;
        private final Set<String> paneModelOverrides;
        private final boolean hasLegacyPaneRules;

        Snapshot(List<Rule> rules, List<TerrainCtmModule.LayerRule> layers) {
            this(rules, layers, null);
        }

        Snapshot(List<Rule> rules, List<TerrainCtmModule.LayerRule> layers, @Nullable BetterGrass grass) {
            this(rules, layers, grass, Set.of());
        }

        private Snapshot(List<Rule> rules, List<TerrainCtmModule.LayerRule> layers, @Nullable BetterGrass grass,
                Set<String> paneModelOverrides) {
            this.rules = List.copyOf(rules);
            this.layers = List.copyOf(layers);
            this.grass = grass;
            this.paneModelOverrides = paneModelOverrides;
            this.hasLegacyPaneRules = rules.stream().anyMatch(Rule::legacyPane);
        }

        Snapshot withLegacyPaneModelOverrides(ResourcePackIndex index) {
            return hasLegacyPaneRules ? new Snapshot(rules, layers, grass, LegacyGlassPane.overriddenModels(index)) : this;
        }
    }

    record Rule(QuadProcessor processor, CachingPredicates predicates, @Nullable BlockRenderLayer layer,
            @Nullable Light light, boolean emissiveFallback, boolean legacyPane) {
        Rule(QuadProcessor processor, CachingPredicates predicates, @Nullable BlockRenderLayer layer,
                @Nullable Light light, boolean emissiveFallback) {
            this(processor, predicates, layer, light, emissiveFallback, false);
        }
        boolean matches(IBlockState state, TextureAtlasSprite sprite, int pass) {
            return (pass == 0 || predicates.isValidForMultipass() && predicates.affectsSprites())
                    && (!predicates.affectsBlockStates() || predicates.affectsBlockState(state))
                    && (!predicates.affectsSprites() || predicates.affectsSprite(sprite));
        }
    }
}
