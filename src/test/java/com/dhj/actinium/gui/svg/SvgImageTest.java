package com.dhj.actinium.gui.svg;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SvgImageTest {
    private static final String COLORED_SVG = """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16">
            <rect x="2" y="2" width="4" height="12" fill="#ff0000"/>
            <rect x="10" y="2" width="4" height="12" fill="#00ff00" fill-opacity="0.5"/>
        </svg>
        """;

    @Test
    void rendersColorsAndOpacityOnTransparentBackground() throws IOException {
        BufferedImage image = load(COLORED_SVG).rasterize(32, 32);

        assertEquals(BufferedImage.TYPE_INT_ARGB, image.getType());
        assertEquals(0, image.getRGB(0, 0));
        assertEquals(Color.RED.getRGB(), image.getRGB(5, 12));
        Color translucentGreen = new Color(image.getRGB(24, 12), true);
        assertEquals(0, translucentGreen.getRed());
        assertEquals(255, translucentGreen.getGreen());
        assertEquals(0, translucentGreen.getBlue());
        assertEquals(128, translucentGreen.getAlpha(), 1);
    }

    @Test
    void antialiasesDiagonalPaths() throws IOException {
        SvgImage svg = load("""
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16">
                <path d="M0 0 L16 16 H0 Z" fill="#0000ff"/>
            </svg>
            """);

        BufferedImage image = svg.rasterize(16, 16);

        assertEquals(Color.BLUE.getRGB(), image.getRGB(1, 14));
        assertEquals(0, image.getRGB(14, 1));
        int edgeAlpha = image.getRGB(8, 8) >>> 24;
        assertTrue(edgeAlpha > 0 && edgeAlpha < 255, "Diagonal edges need partial pixel coverage");
    }

    @Test
    void resolvesSubpixelGeometryWhenRasterizingAtHigherResolution() throws IOException {
        SvgImage svg = load("""
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16">
                <rect x="7.5" y="2" width="0.5" height="12" fill="#ffffff"/>
            </svg>
            """);

        BufferedImage small = svg.rasterize(16, 16);
        BufferedImage large = svg.rasterize(64, 64);

        int smallAlpha = small.getRGB(7, 8) >>> 24;
        assertTrue(smallAlpha > 0 && smallAlpha < 255, "The small raster only partially covers one pixel");
        assertEquals(64, large.getWidth());
        assertEquals(64, large.getHeight());
        assertEquals(Color.WHITE.getRGB(), large.getRGB(30, 32));
        assertEquals(Color.WHITE.getRGB(), large.getRGB(31, 32));
        assertEquals(0, large.getRGB(29, 32));
        assertEquals(0, large.getRGB(32, 32));
    }

    @Test
    void reusesOnlyTheMostRecentRasterization() throws IOException {
        SvgImage svg = load(COLORED_SVG);

        BufferedImage original = svg.rasterize(16, 16);
        assertSame(original, svg.rasterize(16, 16));

        BufferedImage wider = svg.rasterize(32, 16);
        assertNotSame(original, wider);
        assertEquals(32, wider.getWidth());
        assertEquals(16, wider.getHeight());
        assertSame(wider, svg.rasterize(32, 16));

        BufferedImage taller = svg.rasterize(32, 32);
        assertNotSame(wider, taller);
        assertEquals(32, taller.getWidth());
        assertEquals(32, taller.getHeight());
        assertSame(taller, svg.rasterize(32, 32));

        BufferedImage rebuilt = svg.rasterize(16, 16);
        assertNotSame(original, rebuilt);
        assertSame(rebuilt, svg.rasterize(16, 16));
        assertEquals(Color.RED.getRGB(), rebuilt.getRGB(3, 8));
    }

    @Test
    void rejectsZeroOrNegativeRasterDimensions() throws IOException {
        SvgImage svg = load(COLORED_SVG);

        assertThrows(IllegalArgumentException.class, () -> svg.rasterize(0, 16));
        assertThrows(IllegalArgumentException.class, () -> svg.rasterize(16, 0));
        assertThrows(IllegalArgumentException.class, () -> svg.rasterize(-1, 16));
        assertThrows(IllegalArgumentException.class, () -> svg.rasterize(16, -1));
    }

    @Test
    void rejectsMalformedOrNonSvgDocuments() {
        String[] invalidDocuments = {
            "",
            "This is not an SVG document",
            "<svg xmlns=\"http://www.w3.org/2000/svg\"><path",
            "<document/>"
        };
        for (String invalidDocument : invalidDocuments) {
            assertThrows(IOException.class, () -> load(invalidDocument));
        }
    }

    @Test
    void acceptsAnEmptySvgAsTransparentArtwork() throws IOException {
        SvgImage svg = load("""
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16"/>
            """);

        BufferedImage image = svg.rasterize(12, 8);

        assertEquals(12, image.getWidth());
        assertEquals(8, image.getHeight());
        assertTrue(Arrays.stream(image.getRGB(0, 0, 12, 8, null, 0, 12)).allMatch(pixel -> pixel == 0));
    }

    @Test
    void rendersBundledActionIconsAtGuiPixelSizes() throws IOException {
        String[] iconNames = {"undo_to_unmodified", "reset_to_default"};
        for (String iconName : iconNames) {
            String resource = "assets/reeses-sodium-options/textures/gui/" + iconName + ".svg";
            try (InputStream input = SvgImageTest.class.getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(input, resource);
                SvgImage svg = SvgImage.load(input);
                for (int pixelSize : new int[] {10, 20, 30, 40}) {
                    BufferedImage image = svg.rasterize(pixelSize, pixelSize);
                    assertEquals(pixelSize, image.getWidth());
                    assertEquals(pixelSize, image.getHeight());
                    int[] pixels = image.getRGB(0, 0, pixelSize, pixelSize, null, 0, pixelSize);
                    assertTrue(Arrays.stream(pixels).anyMatch(pixel -> (pixel >>> 24) > 0), resource);
                    assertTrue(Arrays.stream(pixels).anyMatch(pixel -> (pixel >>> 24) == 0), resource);
                }
            }
        }
    }

    private static SvgImage load(String source) throws IOException {
        return SvgImage.load(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)));
    }
}
