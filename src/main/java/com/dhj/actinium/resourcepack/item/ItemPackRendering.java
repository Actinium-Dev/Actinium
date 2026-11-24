package com.dhj.actinium.resourcepack.item;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.items.DroppedItemInstancer;
import com.dhj.actinium.render.ArmorGlintBatching;
import com.dhj.actinium.render.GuiGlStateBoundary;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.vecmath.Matrix4f;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.coderbot.iris.gbuffer_overrides.matching.SpecialCondition;
import net.coderbot.iris.layer.GbufferPrograms;
import org.apache.commons.lang3.tuple.Pair;
import org.lwjgl.opengl.GL11;

/** Applies pack effects at the final item draw, after model overrides and perspective. */
public final class ItemPackRendering {
    private static final ThreadLocal<CitConditions.Hand> HAND = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> PERSPECTIVE = new ThreadLocal<>();

    private ItemPackRendering() {}

    public static void withPerspective(TransformType transform, EnumHandSide primaryHand, Runnable render) {
        CitConditions.Hand previous = HAND.get();
        Boolean previousPerspective = PERSPECTIVE.get();
        CitConditions.Hand current = switch (transform) {
            case FIRST_PERSON_LEFT_HAND, THIRD_PERSON_LEFT_HAND -> primaryHand == EnumHandSide.LEFT ? CitConditions.Hand.MAIN : CitConditions.Hand.OFF;
            case FIRST_PERSON_RIGHT_HAND, THIRD_PERSON_RIGHT_HAND -> primaryHand == EnumHandSide.RIGHT ? CitConditions.Hand.MAIN : CitConditions.Hand.OFF;
            default -> CitConditions.Hand.ANY;
        };
        HAND.set(current);
        PERSPECTIVE.set(true);
        try {
            render.run();
        } finally {
            if (previous == null) HAND.remove();
            else HAND.set(previous);
            if (previousPerspective == null) PERSPECTIVE.remove();
            else PERSPECTIVE.set(previousPerspective);
        }
    }

    private static CitConditions.Hand hand() {
        CitConditions.Hand current = HAND.get();
        return current == null ? CitConditions.Hand.ANY : current;
    }

    public static IBakedModel selectModel(ItemStack stack, IBakedModel model) {
        CitSnapshot snapshot = ResourcePackItemFeatures.cit();
        CitRule rule = snapshot == null ? null : snapshot.first(CitRule.Type.ITEM, stack, hand());
        return selectModel(rule, model, snapshot == null ? Map.of() : snapshot.models());
    }

    static IBakedModel selectModel(CitRule rule, IBakedModel model, Map<ResourceLocation, IBakedModel> baked) {
        CitModelState.Selection selection = CitModelState.select(rule, model, baked);
        return selection.replaced() && !rule.textures().isEmpty()
            ? new FullModelDraw(selection.model()) : selection.model();
    }

    public static IBakedModel selectModelForDraw(ItemStack stack, IBakedModel model) {
        return PERSPECTIVE.get() == null ? selectModel(stack, model) : model;
    }

    private static ResourceLocation asset(Map<String, ResourceLocation> replacements, TextureAtlasSprite sprite, String fallback) {
        if (sprite == null) return replacements.get(fallback);
        ResourceLocation name = new ResourceLocation(sprite.getIconName());
        String path = name.getPath();
        ResourceLocation result = replacements.get(name.toString());
        if (result == null) result = replacements.get(path);
        if (result == null) result = replacements.get(path.substring(path.lastIndexOf('/') + 1));
        return result == null ? replacements.get(fallback) : result;
    }

    public static boolean requiresImmediate(ItemStack stack, IBakedModel model) {
        CitSnapshot cit = ResourcePackItemFeatures.cit();
        if (cit != null && (cit.first(CitRule.Type.ITEM, stack, hand()) != null || !cit.glints(stack, hand()).isEmpty()
            || stack.hasEffect() && !cit.vanillaGlint())) return true;
        AtlasEmissive atlas = ResourcePackItemFeatures.atlasEmissive();
        if (atlas == null || atlas.isEmpty() || model.isBuiltInRenderer()) return false;
        for (EnumFacing face : EnumFacing.VALUES) if (hasEmissive(atlas, model, face)) return true;
        return hasEmissive(atlas, model, null);
    }

