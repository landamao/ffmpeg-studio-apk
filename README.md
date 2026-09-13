# FFmpeg Studio

Android 原生 FFmpeg 转码器。真实调用 FFmpeg 引擎转码——不是命令行包装器,也不是模拟演示:选文件、调参数、看实时进度,输出直接进系统媒体库。免费、无广告、完全离线。

[下载最新版 APK](https://github.com/landamao/ffmpeg-studio-apk/releases/latest) · [更新日志](CHANGELOG.md) · [问题反馈](https://github.com/landamao/ffmpeg-studio-apk/issues)

## 功能特性

**转码核心**

- 内置 [ffmpeg-kit](https://github.com/AntonKarpenko/ffmpeg-kit)(full-gpl,FFmpeg n8.1.2):H.264 / H.265 / VP9 / AAC / MP3 / FLAC 等编码器真实可用,也可复制流(copy)不重编码
- 文件经系统 SAF 选择器选取,`content://` 直读不复制;输出经 MediaStore 写入系统媒体库(视频 → `Movies/FFmpegStudio`,音频 → `Music/FFmpegStudio`,GIF → `Pictures/FFmpegStudio`)
- 执行日志页实时显示:百分比、已编码时长、帧率、处理速度,可随时停止;执行统计中的「速度 x.x」= 相对实时的处理速度,预计耗时 ≈ 媒体时长 ÷ 速度
- 带空格/中文的文件名与参数安全处理(`FFmpegKitConfig.parseArguments` 切分,单/双引号均可分组)

**参数控制**

- 视频:编码器(H.264/H.265/VP9/copy/自定义)、CRF 0–51、preset 9 档、分辨率(预设+自定义)、帧率 0–240(默认/丢帧/混合补帧/平滑插帧)、移除视频
- 音频:编码器(AAC/MP3/FLAC/WAV/copy/自定义)、码率(档位+自由输入)、移除音频
- 裁剪时间:一起 / 分开 / 不设置;秒数与时:分:秒双格式自动换算;终点 -to / -t;分开模式自动生成 trim/atrim 的 `-filter_complex`
- 输出:9 种容器格式 + 自动/跟随源文件/自定义(按流与编码自动选容器);输出路径模板(`<raw_dir>` `<raw_name>` `<ext>` `<N>` `<T,val=…>`,重名自动递增)
- 可选:覆盖已有文件、完成后删除源文件、覆盖源文件、追加自定义参数

**预设与历史**

- 参数预设与原始命令预设:星标显示到主页、长按拖动排序、每行 2/3/4 列可调
- 原始命令模式:直接编辑 ffmpeg 命令,保存时自动把 `-c:v -crf -preset -vf -r -c:a -b:a -an -vn -ss -to -t` 映射回控件,映射不了的进自定义参数
- 历史记录:全部/成功/失败筛选、视频缩略图、平均帧率/速度、完整日志,可一键加载回工作台

**界面与体验**

- 单 Activity + Jetpack Compose,完整浅色/深色两套配色
- 主页快速开始网格(星标预设,最多 6 个)、本周概览统计、最近文件
- 退出重进保留工作台全部状态(参数、已选文件、折叠状态)
- AI 助手界面预留(未实现,界面如实标注)

## 下载

到 [Releases](https://github.com/landamao/ffmpeg-studio-apk/releases/latest) 下载:

| 文件 | 用途 |
|---|---|
| `FFmpeg.Studio_x.y.z-arm64.apk` | 绝大多数手机 |
| `FFmpeg.Studio_x.y.z-x86_64.apk` | 模拟器 |

系统要求 Android 8.0+(minSdk 26)。release 包经 R8 混淆与资源收缩,arm64 包约 45MB。

## 构建

需 JDK 17+ 与 Android SDK。Android Studio 直接打开 `android/` 目录,或命令行:

```bash
cd android
./gradlew assembleDebug     # 调试包
./gradlew assembleRelease   # 正式包
```

`assembleRelease` 需要 `android/keystore.properties` 指向签名文件(格式如下);文件不存在时仍会构建,产出未签名包:

```properties
storeFile=signing/ffmpeg-studio.keystore
storePassword=...
keyAlias=...
keyPassword=...
```

产物按 ABI 拆分;每次发版均提升 versionCode/versionName,关于页版本号运行时从包信息读取。

## 技术要点

- 自绘组件(折叠块、分段控件、小开关、下拉选择、拖拽排序),拖拽排序为绘制层让位 + 松手提交方案,见 `ui/DragDrop.kt`
- 命令构建与原始命令解析:`logic/CommandBuilder.kt`;执行、进度解析、SAF/MediaStore 交互:`data/AppViewModel.kt`
- 容器「自动」模式用 MediaExtractor 异步探测源编码:`logic/MediaProbe.kt`
- 持久化:SharedPreferences + JSON(预设、主题、输出目录);历史为一条一个 JSON 文件(`filesDir/history/`,保留最近 100 条)
- 「打开」输出文件用 FileProvider(`${applicationId}.files`,见 `res/xml/file_paths.xml`)
- 已在 Pixel 6 布局 · API 35 模拟器实测:真实转码、空格+中文文件名、GIF 输出、原始命令模式、深色主题

## 许可

[GPL-3.0](LICENSE) · Copyright (C) 2026 landamao

本项目使用 [ffmpeg-kit](https://github.com/AntonKarpenko/ffmpeg-kit) full-gpl 构建,其含 GPL 组件(libx264 等),因此本项目整体以 GPL v3 开源。
