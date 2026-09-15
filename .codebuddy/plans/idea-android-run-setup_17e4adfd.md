---
name: idea-android-run-setup
overview: 产出一份「在 IntelliJ IDEA 中打开并 USB 真机运行 express-ocr 项目」的环境配置清单与操作步骤说明；本次交付为纯说明文档，不修改项目中的任何文件。
todos:
  - id: idea-version-guide
    content: 撰写 IDEA 版本判断与安装建议，说明统一版现状、版本辨别方法与 Android Studio 兜底方案
    status: completed
  - id: plugin-install-guide
    content: 撰写 Android 与 Android Design Tools 插件的安装步骤，并标注订阅层级需实测及验证方法
    status: completed
    dependencies:
      - idea-version-guide
  - id: jdk-sdk-guide
    content: 撰写 JDK 17 与 Android SDK 34 准备说明，含 Gradle JDK 与 SDK 路径指向位置
    status: completed
    dependencies:
      - idea-version-guide
  - id: fill-project-gaps
    content: 撰写手动补齐 Gradle Wrapper 与创建 local.properties 的具体命令与路径转义注意事项
    status: completed
    dependencies:
      - jdk-sdk-guide
  - id: import-and-run-guide
    content: 撰写项目导入、Gradle Sync、USB 真机调试与运行、相机权限授予的完整步骤
    status: completed
    dependencies:
      - fill-project-gaps
  - id: troubleshooting-guide
    content: 汇总常见报错排查清单，覆盖 SDK 缺失、JDK 不符、Sync 失败、真机不识别与相机黑屏
    status: completed
    dependencies:
      - import-and-run-guide
---

## 用户需求
在 IntelliJ IDEA 中打开并运行当前 Android 项目（`h:/Work/express-ocr`），用户希望先弄清楚「需要准备和配置哪些东西」。

## 交付物概述
一份结构化、可直接照做的环境配置说明清单。用户已明确：**本次只出说明、不改动任何代码或项目文件**，所有补齐动作由用户手动执行。

## 核心内容
- **IDEA 版本判断与下载建议**：需说明自 2025.3 起 Community 与 Ultimate 已合并为「统一版 IntelliJ IDEA」，不存在两个独立安装包；同时说明 Android 开发支持已不再随 IDE 内置。
- **必装插件**：Android 与 Android Design Tools 的安装位置与重启要求。
- **JDK 与 SDK 准备**：JDK 17、Android SDK Platform 34 / Build-Tools / Platform-Tools 的获取方式，以及 IDE 中 Gradle JDK 与 SDK 路径的指向位置。
- **补齐项目缺口的手动操作**：项目当前缺失 Gradle Wrapper 可执行文件（`gradlew`、`gradlew.bat`、`gradle-wrapper.jar`）与 `local.properties`，需给出用户可自行执行的补齐命令。
- **导入与运行**：打开项目、Gradle Sync、USB 真机调试与运行、相机权限授予。
- **报错排查**：SDK 未找到、JDK 版本不符、Sync 失败、真机不识别、相机黑屏等常见问题。

## 关键约束与不确定性
- 用户使用 **USB 连真机**（非模拟器），说明需围绕真机路径展开。
- Android 插件在统一版免费层是否可用，官方文档未明确说明，说明中须如实标注为「需实测项」，给出验证方法，并提供兜底方案（改用免费的 Android Studio，或使用 30 天 Ultimate 试用）。


## Tech Stack Selection
本任务为环境配置指导，不涉及代码实现。方案基于项目已确认的工具链，不做任何版本变更：

- Android Gradle Plugin 8.5.2（`build.gradle.kts` 根文件）
- Gradle 8.7（`gradle/wrapper/gradle-wrapper.properties` 中 distributionUrl 已锁定）
- Kotlin 1.9.24（`org.jetbrains.kotlin.android`）
- JDK 17（`app/build.gradle.kts` 中 compileOptions 与 kotlinOptions.jvmTarget 均为 17）
- compileSdk / targetSdk 34，minSdk 24
- 依赖来源 google() 与 mavenCentral()（`settings.gradle.kts`），CameraX 1.3.4、ML Kit 中文识别 16.0.1
- 运行目标：Windows 主机 + USB 真机（minSdk 24，即 Android 7.0 及以上）

