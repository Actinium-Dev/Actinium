# MobileGlues 的 OpenGL 版本显示

FCL 使用 MobileGlues 时，`GL_VERSION` 报告桌面 OpenGL 4.0，`GL_MAJOR_VERSION` 和 `GL_MINOR_VERSION` 则返回底层 GLES 3.2。图形设置现在读取 `GL_VERSION`，与启动时校验的版本一致。

通过 `POJAV_RENDERER` 识别到 Android 启动器时，Actinium 只请求最低支持的桌面 OpenGL 3.3，由启动器选择实际上下文。图形设置隐藏游戏内的版本选择项。

2026 年 11 月 26 日，FCL 和 MobileGlues 2.0.1·Dev1 实机日志记录了请求 OpenGL 3.3、实际报告 OpenGL 4.0。设置页显示 OpenGL 4.0 已经实机确认。
