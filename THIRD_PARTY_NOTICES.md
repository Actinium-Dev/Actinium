# 第三方声明

| 项目 | 对应目录 | 许可证 |
| --- | --- | --- |
| Angelica / GLSM | `glsm/`、`sdl-gpu/`、`src/main/java/com/gtnewhorizons/angelica/`、`src/main/resources/assets/angelica/`，以及 `src/main/java/com/dhj/actinium/` 中的部分渲染代码 | [LGPL-3.0](src/main/resources/META-INF/licenses/Angelica-LGPL-3.0.txt)（个别文件见文件头） |
| GTNHLib | `GTNHLib/` | LGPL-3.0（个别文件见文件头） |
| Iris | `shader/`、`src/main/java/net/coderbot/batchedentityrendering/`、`src/main/java/com/dhj/actinium/mixin/features/iris/`、`src/main/resources/assets/iris/` | LGPL-3.0 |
| Celeritas / Embeddium / Sodium | `celeritas-common/` | LGPL-3.0（个别文件见文件头） |
| Reese’s Sodium Options | `src/main/java/me/flashyreese/mods/reeses_sodium_options/`、`src/main/resources/assets/reeses-sodium-options/`（SVG 图标见 Material Design Icons） | [MIT](LICENSE-REESES-SODIUM-OPTIONS.md) |
| Material Design Icons | `src/main/resources/assets/reeses-sodium-options/textures/gui/*.svg`（Google `undo`、`settings_backup_restore` 图标，填充色采用原图标的橙色 `#FF8D30`） | [Apache-2.0](src/main/resources/META-INF/licenses/MaterialIcons-Apache-2.0.txt) |
| JSVG | `gradle/scripts/dependencies.gradle`（SVG 栅格化依赖，打包至 `com.dhj.actinium.shadow.jsvg`） | [MIT](src/main/resources/META-INF/licenses/JSVG-MIT.txt) |
| CeleritasDynamicLights / SodiumDynamicLights / LambDynamicLights | `src/main/java/com/dhj/actinium/api/dynamiclights/`、`src/main/java/com/dhj/actinium/render/dynamiclights/`、`src/main/java/com/dhj/actinium/mixin/vintage/features/dynamiclights/`、`src/main/resources/assets/celeritasdynamiclights/` | MIT（[CeleritasDynamicLights](src/main/resources/META-INF/licenses/CeleritasDynamicLights-MIT.txt)、[LambDynamicLights](src/main/resources/META-INF/licenses/LambDynamicLights-MIT.txt)） |
| Canvas（bitraster 与遮挡盒分解） | `celeritas-common/src/main/java/grondag/bitraster/`、`celeritas-common/src/main/java/dhj/embeddedt/embeddium/impl/render/chunk/occlusion/geometry/` | bitraster 为 [Apache-2.0](third-party/licenses/bitraster-APACHE-2.0.txt)，遮挡盒分解见文件头（LGPL-3.0） |
| Ithaka digraph | `src/main/java/de/odysseus/ithaka/digraph/` | Apache-2.0 |
| Mesa | `glsm/src/main/java/com/gtnewhorizons/angelica/glsm/`（固定管线着色器与显示列表 ID 分配器）、`celeritas-common/src/jmh/natives/`（EGL） | 移植代码为 MIT，EGL 各组件见 [Mesa 许可文件](third-party/licenses/mesa-26.1.8/) |
| mesa-dist-win | `celeritas-common/src/jmh/natives/` | [MIT](third-party/licenses/mesa-dist-win-MIT.txt) |
| LWJGL | `GTNHLib/`、`glsm/` 和 `celeritas-common/` 的部分工具代码，另见 `gradle/scripts/dependencies.gradle` 中的 Shaderc 与 SPIRV-Cross Java 绑定 | BSD-3-Clause |
| ASM | `GTNHLib/src/main/java/com/gtnewhorizon/gtnhlib/asm/` | BSD-3-Clause |
| fastutil | `celeritas-common/src/main/java/dhj/embeddedt/embeddium/impl/util/sorting/`、`gradle/scripts/dependencies.gradle`（打包依赖） | Apache-2.0（排序代码另含 CERN 许可，见 `MergeSort.java` 文件头） |
| GLSL Transformation Library | `gradle/scripts/dependencies.gradle`（打包依赖） | LGPL-3.0 |
| JOML | `gradle/scripts/dependencies.gradle`（打包依赖） | MIT |
| JCPP | `gradle/scripts/dependencies.gradle`（打包依赖） | Apache-2.0 |
| ANTLR 4 Runtime | `gradle/scripts/dependencies.gradle`（打包依赖） | BSD-3-Clause |
| JvmDowngrader Java API | `gradle/scripts/dependencies.gradle`（打包依赖） | LGPL-2.1 或商业许可 |
| MoltenVK | `moltenvk/` | [Apache-2.0](moltenvk/src/main/resources/LICENSE-MoltenVK) |
