# Modular Machinery CE 兼容说明

MMCE 的 GeckoLib 模型在机器成型后由 `MachineControllerRenderer` 将顶点放入队列。MMCE 在原版
`RenderGlobal.renderEntities` 的 `TileEntityRendererDispatcher.drawBatch` 调用之后提交这批顶点。Actinium
替换了这段方块实体渲染流程，MMCE 的回调没有执行，导致成型模型缺失。[Issue #326](https://github.com/Actinium-Dev/Actinium/issues/326)

Actinium 在主视图的 Forge pass 0 中，在关闭光照贴图前调用 `ControllerModelRenderManager.draw()`。
启用光影时，批次由 Iris 的方块实体阶段处理。CurseForge 版启用原生泛光时，由 MMCE 的泛光回调执行
`checkControllerState()`。GreedyCraft2-T 版和 CurseForge 版的非原生泛光路径在主批次绘制后清理。

MMCE 的 `TRANSPARENT` 和 `BLOOM_TRANSPARENT` 两组依赖调用方提供 alpha 测试与混合状态。基因构造器的
玻璃罩贴图 alpha 为 `24/255`，继承 `GL_GREATER 0.1` 时会被裁剪。兼容层将这两组声明为半透明，
用 `GL_GREATER 0` 保留非零 alpha 的片元，并使用标准 alpha 混合。组绘制结束后恢复 alpha 测试与混合状态。

光影场景的兼容范围包含机器本体和半透明部件。机器的原生泛光在此场景暂不适配。

## 验证

- 2026-11-22，用户在 GreedyCraft2-T 中使用 Cleanroom 0.6.13-alpha、MMCE 2.3.2、GeckoLib 3.0.31 和
  Complementary Shaders v4.7.2，确认成型基因构造器的机器本体与半透明玻璃罩恢复显示。
- 使用 Mixin 0.8.7 转换两份 MMCE 2.3.2 的目标类。CurseForge 版在 `draw()` 命中一次绘制注入，
  GreedyCraft2-T 版在 `drawBuffers()` 命中一次，两份包的构造器均命中一次注册注入。