## Implementation Approach
策略为**只读诊断 + 输出说明**：不生成、不修改、不删除任何文件，最终交付仅是一份 Markdown 结构化清单。

关键决策与理由：

1. **按「统一版 IDEA」的新模型给建议，而非旧的 Community/Ultimate 二分模型**。已核实 JetBrains 官方文档与博客：自 IntelliJ IDEA 2025.3 起两个产品已合并，免费层保留 Java/Kotlin 核心能力，Ultimate 为订阅制并含 30 天试用。若按旧模型描述「社区版完全不能做 Android」会误导用户。
2. **同时给出「Android 插件」与「Android Design Tools」两个插件名**。官方帮助页明确这两者需从 Marketplace 单独安装，且 Android 支持不再与 IDE 捆绑。
3. **对「插件是否需 Ultimate」保持诚实标注**。官方帮助页与统一版说明页均未提及 Android 与订阅层级的关系，Marketplace 插件页为动态渲染未能取得确切的 Compatibility 数据，因此不臆断，改为给出验证方法与兜底路径。
4. **兜底路径优先推荐 Android Studio**。该项目 `README.md` 原本即按 Android Studio 编写，且 Android Studio 免费、开箱内置 Android 插件与 SDK 管理能力，是风险最低的路径；IDEA 路径作为用户偏好方案给出。
5. **项目缺口的补齐动作交给用户执行**。已确认仓库缺少 Gradle Wrapper 可执行文件与 `local.properties`，这是「打开就能跑」的两个硬缺口；因用户要求不改文件，故以命令形式给出，并提醒 `local.properties` 属本机私有配置、应保持在忽略列表中。

## Implementation Notes
- Gradle Wrapper 生成命令需匹配现有配置：`gradle wrapper --gradle-version 8.7`，否则会与 `gradle-wrapper.properties` 中已声明的 8.7 冲突。若本机无 Gradle，可从任一新版 Android Studio 空项目拷贝三个 wrapper 文件。
- `local.properties` 中 Windows 路径需转义反斜杠，例如 `sdk.dir=C\:\\Users\\<用户名>\\AppData\\Local\\Android\\Sdk`，避免被属性文件解析吞掉。
- 首次 Gradle Sync 需联网拉取 AGP、Kotlin 与全部依赖，耗时较长属正常现象，不要因等待中断 Sync。
- ML Kit 中文识别模型在部分机型上首次使用需联网下载，下载完成后可离线使用；说明中需提示此点，避免用户在无网络环境误判为功能故障。
- 运行前必须确认真机已开启开发者选项与 USB 调试；Windows 下若 `adb devices` 看不到设备，多为厂商 USB 驱动或数据线与充电线差异导致。
- 说明中涉及「官方未确认」的条目统一标注为需实测项，不给出确定性结论。

## Architecture Design
无需架构改动。本次为纯说明性交付，不引入任何新模块、不调整 Gradle 配置结构、不触碰 `app/src/main/java/com/expressocr/app/` 下任何 Kotlin 源码或资源文件。因此不提供架构图。

## Directory Structure
**本次不新增、不修改、不删除任何文件。** 涉及的所有补齐动作（Gradle Wrapper、`local.properties`）均由用户在本地手动执行，因此不存在待变更的文件清单。

以下为说明中需要引用的既有文件及其作用，仅用于定位核对：

```
express-ocr/
├── build.gradle.kts                          # 根构建脚本，声明 AGP 8.5.2 与 Kotlin 1.9.24 插件版本
├── settings.gradle.kts                       # 仓库配置 google() / mavenCentral()，模块 :app
├── gradle.properties                         # JVM 参数与 AndroidX 开关
├── gradle/wrapper/gradle-wrapper.properties  # 已锁定 Gradle 8.7，但同目录缺少 wrapper 可执行文件
├── app/build.gradle.kts                      # namespace、SDK 版本、JDK 17、依赖清单
└── app/src/main/AndroidManifest.xml          # 相机权限与 CAMERA 特性要求，主 Activity 声明
```

缺失项（说明中需指导用户手动补齐，本次不由我创建）：
- `gradlew`、`gradlew.bat`、`gradle/wrapper/gradle-wrapper.jar`
- `local.properties`（写入 `sdk.dir` 指向本机 Android SDK）

