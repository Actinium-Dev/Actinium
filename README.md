# Actinium<img src="src/main/resources/assets/actinium/icon.png" width="40" height="40" align="right">

[CurseForge](https://www.curseforge.com/minecraft/mc-mods/actinium-1-12-2-optimization) | [English](README.en.md)

Minecraft 1.12.2 的现代渲染与光影模组，移植自 Angelica：Iris 光影、Celeritas 地形与 GL 3.3 core profile。

> 你说的对，但是《Actinium》是由 [Actinium-Dev](https://github.com/Actinium-Dev) 自主（并非）研发的一款全新开源客户端性能模组。游戏发生在一个被称作「[Minecraft 1.12.2](https://minecraft.wiki/w/Java_Edition_1.12.2)」的幻想世界，在这里，被 [Java](https://baike.baidu.com/item/Java/85979) 选中的人将被授予「[LWJGL](https://www.lwjgl.org/)」，导引 [OpenGL](https://www.opengl.org/) 之力。你将扮演一位名为「[Act-Dev](https://github.com/DHJComical)」的神秘开发团队，在自由的「[计算机图形学](https://baike.baidu.com/item/计算机图形学/279486)」 中邂逅性格各异、能力独特的同伴们——[IntelliJ IDEA](https://www.jetbrains.com/idea/)、[Codex](https://github.com/openai/codex)、[RenderDoc](https://renderdoc.org)——和它们一起击败 [OptiFine](https://optifine.net/)，找回失散的帧数，同时逐步发掘「为什么我的光影还是爆炸」的真相。

![Actinium](docs/image/actinium.png)

## 特性

* 光影：Iris 移植与增强
* 地形：Celeritas，多线程区块构建、半透明排序、GPU 遮挡剔除与 MultiDraw
* GL 3.3 core profile，固定管线由生成的 GLSL 着色器模拟（FFP）
* 渲染后端选择：OpenGL，或 SDL GPU（Vulkan / Metal / Direct3D 12）
* 实体、方块实体、物品与粒子的批处理与实例化
* 手持动态光照
* 性能指示器 HUD（FPS / 1% Low / CPU / GPU / 内存 / 显存）
* 内嵌 Reese's Sodium Options 视频设置
* Distant Horizons LOD 支持

## 状态

* OpenGL + Celeritas 地形：默认
* 光影：可用，支持 Iris 1.7+ 的现代光影
* SDL GPU 渲染器：实验性

## SDL GPU 渲染器

可选，OpenGL 是默认后端。

* 经 SDL3 支持 Vulkan、Metal（macOS）与 Direct3D 12（Windows）
* 依赖宿主 SDL 窗口系统，缺失或设备创建失败时回退 OpenGL
* macOS 的 Vulkan 由独立的 `actinium-moltenvk` jar 经 MoltenVK 提供

## Core Profile

目标为 GL 3.3+ core profile。

* 固定管线经生成的 GLSL 着色器模拟
* 显示列表模拟
* GLSM 提供软件矩阵栈与状态管理
* GL_QUADS 到 GL_TRIANGLES 的转换
* 自动变换使用 compat-only GLSL 内置量（`gl_ModelViewMatrix`、`texture2D` 等）的模组着色器

## 运行要求

* Minecraft 1.12.2
* Outlands （2026-11-11+）、Cleanroom （0.3.0 - 0.6.13）、Forge
* 支持至少 OpenGL 3.3+ core profile 的图形环境

## 已知不兼容

* Cleanroom Loader 0.7.0+
* Kirino Graphics 模式：渲染管线互斥，自动回退 Headless 模式
* RenderLib：RenderLib 系列的 Mod
* OptiFine（永不兼容）
* Smooth Font（只能使用字体优化，无法更换字体）

## 构建

* `./gradlew build`、`./gradlew check`
* 产物为 `build/libs/Actinium-<version>.jar`

## AI 声明

本项目代码几乎全部由 AI 编码代理辅助生成，所有改动经人工与 AI 审阅、测试与回归验证后合入。

## Credits

* [Angelica](https://github.com/GTNewHorizons/Angelica)
  * mitchej123、Omni、Makamys、Embeddedt、NanoLive、Caedis、Cardinalstar、Alexdoru、Eigenraven、mist475、Clepto、kurrycat：GLSM、SDL GPU、批处理字体渲染等大量代码移植自 Angelica
* [Iris Shaders](https://github.com/IrisShaders/Iris)
  * Iris 团队：coderbot、IMS212、Justsnoopy30、FoundationGames
  * Asek3 的 Iris Forge 移植 [Oculus](https://github.com/Asek3/Oculus)
  * 移植代码遵循 LGPL
* [Sodium](https://github.com/CaffeineMC/sodium) / [Celeritas](https://git.taumc.org/embeddedt/celeritas)
  * JellySquid 与 CaffeineMC 的 Sodium
  * embeddedt 的 Celeritas，LGPL 3.0
* [Reese's Sodium Options](https://github.com/FlashyReese/reeses-sodium-options)
  * FlashyReese，MIT（内嵌视频设置界面）
* [glsl-transformation-lib](https://github.com/TauMC/glsl-transformation-lib)
  * embeddedt 与 ferriarnus，LGPL 3.0；Monocle 作为参考实现
* [Mesa](https://gitlab.freedesktop.org/mesa/mesa)
  * FFP 顶点与片元着色器生成、显示列表 ID 分配器移植自 Mesa，MIT
* [GTNHLib](https://github.com/GTNewHorizons/GTNHLib)
  * GT New Horizons 团队及 GTNHLib 贡献者。Actinium 内嵌其中的内存管理、缓冲区、后处理和旧版渲染工具代码。上游采用 LGPL-3.0。
* [CeleritasDynamicLights](https://github.com/Karnatour/CeleritasDynamicLights.git)
  * Karnatour 对 SodiumDynamicLights 的移植

## 许可证

Actinium 自有代码的许可证见 `LICENSE`。来自上游项目的代码及围绕它的适配修改，除非源文件另有说明，否则遵循各自上游的原许可证。
