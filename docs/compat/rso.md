# Reese’s Sodium Options 图标渲染

2026-11-28，基于 Actinium `f0a3dd5d7`。

撤回和恢复默认按钮原先使用 10×10 PNG。GUI 放大时，同一张位图被拉伸到更大的物理像素尺寸，导致轮廓模糊；改变窗口分辨率仍会使用同一份低分辨率图像。

两个按钮改用 SVG，填充色沿用原图标的橙色 `#FF8D30`。图标保持 10×10 GUI 单位，并按 `ScaledResolution.getScaleFactor()` 计算物理像素尺寸。`SvgImage` 保留解析后的矢量文档，缓存最近一次尺寸的栅格图；`SvgIcon` 通过 Minecraft 纹理管理器共享 GPU 纹理，尺寸变化时重新栅格化，资源重载时重新读取 SVG。禁用按钮继续使用 40% 不透明度。

JSVG 随发行包重定位到 `com.dhj.actinium.shadow.jsvg`。图标来自 Google Material Design Icons，依赖与图标的许可证包含在发行包中。

验证记录：

- `SvgImageTest` 的 8 项测试通过，覆盖颜色与透明度、抗锯齿、细线的分辨率变化、尺寸缓存、非法输入及两张实际图标的多尺寸绘制。
- `./gradlew check build --no-daemon --console=plain` 通过，发行包检查包含 SVG、重定位后的 JSVG 类与许可证。
- 从发行包加载两张 SVG 并生成 40×40 图像，完全不透明像素保持原图标颜色。
- 构建产物已安装到 `crl_t` 实例，实机回归场景为 GUI 倍率切换、按钮可用与禁用状态，以及资源重载。
