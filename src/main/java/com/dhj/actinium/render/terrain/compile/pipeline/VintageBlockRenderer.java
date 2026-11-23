package com.dhj.actinium.render.terrain.compile.pipeline;

import com.dhj.actinium.api.render.terrain.BlockQuadTransformerHolder;
import com.dhj.actinium.compat.MissingModelCompat;
import com.dhj.actinium.compat.componentmodelhider.ComponentModelHiderCompat;
import com.dhj.actinium.compat.farmersfuturedelight.FarmersFutureDelightCompat;
import com.dhj.actinium.compat.librarianlib.LibrarianLibGlowCompat;
import com.dhj.actinium.resourcepack.color.ResourcePackColors;
import com.dhj.actinium.resourcepack.ctm.TerrainPackRules.OutputQuad;
import net.coderbot.iris.debug.ShaderRegressionDebug;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockModelShapes;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.color.IBlockColor;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldType;
import net.minecraftforge.client.model.pipeline.VertexBufferConsumer;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.registries.IRegistryDelegate;
import dhj.embeddedt.embeddium.api.util.ColorARGB;
import dhj.embeddedt.embeddium.impl.model.light.LightMode;
import dhj.embeddedt.embeddium.impl.model.light.LightPipeline;
import dhj.embeddedt.embeddium.impl.model.light.LightPipelineProvider;
import dhj.embeddedt.embeddium.impl.model.light.data.QuadLightData;
import dhj.embeddedt.embeddium.impl.model.light.debug.AODebug;
import dhj.embeddedt.embeddium.impl.model.quad.BakedQuadView;
import dhj.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import dhj.embeddedt.embeddium.impl.model.quad.properties.ModelQuadOrientation;
import dhj.embeddedt.embeddium.impl.render.chunk.ChunkColorWriter;
import dhj.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers;
import dhj.embeddedt.embeddium.impl.render.chunk.compile.buffers.ChunkModelBuilder;
import dhj.embeddedt.embeddium.impl.render.chunk.compile.pipeline.BakedQuadGroupAnalyzer;
import dhj.embeddedt.embeddium.impl.render.chunk.data.MinecraftBuiltRenderSectionData;
import dhj.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import dhj.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import dhj.embeddedt.embeddium.impl.util.ModelQuadUtil;
import dhj.embeddedt.embeddium.api.shader.vertex.BlockRenderContext;
import dhj.embeddedt.embeddium.api.shader.vertex.ContextAwareChunkVertexEncoder;
import dhj.embeddedt.embeddium.api.shader.vertex.ExtendedDataHelper;
import dhj.embeddedt.embeddium.api.shader.ShaderProvider;
import dhj.embeddedt.embeddium.api.shader.ShaderProviderHolder;
import net.coderbot.iris.block_rendering.BlockMaterialMapping;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import com.dhj.actinium.runtime.ActiniumRuntime;
import com.dhj.actinium.world.cloned.ActiniumBlockAccess;
import com.dhj.actinium.render.terrain.compile.VintageChunkBuildContext;
import com.dhj.actinium.render.terrain.compile.VintageTranslucentMaterials;
import com.dhj.actinium.render.terrain.compile.light.BlockQuadLightOverride;
import com.dhj.actinium.render.terrain.compile.light.LightDataCache;
import com.dhj.actinium.render.terrain.compile.light.VintageDiffuseProvider;
import com.dhj.actinium.mixin.vintage.core.terrain.AccessorBlockColors;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;

import java.util.Arrays;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static net.minecraft.block.material.Material.LAVA;
import static net.minecraft.block.material.Material.WATER;

public class VintageBlockRenderer {
    private final BlockModelShapes shapes;
    private final VintageChunkBuildContext context;
    private final VertexBufferConsumer consumer;
    private final LightPipelineProvider lighters;
    private final Map<IRegistryDelegate<Block>, IBlockColor> blockColors;

    private final QuadLightData quadLightData = new QuadLightData();
    private final int[] quadColors = new int[4];
    private final ChunkVertexEncoder.Vertex[] vertices = ChunkVertexEncoder.Vertex.uninitializedQuad();
    private final ArrayList<ContextAwareChunkVertexEncoder> usedContextEncoders = new ArrayList<>(4);
    private final BlockRenderContext blockRenderContext = new BlockRenderContext();
    private final boolean useRenderPassOptimization;
    private final BlockQuadLightOverride quadLightOverride = LibrarianLibGlowCompat.create();

