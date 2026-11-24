# OpenGL ES 点大小查询

GLSM 在初始化时调用 `GLStateManager.maxPointSize()`，缓存驱动支持的点大小上限。旧实现先查询平滑点的 `GL_POINT_SIZE_RANGE`，再查询 `GL_ALIASED_POINT_SIZE_RANGE`。GLES 只支持后一个枚举，第一次查询会产生 `GL_INVALID_ENUM`。修复后，GLES 直接读取非平滑点范围的上限。桌面 GL 仍依次查询两种范围，并缓存两个上限中的较大值。[Angelica #2346](https://github.com/GTNewHorizons/Angelica/pull/2346)

2026-11-24 在 Windows、Java 25、RTX 4060 Laptop GPU（NVIDIA 566.26）上验证了基于 Actinium `842f8bdc` 的修复。

| 验证场景 | 实测结果 |
| --- | --- |
| Minecraft 1.12.2 `runClient`，桌面 OpenGL 4.5 | 平滑与非平滑点的上限均为 `2047.0`，GLSM 缓存值为 `2047.0`。 |
| 独立 GLES 3.2 上下文 | `maxPointSize()` 返回 `2047.0`，与驱动报告的非平滑点上限一致。查询后 `glGetError()` 返回 `GL_NO_ERROR`。 |
| 独立桌面 OpenGL 3.2 上下文 | `maxPointSize()` 返回 `2047.0`，与驱动报告的两种上限中的较大值一致。查询后 `glGetError()` 返回 `GL_NO_ERROR`。 |

桌面客户端完成点大小查询后，HBM 在预初始化阶段查找 `KeyBinding.HASH` 失败，启动中止。完整 GLES 客户端启动仍待验证。
