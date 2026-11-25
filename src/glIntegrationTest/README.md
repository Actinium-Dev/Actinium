# 原生 OpenGL ES 3.2 集成测试

从仓库根目录运行 `./gradlew -I src/glIntegrationTest/gles.init.gradle glesIntegrationTest`。该任务在独立 JVM 中运行，启动时检查 SDL 创建的上下文是否使用 EGL，以及驱动是否提供 GLES 3.2。每个用例结束后都检查驱动错误。

Linux CI 使用 Mesa 软件渲染。没有桌面会话时，可使用以下命令运行同一套用例。

```bash
LIBGL_ALWAYS_SOFTWARE=1 SDL_VIDEO_DRIVER=x11 xvfb-run -a \
  ./gradlew -I src/glIntegrationTest/gles.init.gradle glesIntegrationTest
```

Windows 可以指定与 JVM 架构匹配的 EGL 驱动目录。该目录应包含同一发行版的 `libEGL.dll`、`libGLESv2.dll` 和驱动所需的其他文件。已验证 Mesa3D for Windows 25.3.6 MSVC 发行包的 `x64` 目录可以用 llvmpipe 建立 GLES 3.2 上下文。

```powershell
$env:GALLIUM_DRIVER = 'llvmpipe'
.\gradlew.bat -I src/glIntegrationTest/gles.init.gradle glesIntegrationTest '-PglesEglDir=C:/path/to/mesa/x64'
```

也可将 `glesEglDir` 指向提供 GLES 3.2 的 ANGLE 发行包，并通过 `ANGLE_DEFAULT_PLATFORM` 选择其支持的后端。测试日志会输出实际的 GLES 版本和 renderer。驱动提供的版本低于 GLES 3.2 时，任务会失败。
