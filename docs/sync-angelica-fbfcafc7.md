# Angelica 上游增量台账（996fb54c → fbfcafc7）

## 基线与范围

上游固定区间为 `996fb54cb05e256ea6039f4e2c8457a221a179b7..fbfcafc7b579b9054ddaca1e9888ba244bf4549d`。2026-11-24 调用 GitHub Compare API，返回 `ahead_by=6`、`behind_by=0`。下表按 API 提交顺序排列，日期采用 UTC 作者日期。[GitHub Compare API](https://api.github.com/repos/GTNewHorizons/Angelica/compare/996fb54c...fbfcafc7)

本地对照为 Actinium `72449152ecaf19a497f92c88e16998b00980e778`，目标为 Minecraft 1.12.2。前序记录见 [8e099e95 → 996fb54c 台账](sync-angelica-996fb54c.md)。

## 逐提交处置

| 提交 | 日期 | 处置 | 上游改动与本地对照 |
| --- | --- | --- | --- |
| [`eff66975`，#2330](https://github.com/GTNewHorizons/Angelica/commit/eff6697583b59f579bf23ca50b2b9504865fa240) | 2026-11-22 | 待移植 | 上游 `TileEntityDepthOrder` 在 TESR 分派前按观察位置从远到近排序。本地 `SimpleWorldRenderer.renderBlockEntities` 依次遍历裁剪后的方块实体和全局方块实体，`ActiniumWorldRenderer` 按 Forge pass 过滤。移植时保留 pass 过滤，验证来自两类列表的重叠 TESR 随视角变化正确排序。现有 `TranslucentSorter` 排序的是地形 quad。 |
| [`ef8648cd`，#2331](https://github.com/GTNewHorizons/Angelica/commit/ef8648cdc06a85c793cad1874d6cbdacf3e09778) | 2026-11-22 | 待移植，接续 #2330 | 上游把 Forge pass 1 的 TESR 提前到半透明地形之前，并在原入口跳过已绘制的对象。本地从 `MixinRenderGlobal` 的 `renderEntities` 入口调度 TESR，`EntityRendererIrisMixin` 在 `renderBlockLayer` 调用周围设置 Iris 阶段。按 1.12.2 调用顺序选择注入点，验证主视图每个 TESR 只绘制一次，阴影绘制保持独立，后续地形取得正确的阶段和状态。 |
| [`586cce1c`，#2332](https://github.com/GTNewHorizons/Angelica/commit/586cce1c40541deb3b4de0158f105eae61ae8c37) | 2026-11-22 | 待移植，接续 #2331 | 上游引入 `Off/Fast/Fancy`、预乘 alpha 混合、分层 framebuffer 和按深度合成，接入地形、TESR、粒子、天气与云。`Fancy` 进一步拆分位于方块实体后方的地形。本地 `VintageTranslucentMaterials` 和 Iris 半透明深度路径可供对照。实现前按本地架构明确 OpenGL、SDL GPU、Iris 与 LWJGL2 路径的 framebuffer、program、纹理单元、视口及混合和深度状态归属。 |
| [`1758f3be`，#2333](https://github.com/GTNewHorizons/Angelica/commit/1758f3be1a543f253159bda6cf8ec7f7c15eee87) | 2026-11-23 | 待移植，依赖 #2332 | 上游把半透明 TESR 重放到 Glow Depth，供发光片元与半透明地形比较深度，支持 TESR 退出重放，并剔除对颜色无贡献的片元。以本地 TESR 批次和分层目标为接入点，验证退出规则以及重放期间粒子和声音的副作用。 |
| [`2658ac58`，#2352](https://github.com/GTNewHorizons/Angelica/commit/2658ac584e2c22d4f842f5903737ae91ead13c5f) | 2026-11-23 | 待移植，接续 #2333 | 上游给 GLSM 和 FFP 加入 depth peeling，按层绘制半透明 TESR 批次，并在启用混合的立即绘制前提交待处理批次。本地已有 `BatchStateGuard`，后续适配须围绕现有批次边界检查提交顺序，并单独验证重叠表面、立即绘制以及状态恢复。 |
| [`fbfcafc7`，#2376](https://github.com/GTNewHorizons/Angelica/commit/fbfcafc7b579b9054ddaca1e9888ba244bf4549d) | 2026-11-23 | 本地已有等价快照，GTNH 补丁不适用 | 上游为 1.7.10 GTNH LiquidLogged 的 `Fluid[]` 快照补充逐格流体等级，并经 `WorldSlice` 跨区段查询。Actinium 支持 1.12.2 JBredwards Fluidlogged API，现有快照保留含 `BlockLiquid.LEVEL` 的 `IBlockState`，无需另存等级或引入 GTNH LiquidLogged。 |

## #2376 流体等级核对

Angelica 在 `ClonedChunkSection.copyLiquidLoggedFluids` 复制 `Fluid[]` 时补读 `LiquidLoggedBlockAccess.liquidlogged$getFluidLevel`，将等级存入 `byte[]`。`WorldSlice.liquidlogged$getFluidLevel` 据世界坐标选择区段和格位。上游测试覆盖源头、流动等级、跨区段读取和快照边界。[GitHub 提交详情 API](https://api.github.com/repos/GTNewHorizons/Angelica/commits/fbfcafc7b579b9054ddaca1e9888ba244bf4549d)

Actinium 以 `modCompileOnly` 声明 JBredwards Fluidlogged API `3.0.6`。启用该模组时，`ClonedChunkSection.init` 建立 `FluidStateStorage`。构造函数把容器内非空流体的 `fluidState.getState()` 写入区段调色板，`get` 再从同一个 `IBlockState` 调用 `FluidState.of`。对项目缓存中的 `fluidlogged-api-3.0.6.jar` 执行 `javap -p -c`，确认 `FluidState.getLevel()` 从状态的 `BlockLiquid.LEVEL` 读取等级，`FluidState.of(IBlockState)` 保留该值。即使 API 将流体方块转换为相应的 Forge `IFluidBlock` 表示，也会调用 `withLevel` 转移原等级。[依赖声明](../gradle/scripts/dependencies.gradle#L81) · [快照初始化](../src/main/java/com/dhj/actinium/world/cloned/ClonedChunkSection.java#L102) · [流体状态存取](../src/main/java/com/dhj/actinium/compat/fluidlogged/FluidStateStorage.java#L20)

`WorldSlice.copyData` 取各区段解包后的 `FluidState` 数组，`getFluidState` 按区段和局部坐标读取。`ChunkBuilderMeshingTask.renderStoredFluid` 委托 `FluidloggedCompat.renderFluidState`，后者从快照取得流体方块状态，再将其 `getActualState` 结果交给 `BlockRendererDispatcher`。因此现有 JBredwards 路径在快照往返中已保留 `LEVEL`。处置为保留当前依赖与实现。实机仍待核对源头与非零等级的渲染高度、区段接面、主线程延迟绘制和移除流体后的重建。[区段解包](../src/main/java/com/dhj/actinium/world/cloned/ClonedChunkSection.java#L251) · [快照组装](../src/main/java/com/dhj/actinium/world/WorldSlice.java#L227) · [坐标查询](../src/main/java/com/dhj/actinium/world/WorldSlice.java#L527) · [网格构建](../src/main/java/com/dhj/actinium/render/terrain/compile/task/ChunkBuilderMeshingTask.java#L409) · [流体绘制](../src/main/java/com/dhj/actinium/compat/fluidlogged/FluidloggedCompat.java#L30)

## 前序待处理项

- **GLES，#2346 `38762184`。** 点大小查询可单独处理。本地 `GLStateManager.maxPointSize()` 已查询 `GL_ALIASED_POINT_SIZE_RANGE`，但此前仍会查询 `GL_POINT_SIZE_RANGE`。需在 GLES 路径去掉这次桌面查询，并在真实 ES 上下文验证。完整原生 GLES 另行跟进，覆盖 LWJGL3 的 ES 函数入口、proxy texture 与 shader 转译。SDL 已有的 proxy texture 查询继续复用。[本地点大小查询](../glsm/src/main/java/com/gtnewhorizons/angelica/glsm/GLStateManager.java#L1048) · [上游 #2346](https://github.com/GTNewHorizons/Angelica/pull/2346)
- **Clip distance，#2348 `9493d630`。** 待移植模拟路径。本地已有原生能力判定，后续需接入实际平面数、FFP 变体和片元裁剪、Iris 变体缓存以及 SDL 能力与 uniform 更新，保留 MobileGlues 判定。验证启停、属性栈、显示列表和 Iris 适用变体。[本地能力判定](../glsm/src/main/java/com/gtnewhorizons/angelica/glsm/RenderSystem.java#L433) · [上游 #2348](https://github.com/GTNewHorizons/Angelica/pull/2348)
- **帧图捕获和调试标记，#2326 `e46eb463`、#2351 `d69fd20a`。** Tracy 帧图等待原生采集后端接入。`CaptureGate` 已能通过环境变量和 Linux 进程映射识别部分工具。`GL_EXT_debug_tool` 探测与帧标记版本信息可独立评估。[本地工具探测](../glsm/src/main/java/com/gtnewhorizons/angelica/glsm/CaptureGate.java) · [上游 #2326](https://github.com/GTNewHorizons/Angelica/pull/2326) · [上游 #2351](https://github.com/GTNewHorizons/Angelica/pull/2351)
- **SDL 读回，#2326 `e46eb463`。** `ReadbackShadows` 已有 transfer buffer、`serve` 和失效入口，槽内记录容量与有效标志。独立评估上游的逐槽 fence、有效字节长度以及写入和释放时的失效处理。验证同一槽从大范围改为小范围读回、改写和重分配，以及提交完成前后的读取行为。[本地 SDL 读回](../sdl-gpu/src/main/java/com/gtnewhorizons/angelica/sdlgpu/resource/ReadbackShadows.java) · [上游 #2326](https://github.com/GTNewHorizons/Angelica/pull/2326)

## 天气实例化与模组 API

`feat/angelica-weather-sync` 以 `72449152e` 为基线，适配上游 [`f2c07dea`，#2127](https://github.com/GTNewHorizons/Angelica/pull/2127) 和 [`996fb54c`，#2364](https://github.com/GTNewHorizons/Angelica/pull/2364) 中的天气改动。原版雨雪共用滚动更新的列缓存，分别按雨纹理和雪纹理提交；自定义降水按 provider 提供的纹理层顺序提交，使用独立的 tick、颜色与衰减半径。实例布局为 80 字节，FFP 和 Iris 共用顶点展开逻辑。

1.12.2 通过 `WorldProvider` 上已注册的 `IRenderHandler` 暴露 `WeatherColumnProvider`。原版天气粒子与声音沿用现有路径。未注册实例化 provider 的 handler、Dynamic Surroundings、嵌套世界绘制和缺少实例化能力或 Iris 变体的场景均由原 `renderRainSnow` 处理。三角带在 SDL GPU 与 OpenGL 共用。Cloud Renderer、Flyby 和 1.7.10 NotFine 树叶兼容另行处理。

`check build --no-daemon` 已通过，包括发行 Jar 的 Forge 启动与 shader 依赖检查。`WeatherColumnsTest` 和 `WeatherColumnBatchTest` 共 22 项通过，覆盖原版列缓存、自定义缓存失效和实例数据分层。LWJGL3 OpenGL 上 `WeatherInstancingGLTest` 14 项通过，包含 FFP、Iris、光照与颜色像素读回。

2026 年 11 月 24 日在 Minecraft 1.12.2、Forge 14.23.5.2864、Outlands 2026.11.22、SDL GPU（Vulkan）实例中测试原版雨天。「渲染计时 DEBUG」的 `[Weather]` 日志记录无光影 FFP 23,002 个 pass，Iris 20,338 个 pass。每个雨天 pass 提交一次实例化绘制，包含 441 列。晴天记录零次降水绘制。MakeUp Ultra Fast 9.4c、BSL v10.1p1 和 Derivative Main d24.4.14 均有 Iris 天气提交记录，Revelation-dev 记录到加载完成。

后续实机对照：

- 无光影和有 `gbuffers_weather` 的光影包各开关一次天气实例化，在雨雪交界处核对纹理滚动方向、光照、范围和透明度。
- 用实现 `WeatherColumnProvider` 的 Forge handler 提交至少两个纹理层，分别保留和替换原版降水。在无雨环境核对自定义列，检查关闭实例化或缺少可用变体时原 handler 每个 pass 只被调用一次。
- 在 SDL GPU、LWJGL2 与常用 LWJGL3 启动路径核对降水列完整。切换维度、退出世界重进，并在红蓝立体模式下核对两眼使用同一次 provider 更新。