    private IBlockState currentState;
    private ActiniumBlockAccess currentBlockAccess;
    private int currentMetadata;
    private int currentShaderMetadata;
    private int currentShaderBlockId;
    private BlockRenderLayer currentRenderLayer;
    private boolean currentWaterloggedWater;
    private boolean overrideQuadLight;

    private final BakedQuadGroupAnalyzer analyzer = new BakedQuadGroupAnalyzer();

    private int currentQuadRenderingFlags;

    private IBakedModel preparedModel;
    private boolean renderedAnyQuad;
    private OutputQuad packQuad;


    public VintageBlockRenderer(VintageChunkBuildContext context, LightDataCache cache) {
        this.shapes = Minecraft.getMinecraft().getBlockRendererDispatcher().getBlockModelShapes();
        this.consumer = new VertexBufferConsumer();
        this.context = context;
        this.lighters = new LightPipelineProvider(cache, VintageDiffuseProvider.INSTANCE, false);
        this.blockColors = ((AccessorBlockColors)Minecraft.getMinecraft().getBlockColors()).getBlockColorMap();
        this.useRenderPassOptimization = ActiniumRuntime.options().performance.useRenderPassOptimization;
    }

    public void resetSharedState() {
        this.currentWaterloggedWater = false;
    }

    protected Material resolveRenderMaterial(ChunkBuildBuffers buffers, IBlockState state, BlockRenderLayer layer) {
        boolean isFluid = state.getMaterial() == WATER || state.getMaterial() == LAVA
                || this.currentWaterloggedWater;
        var configuration = buffers.getRenderPassConfiguration();
        return VintageTranslucentMaterials.select(configuration, layer, configuration.getMaterialForRenderType(layer),
                state.getBlock(), isFluid, this.context.isWriteStainedGlassDepth());
    }

    public void renderBlock(IBlockState state, BlockPos pos, ActiniumBlockAccess blockAccess, BlockRenderLayer layer) {
        this.renderBlock(state, pos, blockAccess, layer, true);
    }

