# 原生 OpenGL ES 3.2

在支持创建 GLES 上下文的 SDL 窗口宿主上，将图形设置的「渲染后端」设为 OpenGL，并在「OpenGL 上下文」选择 OpenGL ES 3.2，重启后生效。也可以用 JVM 参数 `-Dangelica.glProfile=es` 指定 GLES 3.2。当前启动接入在 Windows 和 Linux 上使用 Outlands 提供的窗口及上下文生命周期接口。驱动需提供原生 GLES 3.2。日志中的 `Created native OpenGL ES context` 会记录实际驱动版本和 renderer。

移植覆盖实际 GLES 函数地址、采样器、实例化属性、纹理尺寸探测、纹理读回，以及固定管线和光影着色器的 ESSL 转译。硬件裁剪距离不足时，固定管线和 Iris 的顶点及片元程序执行模拟裁剪。含几何或细分阶段的 Iris 程序不启用模拟裁剪。

分发环境由启动器提供同一版本的 LWJGL `shaderc` 和 `lwjgl-spvc` 原生库。Outlands `2026.11.22` 的 CI 实例包已包含两者的 `3.4.3` 依赖声明。Actinium 主 JAR 保留初始化 GLES 所需的 `lwjgl-opengles` Java 绑定与 JNI 库。Gradle 开发环境使用已发布的 Outlands `2026.11.21`，并在运行时类路径补充当前平台的着色器工具原生库。[Outlands 2026.11.22 CI 构建](https://github.com/Project-Outlands/Outlands/actions/runs/45023893898)

2026-11-25，Windows 上通过 Mesa 25.3.6 llvmpipe 的真实 EGL/GLES 3.2 上下文运行六项集成测试，覆盖采样器、VAO 实例化属性、无纹理及有纹理的固定管线像素、模拟裁剪和 proxy 纹理探测。`check` 和 `build` 通过。完整客户端回归待实测，验证项目为启动画面、进入世界、云层和区块渲染、启用 BSL 或 MakeUp 后的世界渲染，以及 HBM 防爆门的开关动画。集成测试命令见[测试说明](../../src/glIntegrationTest/README.md)。

## 点大小查询修复记录

GLSM 在初始化时调用 `GLStateManager.maxPointSize()`，缓存驱动支持的点大小上限。旧实现先查询平滑点的 `GL_POINT_SIZE_RANGE`，再查询 `GL_ALIASED_POINT_SIZE_RANGE`。GLES 只支持后一个枚举，第一次查询会产生 `GL_INVALID_ENUM`。修复后，GLES 直接读取非平滑点范围的上限。桌面 GL 仍依次查询两种范围，并缓存两个上限中的较大值。[Angelica #2346](https://github.com/GTNewHorizons/Angelica/pull/2346)

2026-11-24 在 Windows、Java 25、RTX 4060 Laptop GPU（NVIDIA 566.26）上验证了基于 Actinium `842f8bdc` 的修复。

| 验证场景 | 实测结果 |
| --- | --- |
| Minecraft 1.12.2 `runClient`，桌面 OpenGL 4.5 | 平滑与非平滑点的上限均为 `2047.0`，GLSM 缓存值为 `2047.0`。 |
| 独立 GLES 3.2 上下文 | `maxPointSize()` 返回 `2047.0`，与驱动报告的非平滑点上限一致。查询后 `glGetError()` 返回 `GL_NO_ERROR`。 |
| 独立桌面 OpenGL 3.2 上下文 | `maxPointSize()` 返回 `2047.0`，与驱动报告的两种上限中的较大值一致。查询后 `glGetError()` 返回 `GL_NO_ERROR`。 |

桌面客户端完成点大小查询后，HBM 在预初始化阶段查找 `KeyBinding.HASH` 失败，启动中止。完整 GLES 客户端启动仍待验证。
