package com.dhj.actinium.debug;

import dhj.embeddedt.embeddium.impl.render.chunk.RenderSection;
import dhj.embeddedt.embeddium.impl.render.chunk.region.TestRegions;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerrainRenderInventoryTest {
    @BeforeAll
    static void bootstrapRegistries() {
        Bootstrap.register();
    }

    @Test
    void retainsReplacedBlocksAndCountsEachIdAtEachPositionOnce() {
        var inventory = new TerrainRenderInventory();
        var section = new RenderSection(TestRegions.create(0), 0, 0, 0);
        var original = new TerrainRenderInventory.SectionData();
        original.record(Blocks.STONE, BlockPos.ORIGIN);
        original.finish(true);
        inventory.recordInstalled(section, original);

        var edited = new TerrainRenderInventory.SectionData();
        edited.record(Blocks.DIRT, BlockPos.ORIGIN);
        edited.record(Blocks.STONE, BlockPos.ORIGIN.east());
        edited.finish(true);
        inventory.recordInstalled(section, edited);
        inventory.recordInstalled(section, edited);

        assertEquals(List.of("TERRAIN_MESH\t1\tminecraft:dirt", "TERRAIN_MESH\t2\tminecraft:stone"),
                inventory.export().lines().skip(1).toList());
    }
}