    public void renderBlock(IBlockState state, BlockPos pos, ActiniumBlockAccess blockAccess, BlockRenderLayer layer, boolean allowRenderPassOptimization) {
        int defaultFlags = BakedQuadGroupAnalyzer.USE_ALL_THINGS;
        if (!this.useRenderPassOptimization || !allowRenderPassOptimization) {
            defaultFlags &= ~BakedQuadGroupAnalyzer.USE_RENDER_PASS_OPTIMIZATION;
        }
        this.analyzer.setDefaultRenderingFlags(defaultFlags);
        this.usedContextEncoders.clear();
        this.renderedAnyQuad = false;

        IBakedModel model = this.preparedModel;
        if (model == null) {
            if (blockAccess.getWorldType() != WorldType.DEBUG_ALL_BLOCK_STATES) {
                state = state.getActualState(blockAccess, pos);
            }
            model = this.shapes.getModelForState(state);
        }
        if (MissingModelCompat.isMissingModel(model)) {
            // Missing models have no renderable quads; Forge's fancy variant lazily renders a
            // "missing" label through the font renderer, which requires a GL context and crashes
            // when chunk meshes are built on worker threads.
            return;
        }
        this.currentBlockAccess = blockAccess;
        this.currentMetadata = state.getBlock().getMetaFromState(state);
        this.currentShaderMetadata = BlockMaterialMapping.resolveShaderMetadata(state, pos, blockAccess, this.currentMetadata);
        IBlockState actualState = state;
        state = state.getBlock().getExtendedState(state, blockAccess, pos);
        this.currentState = state;
        this.currentWaterloggedWater = FarmersFutureDelightCompat.usesWaterIdentity(state, model, layer);
        this.currentShaderBlockId = resolveShaderBlockId(state, pos);
        this.currentRenderLayer = layer;

        var buffers = this.context.getRenderBuffers();
        var packGeometry = TerrainPackGeometry.capture();
        BlockRenderLayer sourceLayer = MinecraftForgeClient.getRenderLayer();
        if (sourceLayer == null) {
            sourceLayer = layer;
        }
        BlockRenderLayer targetLayer = packGeometry == null ? layer : packGeometry.targetLayer(state, layer, !allowRenderPassOptimization);
        this.currentRenderLayer = targetLayer;
        var material = resolveRenderMaterial(buffers, state, targetLayer);
        var buffer = buffers.get(material);

        long rand = MathHelper.getPositionRandom(pos);
        IBlockState modelState = state;
        IBakedModel blockModel = model;
        var pane = packGeometry == null || !packGeometry.needsPaneGroups(state) ? null
                : packGeometry.preparePane(state, blockAccess, pos, sourceLayer, rand, side -> {
            var faces = blockModel.getQuads(modelState, side, rand);
            return faces.isEmpty() ? faces : BlockQuadTransformerHolder.transform(modelState, pos, blockAccess, layer, side, faces);
        });

        IBlockColor colorProvider = this.blockColors.get(state.getBlock().delegate);

        var offset = this.currentState.getOffset(blockAccess, pos);

        var aoEnabled = Minecraft.isAmbientOcclusionEnabled() && state.getLightValue(blockAccess, pos) == 0 && model.isAmbientOcclusion(state);

        var lighter = this.lighters.getLighter(aoEnabled ? LightMode.SMOOTH : LightMode.FLAT);

        AODebug.logRenderDecision(
            "block",
            Block.getIdFromBlock(state.getBlock()),
            pos.getX(),
            pos.getY(),
            pos.getZ(),
            Minecraft.isAmbientOcclusionEnabled(),
            state.getLightValue(blockAccess, pos),
            model.isAmbientOcclusion(state),
            aoEnabled,
            BlockRenderingSettings.INSTANCE.getAmbientOcclusionLevel(),
            BlockRenderingSettings.INSTANCE.shouldUseSeparateAo()
        );

        this.overrideQuadLight = this.quadLightOverride != null
                && this.quadLightOverride.beginBlock(blockAccess, actualState, pos);
        try {
            for (var dir : EnumFacing.VALUES) {
                var quads = pane == null ? model.getQuads(state, dir, rand) : pane.quads(dir);

                // Keep the culling call in renderBlock for addon redirects. Hidden blocks do not occlude neighbours.
                if (quads.isEmpty()
                        || (!state.shouldSideBeRendered(blockAccess, pos, dir)
                                && !ComponentModelHiderCompat.isNeighbourHidden(pos, dir))) {
                    continue;
                }

                if (pane == null) {
                    quads = BlockQuadTransformerHolder.transform(state, pos, blockAccess, layer, dir, quads);
                }
                if (quads.isEmpty()) {
                    continue;
                }

                var transformed = packGeometry == null ? null
                        : packGeometry.transform(state, blockAccess, pos, sourceLayer, targetLayer,
                                !allowRenderPassOptimization, dir, rand, quads, pane);
                if (transformed != null) {
                    quads = new PackBakedQuads(transformed);
                }
                if (!quads.isEmpty()) {
                    this.currentQuadRenderingFlags = this.analyzer.getFlagsForRendering(VintageDiffuseProvider.fromEnumFacing(dir), BakedQuadView.ofList(quads));
                    renderQuadList(buffer, buffers, material, pos, dir, lighter, colorProvider, offset, quads);
                    this.renderedAnyQuad = true;
                }
            }

            var quads = pane == null ? model.getQuads(state, null, rand) : pane.quads(null);

            if (!quads.isEmpty()) {
                if (pane == null) {
                    quads = BlockQuadTransformerHolder.transform(state, pos, blockAccess, layer, null, quads);
                }
                if (!quads.isEmpty()) {
                    var transformed = packGeometry == null ? null
                            : packGeometry.transform(state, blockAccess, pos, sourceLayer, targetLayer,
                                    !allowRenderPassOptimization, null, rand, quads, pane);
                    if (transformed != null) {
                        quads = new PackBakedQuads(transformed);
                    }
                    if (!quads.isEmpty()) {
                        this.currentQuadRenderingFlags = this.analyzer.getFlagsForRendering(ModelQuadFacing.UNASSIGNED, BakedQuadView.ofList(quads));
                        renderQuadList(buffer, buffers, material, pos, null, lighter, colorProvider, offset, quads);
                        this.renderedAnyQuad = true;
                    }
                }
            }
        } finally {
            if (this.overrideQuadLight) {
                this.quadLightOverride.endBlock();
                this.overrideQuadLight = false;
            }
        }

        this.usedContextEncoders.forEach(ContextAwareChunkVertexEncoder::finishRenderingBlock);
        this.usedContextEncoders.clear();
        this.blockRenderContext.reset();
        this.currentState = null;
        this.currentBlockAccess = null;
        this.currentShaderMetadata = 0;
        this.currentShaderBlockId = -1;
        this.currentRenderLayer = null;
        this.currentWaterloggedWater = false;
    }

