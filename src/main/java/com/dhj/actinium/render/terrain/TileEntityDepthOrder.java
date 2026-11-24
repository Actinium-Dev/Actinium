package com.dhj.actinium.render.terrain;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.tileentity.TileEntity;

import java.util.Arrays;

/** Orders block entities by distance from the dispatcher origin, preserving traversal order at equal distances. */
final class TileEntityDepthOrder {
    private static final int RADIX_BITS = 8;
    private static final int BUCKET_COUNT = 1 << RADIX_BITS;
    private static final int BUCKET_MASK = BUCKET_COUNT - 1;

    private final int[] counts = new int[BUCKET_COUNT];
    private int[] keys = new int[0];
    private int[] scratchKeys = new int[0];
    private Object[] scratch = new Object[0];

    void sortBackToFront(ObjectArrayList<TileEntity> entities, double originX, double originY, double originZ) {
        int size = entities.size();
        if (size < 2) {
            return;
        }

        Object[] source = entities.elements();
        if (keys.length < size) {
            keys = new int[source.length];
            scratchKeys = new int[source.length];
            scratch = new Object[source.length];
        }

        for (int i = 0; i < size; i++) {
            var pos = entities.get(i).getPos();
            double x = pos.getX() + 0.5 - originX;
            double y = pos.getY() + 0.5 - originY;
            double z = pos.getZ() + 0.5 - originZ;
            // Positive float bits increase with distance; inverting them places the farthest first.
            keys[i] = Integer.MAX_VALUE - Float.floatToRawIntBits((float) (x * x + y * y + z * z));
        }

        Object[] input = source;
        Object[] output = scratch;
        int[] inputKeys = keys;
        int[] outputKeys = scratchKeys;
        for (int shift = 0; shift < Integer.SIZE; shift += RADIX_BITS) {
            Arrays.fill(counts, 0);
            for (int i = 0; i < size; i++) {
                counts[(inputKeys[i] >>> shift) & BUCKET_MASK]++;
            }
            if (counts[(inputKeys[0] >>> shift) & BUCKET_MASK] == size) {
                continue;
            }

            int offset = 0;
            for (int bucket = 0; bucket < BUCKET_COUNT; bucket++) {
                int count = counts[bucket];
                counts[bucket] = offset;
                offset += count;
            }
            for (int i = 0; i < size; i++) {
                int key = inputKeys[i];
                int dest = counts[(key >>> shift) & BUCKET_MASK]++;
                outputKeys[dest] = key;
                output[dest] = input[i];
            }

            Object[] nextOutput = input;
            input = output;
            output = nextOutput;
            int[] nextOutputKeys = inputKeys;
            inputKeys = outputKeys;
            outputKeys = nextOutputKeys;
        }

        if (input != source) {
            System.arraycopy(input, 0, source, 0, size);
        }
        Arrays.fill(scratch, 0, size, null);
    }
}
