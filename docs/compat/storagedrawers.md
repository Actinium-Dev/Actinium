# Storage Drawers 兼容性说明

兼容状态：**已验证**（抽屉内方块物品恢复正常光照）

最后更新：2026-11-25

## 验证范围

- Minecraft 1.12.2、Cleanroom Loader 0.6.12-alpha
- Storage Drawers 5.5.3、Chameleon 4.1.3
- 无光影；使用 Grass 与 Grass Block 放入 Basic Drawer 复现
- 相关 issue：[#118](https://github.com/DHJComical/Actinium/issues/118)

## 症状与根因

抽屉正面显示的方块物品光照不均，创造模式背包中的同一物品正常。问题来自 Storage Drawers 的
`TileEntityDrawersRenderer.renderFastItem`：它在抽屉局部缩放和旋转矩阵仍生效时调用
`RenderHelper.enableStandardItemLighting()`，之后才恢复矩阵并绘制物品。

OpenGL 在设置方向光时会用当时的 model-view 矩阵变换灯光方向。弹出抽屉局部矩阵不会撤销已保存的灯光方向，
所以物品绘制沿用了受抽屉变换影响的光照；创造背包在不同矩阵上下文设置标准物品灯光，因此不受影响。

## 修复

由 `mixins.actinium.storagedrawers.json` 在 `storagedrawers` 模组存在时加载兼容 Mixin。Mixin 保存
进入 `renderFastItem` 时的 model-view 矩阵；Storage Drawers 设置标准物品灯光时，Actinium 临时恢复该矩阵，
然后再恢复抽屉的渲染矩阵。抽屉中绘制的物品继续使用原有变换，方向光使用视图空间方向。

## 验证记录

- `./gradlew check --no-daemon` 通过。
- 用户实机确认：在无光影的 issue #118 场景中，Basic Drawer 内 Grass 与 Grass Block 的光照恢复正常；
  创造模式背包物品显示正常。

## 压扁图标的方向光

Storage Drawers 5.5.3 在抽屉正面绘制物品时大幅压缩模型深度。直接用压扁后的模型矩阵变换法线会改变各面的方向光，开启或关闭光影都能出现明暗异常。

Actinium 在共用 TESR 绘制路径中提取模型的旋转和镜像变换，按该朝向计算方向光并写入顶点颜色。图标几何继续使用原矩阵。开启光影时 Iris 接收修改后的顶点颜色。关闭光影时，绘制这批顶点期间关闭固定管线光照，避免重复计算。需要按当前视角更新颜色的图标跳过物品显示列表和实例化缓存。

2026 年 11 月 25 日，用户确认 Cleanroom 的抽屉场景在关闭光影和开启 MakeUp Ultra Fast 9.4c 时均恢复正常。`check build --no-daemon` 和 `FlattenedItemLightingGLTest` 的三项测试通过，覆盖压扁判定、旋转模型压扁前后的面光照一致性及顶点透明度保留。