    /**
     * Renders a block model the {@code BlockRendererDispatcher} already resolved, so model
     * selection stays with the dispatcher while the geometry still goes through the fast mesher.
     * The state is the one the dispatcher derived ({@code getActualState}), matching what
     * {@code BlockModelRenderer#renderModel} receives.
     *
     * @return whether any quad list was handed to {@code renderQuadList}
     */
    public boolean renderModel(IBakedModel model, IBlockState state, BlockPos pos,
                               ActiniumBlockAccess blockAccess, BlockRenderLayer layer) {
        return this.renderModel(model, state, pos, blockAccess, layer, true);
    }

    /**
     * Variant of {@link #renderModel(IBakedModel, IBlockState, BlockPos, ActiniumBlockAccess,
     * BlockRenderLayer)} for renders that must not merge quads into a cheaper render pass, such as
     * the shader layer override and the Better Foliage dispatcher hook it reaches.
     *
     * @return whether any quad list was handed to {@code renderQuadList}
     */
    public boolean renderModel(IBakedModel model, IBlockState state, BlockPos pos,
                               ActiniumBlockAccess blockAccess, BlockRenderLayer layer,
                               boolean allowRenderPassOptimization) {
        IBakedModel previousModel = this.preparedModel;
        boolean previousRendered = this.renderedAnyQuad;
        this.preparedModel = model;
        try {
            this.renderBlock(state, pos, blockAccess, layer, allowRenderPassOptimization);
            return this.renderedAnyQuad;
        } finally {
            this.preparedModel = previousModel;
            this.renderedAnyQuad = previousRendered;
        }
    }

    private QuadLightData getVertexLight(LightPipeline lighter, BlockPos pos, EnumFacing cullFace, BakedQuadView quad) {
        QuadLightData light = this.quadLightData;
        boolean applyDirectionalShading = quad.hasShade() && !BlockRenderingSettings.INSTANCE.shouldDisableDirectionalShading();
        lighter.calculate(quad, pos.getX(), pos.getY(), pos.getZ(), light, VintageDiffuseProvider.fromEnumFacingOrUnassigned(cullFace), quad.getLightFace(), applyDirectionalShading, true);

        if (this.packQuad != null && this.packQuad.light() != null) {
            var minimum = this.packQuad.light();
            for (int vertex = 0; vertex < 4; vertex++) {
                light.lm[vertex] = TerrainPackGeometry.applyMinimumLight(light.lm[vertex], minimum);
            }
            if (minimum.block() == 15 && minimum.sky() == 15) {
                Arrays.fill(light.br, 1.0f);
            }
        }

        return light;
    }

    private int[] getVertexColors(BlockPos pos, IBlockColor colorProvider, BakedQuadView quad) {
        final int[] vertexColors = this.quadColors;
        var state = this.packQuad != null && this.packQuad.tintState() != null
                ? this.packQuad.tintState() : this.currentState;
        if (state != this.currentState) {
            colorProvider = this.blockColors.get(state.getBlock().delegate);
        }
        int tint = this.packQuad == null ? quad.getColorIndex() : this.packQuad.tintIndex();
        int custom = ResourcePackColors.block(state, this.currentBlockAccess, pos, tint);

        if (custom != -1) {
            Arrays.fill(vertexColors, 0xff000000 | ColorARGB.toABGR(custom));
        } else if (colorProvider != null && tint >= 0) {
            Arrays.fill(vertexColors, 0xff000000 | ColorARGB.toABGR(colorProvider.colorMultiplier(state, this.currentBlockAccess, pos, tint)));
        } else {
            Arrays.fill(vertexColors, 0xFFFFFFFF);
        }

        return vertexColors;
    }

    private void renderPackQuads(List<OutputQuad> quads, ChunkBuildBuffers buffers, Material baseMaterial, BlockPos pos,
            EnumFacing side, LightPipeline lighter, IBlockColor provider, Vec3d offset) {
        BlockRenderLayer previous = this.currentRenderLayer;
        int previousFlags = this.currentQuadRenderingFlags;
        this.currentQuadRenderingFlags = BakedQuadGroupAnalyzer.USE_ALL_THINGS & ~BakedQuadGroupAnalyzer.USE_RENDER_PASS_OPTIMIZATION;
        try {
            for (OutputQuad quad : quads) {
                this.packQuad = quad;
                this.currentRenderLayer = quad.layer();
                var material = quad.layer() == previous ? baseMaterial : resolveRenderMaterial(buffers, this.currentState, quad.layer());
                this.renderQuadList(buffers.get(material), buffers, material, pos, side, lighter, provider, offset,
                        List.of(quad.quad()));
            }
        } finally {
            this.currentRenderLayer = previous;
            this.currentQuadRenderingFlags = previousFlags;
            this.packQuad = null;
        }
    }

