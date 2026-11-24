package net.coderbot.iris.pipeline.transform;

import com.gtnewhorizons.angelica.glsm.GlslTransformUtils;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.ffp.ClipDistanceGlsl;
import net.coderbot.iris.gl.shader.ShaderType;
import net.coderbot.iris.pipeline.transform.parameter.AttributeParameters;
import net.coderbot.iris.pipeline.transform.parameter.Parameters;
import org.taumc.glsl.Transformer;

import java.util.HashSet;
import java.util.Set;

public class CommonTransformer {

	public static void transform(Transformer root, Parameters parameters, boolean core, int glslVersion) {
		root.rename("gl_FogFragCoord", "iris_FogFragCoord");
		if (parameters.type == ShaderType.VERTEX) {
			root.injectVariable("out float iris_FogFragCoord;");
			root.prependMain("iris_FogFragCoord = 0.0f;");
		} else if (parameters.type == ShaderType.FRAGMENT) {
			root.injectVariable("in float iris_FogFragCoord;");
		}

		if (parameters.type == ShaderType.VERTEX) {
			root.injectVariable("out vec4 iris_FrontColor;");
			root.rename("gl_FrontColor", "iris_FrontColor");
			// Legacy packs read gl_Color in the composite vertex stage (e.g. Sildur's
			// Enhanced Default). The full-screen quad has no color attribute, so map it
			// to the same white-initialized out variable that gl_FrontColor uses.
			// Only COMPOSITE: other patches map gl_Color themselves (ATTRIBUTES to the
			// vertex color / modulator, Celeritas and DH terrain to _vert_color).
			if (parameters.patch == Patch.COMPOSITE) {
				root.rename("gl_Color", "iris_FrontColor");
			}
			root.prependMain("iris_FrontColor = vec4(1.0);");
		} else if (parameters.type == ShaderType.FRAGMENT) {
			root.injectVariable("in vec4 iris_FrontColor;");
			root.rename("gl_Color", "iris_FrontColor");
		}

		if (parameters.type == ShaderType.FRAGMENT) {
			if (root.containsCall("gl_FragColor")) {
				root.replaceExpression("gl_FragColor", "gl_FragData[0]");
			}

			if (core) {
				// Core profile: gl_FragData doesn't exist. Flatten gl_FragData[N] → iris_FragDataN with layout-qualified out declarations.
				Set<Integer> found = new HashSet<>();
				root.renameArray("gl_FragData", "iris_FragData", found);

				for (Integer i : found) {
					root.injectVariable("layout (location = " + i + ") out vec4 iris_FragData" + i + ";");
				}

				// Core profile: GL_ALPHA_TEST is removed. 1.7.10 engine relies on alpha test to discard transparent fragments. Inject
				// runtime discard using the GLSM-tracked alpha reference value.
				if (found.contains(0) && parameters.patch != Patch.COMPOSITE && parameters.patch != Patch.COMPUTE) {
					root.injectVariable("uniform float iris_currentAlphaTest;");
					root.appendMain("if (iris_FragData0.a <= iris_currentAlphaTest) discard;");
				}
			}
		}

		if (root.containsCall("texture") && root.hasVariable("texture")) {
			root.rename("texture", "gtexture");
		}

		if (root.hasVariable("actinium_renamed_texture")) {
			root.rename("actinium_renamed_texture", "gtexture");
		}

		if (root.containsCall("gcolor") && root.hasVariable("gcolor")) {
			root.rename("gcolor", "gtexture");
		}

		root.rename("gl_Fog", "iris_Fog");
		root.injectVariable("uniform float iris_FogDensity;");
		root.injectVariable("uniform float iris_FogStart;");
		root.injectVariable("uniform float iris_FogEnd;");
		root.injectVariable("uniform vec4 iris_FogColor;");
		root.injectFunction("struct iris_FogParameters {vec4 color;float density;float start;float end;float scale;};");
		root.injectFunction("iris_FogParameters iris_Fog = iris_FogParameters(iris_FogColor, iris_FogDensity, iris_FogStart, iris_FogEnd, 1.0f / (iris_FogEnd - iris_FogStart));");

		root.renameFunctionCall(GlslTransformUtils.TEXTURE_RENAMES);
		root.renameAndWrapShadow("shadow2D", "texture");
		root.renameAndWrapShadow("shadow2DLod", "textureLod");
		AdaptiveShadowBoundsTransformer.transform(root, parameters.type);

		if (parameters instanceof AttributeParameters attributes) {
			injectClipDistances(root, attributes);
		}
	}

	private static final String EMULATED_VERTEX_CLIP;
	private static final String NATIVE_VERTEX_CLIP;
	static {
		String eyePosition = "vec4 _cp_ep = iris_ModelViewMatrix * iris_Vertex; ";
		StringBuilder nativeClip = new StringBuilder("{ ").append(eyePosition);
		String[] distances = new String[8];
		for (int i = 0; i < distances.length; i++) {
			distances[i] = "dot(actinium_ClipPlane[" + i + "], _cp_ep)";
			nativeClip.append("gl_ClipDistance[").append(i).append("] = ").append(distances[i]).append("; ");
		}
		NATIVE_VERTEX_CLIP = nativeClip.append('}').toString();
		StringBuilder emulatedClip = new StringBuilder("{ ").append(eyePosition);
		ClipDistanceGlsl.appendVsAssign(emulatedClip, distances);
		EMULATED_VERTEX_CLIP = emulatedClip.append('}').toString();
	}

	private static void injectClipDistances(Transformer root, AttributeParameters parameters) {
		if (RenderSystem.isClipDistanceEmulated()) {
			if (!parameters.clipDiscard || parameters.hasGeometry || parameters.hasTesselation) return;
			if (parameters.type == ShaderType.VERTEX) {
				root.injectVariable("uniform vec4 actinium_ClipPlane[8];");
				root.injectVariable(ClipDistanceGlsl.VS_OUT_DIST0);
				root.injectVariable(ClipDistanceGlsl.VS_OUT_DIST1);
				root.prependMain(EMULATED_VERTEX_CLIP);
			} else if (parameters.type == ShaderType.FRAGMENT) {
				root.injectVariable(ClipDistanceGlsl.FS_IN_DIST0);
				root.injectVariable(ClipDistanceGlsl.FS_IN_DIST1);
				root.prependMain(ClipDistanceGlsl.FS_DISCARD);
			}
		} else if (parameters.type == ShaderType.VERTEX) {
			root.injectVariable("uniform vec4 actinium_ClipPlane[8];");
			if (BackendManager.RENDER_BACKEND.isSDLGPU()) {
				// Vulkan consumes every declared distance. Disabled planes have zero equations;
				// a pack can override the resulting defaults before returning from main.
				root.prependMain(NATIVE_VERTEX_CLIP);
			} else {
				root.injectVariable("uniform bool actinium_ClipPlanesEnabled;");
				root.appendMain("if (actinium_ClipPlanesEnabled) " + NATIVE_VERTEX_CLIP);
			}
		}
	}
}
