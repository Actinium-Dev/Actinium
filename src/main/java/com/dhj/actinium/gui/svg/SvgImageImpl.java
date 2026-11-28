package com.dhj.actinium.gui.svg;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.LoaderContext;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.view.ViewBox;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/** Rasterizes the retained vector document whenever the GUI needs a different pixel size. */
final class SvgImageImpl implements SvgImage {
    /** Retains vector geometry so size changes do not resample an existing bitmap. */
    private final SVGDocument document;

    /** Avoids allocating and rendering another image while the requested size is unchanged. */
    private BufferedImage cachedImage;

    private SvgImageImpl(SVGDocument document) {
        this.document = document;
    }

    static SvgImage load(InputStream input) throws IOException {
        Objects.requireNonNull(input, "input");
        SVGDocument document = new SVGLoader().load(input, null, LoaderContext.createDefault());
        if (document == null) {
            throw new IOException("Could not load SVG document");
        }
        return new SvgImageImpl(document);
    }

    @Override
    public BufferedImage rasterize(int pixelWidth, int pixelHeight) {
        if (pixelWidth <= 0 || pixelHeight <= 0) {
            throw new IllegalArgumentException("SVG raster dimensions must be positive: "
                + pixelWidth + "x" + pixelHeight);
        }
        if (cachedImage != null
            && cachedImage.getWidth() == pixelWidth
            && cachedImage.getHeight() == pixelHeight) {
            return cachedImage;
        }

        BufferedImage image = new BufferedImage(pixelWidth, pixelHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            document.render(null, graphics, new ViewBox(pixelWidth, pixelHeight));
        } finally {
            graphics.dispose();
        }
        cachedImage = image;
        return image;
    }
}
