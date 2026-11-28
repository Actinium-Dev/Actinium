package com.dhj.actinium.gui.svg;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * Loads vector artwork once and rasterizes it at the pixel size required by the GUI.
 * Instances are intended for use on a single thread.
 */
public interface SvgImage {
    /**
     * Parses an SVG document for subsequent rasterization.
     *
     * @param input a non-null stream containing the SVG; loading consumes and closes it
     * @return the parsed image
     * @throws IOException if the stream cannot be read or parsed as an SVG document
     * @throws NullPointerException if input is null
     */
    static SvgImage load(InputStream input) throws IOException {
        return SvgImageImpl.load(input);
    }

    /**
     * Renders the document onto a transparent ARGB image at the requested pixel dimensions.
     * Only the most recent size is cached; consecutive requests for that size return the same image.
     * Callers must treat the returned image as read-only.
     *
     * @param pixelWidth positive output width in physical pixels
     * @param pixelHeight positive output height in physical pixels
     * @return the rasterized image with the SVG's aspect ratio rules preserved
     * @throws IllegalArgumentException if either dimension is zero or negative
     */
    BufferedImage rasterize(int pixelWidth, int pixelHeight);
}
