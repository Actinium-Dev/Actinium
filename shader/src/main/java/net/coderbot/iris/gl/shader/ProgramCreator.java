// This file is based on code from Sodium by JellySquid, licensed under the LGPLv3 license.

package net.coderbot.iris.gl.shader;

import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.ffp.InstancedAttribs;
import com.gtnewhorizons.angelica.glsm.shader.ProgramBinaryCache;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.KHRDebug;

public class ProgramCreator {

	private static final Logger LOGGER = LogManager.getLogger("ProgramCreator");

	public static final int MC_ENTITY = 11;
	public static final int MC_MID_TEX_COORD = 12;
	public static final int AT_TANGENT = 13;
	public static final int AT_MIDBLOCK = 14;

	private record AttributeBinding(String name, int location) {}

	private static final AttributeBinding[] ATTRIBUTE_BINDINGS = {
		new AttributeBinding("iris_InstEntity", InstancedAttribs.LOC_ENTITY),
		new AttributeBinding("mc_Entity", MC_ENTITY),
		new AttributeBinding("mc_midTexCoord", MC_MID_TEX_COORD),
		new AttributeBinding("iris_CubeTangent", AT_TANGENT),
		new AttributeBinding("at_tangent", AT_TANGENT),
		new AttributeBinding("at_midBlock", AT_MIDBLOCK)
	};

	@Nullable
	public static ProgramBinaryCache.Key cacheKey() {
		if (!ProgramBinaryCache.isEnabled()) return null;
		final ProgramBinaryCache.Key key = ProgramBinaryCache.key();
		for (AttributeBinding binding : ATTRIBUTE_BINDINGS) {
			key.attribute(binding.name(), binding.location());
		}
		return key;
	}

	public static int load(String name, ProgramBinaryCache.Key cacheKey) {
		final int program = ProgramBinaryCache.load(cacheKey);
		if (program != 0) {
			GLDebug.nameObject(KHRDebug.GL_PROGRAM, program, name);
		}
		return program;
	}

	public static int create(String name, GlShader... shaders) {
		return create(name, null, shaders);
	}

	public static int create(String name, @Nullable ProgramBinaryCache.Key cacheKey, GlShader... shaders) {
		int program = GLSMPrograms.glCreateProgram();

		for (AttributeBinding binding : ATTRIBUTE_BINDINGS) {
			RenderSystem.bindAttributeLocation(program, binding.location(), binding.name());
		}

		for (GlShader shader : shaders) {
            GLSMPrograms.glAttachShader(program, shader.getHandle());
		}

        GLSMPrograms.glLinkProgram(program);

		GLDebug.nameObject(KHRDebug.GL_PROGRAM, program, name);

        for (GlShader shader : shaders) {
            RenderSystem.detachShader(program, shader.getHandle());
        }

		String log = RenderSystem.getProgramInfoLog(program);

		if (!log.isEmpty()) {
			LOGGER.warn("Program link log for " + name + ": " + log);
		}

		int result = GLSMPrograms.glGetProgrami(program, GL20.GL_LINK_STATUS);

		if (result != GL11.GL_TRUE) {
			throw new RuntimeException("Shader program linking failed, see log for details");
		}

		if (cacheKey != null) {
			ProgramBinaryCache.save(cacheKey, program);
		}

		return program;
	}
}