    private static boolean hasEmissive(AtlasEmissive atlas, IBakedModel model, EnumFacing face) {
        for (BakedQuad quad : model.getQuads(null, face, 0)) if (atlas.sprite(quad.getSprite()) != null) return true;
        return false;
    }

    public static IBakedModel drawModel(ItemStack stack, IBakedModel model) {
        CitSnapshot snapshot = ResourcePackItemFeatures.cit();
        CitRule rule = snapshot == null ? null : snapshot.first(CitRule.Type.ITEM, stack, hand());
        return drawModel(rule, model, snapshot == null ? Map.of() : snapshot.sprites());
    }

    static IBakedModel drawModel(CitRule rule, IBakedModel model, Map<ResourceLocation, TextureAtlasSprite> sprites) {
        if (model instanceof FullModelDraw full) return full.model();
        if (rule == null || rule.textures().isEmpty()) return model;
        return new TexturedDraw(model, rule.textures(), sprites);
    }

    private static final class FullModelDraw extends BakedModelWrapper<IBakedModel> {
        FullModelDraw(IBakedModel model) {
            super(model);
        }

        IBakedModel model() {
            return originalModel;
        }

        @Override
        public Pair<? extends IBakedModel, Matrix4f> handlePerspective(TransformType transform) {
            var perspective = originalModel.handlePerspective(transform);
            IBakedModel model = perspective.getLeft();
            return Pair.of(model == originalModel ? this : new FullModelDraw(model), perspective.getRight());
        }
    }

    private record TexturedDraw(IBakedModel model, Map<String, ResourceLocation> textures,
                                Map<ResourceLocation, TextureAtlasSprite> sprites) implements IBakedModel {
        @Override public List<BakedQuad> getQuads(IBlockState state, EnumFacing face, long seed) {
            return model.getQuads(state, face, seed).stream().map(quad -> {
                ResourceLocation file = asset(textures, quad.getSprite(), "");
                TextureAtlasSprite replacement = file == null ? null : sprites.get(file);
                return replacement == null ? quad : AtlasEmissive.copyQuad(quad, replacement, false);
            }).toList();
        }

        @Override public boolean isAmbientOcclusion() { return model.isAmbientOcclusion(); }
        @Override public boolean isGui3d() { return model.isGui3d(); }
        @Override public boolean isBuiltInRenderer() { return model.isBuiltInRenderer(); }
        @Override public TextureAtlasSprite getParticleTexture() { return model.getParticleTexture(); }
        @Override public ItemCameraTransforms getItemCameraTransforms() { return model.getItemCameraTransforms(); }
        @Override public ItemOverrideList getOverrides() { return model.getOverrides(); }
    }

