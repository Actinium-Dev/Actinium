package com.dhj.actinium.resourcepack.hd;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExtendedHdTest {
    @Test
    void animatesReorderedTilesWithInterpolationAndReset() {
        BufferedImage strip = image(1, 2);
        strip.setRGB(0, 0, 0xff000000);
        strip.setRGB(0, 1, 0xffffffff);
        Properties p = new Properties();
        p.putAll(Map.of("x", "1", "y", "0", "w", "1", "h", "1", "tile.0", "1", "tile.1", "0",
            "duration.0", "4", "interpolate", "true", "skip", "1"));
        var cursor = HdAnimation.parse(p, strip, image(2, 1)).cursor();
        assertEquals(0xffffffff, cursor.tick()[0]);
        assertNull(cursor.tick());
        assertEquals(0xff7f7f7f, cursor.tick()[0]);
        assertNull(cursor.tick());
        assertEquals(0xff000000, cursor.tick()[0]);
        cursor.reset();
        assertEquals(0xffffffff, cursor.tick()[0]);
        assertThrows(IllegalArgumentException.class, () -> HdAnimation.parse(p, strip, image(1, 1)));
    }

    @Test
    void rotatesOnlyTheMovingDialLayer() throws Exception {
        BufferedImage base = image(2, 2), needle = image(2, 2);
        base.setRGB(0, 1, 0xff008000);
        needle.setRGB(0, 0, 0xffff0000);
        Properties p = new Properties();
        p.putAll(Map.of("source.0", "base", "source.1", "needle", "rotationSpeed.1", "1"));
        HdDial dial = HdDial.parse(p, key -> key.equals("base") ? base : needle);
        assertEquals(0xffff0000, dial.render(2, 2, 0)[0]);
        int[] rotated = dial.render(2, 2, 180);
        assertEquals(0, rotated[0]);
        assertEquals(0xff008000, rotated[2]);
        assertEquals(0xffff0000, rotated[3]);
    }

    @Test
    void computesFractionalFontAdvanceFromTheSelectedImage() {
        BufferedImage atlas = image(256, 256);
        atlas.setRGB(16 + 4, 4 * 16, 0xff000000);
        var widths = HdFontMetrics.measure(atlas);
        assertEquals(3.5F, widths.get(65));
        assertEquals(1.75F, widths.get(32));
    }

    private static BufferedImage image(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }
}
