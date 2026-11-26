# MobileGlues 的 OpenGL 版本显示

FCL 使用 MobileGlues 时，`GL_VERSION` 报告桌面 OpenGL 4.0，`GL_MAJOR_VERSION` 和 `GL_MINOR_VERSION` 则返回底层 GLES 3.2。图形设置现在读取 `GL_VERSION`，与启动时校验的版本一致。

通过 `POJAV_RENDERER` 识别到 Android 启动器时，Actinium 使用与桌面端 `Auto` 相同的候选版本探测流程。创建窗口后读取 `GL_VERSION`，报告版本达到 OpenGL 3.3 时直接使用当前上下文。图形设置隐藏游戏内的版本选择项。

2026 年 11 月 26 日，FCL 和 MobileGlues 2.0.1·Dev1 实机日志记录了请求 OpenGL 4.6、实际报告 OpenGL 4.0，游戏随后进入了世界。设置页显示 OpenGL 4.0 已经实机确认。

同日的地形实测中，MobileGlues 的 `glMultiDrawElementsIndirect` 只绘制了批次中的第一条命令。Actinium 仅在前端上下文声明 OpenGL 4.3、`GL_ARB_multi_draw_indirect` 或 `GL_EXT_multi_draw_indirect`，且提供相应批量入口时开放 `Indirect` 模式。前端仅报告 OpenGL 4.0 且未声明批量扩展时，该选项不可用。
