package com.dhj.actinium.render.dynamiclights;

import com.dhj.actinium.api.dynamiclights.DynamicLightHandler;
import com.dhj.actinium.api.dynamiclights.DynamicLightHandlers;
import com.dhj.actinium.api.dynamiclights.ItemLightSource.StaticItemLightSource;
import com.dhj.actinium.api.dynamiclights.ItemLightSources;
import com.dhj.actinium.render.dynamiclights.DynamicLightsSettings.ExplosiveMode;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.FolderResourcePack;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import net.minecraft.client.resources.data.MetadataSerializer;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.projectile.EntitySpectralArrow;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.SaveHandlerMP;
import net.minecraft.world.storage.WorldInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicLightRulesTest {
    private static final String ITEM_ASSETS = "assets/celeritasdynamiclights/dynamiclights/item";

    @TempDir
    Path tempDir;

    @BeforeAll
    static void registerVanilla() {
        Bootstrap.register();
    }

    @Test
    void resourcePackReplacesAndRestoresDefinitionWhileStaticRuleRemains() throws Exception {
        var resources = new SimpleReloadableResourceManager(new MetadataSerializer());
        var defaults = builtInPack();
        var override = new FolderResourcePack(tempDir.toFile());
        Path directory = Files.createDirectories(tempDir.resolve(ITEM_ASSETS));
        Files.writeString(directory.resolve("fire_charge.json"), """
                {"item":"minecraft:fire_charge","luminance":4}
                """);
        Files.writeString(directory.resolve("blaze_powder.json"), """
                {"item":"minecraft:sugar","luminance":2}
                """);

        ItemStack stack = new ItemStack(Items.FIRE_CHARGE);
        ItemStack sugar = new ItemStack(Items.SUGAR);
        resources.reloadResources(List.of(defaults));
        DynamicLightRules.reload(resources);
        int defaultLight = ItemLightSources.getLuminance(stack, () -> false);
        assertTrue(defaultLight > 0);
        assertEquals(0, ItemLightSources.getLuminance(stack, () -> true));

        resources.reloadResources(List.of(defaults, override));
        DynamicLightRules.reload(resources);
        assertEquals(4, ItemLightSources.getLuminance(stack, () -> true));
        assertEquals(2, ItemLightSources.getLuminance(sugar, () -> false));
        ItemLightSources.registerItemLightSource(new StaticItemLightSource(
                new ResourceLocation("actinium", "test_static_item"), Items.SUGAR, 12, true));
        assertEquals(12, ItemLightSources.getLuminance(sugar, () -> false));
        assertEquals(0, ItemLightSources.getLuminance(sugar, () -> true));

        resources.reloadResources(List.of(defaults));
        DynamicLightRules.reload(resources);
        assertEquals(defaultLight, ItemLightSources.getLuminance(stack, () -> false));
        assertEquals(12, ItemLightSources.getLuminance(sugar, () -> false));
    }

    @Test
    void heldPistonsHaveNoDynamicLight() {
        assertAll(
                () -> assertEquals(0, ItemLightSources.getLuminance(new ItemStack(Blocks.PISTON), () -> false)),
                () -> assertEquals(0, ItemLightSources.getLuminance(new ItemStack(Blocks.STICKY_PISTON), () -> false)));
    }

    @Test
    void submersionExtinguishesTheTorchButNotGlowstoneDust() {
        var resources = new SimpleReloadableResourceManager(new MetadataSerializer());
        resources.reloadResources(List.of(builtInPack()));
        DynamicLightRules.reload(resources);

        var torch = new ItemStack(Blocks.TORCH);
        assertEquals(Blocks.TORCH.getDefaultState().getLightValue(), ItemLightSources.getLuminance(torch, () -> false));
        assertEquals(0, ItemLightSources.getLuminance(torch, () -> true));

        var dust = new ItemStack(Items.GLOWSTONE_DUST);
        int dry = ItemLightSources.getLuminance(dust, () -> false);
        assertTrue(dry > 0);
        assertEquals(dry, ItemLightSources.getLuminance(dust, () -> true));
    }

    @Test
    void blockStateTagOverridesTheDefaultLight() {
        var block = new VariableLightBlock();
        var stack = new ItemStack(new ItemBlock(block), 1, 2);
        int defaultLight = block.getDefaultState().getLightValue();
        assertEquals(defaultLight, ItemLightSources.getLuminance(stack, () -> false));

        var properties = new NBTTagCompound();
        properties.setString("light", "7");
        properties.setString("unknown", "4");
        var tag = new NBTTagCompound();
        tag.setTag("BlockStateTag", properties);
        stack.setTagCompound(tag);
        assertEquals(7, ItemLightSources.getLuminance(stack, () -> false));

        properties.setString("light", "invalid");
        assertEquals(defaultLight, ItemLightSources.getLuminance(stack, () -> false));
    }

    @Test
    void submergedHandlersDoNotSuppressDryContributionsFromSameOrParentClass() {
        DynamicLightHandlers.registerTileEntityDynamicLightHandler(ParentLightTile.class, tile -> 5);
        DynamicLightHandlers.registerTileEntityDynamicLightHandler(LightTile.class, tile -> 7);
        DynamicLightHandlers.registerTileEntityDynamicLightHandler(LightTile.class,
                DynamicLightHandler.makeHandler(tile -> 12, tile -> true));

        var tile = new LightTile();
        assertTrue(DynamicLightRules.hasTileHandlers());
        assertEquals(12, DynamicLightHandlers.getLuminanceFrom(tile, () -> false));
        assertEquals(7, DynamicLightHandlers.getLuminanceFrom(tile, () -> true));
        assertEquals(5, DynamicLightHandlers.getLuminanceFrom(new InheritedLightTile(), () -> true));

        var settings = settings();
        settings.blockEntitiesLightSource = false;
        assertEquals(0, DynamicLightRules.tileEntity(tile, settings));
        settings.blockEntitiesLightSource = true;
        assertEquals(12, DynamicLightRules.tileEntity(tile, settings));
        tile.invalidate();
        assertEquals(0, DynamicLightRules.tileEntity(tile, settings));
    }

    @Test
    void fancyTntGetsBrighterAsFuseRunsOutAndCanBeDisabled() {
        var settings = settings();
        var tnt = new EntityTNTPrimed(new FixtureWorld(Blocks.AIR));
        settings.tntLightingMode = ExplosiveMode.FANCY;
        tnt.setFuse(70);
        int early = DynamicLightRules.entity(tnt, null, settings);
        tnt.setFuse(20);
        int late = DynamicLightRules.entity(tnt, null, settings);
        assertTrue(early > 0 && late > early);

        settings.tntLightingMode = ExplosiveMode.SIMPLE;
        int simple = DynamicLightRules.entity(tnt, null, settings);
        tnt.setFuse(70);
        assertEquals(simple, DynamicLightRules.entity(tnt, null, settings));
        settings.tntLightingMode = ExplosiveMode.OFF;
        assertEquals(0, DynamicLightRules.entity(tnt, null, settings));
    }

    @Test
    void registeredEntityContributionCanRaiseIntrinsicLightAndObeysEntitySetting() {
        var settings = settings();
        var arrow = new LightArrow(new FixtureWorld(Blocks.AIR));
        int intrinsic = DynamicLightRules.entity(arrow, null, settings);
        assertTrue(intrinsic > 0);

        DynamicLightHandlers.registerEntityDynamicLightHandler(LightArrow.class, entity -> intrinsic + 1);
        assertEquals(intrinsic + 1, DynamicLightRules.entity(arrow, null, settings));
        settings.entitiesLightSource = false;
        assertEquals(0, DynamicLightRules.entity(arrow, null, settings));
    }

    private DynamicLightsSettings settings() {
        return DynamicLightsSettings.load(tempDir.resolve("dynamiclights.cfg").toFile());
    }

    private static FolderResourcePack builtInPack() {
        return new FolderResourcePack(Path.of(System.getProperty("actinium.projectRoot", "."), "src/main/resources").toFile());
    }

    private static class ParentLightTile extends TileEntity {
    }

    private static final class LightTile extends ParentLightTile {
    }

    private static final class InheritedLightTile extends ParentLightTile {
    }

    private static final class LightArrow extends EntitySpectralArrow {
        private LightArrow(World world) {
            super(world);
        }
    }

    private static final class FixtureWorld extends World {
        private final IBlockState state;

        private FixtureWorld(Block block) {
            super(new SaveHandlerMP(), new WorldInfo(
                    new WorldSettings(0L, GameType.SURVIVAL, false, false, WorldType.DEFAULT), "DynamicLightRulesTest"),
                    new WorldProviderSurface(), new Profiler(), true);
            state = block.getDefaultState();
        }

        @Override
        public IBlockState getBlockState(BlockPos pos) {
            return state;
        }

        @Override
        protected IChunkProvider createChunkProvider() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) {
            return false;
        }
    }

    private static final class VariableLightBlock extends Block {
        private static final PropertyInteger LIGHT = PropertyInteger.create("light", 0, 15);

        private VariableLightBlock() {
            super(Material.ROCK);
            setDefaultState(getDefaultState().withProperty(LIGHT, 3));
        }

        @Override
        protected BlockStateContainer createBlockState() {
            return new BlockStateContainer(this, LIGHT);
        }

        @Override
        public IBlockState getStateFromMeta(int meta) {
            return getDefaultState().withProperty(LIGHT, meta);
        }

        @Override
        public int getLightValue(IBlockState state) {
            return state.getValue(LIGHT);
        }
    }
}
