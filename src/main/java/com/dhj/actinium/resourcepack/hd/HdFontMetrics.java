package com.dhj.actinium.resourcepack.hd;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

final class HdFontMetrics {
    private HdFontMetrics() {}

    static Map<Integer, Float> measure(BufferedImage image) {
        int w = image.getWidth() / 16, h = image.getHeight() / 16;
        if (w == 0 || h == 0 || image.getWidth() % 16 != 0 || image.getHeight() % 16 != 0) {
            throw new IllegalArgumentException("Expected a 16 by 16 font grid");
        }
        Map<Integer, Float> widths = new HashMap<>();
        float sum = 0;
        int count = 0;
        for (int glyph = 0; glyph < 256; glyph++) {
            int left = glyph % 16 * w, top = glyph / 16 * h;
            int extent = 0;
            for (int y = 0; y < h; y++) {
                for (int x = w - 1; x >= extent; x--) {
                    if (image.getRGB(left + x, top + y) >>> 24 != 0) extent = x + 1;
                }
            }
            float advance = 1 + extent * 8F / w;
            widths.put(glyph, advance);
            if (extent != 0 && glyph < 128 && Character.isLetterOrDigit(glyph)) {
                sum += advance;
                count++;
            }
        }
        widths.put(32, count == 0 ? 4F : sum / count / 2);
        return widths;
    }
}
