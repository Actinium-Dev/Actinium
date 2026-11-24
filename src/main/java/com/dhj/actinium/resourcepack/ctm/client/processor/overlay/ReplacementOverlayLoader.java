package com.dhj.actinium.resourcepack.ctm.client.processor.overlay;

import com.dhj.actinium.resourcepack.ctm.api.client.CachingPredicates;
import com.dhj.actinium.resourcepack.ctm.api.client.CtmLoader;
import com.dhj.actinium.resourcepack.ctm.api.client.CtmProperties;
import com.dhj.actinium.resourcepack.ctm.api.client.QuadProcessor;
import com.dhj.actinium.resourcepack.ctm.api.client.QuadProcessor.ProcessingResult;
import com.dhj.actinium.resourcepack.ctm.client.model.OverlayBakedQuad;
import com.dhj.actinium.resourcepack.ctm.client.properties.BaseCtmProperties;
import com.dhj.actinium.resourcepack.ctm.client.properties.overlay.StandardOverlayCtmProperties;

/** Reuses a tile selector while emitting its replacement as an additive surface. */
public record ReplacementOverlayLoader<T extends BaseCtmProperties>(CtmLoader<T> delegate) implements CtmLoader<T> {
    @Override
    public CtmProperties.Factory<T> getPropertiesFactory() {
        return delegate.getPropertiesFactory();
    }

    @Override
    public QuadProcessor.Factory<T> getProcessorFactory() {
        return (properties, sprites) -> {
            QuadProcessor processor = delegate.getProcessorFactory().createProcessor(properties, sprites);
            var overlay = new StandardOverlayCtmProperties(properties.getProperties(), properties.getResourceId(),
                    properties.getPackId(), 0, null, properties.getMethod());
            overlay.parseOverlayOptions();
            return (quad, sprite, world, pos, appearance, state, seed, pass, context) -> {
                if (quad.getFace() == null) {
                    return ProcessingResult.NEXT_PROCESSOR;
                }
                processor.processQuad(quad, sprite, world, pos, appearance, state, seed, pass, context);
                context.getExtraQuads().replaceAll(out -> new OverlayBakedQuad(out.getVertexData(),
                        overlay.getTintIndex(), out.getFace(), out.getSprite(), out.shouldApplyDiffuseLighting(),
                        out.getFormat(), overlay.getLayer(), overlay.getTintBlock()));
                return ProcessingResult.NEXT_PROCESSOR;
            };
        };
    }

    @Override
    public CachingPredicates.Factory<T> getPredicatesFactory() {
        return delegate.getPredicatesFactory();
    }
}
