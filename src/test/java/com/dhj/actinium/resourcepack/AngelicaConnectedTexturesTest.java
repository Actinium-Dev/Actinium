package com.dhj.actinium.resourcepack;

import com.dhj.actinium.resourcepack.ctm.api.client.CtmLoaderRegistry;
import com.dhj.actinium.resourcepack.ctm.client.CtmRuleTypes;
import com.dhj.actinium.resourcepack.ctm.client.properties.ConnectingCtmProperties;
import com.dhj.actinium.resourcepack.ctm.client.resource.ResourceRedirectHandler;
import net.minecraft.client.resources.FileResourcePack;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import net.minecraft.client.resources.data.MetadataSerializer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AngelicaConnectedTexturesTest {
    @Test
    void bundledRulesResolveTheirTexturesAndKeepDifferentGlassDyesSeparate(@TempDir Path directory) throws Exception {
        Bootstrap.register();
        CtmRuleTypes.registerLoaders();
        BuiltinResourcePacks.install(directory);

        try (var pack = new FileResourcePack(directory.resolve("Actinium-Connected-Textures.zip").toFile())) {
            var manager = new SimpleReloadableResourceManager(new MetadataSerializer());
            manager.reloadResourcePack(pack);
            var index = new ResourcePackIndex(manager, List.of(pack), List.of());
            var rules = index.find(id -> id.getPath().startsWith("mcpatcher/ctm/") && id.getPath().endsWith(".properties"));
            assertEquals(2 * (EnumDyeColor.values().length + 1) + 2, rules.size());

            var red = Blocks.STAINED_GLASS.getStateFromMeta(EnumDyeColor.RED.getMetadata());
            var blue = Blocks.STAINED_GLASS.getStateFromMeta(EnumDyeColor.BLUE.getMetadata());
            for (var id : rules) {
                var values = index.readProperties(id);
                var method = values.getProperty("method");
                var loader = CtmLoaderRegistry.get().getLoader(method);
                assertNotNull(loader, id::toString);
                var rule = (ConnectingCtmProperties) loader.getPropertiesFactory()
                        .createProperties(values, id, pack.getPackName(), index.priority(id), manager, method);
                assertNotNull(rule, id::toString);
                for (var sprite : rule.getSpriteDependencies()) {
                    var png = ResourceRedirectHandler.redirect(new ResourceLocation(sprite.getNamespace(),
                            "textures/" + sprite.getPath() + ".png"));
                    try (var image = index.open(png)) {
                        assertNotNull(ImageIO.read(image), png::toString);
                    }
                }
                if (id.getPath().endsWith("/red_stained_glass.properties")) {
                    var predicate = rule.getConnectionPredicate();
                    assertTrue(predicate.shouldConnect(null, BlockPos.ORIGIN, red, red, BlockPos.ORIGIN.east(),
                            red, red, null, null));
                    assertFalse(predicate.shouldConnect(null, BlockPos.ORIGIN, red, red, BlockPos.ORIGIN.east(),
                            blue, blue, null, null));
                }
                if (method.equals("top")) {
                    assertTrue(rule.getMatchBlocksPredicate().test(Blocks.SANDSTONE.getDefaultState()));
                    assertFalse(rule.getMatchBlocksPredicate().test(Blocks.STONE.getDefaultState()));
                    assertEquals(List.of(new ResourceLocation("blocks/sandstone_normal")), List.copyOf(rule.getMatchTilesSet()));
                }
            }
        }
    }
}
