package com.dhj.actinium.resourcepack.hd;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

record HdDial(List<Layer> layers) {
    interface Images { BufferedImage load(String path) throws IOException; }

    record Layer(BufferedImage image, double scaleX, double scaleY, double offsetX, double offsetY,
                 double speed, double rotation, Blend blend) {}

    enum Blend { ALPHA, ADD, SUBTRACT, MULTIPLY, DODGE, BURN, SCREEN, OVERLAY, REPLACE }

    static HdDial parse(Properties p, Images images) throws IOException {
        List<Layer> layers = new ArrayList<>();
        for (int i = 0; p.containsKey("source." + i); i++) {
            String suffix = "." + i;
            double sx = number(p, "scaleX" + suffix, 1), sy = number(p, "scaleY" + suffix, 1);
            if (sx == 0 || sy == 0) throw new IllegalArgumentException("Dial scale cannot be zero");
            layers.add(new Layer(images.load(p.getProperty("source" + suffix)), sx, sy,
                number(p, "offsetX" + suffix, 0), number(p, "offsetY" + suffix, 0),
                number(p, "rotationSpeed" + suffix, 0), number(p, "rotationOffset" + suffix, 0),
                Blend.valueOf(p.getProperty("blend" + suffix, "alpha").trim().toUpperCase(Locale.ROOT))));
        }
        if (layers.size() < 2) throw new IllegalArgumentException("A dial requires at least two consecutive layers starting at source.0");
        return new HdDial(List.copyOf(layers));
    }

    int[] render(int w, int h, double angle) {
        int[] pixels = new int[Math.multiplyExact(w, h)];
        for (Layer layer : layers) {
            double radians = Math.toRadians(angle * layer.speed + layer.rotation);
            double sin = Math.sin(radians), cos = Math.cos(radians);
            for (int y = 0; y < h; y++) {
                double py = 1 - (y + 0.5) * 2 / h - layer.offsetY;
                for (int x = 0; x < w; x++) {
                    double px = (x + 0.5) * 2 / w - 1 - layer.offsetX;
                    double u = (cos * px / layer.scaleX + sin * py / layer.scaleY + 1) * 0.5;
                    double v = (1 + sin * px / layer.scaleX - cos * py / layer.scaleY) * 0.5;
                    if (u < 0 || u >= 1 || v < 0 || v >= 1) continue;
                    int src = layer.image.getRGB((int) (u * layer.image.getWidth()), (int) (v * layer.image.getHeight()));
                    int i = y * w + x;
                    pixels[i] = composite(pixels[i], src, layer.blend);
                }
            }
        }
        return pixels;
    }

    private static int composite(int dst, int src, Blend blend) {
        int sa = src >>> 24, da = dst >>> 24;
        if (sa == 0) return dst;
        if (blend == Blend.REPLACE || da == 0) return src;
        double a = sa / 255.0, b = da / 255.0;
        double alpha = a + b * (1 - a);
        int result = (int) Math.round(alpha * 255) << 24;
        for (int bit = 0; bit < 24; bit += 8) {
            double s = (src >>> bit & 255) / 255.0, d = (dst >>> bit & 255) / 255.0;
            double color = switch (blend) {
                case ADD, DODGE -> Math.min(1, s + d);
                case SUBTRACT -> s * (1 - d);
                case MULTIPLY -> s * d;
                case BURN -> d * (1 - s);
                case SCREEN -> s + d - s * d;
                case OVERLAY -> Math.min(1, 2 * d * s);
                default -> s;
            };
            result |= (int) Math.round((a * ((1 - b) * s + b * color) + (1 - a) * b * d) / alpha * 255) << bit;
        }
        return result;
    }

    private static double number(Properties p, String key, double defaultValue) {
        double value = Double.parseDouble(p.getProperty(key, Double.toString(defaultValue)));
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Invalid dial value for " + key);
        return value;
    }
}
