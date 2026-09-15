# 取件码识别（Android）

离线识别快递取件码 / 尾号。打开即预览相机，支持拍照识别与视频识别；匹配到后用红框标出，视频模式会停在命中帧。

## 功能

- 启动自动打开后置摄像头
- 输入取件码或尾号（至少 3 位数字），如画面 `2-2-2302`，可只填 `2302`
- **拍照识别**：拍一帧 → OCR → 红框
- **视频识别**：连续扫帧 → 命中即停 → 冻帧 + 红框
- ML Kit 中文端上 OCR，识别过程可不联网

## 环境要求（请你本机准备）

当前仓库可直接用 **Android Studio** 或已配置 Android 插件的 **IntelliJ IDEA** 打开。

依赖与 Gradle 发行版已配置国内镜像（阿里云 Maven + 腾讯云 Gradle），国内网络下 Sync 会快很多。

1. 安装 Android Studio，或在 IDEA 中安装 Android 插件并配置 Android SDK
2. 打开本项目，等待 Gradle Sync 下载依赖
3. 用 USB 调试连接手机，或使用模拟器（模拟器无真实快递柜画面，建议真机）
4. 点击 Run 安装到手机

首次使用 ML Kit 时，部分机型可能需联网下载文字识别模型；下载完成后可离线使用。

## 操作说明

1. 允许相机权限  
2. 输入尾号，例如 `2302`  
3. 点「拍照识别」或「视频识别」  
4. 命中后看红框；点「继续扫描」回到实时预览  

## 技术栈

- Kotlin + CameraX  
- ML Kit Text Recognition（Chinese，端上）  
- 匹配：去掉非数字后 `endsWith` / 全等 / 包含  

## 模块

- `match/CodeMatcher`：尾号匹配  
- `ocr/OcrHelper`：ML Kit 封装  
- `ui/OverlayView`：红框  
- `camera/ImageConverters`：ImageProxy → Bitmap  
- `MainActivity`：拍照 / 视频流程  

## 打包 APK

Android Studio：`Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`。  
生成路径一般在 `app/build/outputs/apk/`。