    public static void drawEmissive(RenderItem renderer, ItemStack stack, IBakedModel model) {
        AtlasEmissive atlas = ResourcePackItemFeatures.atlasEmissive();
        if (atlas == null || atlas.isEmpty()) return;
        List<BakedQuad> quads = new ArrayList<>();
        for (EnumFacing face : EnumFacing.VALUES) appendEmissive(quads, atlas, model, face);
        appendEmissive(quads, atlas, model, null);
        if (quads.isEmpty()) return;
        float lightX = OpenGlHelper.lastBrightnessX;
        float lightY = OpenGlHelper.lastBrightnessY;
        GLStateManager.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        try {
            GLStateManager.glDisable(GL11.GL_LIGHTING);
            GLStateManager.glDepthMask(false);
            GLStateManager.glDepthFunc(GL11.GL_EQUAL);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.ITEM);
            renderer.renderQuads(buffer, quads, -1, stack);
            tessellator.draw();
        } finally {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
            GLStateManager.glPopAttrib();
        }
    }

    private static void appendEmissive(List<BakedQuad> result, AtlasEmissive atlas, IBakedModel model, EnumFacing face) {
        for (BakedQuad quad : model.getQuads(null, face, 0)) {
            BakedQuad copy = atlas.copy(quad);
            if (copy != null) result.add(copy);
        }
    }

    public static boolean drawGlint(ItemStack stack, Runnable render) {
        if (!DroppedItemInstancer.shouldRenderEffect()) return true;
        CitSnapshot snapshot = ResourcePackItemFeatures.cit();
        if (snapshot == null) return false;
        List<CitGlint> effects = snapshot.glints(stack, hand());
        drawGlintLayers(effects, snapshot.glintSettings(), render, true);
        return !effects.isEmpty() || !snapshot.vanillaGlint();
    }

    public static boolean drawEquipmentGlint(ItemStack stack, Runnable render) {
        if (ArmorGlintBatching.isWorldArmor() && !ArmorGlintBatching.isWorldGlintEnabled()) return true;
        CitSnapshot snapshot = ResourcePackItemFeatures.cit();
        if (snapshot == null) return false;
        List<CitGlint> effects = snapshot.glints(stack, CitConditions.Hand.ANY);
        drawGlintLayers(effects, snapshot.glintSettings(), render, !GuiGlStateBoundary.isEntityGuiSurface());
        return !effects.isEmpty() || !snapshot.vanillaGlint();
    }

    private static void drawGlintLayers(List<CitGlint> effects, CitGlint.Settings settings, Runnable render, boolean irisScope) {
        if (effects.isEmpty()) return;
        if (irisScope) GbufferPrograms.setupSpecialRenderCondition(SpecialCondition.GLINT);
        try {
            CitGlint.draw(effects, settings, Minecraft.getMinecraft().getTextureManager()::bindTexture, render);
        } finally {
            if (irisScope) GbufferPrograms.teardownSpecialRenderCondition();
        }
    }

    public static boolean overridesEquipmentGlint(ItemStack stack) {
        CitSnapshot snapshot = ResourcePackItemFeatures.cit();
        return snapshot != null && (!snapshot.vanillaGlint() || !snapshot.glints(stack, CitConditions.Hand.ANY).isEmpty());
    }

    public static boolean shouldDrawGlint(ItemStack stack, boolean vanilla) {
        return shouldDrawGlint(stack, vanilla, hand());
    }

    public static boolean shouldDrawEquipmentGlint(ItemStack stack, boolean vanilla) {
        return shouldDrawGlint(stack, vanilla, CitConditions.Hand.ANY);
    }

    private static boolean shouldDrawGlint(ItemStack stack, boolean vanilla, CitConditions.Hand hand) {
        CitSnapshot snapshot = ResourcePackItemFeatures.cit();
        return snapshot == null ? vanilla : vanilla && snapshot.vanillaGlint() || !snapshot.glints(stack, hand).isEmpty();
    }

    public static ResourceLocation armor(ItemStack stack, ResourceLocation texture) {
        return equipment(CitRule.Type.ARMOR, stack, texture);
    }

    public static ResourceLocation elytra(ItemStack stack, ResourceLocation texture) {
        return equipment(CitRule.Type.ELYTRA, stack, texture);
    }

    private static ResourceLocation equipment(CitRule.Type type, ItemStack stack, ResourceLocation texture) {
        CitSnapshot snapshot = ResourcePackItemFeatures.cit();
        CitRule rule = snapshot == null ? null : snapshot.first(type, stack, CitConditions.Hand.ANY);
        if (rule == null) return texture;
        String path = texture.getPath();
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (name.endsWith(".png")) name = name.substring(0, name.length() - 4);
        return rule.textures().getOrDefault(texture.getNamespace() + ":" + name,
            rule.textures().getOrDefault(name, rule.textures().getOrDefault("", texture)));
    }
}
