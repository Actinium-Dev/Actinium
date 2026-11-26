# Actinium<img src="src/main/resources/assets/actinium/icon.png" width="40" height="40" align="right">

[CurseForge](https://www.curseforge.com/minecraft/mc-mods/actinium-1-12-2-optimization) | [简体中文](README.md)

A modern rendering and shader mod for Minecraft 1.12.2, ported from Angelica: Iris shaders, Celeritas terrain, and GL 3.3 core profile.

![Actinium](docs/image/actinium.png)

## Features

* Shaders: Iris port and enhancements
* Terrain: Celeritas, with multithreaded chunk building, translucent sorting, GPU occlusion culling, and MultiDraw
* GL 3.3 core profile, with the fixed-function pipeline emulated by generated GLSL shaders (FFP)
* Selectable rendering backend: OpenGL, or SDL GPU (Vulkan / Metal / Direct3D 12)
* Batching and instancing for entities, block entities, items, and particles
* Held-item dynamic lighting
* Performance HUD (FPS / 1% Low / CPU / GPU / memory / VRAM)
* Embedded Reese's Sodium Options video settings
* Distant Horizons LOD support

## Status

* OpenGL + Celeritas terrain: default
* Shaders: available, with support for modern Iris 1.7+ shader packs
* SDL GPU renderer: experimental

## SDL GPU Renderer

Optional; OpenGL is the default backend.

* Vulkan, Metal (macOS), and Direct3D 12 (Windows) via SDL3
* Requires the host SDL windowing system; falls back to OpenGL when it is missing or device creation fails
* Vulkan on macOS is provided by a separate `actinium-moltenvk` jar through MoltenVK

## Core Profile

Targets GL 3.3+ core profile.

* Fixed-function pipeline emulated by generated GLSL shaders
* Display list emulation
* GLSM provides a software matrix stack and state management
* GL_QUADS to GL_TRIANGLES conversion
* Automatic transformation of mod shaders that rely on compat-only GLSL built-ins (`gl_ModelViewMatrix`, `texture2D`, and others)

## Runtime Requirements

* Minecraft 1.12.2
* Outlands (2026-11-11+), Cleanroom (0.3.0 - 0.6.13), or Forge
* A graphics environment with at least an OpenGL 3.3+ core profile

## Known Incompatibilities

* Cleanroom Loader 0.7.0+
* Kirino Graphics mode: rendering pipelines are mutually exclusive, automatically falls back to Headless mode
* RenderLib: mods in the RenderLib family
* OptiFine (never compatible)
* Smooth Font (only the font optimization is usable, fonts cannot be replaced)

## Building

* `./gradlew build`, `./gradlew check`
* Main mod: `build/libs/Actinium-<version>.jar`
* macOS Vulkan add-on: `build/libs/actinium-moltenvk-<version>.jar`

## AI-Generated Code

Nearly all code in this project is produced with the help of AI coding agents; every change is reviewed, tested, and regression-verified by humans and AI before it is merged.

## Credits

* [Angelica](https://github.com/GTNewHorizons/Angelica)
  * mitchej123, Omni, Makamys, Embeddedt, NanoLive, Caedis, Cardinalstar, Alexdoru, Eigenraven, mist475, Clepto, and kurrycat: much of the GLSM, SDL GPU, and batched font rendering code is ported from Angelica
* [Iris Shaders](https://github.com/IrisShaders/Iris)
  * The Iris team: coderbot, IMS212, Justsnoopy30, FoundationGames
  * [Oculus](https://github.com/Asek3/Oculus), Asek3's Iris Forge port
  * Ported code follows the LGPL
* [Sodium](https://github.com/CaffeineMC/sodium) / [Celeritas](https://git.taumc.org/embeddedt/celeritas)
  * Sodium by JellySquid and CaffeineMC
  * Celeritas by embeddedt, LGPL 3.0
* [Reese's Sodium Options](https://github.com/FlashyReese/reeses-sodium-options)
  * FlashyReese, MIT (embedded video settings screen)
* [glsl-transformation-lib](https://github.com/TauMC/glsl-transformation-lib)
  * embeddedt and ferriarnus, LGPL 3.0; Monocle as a reference implementation
* [Mesa](https://gitlab.freedesktop.org/mesa/mesa)
  * FFP vertex and fragment shader generation and the display list ID allocator are ported from Mesa, MIT
* [GTNHLib](https://github.com/GTNewHorizons/GTNHLib)
  * The GT New Horizons team and GTNHLib contributors. Actinium embeds its memory management, buffer, post-processing, and legacy rendering utility code. Upstream is LGPL-3.0.
* [CeleritasDynamicLights](https://github.com/Karnatour/CeleritasDynamicLights.git)
  * Karnatour's port of SodiumDynamicLights

## License

See `LICENSE` for the license of Actinium's own code. Code from upstream projects, and the adaptation changes made around it, follow each upstream project's original license unless the source file states otherwise.
