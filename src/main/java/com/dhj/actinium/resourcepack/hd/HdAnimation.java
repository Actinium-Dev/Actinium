package com.dhj.actinium.resourcepack.hd;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

record HdAnimation(int x, int y, int width, int height, int[][] tiles, List<Frame> frames, boolean interpolate, int skip) {
    record Frame(int tile, int duration) {}

    static HdAnimation parse(Properties p, BufferedImage strip, BufferedImage target) {
        int x = integer(p, "x", -1), y = integer(p, "y", -1);
        int w = integer(p, "w", 0), h = integer(p, "h", 0);
        if (x < 0 || y < 0 || w <= 0 || h <= 0 || x > target.getWidth() - w || y > target.getHeight() - h
            || w != strip.getWidth() || strip.getHeight() % h != 0) {
            throw new IllegalArgumentException("Invalid animation strip or target rectangle");
        }
        int count = strip.getHeight() / h;
        int[][] tiles = new int[count][];
        for (int i = 0; i < count; i++) tiles[i] = strip.getRGB(0, h * i, w, h, null, 0, w);
        int duration = integer(p, "duration", 1);
        int skip = integer(p, "skip", 0);
        if (duration <= 0 || skip < 0) throw new IllegalArgumentException("Invalid animation timing");
        List<Frame> frames = new ArrayList<>();
        boolean explicit = p.containsKey("tile.0");
        for (int i = 0; explicit ? p.containsKey("tile." + i) : i < count; i++) {
            int tile = integer(p, "tile." + i, i);
            int ticks = integer(p, "duration." + i, duration);
            if (tile < 0 || tile >= count || ticks <= 0) throw new IllegalArgumentException("Invalid animation frame " + i);
            frames.add(new Frame(tile, ticks));
        }
        return new HdAnimation(x, y, w, h, tiles, List.copyOf(frames), Boolean.parseBoolean(p.getProperty("interpolate")), skip);
    }

    private static int integer(Properties p, String key, int defaultValue) {
        return Integer.parseInt(p.getProperty(key, Integer.toString(defaultValue)).trim());
    }

    boolean overlaps(HdAnimation other) {
        return x < other.x + other.width && other.x < x + width && y < other.y + other.height && other.y < y + height;
    }

    Cursor cursor() { return new Cursor(); }

    final class Cursor {
        private int index;
        private int elapsed;
        private final int[] mixed = interpolate ? new int[width * height] : null;

        void reset() { index = elapsed = 0; }

        int[] tick() {
            Frame frame = frames.get(index);
            int[] current = tiles[frame.tile];
            int[] result = null;
            int next = (index + 1) % frames.size();
            if (elapsed == 0) {
                result = current;
            } else if (mixed != null && elapsed % (1L + skip) == 0) {
                int[] target = tiles[frames.get(next).tile];
                float fraction = (float) elapsed / frame.duration;
                for (int i = 0; i < mixed.length; i++) mixed[i] = mix(current[i], target[i], fraction);
                result = mixed;
            }
            if (++elapsed == frame.duration) {
                elapsed = 0;
                index = next;
            }
            return result;
        }
    }

    private static int mix(int from, int to, float f) {
        int result = 0;
        for (int bit = 0; bit <= 24; bit += 8) {
            int a = from >>> bit & 255, b = to >>> bit & 255;
            result |= (int) (a + (b - a) * f) << bit;
        }
        return result;
    }
}
