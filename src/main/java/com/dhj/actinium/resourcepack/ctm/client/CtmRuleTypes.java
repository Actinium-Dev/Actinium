// Derived from Continuity-For-Actinium (4281538), PepperCode1 and contributors.
// SPDX-License-Identifier: LGPL-3.0-only
package com.dhj.actinium.resourcepack.ctm.client;

import com.dhj.actinium.resourcepack.ctm.api.client.CtmLoader;
import com.dhj.actinium.resourcepack.ctm.api.client.CtmLoaderRegistry;
import com.dhj.actinium.resourcepack.ctm.api.client.CachingPredicates;
import com.dhj.actinium.resourcepack.ctm.api.client.CtmProperties;
import com.dhj.actinium.resourcepack.ctm.api.client.QuadProcessor;
import com.dhj.actinium.resourcepack.ctm.client.processor.BaseCachingPredicates;
import com.dhj.actinium.resourcepack.ctm.client.processor.ProcessingDataKeys;
import com.dhj.actinium.resourcepack.ctm.client.processor.TopQuadProcessor;
import com.dhj.actinium.resourcepack.ctm.client.processor.overlay.StandardOverlayQuadProcessor;
import com.dhj.actinium.resourcepack.ctm.client.processor.overlay.ReplacementOverlayLoader;
import com.dhj.actinium.resourcepack.ctm.client.processor.simple.CtmSpriteProvider;
import com.dhj.actinium.resourcepack.ctm.client.processor.simple.CombinedSpriteProvider;
import com.dhj.actinium.resourcepack.ctm.client.processor.simple.CompactQuadProcessor;
import com.dhj.actinium.resourcepack.ctm.client.processor.simple.FixedSpriteProvider;
import com.dhj.actinium.resourcepack.ctm.client.processor.simple.HorizontalSpriteProvider;
import com.dhj.actinium.resourcepack.ctm.client.processor.simple.RandomSpriteProvider;
import com.dhj.actinium.resourcepack.ctm.client.processor.simple.RepeatSpriteProvider;
import com.dhj.actinium.resourcepack.ctm.client.processor.simple.SimpleQuadProcessor;
import com.dhj.actinium.resourcepack.ctm.client.processor.simple.VerticalSpriteProvider;
import com.dhj.actinium.resourcepack.ctm.client.properties.BaseCtmProperties;
import com.dhj.actinium.resourcepack.ctm.client.properties.ConnectingCtmProperties;
import com.dhj.actinium.resourcepack.ctm.client.properties.OrientedConnectingCtmProperties;
import com.dhj.actinium.resourcepack.ctm.client.properties.RandomCtmProperties;
import com.dhj.actinium.resourcepack.ctm.client.properties.RepeatCtmProperties;
import com.dhj.actinium.resourcepack.ctm.client.properties.overlay.StandardOverlayCtmProperties;
import com.dhj.actinium.resourcepack.ctm.client.properties.TileAmountValidator;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class CtmRuleTypes {
	public static final String ID = "actinium";
	public static final String NAME = "Actinium-CTM";
	public static final Logger LOGGER = LogManager.getLogger(NAME);

	private CtmRuleTypes() {
	}

	public static ResourceLocation asId(String path) {
		return new ResourceLocation(ID, path);
	}

	public static void registerLoaders() {
		ProcessingDataKeys.init();

		CtmLoaderRegistry registry = CtmLoaderRegistry.get();

		CtmLoader<OrientedConnectingCtmProperties> ctmLoader = new CtmLoader<>() {
			@Override
			public CtmProperties.Factory<OrientedConnectingCtmProperties> getPropertiesFactory() {
				return TileAmountValidator.wrapFactory(BaseCtmProperties.wrapFactory(OrientedConnectingCtmProperties::new), new TileAmountValidator.AtLeast<>(47));
			}

			@Override
			public QuadProcessor.Factory<OrientedConnectingCtmProperties> getProcessorFactory() {
				return new SimpleQuadProcessor.Factory<>(new CtmSpriteProvider.Factory());
			}

			@Override
			public CachingPredicates.Factory<OrientedConnectingCtmProperties> getPredicatesFactory() {
				return new BaseCachingPredicates.Factory<>(true);
			}
		};
		registry.registerLoader("ctm", ctmLoader);
		registry.registerLoader("glass", ctmLoader);

		CtmLoader<OrientedConnectingCtmProperties> horizontalLoader = new CtmLoader<>() {
			@Override
			public CtmProperties.Factory<OrientedConnectingCtmProperties> getPropertiesFactory() {
				return TileAmountValidator.wrapFactory(BaseCtmProperties.wrapFactory(OrientedConnectingCtmProperties::new), new TileAmountValidator.Exactly<>(4));
			}

			@Override
			public QuadProcessor.Factory<OrientedConnectingCtmProperties> getProcessorFactory() {
				return new SimpleQuadProcessor.Factory<>(new HorizontalSpriteProvider.Factory());
			}

			@Override
			public CachingPredicates.Factory<OrientedConnectingCtmProperties> getPredicatesFactory() {
				return new BaseCachingPredicates.Factory<>(true);
			}
		};
		registry.registerLoader("horizontal", horizontalLoader);
		registry.registerLoader("bookshelf", horizontalLoader);

		CtmLoader<OrientedConnectingCtmProperties> verticalLoader = new CtmLoader<>() {
			@Override
			public CtmProperties.Factory<OrientedConnectingCtmProperties> getPropertiesFactory() {
				return TileAmountValidator.wrapFactory(BaseCtmProperties.wrapFactory(OrientedConnectingCtmProperties::new), new TileAmountValidator.Exactly<>(4));
			}

			@Override
			public QuadProcessor.Factory<OrientedConnectingCtmProperties> getProcessorFactory() {
				return new SimpleQuadProcessor.Factory<>(new VerticalSpriteProvider.Factory());
			}

			@Override
			public CachingPredicates.Factory<OrientedConnectingCtmProperties> getPredicatesFactory() {
				return new BaseCachingPredicates.Factory<>(true);
			}
		};
		registry.registerLoader("vertical", verticalLoader);

		CtmLoader<ConnectingCtmProperties> topLoader = new CtmLoader<>() {
			@Override
			public CtmProperties.Factory<ConnectingCtmProperties> getPropertiesFactory() {
				return TileAmountValidator.wrapFactory(BaseCtmProperties.wrapFactory(ConnectingCtmProperties::new), new TileAmountValidator.Exactly<>(1));
			}

			@Override
			public QuadProcessor.Factory<ConnectingCtmProperties> getProcessorFactory() {
				return new TopQuadProcessor.Factory();
			}

			@Override
			public CachingPredicates.Factory<ConnectingCtmProperties> getPredicatesFactory() {
				return new BaseCachingPredicates.Factory<>(true);
			}
		};
		registry.registerLoader("top", topLoader);

		CtmLoader<BaseCtmProperties> fixedLoader = new CtmLoader<>() {
			@Override
			public CtmProperties.Factory<BaseCtmProperties> getPropertiesFactory() {
				return BaseCtmProperties::new;
			}

			@Override
			public QuadProcessor.Factory<BaseCtmProperties> getProcessorFactory() {
				return new SimpleQuadProcessor.Factory<>(new FixedSpriteProvider.Factory());
			}

			@Override
			public CachingPredicates.Factory<BaseCtmProperties> getPredicatesFactory() {
				return new BaseCachingPredicates.Factory<>(true);
			}
		};
		registry.registerLoader("fixed", fixedLoader);

		CtmLoader<RandomCtmProperties> randomLoader = new CtmLoader<>() {
			@Override
			public CtmProperties.Factory<RandomCtmProperties> getPropertiesFactory() {

				return BaseCtmProperties.wrapFactory(RandomCtmProperties::new);
			}

			@Override
			public QuadProcessor.Factory<RandomCtmProperties> getProcessorFactory() {
				return new SimpleQuadProcessor.Factory<>(new RandomSpriteProvider.Factory());
			}

			@Override
			public CachingPredicates.Factory<RandomCtmProperties> getPredicatesFactory() {
				return new BaseCachingPredicates.Factory<>(true);
			}
		};
		registry.registerLoader("random", randomLoader);

		CtmLoader<RepeatCtmProperties> repeatLoader = new CtmLoader<>() {
			@Override
			public CtmProperties.Factory<RepeatCtmProperties> getPropertiesFactory() {
				return TileAmountValidator.wrapFactory(BaseCtmProperties.wrapFactory(RepeatCtmProperties::new), new RepeatCtmProperties.Validator<>());
			}

			@Override
			public QuadProcessor.Factory<RepeatCtmProperties> getProcessorFactory() {
				return new SimpleQuadProcessor.Factory<>(new RepeatSpriteProvider.Factory());
			}

			@Override
			public CachingPredicates.Factory<RepeatCtmProperties> getPredicatesFactory() {
				return new BaseCachingPredicates.Factory<>(true);
			}
		};
		registry.registerLoader("repeat", repeatLoader);

		CtmLoader<StandardOverlayCtmProperties> overlayLoader = new CtmLoader<>() {
			@Override
			public CtmProperties.Factory<StandardOverlayCtmProperties> getPropertiesFactory() {
				return TileAmountValidator.wrapFactory(
						BaseCtmProperties.wrapFactory(StandardOverlayCtmProperties::new),
						new TileAmountValidator.AtLeast<>(17));
			}

			@Override
			public QuadProcessor.Factory<StandardOverlayCtmProperties> getProcessorFactory() {
				return new StandardOverlayQuadProcessor.Factory();
			}

			@Override
			public CachingPredicates.Factory<StandardOverlayCtmProperties> getPredicatesFactory() {
				return new BaseCachingPredicates.Factory<>(true);
			}
		};
		registry.registerLoader("overlay", overlayLoader);
		registry.registerLoader("overlay_ctm", new ReplacementOverlayLoader<>(ctmLoader));
		registry.registerLoader("overlay_fixed", new ReplacementOverlayLoader<>(fixedLoader));
		registry.registerLoader("overlay_random", new ReplacementOverlayLoader<>(randomLoader));
		registry.registerLoader("overlay_repeat", new ReplacementOverlayLoader<>(repeatLoader));
		CtmLoader<OrientedConnectingCtmProperties> horizontalVertical = combined(true);
		CtmLoader<OrientedConnectingCtmProperties> verticalHorizontal = combined(false);
		registry.registerLoader("horizontal+vertical", horizontalVertical);
		registry.registerLoader("h+v", horizontalVertical);
		registry.registerLoader("vertical+horizontal", verticalHorizontal);
		registry.registerLoader("v+h", verticalHorizontal);
		CtmLoader<OrientedConnectingCtmProperties> compact = new CtmLoader<>() {
			@Override
			public CtmProperties.Factory<OrientedConnectingCtmProperties> getPropertiesFactory() {
				return TileAmountValidator.wrapFactory(BaseCtmProperties.wrapFactory(OrientedConnectingCtmProperties::new), new TileAmountValidator.AtLeast<>(5));
			}

			@Override
			public QuadProcessor.Factory<OrientedConnectingCtmProperties> getProcessorFactory() {
				return new CompactQuadProcessor.Factory();
			}

			@Override
			public CachingPredicates.Factory<OrientedConnectingCtmProperties> getPredicatesFactory() {
				return new BaseCachingPredicates.Factory<>(true);
			}
		};
		registry.registerLoader("ctm_compact", compact);
		registry.registerLoader("compact", compact);
	}

	private static CtmLoader<OrientedConnectingCtmProperties> combined(boolean horizontalFirst) {
		return new CtmLoader<>() {
			@Override
			public CtmProperties.Factory<OrientedConnectingCtmProperties> getPropertiesFactory() {
				return TileAmountValidator.wrapFactory(BaseCtmProperties.wrapFactory(OrientedConnectingCtmProperties::new), new TileAmountValidator.AtLeast<>(7));
			}

			@Override
			public QuadProcessor.Factory<OrientedConnectingCtmProperties> getProcessorFactory() {
				return new SimpleQuadProcessor.Factory<>(new CombinedSpriteProvider.Factory(horizontalFirst));
			}

			@Override
			public CachingPredicates.Factory<OrientedConnectingCtmProperties> getPredicatesFactory() {
				return new BaseCachingPredicates.Factory<>(true);
			}
		};
	}
}
