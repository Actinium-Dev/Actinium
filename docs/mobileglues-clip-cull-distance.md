# MobileGlues / Mali：`GL_EXT_clip_cull_distance` 与裁剪面着色器注入

> 对应 issue：[#214](https://github.com/Q-Engineering-Source/Actinium/issues/214)。

## 症状

在 Android 上通过 MobileGlues 2.0.0（ANGLE/Vulkan 后端，Mali-G720，POCO X7 Pro）运行并开启光影包后：

1. 所有 `gbuffers_*` 与 shadow 顶点着色器编译失败，日志中出现：
   ```text
   ERROR: 0:x: 'GL_EXT_clip_cull_distance' : extension is not supported
   ```
2. 实体不渲染（程序链接失败后被跳过）。
3. 随后 `DeferredWorldRenderingPipeline` 的阴影供应器返回 `null`，
   `CeleritasTerrainPipeline.initTerrainSamplers` 抛出 NPE，客户端崩溃。

## 根因链

1. **Actinium 无条件注入裁剪面代码**。`CommonTransformer`（`shader/.../pipeline/transform/CommonTransformer.java`）
   在 `Patch.ATTRIBUTES` + VERTEX 时向每个顶点着色器注入
   `uniform vec4 actinium_ClipPlane[8]` 和 8 条 `gl_ClipDistance[i] = dot(...)` 写入，
   仅靠运行时 uniform 门控，编译期必然出现 `gl_ClipDistance`。
2. **能力位误判**。MobileGlues 把 `GL_VERSION` 伪装成桌面格式（`"4.3.0 MobileGlues 2.0.0"`），
   不含 `"OpenGL ES "` 前缀，因此 `RenderSystem.isGLES()` 为 false；原判定
   `hasClipCullDistance = !isGLES || hasExtension("GL_EXT_clip_cull_distance")`
   把它当作桌面 GL，判定支持 `gl_ClipDistance`（桌面 GLSL 130+ 为核心特性）。
3. **转译后端没有该扩展**。MobileGlues 内部走 glslang → SPIRV-Cross 转 ESSL；
   SPIRV-Cross 见到 `gl_ClipDistance` 会输出 `#extension GL_EXT_clip_cull_distance : require`
   （`MobileGlues-cpp/3rdparty/SPIRV-Cross/spirv_glsl.cpp`），而 ANGLE/Mali 后端不支持该扩展，
   于是所有含注入代码的程序编译失败。

## 裁剪能力与模拟路径

`RenderSystem.initRenderer` 通过版本字符串中的 `MobileGlues` 或扩展表中的 `GL_MG_mobileglues` 识别 MobileGlues。对 GLES 和识别出的 MobileGlues，先确认 `GL_EXT_clip_cull_distance` 存在，再查询 `GL_MAX_CLIP_DISTANCES`。桌面 GL 直接查询上限。驱动支持至少八个平面时使用原生裁剪，其余情况使用片元模拟。[Angelica #2348](https://github.com/GTNewHorizons/Angelica/pull/2348)

模拟时，GLSM 记录八个平面的启用状态和眼空间方程。固定管线在顶点阶段计算到各平面的有符号距离并插值，片元着色器丢弃任一有效距离为负的片元。禁用的平面使用零方程。`GL_ENABLE_BIT` 恢复启用状态，`GL_TRANSFORM_BIT` 同时恢复方程。

Iris 对仅含顶点和片元阶段的 `Patch.ATTRIBUTES` 程序提供裁剪变体。启用第一个平面或关闭最后一个平面时，下一次绘制会选择对应变体。含几何或细分阶段的程序沿用原变体。SDL Vulkan 创建设备时请求原生裁剪能力，请求失败后可使用模拟路径。SDL 的 uniform 数组支持整组上传和按下标更新。

MobileGlues 侧的 `InitGLESCapabilities()` 根据真实 ES 后端能力公布扩展。其 `hideMGEnvLevel ≥ 1` 设置会隐藏版本标记和扩展 `GL_MG_mobileglues`，影响 Actinium 对伪装桌面上下文的识别。此前对 MobileGlues 的验证为使用 NDK 27.3.13750724 对 `gles/loader.cpp` 执行 C++ 语法检查。

## 游戏内验证方法

1. 在 Minecraft 1.12.2 安装 Actinium 和 HBM Nuclear Tech Mod Community Edition，放置滑动防爆门。`RenderSlidingBlastDoor.render` 绘制左右门扇时会启用两个裁剪平面。
2. 在桌面 OpenGL 下关闭光影，从正面和斜侧面各观察一次完整开关门动画。门扇滑入门框时应在边缘截断。重复开关后，邻近实体和方块实体应保持完整。
3. 在缺少八个原生裁剪平面的 MobileGlues 或 SDL Vulkan 设备上打开同一场景。先在 `latest.log` 搜索 `Emulating clip distances` 确认使用模拟路径，再分别关闭光影、启用使用顶点和片元两阶段方块实体程序的光影包。门板边界应与桌面对照一致，切换光影后实体应继续显示。记录设备、后端、光影包版本及相同视角的画面。
