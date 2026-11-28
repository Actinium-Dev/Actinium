# The Betweenlands

## 启动时 GeometryBuffer 纹理注册崩溃（#235）

- 问题：[issue #235](https://github.com/Q-Engineering-Source/Actinium/issues/235)。
- 报告环境：Actinium alpha-0.0.13、The Betweenlands 3.7.3、Minecraft 1.12.2、
  Cleanroom 0.6.13-alpha、Java 25.0.4.1、Windows 11、NVIDIA RTX 4090 Laptop。
- 触发条件：客户端 tick 初始化 Betweenlands 天空渲染资源；`use_shader=false` 仍会触发。
- 状态：代码修复和自动化验证完成，实机启动及渲染回归待确认。

### 根因与触发链

Betweenlands 3.7.3 的 `GeometryBuffer` 构造器调用 `TextureManager.loadTexture(name, this)`
注册自身，但其 `loadTexture` 是空实现。真正的 framebuffer 在后续
`updateGeometryBuffer` 中分配；在此之前，覆盖的 `getGlTextureId()` 返回 `-1`。
此生命周期可在对应版本的
[官方源码](https://github.com/Angry-Pixel/The-Betweenlands/blob/v3.7.3-release/src/main/java/thebetweenlands/client/render/shader/GeometryBuffer.java)
中核对。

Actinium 的 `MixinTextureManagerDebugLabels` 在注册返回时给纹理添加 GL 调试标签，
原先无条件绑定 `getGlTextureId()` 的结果。alpha-0.0.13 中，提交 `8c137889` 为防止
非 2D 纹理被延迟删除逻辑错误缩减，在 `GLStateManager.glBindTexture` 中新增了纹理
target 记录。`TextureInfoCache.getInfo(-1)` 返回 `null`，紧接着的 `setTarget` 因此抛出 NPE。

### 修复

调试标签实现移至 Mixin 包外的 `TextureDebugLabels`。当纹理 owner 返回负数 ID 时，
跳过绑定和命名操作，保持 framebuffer 的分配归属；有效 ID 继续使用原标签流程。
保留无 GL context、display-list 录制和缺失纹理门控，并在命名后通过 `finally`
恢复原纹理绑定。GLSM 的 target 跟踪与缓存语义保持原样。

### 验证记录

- 基于 `main` 的 `b5f92339`，使用 Java 25 工具链。
- `TextureDebugLabelsTest` 直接调用生产 helper，以真实的
  `TextureInfoCache.getInfo(id).setTarget(...)` 重现同一 NPE；同时验证
  `-1 -> 42 -> -1 -> 73` 的附件分配、释放和重建过程。
- 未加负 ID 判断时，两项新增测试均失败；加入修复后均通过。
- `./gradlew check build --no-daemon` 通过：785 项测试，0 失败、4 跳过；
  发布 Jar 结构校验通过。
- 未运行 `runClient`。待实机检查：Cleanroom 0.6.13-alpha + Betweenlands 3.7.3，
  分别使用 `use_shader=false` 和 `true` 启动到主菜单、进入 Betweenlands 维度并检查天空，
  再检查窗口尺寸改变或资源重载后的画面。自动化测试未覆盖真实 GL 绑定恢复与画面表现。

## 陷阱在阴影中首次绘制模型时崩溃（#375）

[报告 #375][375-issue] 使用 Minecraft 1.12.2、Actinium `beta-0.1.0-54b7d9f3`、The Betweenlands 3.9.6、Cleanroom `0.6.13-alpha`（报告内列出的 Forge 为 `14.23.5.2864`），光影为 `ComplementaryUnbound_r5.10 + EuphoriaPatches_1.11.2`。在[陷阱崩溃报告][375-trap-log]中，坐标 `(-1498, 109, 4800)` 的泥砖陷阱在阴影视图绘制面纹时抛出 `IllegalStateException: Already building!`；预期是覆盖层、面纹与活动尖刺各用自己的材质绘制，并按光影包规则参与投影。同一 issue 的[另一份报告][375-beam-log]来自 Thaumcraft 的 `FXBeamBore.renderParticle`，走粒子管线。

采用阴影视图按 `hasFastRenderer()` 分派、逐个方块实体提交的处理。它把陷阱的方块顶点与嵌套模型分给两个缓冲，并给每次提交保留明确的 `blockEntityId`。直接给整个阴影循环加一对 `preDrawBatch()` / `drawBatch(0)` 可以避开报告中的第二次 `begin`，但会把不同 ID 的覆盖层留到同一次绘制，并改变覆盖层与相邻模型的次序。以下触发链以 [3.9.6 上游 tag][375-bl-tag]（`eafac1c2`）、[Cleanroom 0.6.13-alpha][375-cr-dispatch]（`6bc8dd7b`）和[报告所用 Actinium 提交][375-act]为准。

### 触发链与材质

[客户端注册][375-register]分别绑定普通、泥砖和泥瓦三种陷阱。它们的方块实体均使用 [`TileEntitySpikeTrap.hasFastRenderer()`][375-bl-tile] 的 `true`，渲染器继承 `TileEntitySpecialRenderer`。`RenderSpikeTrap.render()` 自己实现了与 [Forge `FastTESR.render()`][375-fast-base] 相同的缓冲生命周期：绑定方块图集，在 `Tessellator.getInstance().getBuffer()` 上 `begin(GL_QUADS, BLOCK)`，虚调用 `renderTileEntityFast(...)`，归零顶点平移，再 `draw()`。按 `instanceof FastTESR` 选择入口会漏掉这三个渲染器。[基类普通入口][375-bl-normal]、[泥瓦构造器][375-bl-tiles]。

| 几何 | 条件与数据来源 | 回调里的实际次序和状态 |
| --- | --- | --- |
| 方块覆盖层 | `type != 0`，根据 `FACING` 选择预烘焙的 `overlay=true` 模型 | 最先设置传入缓冲的平移并写入方块图集顶点；此处没有 `draw()`。[覆盖层源码][375-bl-overlay] |
| 尖刺 | `animationTicks > 0`，`ModelSpikeBlock` 插值位移 | 写完覆盖层后，读取朝向相邻方块的光照，绑定独立的尖刺纹理，设置颜色、混合因子、朝向矩阵和剔除状态，再逐个渲染模型部件。普通陷阱用 `spikes_1`，泥砖和泥瓦用 `spikes_2`。[尖刺绘制][375-bl-spikes]、[尖刺模型][375-bl-model] |
| 泥砖面纹 | 子类先调 `super`，再检查 `spoopAnimationTicks > 0` | 绑定 `mud_brick_spike_block_spoop_layer.png`，用插值透明度和 `SRC_ALPHA / ONE_MINUS_SRC_ALPHA` 绘制 `face`，随后关闭混合。该动画可在尖刺收回时单独发生。[泥砖绘制][375-bl-brick]、[面纹模型][375-bl-face]、[动画更新][375-bl-tick] |

因此，调用次序是**写覆盖层顶点、调用尖刺模型、调用面纹模型、最后提交覆盖层**。普通入口仅在 `begin` 前绑定图集；模型可以在外层 `draw()` 前留下尖刺或面纹纹理、当前颜色和新的混合状态。只给模型换一个缓冲虽能消除两次 `begin` 的冲突，仍须解决外层提交时用哪张纹理、以什么状态绘制。Forge 批入口则会在最后提交时重新绑定图集。[普通入口][375-bl-normal]、[泥砖绘制][375-bl-brick]、[dispatcher][375-cr-dispatch]。

本次报告的 NBT 是 `type:1b, animationTicks:0, active:0b`。堆栈从 `ShadowRenderer.renderTileEntity` 经 dispatcher 的**普通** `render` 进入 `RenderSpikeTrap.renderFast`，随后从 `RenderMudBrickSpikeTrap.renderTileEntityFast:81` 进入 `ModelDungeonSpoopLayer.renderSpoop:28`。此时全局缓冲已在写覆盖层。Actinium 的 [`ModelRendererIrisMixin`][375-model] 首次编译该部件的显示列表，又在同一个缓冲上 `begin(GL_QUADS, OLDMODEL_POSITION_TEX_NORMAL)`，正好命中异常。列表缓存命中会绕开首次编译；关闭显示列表时，模型的直接绘制分支也使用全局缓冲，不能用热缓存或单一配置验收。[陷阱崩溃报告][375-trap-log]。

### Forge 分派与 Actinium 两个视图

[Forge 1.12.x 的补丁][375-forge-dispatch]和[对应 Cleanroom 实现][375-cr-dispatch]均由 dispatcher 持有独立的 `new Tessellator(0x200000)`。`preDrawBatch()` 在**这个私有缓冲**上开始 `QUADS/BLOCK` 并设置 `drawingBatch = true`。只有 `drawingBatch && te.hasFastRenderer()` 才调用带缓冲的 `renderTileEntityFast`；其余情况调用普通 `render`。世界坐标入口还会在 fast 批中跳过普通的逐 TE 光照初始化。[Forge 对 fast 的说明][375-cr-tile]要求渲染器能与其他对象合批且不操作 GL；陷阱同时写缓冲、绑定纹理和绘制独立模型，超出了这一要求。

`drawBatch(pass)` 在**提交时**绑定方块图集、关闭标准物品光照、启用普通透明混合、关闭面剔除并按 AO 选择着色模式；仅 `pass > 0` 时按 `ActiveRenderInfo` 相机坐标排序四边形。绘制后开启标准物品光照并清除 `drawingBatch`。它没有为每个 TE 记录材质或 ID，也没有重置各 TE 留下的顶点平移。[Cleanroom `RenderGlobal`][375-cr-global] 在每个 Forge pass 的区块 TE 与额外 `setTileEntities` 两段遍历之前开批，在两段之后、破坏覆盖层之前提交；`hasFastRenderer() == false` 的 TE 即使位于两者之间，仍正常调用普通入口。

- **主视图：** [`ActiniumWorldRenderer`][375-main] 在方块实体阶段保留上述 Forge 分派，按 `shouldRenderInPass(pass)` 遍历。`finally` 中先 `TileEntityGlStateGuard.restoreForBatch()` 恢复入口处跟踪的属性，再 `drawBatch(pass)`，随后结束自有待绘制任务和模型批。额外的 [`setTileEntities`][375-extra] 有独立的一对开批与提交。陷阱写的是 dispatcher 缓冲，嵌套模型可以使用全局缓冲。三种陷阱继承默认的 pass 0；主视图 pass 1 仍要用其他 FastTESR 做排序回归。[状态恢复][375-guard]、[默认 pass][375-cr-tile]。
- **阴影视图：** [`ShadowRenderer.renderTileEntities`][375-shadow-loop] 在阴影矩阵和 `BLOCK_ENTITIES` 阶段内分别遍历 `visibleTileEntities`、`globalTileEntities`，保留距离和仅光源等筛选，对每个 TE 设光照和白色再调用 dispatcher。这里没有 `preDrawBatch()`，正常进入时 `drawingBatch` 为 `false`。[`MixinShadowRendererTesr`][375-shadow-mixin] 包住整个方法；[`TesrBrackets.shadowBlockEntities`][375-brackets] 开的是 Actinium 的 `PASS_SHADOW`、`ModelPartBatcher` 和 `PendingTesrDraws`。它们的结束和提交在原方法返回之后，此时原方法已结束 `BLOCK_ENTITIES` 并恢复矩阵。把 Forge 提交加在这个外层 `finally` 会错过原来的绘制状态。
- **两种入口上的 Mixin：** [`MixinTileEntityRendererDispatcherTesr`][375-dispatch-mixin] 将普通调用交给 `TesrBrackets.renderTileEntity`（含 provider、批处理资格和 glow 跟踪）；fast 调用只做统计，并经 [`TesrGlowReplay.drawImmediate`][375-glow] 原样调用带缓冲的回调。这里的 `drawImmediate` 控制 glow 跟踪，不会提交传入的顶点。[`MixinTileEntityRendererDispatcherBatch`][375-batch-mixin] 只在 `drawBatch` 内防止再次提交已结束的缓冲；报告中的异常发生在模型的 `begin`，早于该检查。

### 方案比较与采用条件

| 方案 | 能解决什么 | 采用条件或限制 |
| --- | --- | --- |
| 在阴影的两个 TE 列表外开一个 Forge 批 | 使所有声明 fast 的 TE 使用私有缓冲，保留普通 TESR 分派；纯图集顶点可合并提交 | `preDrawBatch()` 应在阴影矩阵和方块实体阶段建立后、首个 TE 前；`drawBatch(0)` 应在第二个列表后、`endBlockEntities()` 和矩阵恢复前。`0` 表示不做主相机透明排序。整批把所有图集顶点推到独立模型之后；还须为私有缓冲记录每段 ID，当前不宜直接采用。 |
| 阴影按 TE 开批并提交（已采用，待实测） | 由 dispatcher 按 TE 的能力选择普通或 fast，保留两条调用上的 Mixin。fast 回调写独立缓冲，尖刺、面纹仍可使用全局缓冲；图集顶点随本 TE 的 ID 提交 | 在选中 fast 回调后建立批次，在本 TE 的 ID 作用域内恢复图集绘制状态并 `drawBatch(0)`。只在 dispatcher 返回之后包一个 `drawBatch` 会读到已清零的 ID。提交前须处理尚未实际绘制的模型，归零缓冲平移并隔离下一 TE 的状态。逐 TE 提交限制跨对象合批，须用相邻透明几何对照。 |
| 保留普通入口，只隔离 `ModelRenderer` 的嵌套构建 | 显示列表编译及直接绘制都使用模型自有缓冲时，可以避开同一 builder 上的重复 `begin` | 模组外层仍按回调结束时的材质绘制，其他自行调用全局缓冲的渲染器也不受保护。全局遇到 `Already building!` 就提前 `draw`、丢弃顶点或重试，还会提前提交半批数据或重复绘制有副作用的模型。 |
| 上游拆开图集几何与独立模型 | 模组知道覆盖层、尖刺、面纹各自的材质，可明确安排顺序：纯 fast 回调只追加顶点；有独立模型时由普通路径在清楚的材质与绘制边界分别提交 | 需要上游修改能力声明及普通入口，覆盖三种变体。Actinium 内按类名、注册名或模组 ID 重写 `renderFast` 会复制该版本的私有实现；按 `FastTESR` 基类筛选又会漏掉本例。 |

**逐 TE 提交仍有三个实现前提：**

1. **ID 必须跟随实际 draw。** [dispatcher 的 Iris Mixin][375-id] 在坐标重载入口按方块、metadata 和 NBT 解析 `blockEntityId`，正常返回时清零。光影程序可把这个值从 `iris_Entity.y` 读入；[`TessellatorEntityIds.onIdsChanged`][375-id-runs] 只跟踪 `Tessellator.getInstance()` 的顶点范围，不能给 dispatcher 私有缓冲补回 ID。整批方案需按实际 builder 保存每段 ID，并保证顶点重排时 ID 一起移动；逐 TE 方案需在延迟批消费几何时仍能取回提交者的 ID。`GbufferPrograms.setBlockEntityDefaults()` 设置的 `mc_Entity` 等默认属性不能代替它。[ID 变换][375-id-shader]、[阶段默认属性][375-defaults]。
2. **按真正的 GL 绘制时刻核对顺序和目标。** [`TessellatorDrawRouter`][375-router] 可以把 `draw()` 交给 `ModelPartBatcher`；Java 回调先后不足以确定 GPU 提交顺序。覆盖层要绑定方块图集，尖刺和面纹保留各自纹理、光照、矩阵和透明度。新提交必须位于当前 shadow program 对应的 draw FBO、draw buffers、阴影 viewport 和矩阵有效期间，并早于 `copyPreTranslucentDepth()`；该复制之后的几何会进入不同的深度或有色阴影阶段。[阴影目标与深度复制][375-shadow-order]、[管线选择阴影 FBO][375-fbo]。`BLOCK` 顶点与模型顶点布局不同，须让现有上传器绑定匹配的 VAO/VBO/EBO，并核对下一次绘制所需的绑定、纹理单元、alpha、混合、剔除与深度写入。流式上传器会恢复保存的 VAO，非流式 `drawRaw` 结束时绑定 VAO 0；[主视图属性保护][375-guard]不能代替对两条实际绘制路径的检查。[流式 VAO][375-vao-stream]、[非流式 VAO][375-vao-plain]。
3. **区分回调入口和提交后的状态。** `preDrawBatch()` 只开启缓冲。切到 fast 后会跳过模组普通入口的 `enableBlend()`，尖刺仅设混合因子，面纹则自己启停混合；`drawBatch(0)` 最后设置的状态对先前的模型无效。原型须对照主视图确定回调入口状态，并在提交后恢复调用方状态。dispatcher 只有一个 `drawingBatch` 标志：遇到已开启的外层批次，禁止再次 `preDrawBatch()` 或代外层提交。还须区分正常提交与异常清理，避免把半个 TE 的顶点送进下一次绘制。[普通入口][375-bl-normal]、[尖刺绘制][375-bl-spikes]、[面纹模型][375-bl-face]、[dispatcher][375-cr-dispatch]。

### 实现：逐个提交阴影 FastTESR（待实机验证）

保留 `ShadowRenderer` 的可见性、距离、仅光源筛选和两份方块实体列表。只改变通过筛选后的单次阴影分派：普通 TESR 沿用已有调用；声明 `hasFastRenderer()` 的方块实体使用 Forge 的 `renderTileEntityFast`，图集顶点在本次分派返回前提交。三种陷阱和其他模组共用这条规则，实现中不引用交错次元的类或注册名。

1. **标记阴影调用。** 在根项目的 `MixinShadowRendererTesr` 包住 `ShadowRenderer.renderTileEntity` 内的 `TileEntityRendererDispatcher.render` 调用，将本次阴影分派的范围限制到该调用及其嵌套渲染。方块实体的位置、光照、`partialTicks`、破坏阶段和透明度继续由原调用链传递。`shader` 子项目不依赖根项目实现；主视图和其他直接调用 dispatcher 的入口不设这个标记。
2. **让 Forge 选择真实回调。** 在阴影范围内，将七参数 dispatcher 重载读取的 `drawingBatch` 视为已开启，沿 Forge 的判断查询一次 `hasFastRenderer()`。找到渲染器且能力判断通过后，fast 调用点才借用独立的 `Tessellator`，归零平移并调用 `preDrawBatch()`，将 `QUADS/BLOCK` 缓冲交给原回调。普通 TESR 和没有渲染器的方块实体无需分配或开启批次。`actinium_at.cfg` 开放 Forge 的 `batchBuffer`、`drawingBatch` 字段，调用结束时直接恢复父缓冲和开批标志。嵌套 fast 调用复用下一深度的缓冲，父缓冲中的顶点和平移保留到父回调结束。
3. **在当前 ID 有效时提交。** `TileEntityRendererDispatcherIrisMixin` 包住七参数重载，调用 `ShadowFastTesr.withBlockEntity` 设置 NBT 优先 ID。原 fast `@WrapOperation` 调用 `ShadowFastTesr.render`，保留 `WorldRenderInventory` 的单次计数、`TesrGlowReplay` 规则和 `TesrMeshProvider` 优先级。provider 接受后直接绘制；拒绝时调用原 fast 回调一次。模型先提交，再恢复阴影阶段、ID 和入口状态，将活动纹理切回单元 0，归零覆盖层平移。覆盖层有顶点时调用 `drawBatch(0)` 一次，沿用 Forge 的图集绑定和绘制状态；零顶点时关闭空批，连同图集绑定和绘制状态切换一起跳过。提交在 `endBlockEntities()` 与 `copyPreTranslucentDepth()` 之前完成。
4. **让实际绘制完成后再换方块实体。** `ShadowTesrDraws` 分别检查模型和 TESR 队列，只提交有待绘制几何的队列。保留的实例通过既有的 `declareUse()` 进入同一待绘制层记录，两种队列提交后继续当前阴影 pass，空队列跳过批次结束和重建。阴影中的 `PendingTesrDraws.canDefer()` 返回 `false`，已有任务在换 ID 前提交。`TessellatorDrawRouter` 仅在本次 `drawBatch(0)` 范围内让指定 builder 直接上传，覆盖层不会再次进入延迟模型队列。进入子分派前先提交父模型，父图集顶点继续保留。缓冲和纹理保存对象按实际嵌套深度复用。
5. **分别处理成功和异常退出。** 调用开始时保存阴影目标、program、viewport、矩阵、顶点输入、活动纹理及影响绘制的混合、深度、光照与剔除状态。模组的独立模型使用回调指定的材质；提交图集前必须恢复阴影入口状态，再由 Forge 设置图集所需状态。`finally` 恢复父缓冲、开批标志、父 ID 和 GL 状态。回调或上传抛出异常时，只丢弃本次尚未提交的顶点，保留原异常和方块实体崩溃上下文，禁止在 `finally` 提交半批数据、重试回调或清理父批次。缓冲只随实际嵌套深度增长；客户端世界卸载或渲染器关闭时释放引用。

阴影分派通过 `CapturedRenderingState.pushCurrentBlockEntity()` 保存父 ID，并在 `finally` 调用 `popCurrentBlockEntity()`。`TesrProviderDispatch.tryRender` 在阴影范围沿用 dispatcher 已解析的 ID，避免 metadata 匹配覆盖 NBT 匹配。两个阴影列表都经同一个分派入口处理待绘制任务。标准光照在 fast 回调前准备，回调自定的材质只作用于独立模型；图集绘制和退出分别恢复入口状态。

**自动化验证记录（2026-11-28）：**

- `:test --tests '*ShadowFastTesrIntegrationTest' --no-daemon`：17 个场景通过。无窗口 JVM 加载生产 dispatcher、BufferBuilder 和相关 Mixin。测试在普通入口复现全局缓冲重复 `begin` 的 `Already building!`，再对同一个测试渲染器运行阴影 fast 分派，验证模型缓冲与图集缓冲分离。覆盖能力查询与回调次数、空批、主视图批次、已有父批次、父子嵌套、NBT ID、provider 接受与拒绝、回调及上传异常、异常后复用和待绘制任务的 ID 边界。
- `glIntegrationTest --tests '*ShadowFastTesrStateGLTest' --tests '*ShadowTesrDrawsGLTest' -PglsmRedirectAgent --no-daemon`：7 个场景通过。使用隐藏 GL 上下文读取颜色、半透明混合结果与深度副本，检查入口 FBO、program、viewport、矩阵、顶点输入、纹理单元和 lightmap 恢复；检查 retained、模型与实例化队列逐段提交、空队列跳过，以及异常后清理和继续绘制。
- 队列断言改用 GPU 的 `GL_PRIMITIVES_GENERATED` 查询后，去掉 `-PglsmRedirectAgent` 重跑上述两类测试，7 个场景通过。直接调用 OpenGL 的 retained 绘制同样进入图元计数，空批与异常丢弃路径没有额外提交。
- 矩阵模式由 `GL_ALL_ATTRIB_BITS` 中的 `GL_TRANSFORM_BIT` 保存和恢复。删除重复状态记录后，再次通过 7 个 GL 场景，其中嵌套异常场景同时检查父 `GL_TEXTURE`、子 `GL_PROJECTION` 模式及 model-view、projection、texture matrix 内容恢复。
- 最终执行 `check build --no-daemon --max-workers=2` 成功，单元测试合计 2065 项，0 失败、5 项跳过。发行包的 AT、Mixin 映射、Forge 加载、着色器依赖及模块边界检查通过。

dispatcher 测试使用合成 TESR，上传 fixture 记录真实 CPU buffer；GL 测试直接执行状态和队列实现。交错次元真实模型的首次显示列表编译、完整阴影世界遍历及光影包画面按下列场景实测。性能优化包括按嵌套深度复用缓冲与状态对象、跳过空图集批、仅提交非空模型队列；密集陷阱场景的 draw 次数、阴影耗时与 FPS 待实机记录。

### 实机验证判据（待验证）

1. 用报告的光影包、补丁和所附预设冷启动，在阴影距离内观察三种陷阱。分别触发仅覆盖层、尖刺伸缩、泥砖仅面纹及尖刺与面纹同时出现；资源重载后重复首次绘制。对照无光影的主视图，检查覆盖层图集、尖刺纹理、面纹淡入淡出和朝向，不能以异常消失代替材质检查。
2. 在相邻位置放置普通 TESR、只追加图集顶点的 FastTESR、两种带不同非零 `blockEntityId` 的方块实体，使模型与透明表面在视线和投影方向重叠。记录各 TE 的普通或 fast 入口、写入顶点数及平移、每次实际 draw 的 ID 和纹理。对照主视图 pass 0、pass 1 与阴影，检查后一个 TE 的位置、层次和光照，排除漏画、重复绘制及 ID 串用。
3. 分别启停模型批处理和显示列表，覆盖首次编译、缓存命中、模型被批处理和直接提交。用帧捕获核对阴影 draw 的 program、FBO 附件、viewport、VAO 属性及深度/混合状态；对比半透明地形前复制的阴影深度与最终阴影纹理，再检查回到主视图后的首个绘制。以关闭方块实体投影、仅光源、移出阴影距离作对照。逐 TE 方案还须记录密集 FastTESR 场景的 draw call 增量和阴影耗时，再决定是否合并连续提交。

[375-issue]: https://github.com/Actinium-Dev/Actinium/issues/375
[375-trap-log]: https://mclo.gs/KkrOSed
[375-beam-log]: https://mclo.gs/1Tqijcr
[375-bl-tag]: https://github.com/Angry-Pixel/The-Betweenlands/tree/v3.9.6-release
[375-act]: https://github.com/Actinium-Dev/Actinium/commit/54b7d9f39bf86724cfd978cee7f999811a4ea694
[375-register]: https://github.com/Angry-Pixel/The-Betweenlands/blob/v3.9.6-release/src/main/java/thebetweenlands/client/proxy/ClientProxy.java#L772-L796
[375-bl-tile]: https://github.com/Angry-Pixel/The-Betweenlands/blob/v3.9.6-release/src/main/java/thebetweenlands/common/tile/TileEntitySpikeTrap.java#L157-L160
[375-bl-normal]: https://github.com/Angry-Pixel/The-Betweenlands/blob/v3.9.6-release/src/main/java/thebetweenlands/client/render/tile/RenderSpikeTrap.java#L39-L107
[375-bl-overlay]: https://github.com/Angry-Pixel/The-Betweenlands/blob/v3.9.6-release/src/main/java/thebetweenlands/client/render/tile/RenderSpikeTrap.java#L163-L189
[375-bl-spikes]: https://github.com/Angry-Pixel/The-Betweenlands/blob/v3.9.6-release/src/main/java/thebetweenlands/client/render/tile/RenderSpikeTrap.java#L109-L160
[375-bl-model]: https://github.com/Angry-Pixel/The-Betweenlands/blob/v3.9.6-release/src/main/java/thebetweenlands/client/render/model/tile/ModelSpikeBlock.java#L63-L79
[375-bl-brick]: https://github.com/Angry-Pixel/The-Betweenlands/blob/v3.9.6-release/src/main/java/thebetweenlands/client/render/tile/RenderMudBrickSpikeTrap.java#L19-L85
[375-bl-tiles]: https://github.com/Angry-Pixel/The-Betweenlands/blob/v3.9.6-release/src/main/java/thebetweenlands/client/render/tile/RenderMudTilesSpikeTrap.java
[375-bl-face]: https://github.com/Angry-Pixel/The-Betweenlands/blob/v3.9.6-release/src/main/java/thebetweenlands/client/render/model/tile/ModelDungeonSpoopLayer.java#L22-L30
[375-bl-tick]: https://github.com/Angry-Pixel/The-Betweenlands/blob/v3.9.6-release/src/main/java/thebetweenlands/common/tile/TileEntityMudBricksSpikeTrap.java
[375-forge-dispatch]: https://github.com/MinecraftForge/MinecraftForge/blob/3effde4f1fc9d14d6ed1dbf6bebc39c2b18780e1/patches/minecraft/net/minecraft/client/renderer/tileentity/TileEntityRendererDispatcher.java.patch
[375-cr-dispatch]: https://github.com/CleanroomMC/Cleanroom/blob/0.6.13-alpha/patches/minecraft/net/minecraft/client/renderer/tileentity/TileEntityRendererDispatcher.java.patch
[375-cr-global]: https://github.com/CleanroomMC/Cleanroom/blob/0.6.13-alpha/patches/minecraft/net/minecraft/client/renderer/RenderGlobal.java.patch#L59-L85
[375-cr-tile]: https://github.com/CleanroomMC/Cleanroom/blob/0.6.13-alpha/patches/minecraft/net/minecraft/tileentity/TileEntity.java.patch#L136-L252
[375-fast-base]: https://github.com/CleanroomMC/Cleanroom/blob/0.6.13-alpha/src/main/java/net/minecraftforge/client/model/animation/FastTESR.java
[375-model]: https://github.com/Actinium-Dev/Actinium/blob/54b7d9f3/src/main/java/com/dhj/actinium/mixin/features/iris/ModelRendererIrisMixin.java#L204-L298
[375-main]: https://github.com/Actinium-Dev/Actinium/blob/54b7d9f3/src/main/java/com/dhj/actinium/render/terrain/ActiniumWorldRenderer.java#L358-L505
[375-extra]: https://github.com/Actinium-Dev/Actinium/blob/54b7d9f3/src/main/java/com/dhj/actinium/mixin/vintage/core/terrain/MixinRenderGlobal.java#L312-L356
[375-guard]: https://github.com/Actinium-Dev/Actinium/blob/54b7d9f3/src/main/java/com/dhj/actinium/render/terrain/TileEntityGlStateGuard.java#L22-L46
[375-shadow-loop]: https://github.com/Actinium-Dev/Actinium/blob/54b7d9f3/shader/src/main/java/net/coderbot/iris/pipeline/ShadowRenderer.java#L680-L770
[375-shadow-mixin]: https://github.com/Actinium-Dev/Actinium/blob/54b7d9f3/src/main/java/com/dhj/actinium/mixin/features/tesr/MixinShadowRendererTesr.java#L14-L21
[375-brackets]: https://github.com/Actinium-Dev/Actinium/blob/54b7d9f3/src/main/java/com/gtnewhorizons/angelica/rendering/tesr/TesrBrackets.java#L129-L141
[375-dispatch-mixin]: https://github.com/Actinium-Dev/Actinium/blob/54b7d9f3/src/main/java/com/dhj/actinium/mixin/features/tesr/MixinTileEntityRendererDispatcherTesr.java#L19-L43
[375-glow]: https://github.com/Actinium-Dev/Actinium/blob/54b7d9f3/src/main/java/com/gtnewhorizons/angelica/rendering/tesr/TesrGlowReplay.java#L76-L87
[375-batch-mixin]: https://github.com/Actinium-Dev/Actinium/blob/54b7d9f3/src/main/java/com/dhj/actinium/mixin/vintage/core/MixinTileEntityRendererDispatcherBatch.java#L20-L34
[375-id]: https://github.com/Actinium-Dev/Actinium/blob/54b7d9f3/src/main/java/com/dhj/actinium/mixin/features/iris/TileEntityRendererDispatcherIrisMixin.java#L18-L79
[375-id-runs]: https://github.com/Actinium-Dev/Actinium/blob/54b7d9f3/glsm/src/main/java/com/gtnewhorizons/angelica/glsm/streaming/TessellatorEntityIds.java#L28-L74
[375-id-shader]: https://github.com/Actinium-Dev/Actinium/blob/54b7d9f3/shader/src/main/java/net/coderbot/iris/pipeline/transform/EntityPatcher.java#L25-L75
[375-defaults]: https://github.com/Actinium-Dev/Actinium/blob/54b7d9f3/shader/src/main/java/net/coderbot/iris/layer/GbufferPrograms.java#L222-L227
[375-router]: https://github.com/Actinium-Dev/Actinium/blob/54b7d9f3/src/main/java/com/dhj/actinium/render/TessellatorDrawRouter.java#L31-L62
[375-shadow-order]: https://github.com/Actinium-Dev/Actinium/blob/54b7d9f3/shader/src/main/java/net/coderbot/iris/pipeline/ShadowRenderer.java#L945-L1008
[375-fbo]: https://github.com/Actinium-Dev/Actinium/blob/54b7d9f3/shader/src/main/java/net/coderbot/iris/pipeline/DeferredWorldRenderingPipeline.java#L1345-L1366
[375-vao-stream]: https://github.com/Actinium-Dev/Actinium/blob/54b7d9f3/src/main/java/com/dhj/actinium/render/BufferBuilderStreamingDrawer.java#L123-L241
[375-vao-plain]: https://github.com/Actinium-Dev/Actinium/blob/54b7d9f3/src/main/java/com/dhj/actinium/render/VanillaBufferBuilderRenderer.java#L62-L108
