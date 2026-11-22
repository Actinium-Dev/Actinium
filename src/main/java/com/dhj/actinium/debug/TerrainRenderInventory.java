package com.dhj.actinium.debug;

import dhj.embeddedt.embeddium.impl.render.chunk.RenderSection;
import dhj.embeddedt.embeddium.impl.render.chunk.data.MinecraftBuiltRenderSectionData;
import dhj.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderListIterable;
import dhj.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Accumulates terrain mesh and main-view draw candidates on the render thread for one renderer lifetime. */
public final class TerrainRenderInventory {
    private final History meshHistory = new History();
    private final History drawCandidateHistory = new History();
    private final ReferenceOpenHashSet<TerrainRenderPass> sampledPasses = new ReferenceOpenHashSet<>();
    private @Nullable ChunkRenderListIterable sampledLists;
    private long revision;

    /** Carries block positions through the build and deferred capture until the mesh is installed. */
    public static final class SectionData extends MinecraftBuiltRenderSectionData<TextureAtlasSprite, TileEntity> {
        private Map<ResourceLocation, BitSet> blocks = new HashMap<>();
        private boolean sampledForDraw;

        /** Records a block ID at its local position, regardless of how many layers it used. */
        public void record(Block block, BlockPos pos) {
            ResourceLocation id = Objects.requireNonNull(block.getRegistryName(), "Meshed block has no registry ID");
            int index = ((pos.getY() & 15) << 8) | ((pos.getZ() & 15) << 4) | (pos.getX() & 15);
            this.blocks.computeIfAbsent(id, ignored -> new BitSet()).set(index);
        }

        /** Freezes the recorded positions before the build result is published. */
        public void finish(boolean hasMesh) {
            this.blocks = hasMesh ? Map.copyOf(this.blocks) : Map.of();
        }
    }

    /** Adds the installed mesh to the session history. */
    public void recordInstalled(RenderSection section, SectionData data) {
        if (data.blocks.isEmpty()) {
            return;
        }
        if (this.meshHistory.record(section.positionAsLong(), data.blocks)) {
            this.revision++;
        }

        // An unchanged list can now reference a newly installed mesh at this coordinate.
        this.sampledPasses.clear();
    }

    /** Adds the positions from each installed mesh the first time it enters a main-view draw list. */
    public void recordDrawCandidates(ChunkRenderListIterable renderLists, TerrainRenderPass pass) {
        if (this.sampledLists != renderLists) {
            this.sampledLists = renderLists;
            this.sampledPasses.clear();
        }
        if (!this.sampledPasses.add(pass)) {
            return;
        }

        for (var lists = renderLists.iterator(); lists.hasNext();) {
            var list = lists.next();
            var region = list.getRegion();
            var storage = region.getStorage(pass);
            var indices = list.sectionsWithGeometryIterator();
            if (storage == null || indices == null) {
                continue;
            }
            while (indices.hasNext()) {
                int index = indices.nextByteAsInt();
                RenderSection section = region.getSection(index);
                if (section == null || storage.getSliceMask(index) == 0) {
                    continue;
                }
                if (section.getBuiltContext() instanceof SectionData data && !data.sampledForDraw && !data.blocks.isEmpty()) {
                    data.sampledForDraw = true;
                    if (this.drawCandidateHistory.record(section.positionAsLong(), data.blocks)) {
                        this.revision++;
                    }
                }
            }
        }
    }

    /** Returns a complete snapshot of the session counts. Call on the render thread. */
    public String export() {
        StringBuilder output = new StringBuilder("installedSections=").append(this.meshHistory.sections.size())
                .append(" drawCandidateSections=").append(this.drawCandidateHistory.sections.size());
        this.meshHistory.appendCounts(output, "TERRAIN_MESH");
        this.drawCandidateHistory.appendCounts(output, "TERRAIN_DRAW_CANDIDATE");
        return output.toString();
    }

    /** Returns the render-thread revision of the recorded section counts. */
    public long revision() {
        return this.revision;
    }

    private static final class History {
        private final Long2ObjectOpenHashMap<Map<ResourceLocation, BitSet>> sections = new Long2ObjectOpenHashMap<>();
        private final Map<String, Long> totals = new TreeMap<>();

        private boolean record(long section, Map<ResourceLocation, BitSet> blocks) {
            var known = this.sections.computeIfAbsent(section, ignored -> new HashMap<>());
            boolean changed = false;
            for (var block : blocks.entrySet()) {
                ResourceLocation id = block.getKey();
                BitSet positions = block.getValue();
                BitSet previous = known.get(id);
                int added;
                if (previous == null) {
                    // Completed build positions are read-only, so both histories can share them.
                    known.put(id, positions);
                    added = positions.cardinality();
                } else {
                    if (previous.equals(positions)) {
                        continue;
                    }
                    BitSet union = (BitSet) previous.clone();
                    union.or(positions);
                    added = union.cardinality() - previous.cardinality();
                    if (added == 0) {
                        continue;
                    }
                    known.put(id, union);
                }
                this.totals.merge(id.toString(), (long) added, Long::sum);
                changed = true;
            }
            return changed;
        }

        private void appendCounts(StringBuilder output, String label) {
            this.totals.forEach((id, count) ->
                    output.append('\n').append(label).append('\t').append(count).append('\t').append(id));
        }
    }
}
