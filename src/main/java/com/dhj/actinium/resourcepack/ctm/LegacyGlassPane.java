package com.dhj.actinium.resourcepack.ctm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.zip.ZipInputStream;
import javax.annotation.Nullable;

import com.dhj.actinium.resourcepack.ResourcePackIndex;
import com.dhj.actinium.resourcepack.ctm.client.util.QuadUtil;
import net.minecraft.block.BlockPane;
import net.minecraft.block.BlockStainedGlassPane;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Rebuilds the vanilla multipart pane with the MCPatcher topology and texture projections. */
final class LegacyGlassPane {
    private static final Logger LOGGER = LogManager.getLogger("Actinium Resource Packs");
    private static final int LOW = 7;
    private static final int HIGH = 9;
    private static final EnumFacing[] SIDES = {EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST};
    private static final String[] PARTS = {"post", "side", "side_alt", "noside", "noside_alt"};

    private LegacyGlassPane() {
    }

    static boolean isVanillaPane(IBlockState state) {
        return state.getBlock() == Blocks.GLASS_PANE || state.getBlock() == Blocks.STAINED_GLASS_PANE;
    }

    static boolean separatesDyes(IBlockState state, IBlockState neighbor) {
        return state.getBlock() == Blocks.STAINED_GLASS_PANE && neighbor.getBlock() == state.getBlock()
                && state.getValue(BlockStainedGlassPane.COLOR) != neighbor.getValue(BlockStainedGlassPane.COLOR);
    }

    static Set<String> overriddenModels(ResourcePackIndex index) {
        var bundled = builtinModelAssets();
        if (!vanillaAssets(index, "pane", false, bundled)) return Set.of("*");
        Set<String> models = new HashSet<>();
        if (!vanillaAssets(index, "glass_pane", true, bundled)) models.add("glass_pane");
        for (EnumDyeColor dye : EnumDyeColor.values()) {
            String model = dye.getName() + "_stained_glass_pane";
            if (!vanillaAssets(index, model, true, bundled)) models.add(model);
        }
        return Set.copyOf(models);
    }

