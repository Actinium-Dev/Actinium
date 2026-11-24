package com.dhj.actinium.render.terrain;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.init.Bootstrap;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TileEntityDepthOrderTest {
    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    void sortsAcrossSourceListsAndPreservesEqualDistances() {
        TileEntity near = tile(0, 1, 0);
        TileEntity far = tile(0, 0, 181);
        TileEntity left = tile(-5, 0, 0);
        TileEntity right = tile(5, 0, 0);
        var entities = new ObjectArrayList<>(List.of(near, left, far, right));

        new TileEntityDepthOrder().sortBackToFront(entities, 0.5, 0.5, 0.5);

        assertEquals(List.of(far, left, right, near), entities);
    }

    @Test
    void resortsFromTheCurrentViewAfterAListIsRepopulated() {
        TileEntity north = tile(0, 0, -24);
        TileEntity south = tile(0, 0, 24);
        TileEntity middle = tile(0, 16, 0);
        var entities = new ObjectArrayList<>(List.of(north, south, middle));
        var order = new TileEntityDepthOrder();

        order.sortBackToFront(entities, 0.5, 0.5, -24.5);
        assertEquals(List.of(south, middle, north), entities);

        entities.clear();
        entities.add(south);
        entities.add(north);
        order.sortBackToFront(entities, 0.5, 0.5, 24.5);
        assertEquals(List.of(north, south), entities);
    }

    private static TileEntity tile(int x, int y, int z) {
        TileEntity tile = new TestTileEntity();
        tile.setPos(new BlockPos(x, y, z));
        return tile;
    }

    private static final class TestTileEntity extends TileEntity {}
}