    private static final class PackBakedQuads extends AbstractList<BakedQuad> {
        private final List<OutputQuad> quads;

        private PackBakedQuads(List<OutputQuad> quads) {
            this.quads = quads;
        }

        @Override
        public BakedQuad get(int index) {
            return quads.get(index).quad();
        }

        @Override
        public int size() {
            return quads.size();
        }
    }

    private void renderQuadList(ChunkModelBuilder defaultBuffer, ChunkBuildBuffers buffers,
                                Material material, BlockPos pos, EnumFacing cullFace,
                                LightPipeline lighter, IBlockColor colorProvider, Vec3d offset,
                                List<BakedQuad> quads) {
        if (quads instanceof PackBakedQuads packQuads) {
            this.renderPackQuads(packQuads.quads, buffers, material, pos, cullFace, lighter, colorProvider, offset);
            return;
        }
        int localX = pos.getX() & 15, localY = pos.getY() & 15, localZ = pos.getZ() & 15;
        var config = buffers.getRenderPassConfiguration();
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0, quadsSize = quads.size(); i < quadsSize; i++) {
            var quad = quads.get(i);
            var format = quad.getFormat();
            if (format != DefaultVertexFormats.ITEM && format != DefaultVertexFormats.BLOCK && format.getElement(0).getUsage() != VertexFormatElement.EnumUsage.POSITION) {
                throw new IllegalStateException("Vertex format does not have POSITION as first element: " + format);
            }

            BakedQuadView quadView = (BakedQuadView)quad;

            int packedLightOverride = this.overrideQuadLight
                    ? this.quadLightOverride.getLight(quad) : BlockQuadLightOverride.UNCHANGED;
            var light = this.getVertexLight(lighter, pos, cullFace, quadView);
            var colors = this.getVertexColors(pos, colorProvider, quadView);

            ModelQuadOrientation orientation = ModelQuadOrientation.NORMAL;

            var quadMaterial = isCurrentFluid() || material == config.defaultFluidMaterial()
                    ? material
                    : BakedQuadGroupAnalyzer.chooseOptimalMaterial(this.currentQuadRenderingFlags, material, config, BakedQuadView.of(quad));
            ChunkModelBuilder buffer = (quadMaterial == material) ? defaultBuffer : buffers.get(quadMaterial);
            this.prepareEncoder(buffer, pos);

            this.writeGeometry(localX, localY, localZ, buffer, offset, quadMaterial,
                    quadView, colors, light, orientation, packedLightOverride);

            TextureAtlasSprite sprite = (TextureAtlasSprite)quadView.celeritas$getSprite();

            if (sprite.hasAnimationMetadata() && buffer.getSectionContextBundle() instanceof MinecraftBuiltRenderSectionData<?,?> mcData) {
                //noinspection unchecked
                ((Collection<TextureAtlasSprite>)mcData.animatedSprites).add(sprite);
            }
        }
    }

    private void prepareEncoder(ChunkModelBuilder builder, BlockPos pos) {
        if (!(builder.getEncoder() instanceof ContextAwareChunkVertexEncoder encoder)) {
            return;
        }

        if (this.usedContextEncoders.contains(encoder)) {
            return;
        }

        Block block = this.currentState.getBlock();
        Block contextBlock = this.currentWaterloggedWater ? Blocks.WATER : block;
        int contextMetadata = this.currentWaterloggedWater ? 0 : this.currentShaderMetadata;
        byte lightValue = (byte) this.currentState.getLightValue(this.currentBlockAccess, pos);
        boolean isFluid = isCurrentFluid();
        int blockId = Block.getIdFromBlock(contextBlock);
        short renderType = isFluid
                ? ExtendedDataHelper.FLUID_RENDER_TYPE
                : ExtendedDataHelper.BLOCK_RENDER_TYPE;

        this.blockRenderContext.set(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, blockId, renderType, lightValue);
        ShaderRegressionDebug.logTerrainState(
                "prepare-encoder",
                block,
                pos,
                this.currentRenderLayer,
                this.currentMetadata,
                this.currentShaderMetadata,
                blockId,
                this.currentShaderBlockId,
                renderType,
                lightValue
        );
        if (isFluid) {
            encoder.prepareToRenderFluid(this.blockRenderContext, contextBlock, contextMetadata, lightValue);
        } else {
            encoder.prepareToRenderBlock(this.blockRenderContext, block, this.currentShaderMetadata, renderType, lightValue);
            if (this.currentShaderBlockId != -1) {
                this.blockRenderContext.blockId = this.currentShaderBlockId;
            }
        }
        this.usedContextEncoders.add(encoder);
    }

    private boolean isCurrentFluid() {
        return this.currentWaterloggedWater
                || this.currentState.getMaterial() == WATER
                || this.currentState.getMaterial() == LAVA;
    }

    private int resolveShaderBlockId(IBlockState state, BlockPos pos) {
        Block block = state.getBlock();
        ShaderProvider provider = ShaderProviderHolder.getProvider();
        int providerId = provider != null ? provider.getBlockStateId(block, this.currentShaderMetadata) : Block.getIdFromBlock(block);
        int shaderBlockId = providerId;
        int nbtBlockId = -1;

        if (BlockRenderingSettings.INSTANCE.getBlockNbtMap() != null && block.hasTileEntity(state)) {
            TileEntity tileEntity = this.currentBlockAccess.getTileEntity(pos);
            nbtBlockId = BlockRenderingSettings.INSTANCE.resolveBlockNbtId(block, this.currentMetadata, tileEntity);
            if (nbtBlockId != -1) {
                shaderBlockId = nbtBlockId;
            }
        }

        ShaderRegressionDebug.logTerrainIdResolution(
                block,
                pos,
                this.currentMetadata,
                this.currentShaderMetadata,
                providerId,
                -1,
                nbtBlockId,
                shaderBlockId
        );

        return shaderBlockId;
    }

    private void writeGeometry(int localX, int localY, int localZ, ChunkModelBuilder builder,
                               Vec3d offset,
                               Material material,
                               BakedQuadView quad,
                               int[] colors,
                               QuadLightData light,
                               ModelQuadOrientation orientation,
                               int packedLightOverride)
    {
        var vertices = this.vertices;
        ChunkColorWriter colorWriter = BlockRenderingSettings.INSTANCE.shouldUseSeparateAo()
                ? ChunkColorWriter.SEPARATE_AO
                : ChunkColorWriter.EMBEDDIUM;

        ModelQuadFacing normalFace = quad.getNormalFace();

        int vanillaNormal = normalFace.getPackedNormal();
        int trueNormal = quad.getComputedFaceNormal();

        for (int dstIndex = 0; dstIndex < 4; dstIndex++) {
            int srcIndex = orientation.getVertexIndex(dstIndex);

            var out = vertices[dstIndex];
            out.x = localX + quad.getX(srcIndex) + (float) offset.x;
            out.y = localY + quad.getY(srcIndex) + (float) offset.y;
            out.z = localZ + quad.getZ(srcIndex) + (float) offset.z;

            int mixedColor = ModelQuadUtil.mixARGBColors(colors[srcIndex], quad.getColor(srcIndex));
            int writtenColor = colorWriter.writeColor(mixedColor, light.br[srcIndex]);
            out.color = writtenColor;
            AODebug.logVertexColor(
                "geometry",
                Block.getIdFromBlock(this.currentState.getBlock()),
                localX,
                localY,
                localZ,
                dstIndex,
                light.br[srcIndex],
                mixedColor,
                writtenColor,
                BlockRenderingSettings.INSTANCE.shouldUseSeparateAo()
            );

            out.u = quad.getTexU(srcIndex);
            out.v = quad.getTexV(srcIndex);

            int packedLight = packedLightOverride != BlockQuadLightOverride.UNCHANGED ? packedLightOverride
                    : ModelQuadUtil.mergeBakedLight(quad.getLight(srcIndex), quad.getVanillaLightEmission(), light.lm[srcIndex]);
            out.light = this.packQuad != null && this.packQuad.light() != null
                    ? TerrainPackGeometry.applyMinimumLight(packedLight, this.packQuad.light()) : packedLight;

            out.vanillaNormal = vanillaNormal;
            out.trueNormal = trueNormal;
        }

        var vertexBuffer = builder.getVertexBuffer(normalFace);
        vertexBuffer.push(vertices, material);
    }
}
