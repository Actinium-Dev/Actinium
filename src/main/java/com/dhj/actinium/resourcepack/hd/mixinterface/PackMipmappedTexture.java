package com.dhj.actinium.resourcepack.hd.mixinterface;

import com.dhj.actinium.resourcepack.hd.HdMipmapTextures.AnimatedImage;

/** Identifies standalone textures whose uploaded mip chain uses MCPatcher sampling. */
public interface PackMipmappedTexture {
    /** Returns the uploaded mip level count, or zero if the texture no longer owns that chain. */
    int actinium$getPackMipmapLevels();

    /** Returns the CPU pixels owned by the current allocation if it has a pack animation. */
    AnimatedImage actinium$getAnimatedImage();
}