    private static Map<String, byte[]> builtinModelAssets() {
        String archive = "/actinium-builtin-packs/Actinium-Glass-Pane-Culling-Fix.zip";
        String root = "assets/minecraft/";
        var resource = LegacyGlassPane.class.getResourceAsStream(archive);
        if (resource == null) {
            LOGGER.warn("Missing bundled pane model archive {}", archive);
            return Map.of();
        }
        try (var zip = new ZipInputStream(resource)) {
            var assets = new HashMap<String, byte[]>();
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                String name = entry.getName();
                if (name.startsWith(root) && name.endsWith(".json")) {
                    assets.put(name.substring(root.length()), zip.readAllBytes());
                }
            }
            return assets;
        } catch (IOException ex) {
            LOGGER.warn("Cannot verify bundled pane model assets", ex);
            return Map.of();
        }
    }

    private static boolean vanillaAssets(ResourcePackIndex index, String model, boolean blockstate,
            Map<String, byte[]> bundled) {
        if (blockstate && !vanillaAsset(index, "blockstates/" + model + ".json", bundled)) return false;
        for (String part : PARTS) {
            if (!vanillaAsset(index, "models/block/" + model + "_" + part + ".json", bundled)) return false;
        }
        return true;
    }

    private static boolean vanillaAsset(ResourcePackIndex index, String path, Map<String, byte[]> bundled) {
        var id = new ResourceLocation("minecraft", path);
        try (var resource = index.manager().getResource(id)) {
            byte[] builtin = bundled.get(path);
            return "Default".equals(resource.getResourcePackName())
                    || builtin != null && Arrays.equals(resource.getInputStream().readAllBytes(), builtin);
        } catch (IOException ex) {
            LOGGER.warn("Cannot verify vanilla pane model asset {}", id, ex);
            return false;
        }
    }

    @Nullable
    static Model identify(IBlockState state, Set<String> overrides, List<BakedQuad> general,
            Function<EnumFacing, List<BakedQuad>> culled) {
        if (!isVanillaPane(state) || overrides.contains("*")) return null;
        boolean thick = state.getBlock() == Blocks.STAINED_GLASS_PANE;
        String dye = thick ? state.getValue(BlockStainedGlassPane.COLOR).getName() : "";
        if (overrides.contains(thick ? dye + "_stained_glass_pane" : "glass_pane")) return null;
        var faceId = new ResourceLocation("blocks/glass" + (thick ? "_" + dye : ""));
        var edgeId = new ResourceLocation("blocks/glass_pane_top" + (thick ? "_" + dye : ""));
        var connections = new Connections(state.getValue(BlockPane.NORTH), state.getValue(BlockPane.SOUTH),
                state.getValue(BlockPane.WEST), state.getValue(BlockPane.EAST));
        var expected = new ArrayList<Part>();
        caps(expected, LOW, LOW, HIGH, HIGH);
        var tips = new Part[SIDES.length];
        for (int i = 0; i < SIDES.length; i++) {
            EnumFacing side = SIDES[i];
            int lo = positive(side) ? HIGH : 0;
            int hi = lo == 0 ? LOW : 16;
            if (connections.contains(side)) {
                if (side.getAxis() == EnumFacing.Axis.X) {
                    caps(expected, lo, LOW, hi, HIGH);
                    expected.add(wall(EnumFacing.NORTH, LOW, lo, hi, false));
                    expected.add(wall(EnumFacing.SOUTH, HIGH, lo, hi, false));
                } else {
                    caps(expected, LOW, lo, HIGH, hi);
                    expected.add(wall(EnumFacing.WEST, LOW, lo, hi, false));
                    expected.add(wall(EnumFacing.EAST, HIGH, lo, hi, false));
                }
                tips[i] = wall(side, lo == 0 ? 0 : 16, LOW, HIGH, true);
            } else {
                expected.add(wall(side, lo == 0 ? LOW : HIGH, LOW, HIGH, false));
            }
        }
        var upper = culled.apply(EnumFacing.UP);
        var lower = culled.apply(EnumFacing.DOWN);
        if (general.isEmpty() || general.size() + upper.size() + lower.size() != expected.size()) return null;
        var format = general.getFirst().getFormat();
        if (!format.hasColor() || !format.hasUvOffset(0)) return null;
        TextureAtlasSprite faceSprite = null;
        TextureAtlasSprite edgeSprite = null;
        for (EnumFacing side : EnumFacing.Plane.VERTICAL) {
            for (BakedQuad quad : side == EnumFacing.UP ? upper : lower) {
                if (quad.getFace() != side || !removePart(quad, expected, faceId, edgeId, format)) return null;
                edgeSprite = quad.getSprite();
            }
        }
        for (BakedQuad quad : general) {
            if (!removePart(quad, expected, faceId, edgeId, format)) return null;
            if (quad.getFace().getAxis() == EnumFacing.Axis.Y) edgeSprite = quad.getSprite();
            else faceSprite = quad.getSprite();
        }
        for (int i = 0; i < SIDES.length; i++) {
            List<BakedQuad> group = culled.apply(SIDES[i]);
            if (tips[i] == null ? !group.isEmpty() : group.size() != 1
                    || !matches(group.getFirst(), tips[i], faceId, edgeId, format)) return null;
        }
        return new Model(connections, thick, format, faceSprite, edgeSprite);
    }

    private static boolean removePart(BakedQuad quad, List<Part> expected, ResourceLocation faceId,
            ResourceLocation edgeId, VertexFormat format) {
        for (int i = 0; i < expected.size(); i++) {
            if (matches(quad, expected.get(i), faceId, edgeId, format)) {
                expected.remove(i);
                return true;
            }
        }
        return false;
    }

    private static void caps(List<Part> expected, int minX, int minZ, int maxX, int maxZ) {
        expected.add(new Part(EnumFacing.UP, minX, 16, minZ, maxX, 16, maxZ, true));
        expected.add(new Part(EnumFacing.DOWN, minX, 0, minZ, maxX, 0, maxZ, true));
    }

    private static Part wall(EnumFacing face, int plane, int lo, int hi, boolean edge) {
        return face.getAxis() == EnumFacing.Axis.X ? new Part(face, plane, 0, lo, plane, 16, hi, edge)
                : new Part(face, lo, 0, plane, hi, 16, plane, edge);
    }

    private static boolean matches(BakedQuad quad, Part part, ResourceLocation faceId, ResourceLocation edgeId,
            VertexFormat format) {
        if (quad.getClass() != BakedQuad.class || quad.getFace() != part.face() || quad.getTintIndex() != -1
                || !quad.shouldApplyDiffuseLighting() || !format.equals(quad.getFormat()) || quad.getSprite() == null
                || !(part.edge() ? edgeId : faceId).equals(new ResourceLocation(quad.getSprite().getIconName()))) return false;
        int corners = 0;
        for (int i = 0; i < 4; i++) {
            int x = endpoint(QuadUtil.positionComponent(quad, i, 0), part.minX(), part.maxX());
            int y = endpoint(QuadUtil.positionComponent(quad, i, 1), part.minY(), part.maxY());
            int z = endpoint(QuadUtil.positionComponent(quad, i, 2), part.minZ(), part.maxZ());
            if ((x | y | z) < 0) return false;
            corners |= 1 << (x + 2 * y + 4 * z);
        }
        return Integer.bitCount(corners) == 4;
    }

    private static int endpoint(float component, int min, int max) {
        float value = component * 16;
        if (Math.abs(value - min) < .00001f) return 0;
        return Math.abs(value - max) < .00001f ? 1 : -1;
    }

    private record Part(EnumFacing face, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean edge) {
    }

    record Connections(boolean north, boolean south, boolean west, boolean east) {
        boolean contains(EnumFacing side) {
            return switch (side) {
                case NORTH -> north;
                case SOUTH -> south;
                case WEST -> west;
                case EAST -> east;
                default -> throw new IllegalArgumentException("Pane connections are horizontal");
            };
        }

        boolean isolated() {
            return !north && !south && !west && !east;
        }
    }

    record Model(Connections connections, boolean thick, VertexFormat format, TextureAtlasSprite faceSprite,
            TextureAtlasSprite edgeSprite) {
        List<BakedQuad> probes() {
            var faces = new ArrayList<BakedQuad>(SIDES.length);
            for (EnumFacing face : SIDES) faces.add(vertical(face, 8, 0, 16, 0, 16, faceSprite));
            return faces;
        }

        List<BakedQuad> mesh(IBlockState state, IBlockAccess world, BlockPos pos) {
            var faces = new ArrayList<BakedQuad>();
            for (EnumFacing face : SIDES) addWall(faces, face);
            for (EnumFacing side : List.of(EnumFacing.UP, EnumFacing.DOWN)) {
                if (thick) addThickEdges(faces, state, world, pos, side);
                else addThinEdges(faces, state, world, pos, side);
            }
            return faces;
        }

        void addWall(List<BakedQuad> faces, EnumFacing face) {
            EnumFacing right = face.rotateYCCW();
            boolean isolated = connections.isolated();
            boolean leftArm = isolated || connections.contains(right.getOpposite());
            boolean rightArm = isolated || connections.contains(right);
            int halfWidth = thick ? 1 : 0;
            int lo = 8 - halfWidth;
            int hi = 8 + halfWidth;
            int plane = positive(face) ? hi : lo;
            if (leftArm && rightArm) {
                faces.add(vertical(face, plane, 0, 16, 0, 16, faceSprite));
                if (isolated && thick) faces.add(vertical(face, positive(face) ? 16 : 0, LOW, HIGH, LOW, HIGH, faceSprite));
            } else if (leftArm || rightArm) {
                boolean inner = connections.contains(face);
                int middle = inner == leftArm ? lo : hi;
                int uvMiddle = 16 - middle;
                faces.add(leftArm ? vertical(face, plane, 0, middle, uvMiddle, 16, faceSprite)
                        : vertical(face, plane, middle, 16, 0, uvMiddle, faceSprite));
            } else if (connections.contains(face.getOpposite()) && !connections.contains(face)) {
                faces.add(vertical(face, plane, LOW, HIGH, LOW, HIGH, thick ? faceSprite : edgeSprite));
                if (!thick) faces.add(vertical(face.getOpposite(), 8, LOW, HIGH, LOW, HIGH, edgeSprite));
            }
        }

        private BakedQuad vertical(EnumFacing face, int plane, int left, int right, int u0, int u1, TextureAtlasSprite sprite) {
            float bottom = thick ? .001f : 0;
            float top = 1 - bottom;
            return bake(face, sprite, sideVertex(face, plane, left, top, u0, 0), sideVertex(face, plane, left, bottom, u0, 16),
                    sideVertex(face, plane, right, bottom, u1, 16), sideVertex(face, plane, right, top, u1, 0));
        }

        private void addThickEdges(List<BakedQuad> faces, IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
            var neighbor = world.getBlockState(pos.offset(side));
            if (neighbor.getBlock() != state.getBlock() || separatesDyes(state, neighbor)) addThickCap(faces, side);
        }

        void addThickCap(List<BakedQuad> faces, EnumFacing side) {
            float y = side == EnumFacing.UP ? .999f : .001f;
            boolean continuousX = connections.west() && connections.east();
            boolean continuousZ = connections.north() && connections.south();
            addThickStrip(faces, side, y, true, connections.west(), connections.east(), false);
            addThickStrip(faces, side, y, false, connections.north(), connections.south(), continuousX);
            if (!continuousX && !continuousZ) faces.add(edge(side, false, y, LOW, HIGH, LOW, HIGH, HIGH, 16));
        }

        private void addThickStrip(List<BakedQuad> faces, EnumFacing side, float y, boolean xAxis, boolean minus,
                boolean plus, boolean skipCenter) {
            int u0 = xAxis && side == EnumFacing.DOWN ? HIGH : LOW;
            int u1 = 16 - u0;
            if (minus && plus) {
                if (skipCenter) {
                    faces.add(edge(side, xAxis, y, 0, LOW, u0, u1, 0, LOW * (LOW / 16f)));
                    faces.add(edge(side, xAxis, y, HIGH, 16, u0, u1, LOW * (HIGH / 16f), LOW));
                } else {
                    faces.add(edge(side, xAxis, y, 0, 16, u0, u1, 0, LOW));
                }
                return;
            }
            if (minus || connections.isolated()) faces.add(edge(side, xAxis, y, 0, LOW, u0, u1, 0, HIGH));
            if (plus || connections.isolated()) {
                int v0 = xAxis && side == EnumFacing.UP ? 0 : LOW;
                faces.add(edge(side, xAxis, y, HIGH, 16, u0, u1, v0, 16));
            }
        }

        private void addThinEdges(List<BakedQuad> faces, IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
            boolean visible = state.shouldSideBeRendered(world, pos, side);
            addThinStrip(faces, world, pos, side, true, visible, EnumFacing.WEST, EnumFacing.EAST);
            addThinStrip(faces, world, pos, side, false, visible, EnumFacing.NORTH, EnumFacing.SOUTH);
        }

        private void addThinStrip(List<BakedQuad> faces, IBlockAccess world, BlockPos pos, EnumFacing side, boolean xAxis,
                boolean visible, EnumFacing negative, EnumFacing positive) {
            boolean minus = connections.isolated() || connections.contains(negative);
            boolean plus = connections.isolated() || connections.contains(positive);
            float offset = xAxis ? .01f : .005f;
            float y = side == EnumFacing.UP ? 1 + offset : -offset;
            if (minus && plus && visible) {
                addThinEdge(faces, xAxis, y, 0, 16, LOW, HIGH, xAxis ? 16 : 0, xAxis ? 0 : 16);
                return;
            }
            BlockPos vertical = pos.offset(side);
            boolean allowOffset = side == EnumFacing.UP ? pos.getY() < 255 : pos.getY() > 1;
            if (minus && (visible || allowOffset && world.isAirBlock(vertical.offset(negative)))) {
                addThinEdge(faces, xAxis, y, 0, 8, xAxis ? LOW : HIGH, xAxis ? HIGH : LOW,
                        xAxis ? 8 : 0, xAxis ? 16 : 8);
            }
            if (plus && (visible || allowOffset && world.isAirBlock(vertical.offset(positive)))) {
                addThinEdge(faces, xAxis, y, 8, 16, LOW, HIGH, xAxis ? 0 : 8, xAxis ? 8 : 16);
            }
        }

        private void addThinEdge(List<BakedQuad> faces, boolean xAxis, float y, int lo, int hi, int u0, int u1, int v0, int v1) {
            faces.add(edge(EnumFacing.UP, xAxis, y, lo, hi, u0, u1, v0, v1));
            faces.add(edge(EnumFacing.DOWN, xAxis, y, lo, hi, u0, u1, v1, v0));
        }

        private BakedQuad edge(EnumFacing side, boolean xAxis, float y, int lo, int hi, int u0, int u1, float v0, float v1) {
            Vertex a = edgeVertex(xAxis, lo, y, LOW, u0, v0);
            Vertex b = edgeVertex(xAxis, hi, y, LOW, u0, v1);
            Vertex c = edgeVertex(xAxis, hi, y, HIGH, u1, v1);
            Vertex d = edgeVertex(xAxis, lo, y, HIGH, u1, v0);
            return xAxis == (side == EnumFacing.UP) ? bake(side, edgeSprite, a, d, c, b) : bake(side, edgeSprite, a, b, c, d);
        }

        private BakedQuad bake(EnumFacing face, TextureAtlasSprite sprite, Vertex... vertices) {
            int stride = format.getIntegerSize();
            int position = format.getOffset(0) / Integer.BYTES;
            int color = format.getColorOffset() / Integer.BYTES;
            int uv = format.getUvOffsetById(0) / Integer.BYTES;
            int[] data = new int[4 * stride];
            for (int i = 0; i < 4; i++) {
                Vertex vertex = vertices[i];
                int at = i * stride;
                data[at + position] = Float.floatToRawIntBits(vertex.x());
                data[at + position + 1] = Float.floatToRawIntBits(vertex.y());
                data[at + position + 2] = Float.floatToRawIntBits(vertex.z());
                data[at + color] = -1;
                data[at + uv] = Float.floatToRawIntBits(sprite.getInterpolatedU(vertex.u()));
                data[at + uv + 1] = Float.floatToRawIntBits(sprite.getInterpolatedV(vertex.v()));
                if (format.hasNormal()) {
                    data[at + format.getNormalOffset() / Integer.BYTES] = (face.getXOffset() * 127 & 255)
                            | (face.getYOffset() * 127 & 255) << 8 | (face.getZOffset() * 127 & 255) << 16;
                }
            }
            // The dye comes from the texture; both renderers apply the block color without directional shade.
            return new BakedQuad(data, 0, face, sprite, false, format);
        }
    }

    private static Vertex sideVertex(EnumFacing face, int plane, int along, float y, int u, int v) {
        float p = plane / 16f;
        float a = (face == EnumFacing.NORTH || face == EnumFacing.EAST ? 16 - along : along) / 16f;
        return face.getAxis() == EnumFacing.Axis.X ? new Vertex(p, y, a, u, v) : new Vertex(a, y, p, u, v);
    }

    private static Vertex edgeVertex(boolean xAxis, int along, float y, int across, int u, float v) {
        return xAxis ? new Vertex(along / 16f, y, across / 16f, u, v) : new Vertex(across / 16f, y, along / 16f, u, v);
    }

    private static boolean positive(EnumFacing face) {
        return face.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE;
    }

    private record Vertex(float x, float y, float z, int u, float v) {
    }
}
