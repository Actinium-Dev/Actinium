package com.dhj.actinium.resourcepack.item;

import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.vecmath.Matrix4f;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.model.SimpleBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.RegistrySimple;
import net.minecraftforge.client.model.BakedModelWrapper;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

class CitModelStateTest {
    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    void selectsByActualLookupEvenWhenTwoLocationsReturnTheSameModel() {
        ItemStack bow = new ItemStack(Items.BOW);
        IBakedModel base = model();
        IBakedModel shared = model();
        IBakedModel first = model();
        IBakedModel second = model();
        ResourceLocation file1 = new ResourceLocation("optifine/cit/first.json");
        ResourceLocation file2 = new ResourceLocation("optifine/cit/second.json");
        CitRule rule = rule(Map.of("bow_pulling_0", file1, "minecraft:item/bow_pulling_1", file2));
        Map<ResourceLocation, IBakedModel> baked = Map.of(file1, first, file2, second);

        assertSame(first, CitModelState.select(rule, resolve(bow, base, shared, "item/bow_pulling_0"), baked).model());
        assertSame(second, CitModelState.select(rule, resolve(bow, base, shared, "item/bow_pulling_1"), baked).model());
        assertSame(shared, CitModelState.select(rule, shared, baked).model());
    }

    @Test
    void usesVerifiedBaseLocationAndFallsBackToGenericModelForUnlistedState() {
        ItemStack bow = new ItemStack(Items.BOW);
        IBakedModel base = model();
        IBakedModel standby = model();
        IBakedModel generic = model();
        ResourceLocation standbyFile = new ResourceLocation("optifine/cit/standby.json");
        ResourceLocation genericFile = new ResourceLocation("optifine/cit/generic.json");
        CitRule rule = rule(Map.of("bow_standby", standbyFile, "", genericFile));
        Map<ResourceLocation, IBakedModel> baked = Map.of(standbyFile, standby, genericFile, generic);
        var registry = new RegistrySimple<ModelResourceLocation, IBakedModel>();
        registry.putObject(new ModelResourceLocation("bow", "inventory"), base);
        IBakedModel located = CitModelState.resolve(ItemOverrideList.NONE, base, bow,
            CitModelState.baseLocations(registry, null), () -> base);

        assertSame(standby, CitModelState.select(rule, located, baked).model());
        assertSame(generic, CitModelState.select(rule, resolve(bow, base, model(), "item/bow_pulling_2"), baked).model());
        assertSame(base, CitModelState.select(null, located, baked).model());

        registry.putObject(new ModelResourceLocation("other_bow", "inventory"), base);
        IBakedModel ambiguous = CitModelState.resolve(ItemOverrideList.NONE, base, bow,
            CitModelState.baseLocations(registry, null), () -> base);
        assertSame(generic, CitModelState.select(rule, ambiguous, baked).model());
    }

    @Test
    void appliesMixedRuleTextureOnlyWhenNoFullModelWasSelected() {
        ItemStack bow = new ItemStack(Items.BOW);
        ResourceLocation modelFile = new ResourceLocation("optifine/cit/pulling.json");
        ResourceLocation textureFile = new ResourceLocation("optifine/cit/bow.png");
        CitRule rule = new CitRule(modelFile, 0, 0, CitRule.Type.ITEM, Set.of(Items.BOW), null,
            Map.of("", textureFile), Map.of("bow_pulling_0", modelFile), null, null);
        TextureAtlasSprite original = sprite("minecraft:items/bow", 0);
        TextureAtlasSprite texture = sprite(textureFile.toString(), 16);
        var quad = new BakedQuad(new int[4 * DefaultVertexFormats.ITEM.getIntegerSize()], -1, EnumFacing.NORTH,
            original, true, DefaultVertexFormats.ITEM);
        IBakedModel base = new SimpleBakedModel(List.of(quad), Map.of(), false, false, original, null, ItemOverrideList.NONE);
        IBakedModel perspectiveModel = model();
        IBakedModel full = new BakedModelWrapper<IBakedModel>(model()) {
            @Override
            public Pair<IBakedModel, Matrix4f> handlePerspective(TransformType type) {
                return Pair.of(perspectiveModel, null);
            }
        };
        var sprites = Map.of(textureFile, texture);
        IBakedModel pulling = resolve(bow, base, base, "item/bow_pulling_0");
        var baked = Map.of(modelFile, full);

        IBakedModel selected = ItemPackRendering.selectModel(rule, pulling, baked);
        assertSame(full, ItemPackRendering.drawModel(rule, selected, sprites));
        assertSame(perspectiveModel, ItemPackRendering.drawModel(rule, selected.handlePerspective(TransformType.GUI).getLeft(), sprites));
        for (IBakedModel unmatched : List.of(ItemPackRendering.selectModel(rule, base, baked),
            ItemPackRendering.selectModel(rule, pulling, Map.of()))) {
            assertSame(texture, ItemPackRendering.drawModel(rule, unmatched, sprites).getQuads(null, null, 0).getFirst().getSprite());
        }
    }

    private static TextureAtlasSprite sprite(String name, int x) {
        TextureAtlasSprite sprite = new TextureAtlasSprite(name) {};
        sprite.setIconWidth(16);
        sprite.setIconHeight(16);
        sprite.initSprite(32, 32, x, 0, false);
        return sprite;
    }

    private static IBakedModel resolve(ItemStack stack, IBakedModel base, IBakedModel selected, String model) {
        return CitModelState.resolve(ItemOverrideList.NONE, base, stack, Map.of(), () -> {
            CitModelState.record(ItemOverrideList.NONE, stack, new ModelResourceLocation(model, "inventory"), selected);
            return selected;
        });
    }

    private static CitRule rule(Map<String, ResourceLocation> models) {
        return new CitRule(new ResourceLocation("optifine/cit/bow.properties"), 0, 0, CitRule.Type.ITEM,
            Set.of(Items.BOW), null, Map.of(), models, null, null);
    }

    private static IBakedModel model() {
        return new SimpleBakedModel(List.of(), Map.of(), false, false, null, null, ItemOverrideList.NONE);
    }
}
