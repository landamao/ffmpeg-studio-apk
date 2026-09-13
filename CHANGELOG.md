# 更新日志

日期均为 2026-09-13(项目集中开发与发版日)。

## v1.0.0

- **首个开源版本**:完整源码发布到 GitHub(本仓库),以 GPL v3 开源(ffmpeg-kit full-gpl 要求);Release 附 arm64 / x86_64 正式签名 APK
- 签名密钥与密码、本机 SDK/代理/工具链配置均不入库(见 `.gitignore`)

## v0.7.8

- 状态恢复行为修正:**启动固定进入主页**,只恢复工作台状态(参数、已选文件、折叠状态)——不再恢复上次停留的页面

## v0.7.7

- 预设网格设置移到预设管理页:「每行数量」(2/3/4)与「默认展开行数」(1/2/3),持久化保存
- 预设管理页「新建预设 / 添加原始命令」两个按钮合并为**右下角加号悬浮按钮**(原始命令在编辑器里切换类型即可)
- **退出应用重进保留状态**:工作台全部参数(含已选文件)、参数块折叠状态在 ON_STOP 时保存,启动时恢复
- **历史记录改为一条一个 JSON 文件**(filesDir/history/),不再全塞在 SharedPreferences;仍保留最近 100 条
- 修复历史时间永远显示「刚刚」:改为存完成时刻的时间戳,展示时实时计算相对时间(刚刚 / n 分钟前 / n 小时前 / 超过一天显示日期)
- 历史卡片新增**平均帧率 / 平均速度**(原日志窗口完成摘要里的平均值挪到这里)
- 修复「完成后保留日志」开关无效:关闭后历史记录不再写入日志内容(展开详情不显示日志块)

## v0.7.6

- 设置 → 关于补充**作者信息**:GitHub 主页、项目地址、QQ 群、Telegram(点击直接跳转)+ 版权声明;去掉「真实转码版」字样,「开源许可」改为指向 GitHub 仓库
- 主页删去「长按拖动排序」「左滑后点击移除」两处提示文字
- 工作台 / AI 设置 / 预设编辑页**点击输入框以外区域收起键盘**
- 执行日志窗口的「输出文件」路径颜色修正(原用深底配色的浅灰 monospace,浅色主题下几乎不可见)
- **AI 功能如实标注未实现**:移除全部模拟行为(假连接测试、假 401 报错、正则拼命令的假回复),AI 设置与聊天界面显示「未实现」提示

## v0.7.5

- 工作台文件卡与预设块的间距对齐(补 10dp,与下方参数块一致)
- 工作台文件卡选中文件后显示**视频首帧预览图**(取不到帧回落胶片图标);缩略图组件抽为公共 `ui.MediaThumb`(LRU + IO 线程取帧),历史记录与文件卡共用

## v0.7.4

- **预设管理页拖拽「行消失」根因修复**:行的 `.background` 写在平移层之外——拖拽让位时背景留在原位、内容平移出裁剪区,视觉上整行消失。背景移入平移层,整行作为整体位移
- 被拖项增加**视口钳制**,拖出裁剪边界会被拦住,不再「滑出即消失」;行宽与容器同宽时跳过水平钳制
- 空自定义编码器防护:buildCmd / paramsToCmdFragment 跳过空 `-c:v`/`-c:a`/`-preset`,避免产生空参数命令
- 本次通过模拟器(headless,测完即关)+ adb motionevent 实机复现:6 卡主页拖拽、预设页跨 5 行拖拽、松手提交、toast,全部通过

## v0.7.3

- **修复下拉「自定义…」失效**:旧逻辑是点自定义就把值改成另一个标准值(值恰好等于该标准值时纯无反应,否则莫名重置)。现给 WsState 增加 vcodecCustom/presetCustom/resCustom/acodecCustom 四个显式模式开关:点「自定义…」进入输入模式(输入框预填当前值),选标准项退出;应用预设时按值是否标准自动设位。标准选项表抽到 `data.StdValues` 供 UI 与 VM 共用
- 主页拖拽遮挡修复:拖动行 zIndex 2、含让位偏移的行 zIndex 1——向上拖时让位卡片不再被下一行盖住「消失」

## v0.7.2

- 撤销 v0.7.1 的底部弹层选择器(渲染在页面内部导致占版面、点外部不关闭),**恢复紧凑 DropdownMenu**:贴着字段展开、点外部自动关闭;增强:选项超高时内部可滚动、当前项高亮打勾、文案不折行
- 拖拽排序实时预览为 v0.7.1 方案(绘制层让位、松手提交)

## v0.7.1

- 拖拽排序**恢复实时目标位置预览**:拖动时其它项用 graphicsLayer 让位偏移模拟换位(零重组、手势不会中断),命中判定用「有效矩形」(布局矩形+让位偏移)带天然滞回,停在两项之间不抖;松手一次性提交,落空弹回
- **SelectField 从贴地下拉改为底部弹层**:选项多时(容器格式 12 项)原 DropdownMenu 向下展开超出屏幕;改为复用 SheetDialog 从底部滑出、可滚动、当前项高亮打勾

## v0.7.0

- **拖拽排序改为「松手才提交」**:拖动全程只做绘制层平移(跟手浮动),期间不换序不重组——跨父容器的项在换位重组时会被销毁重建、手势必断;松手按视觉中心落点一次性换序,落在空隙原样弹回。主页拖动中行级 zIndex 保证卡片浮在其它行之上
- ClearableSelection 仅「原地轻点」才重建容器(无位移 + 未消费 + <350ms),滚动/滑动手势一律不重建,避免打断惯性滚动
- **容器格式新增「自动 / 跟随原文件 / 自定义」**:auto 按流与编码选容器(仅音频:AAC→m4a、MP3→mp3、FLAC→flac、Opus/Vorbis→ogg、PCM→wav、未知→mka;重编码视频默认 MP4,音频为 FLAC/PCM 或编码未知用 MKV;复制视频流跟随源;GIF 出 GIF),源编码用 MediaExtractor 异步探测(`logic/MediaProbe.kt`,带 LRU);source 直接用源后缀;custom 用自填后缀
- 「提取音频」出厂预设改为 **copy 流 + 自动容器**(不再强制转 MP3)
- 输出文件名无后缀时自动补 "." + 容器后缀(ffmpeg 靠后缀猜 muxer,缺了会报 Unable to find a suitable output format)
- 工作台容器选择下方实时显示解析结果(“当前解析:.m4a”),预设详情格式标签同步

## v0.6.1

- **重写拖拽排序实现**(`DragDrop.kt`):旧实现平移量依赖布局回调重算——抓起即跳、拖动中不跟手、跨过邻居瞬移,且「最近中心」兜底导致手指停在两项之间时每帧来回换位。新实现:手指增量累计进 `dragPosition` 由 graphicsLayer 跟手(纯绘制层,不依赖每帧布局);换位后在 `onGloballyPositioned` 反向补偿槽位差保持视觉连续;命中判定改为「被拖项视觉中心落入其他项矩形」,越过才换位,无抖动
- 修复「打开」报「没有应用」:Application 上下文启动 Activity 缺 `FLAG_ACTIVITY_NEW_TASK`(异常被笼统捕获误报);改用 `Intent.createChooser` 系统选择器 + NEW_TASK,每次可选应用

## v0.6.0

- 修复长按拖动排序:换位重组时 `pointerInput(id)` 重建导致手势中断(拖一下就断),网格/列表项改用 `key(id)` 让节点跟随数据;换位过程不再刷 toast,拖完提示一次
- 修复历史页滚动重置:`ClearableSelection` 的 `key(selEpoch)` 重建子树会把内部 `rememberScrollState()` 一起重建,滚动状态提到容器外
- 修复执行日志闪退:`SelectionContainer` 套在 `LazyColumn` 上,选中后滚动、离屏项回收即崩(Compose 已知限制);日志区改为「复制全部」一键复制完整日志,AI 聊天命令块同步处理
- 「打开」按钮:历史详情的输入/输出路径、执行完成后的输出路径,均可调系统应用打开(FileProvider `${applicationId}.files`,见 `res/xml/file_paths.xml`;`AppViewModel.openFile`)
- 历史记录加缩略图:优先输入文件取视频首帧(MediaMetadataRetriever,IO 线程 + LRU 缓存,最大边 256px),取不到显示胶片占位
- 「视频压缩」出厂预设 crf 23→26(`Defaults`;老设备存过的值需在 设置→恢复内置预设 刷新),工作台默认值同步 26;裁剪默认改「不设置」
- 签名证书更换为用户自建密钥(CN=landamao):装过更早临时密钥版本的手机需先卸载再安装

## v0.5.0

- 引入正式发布签名:包名 `com.landamao.ffmpegstudio`(namespace 仍为 `com.ffmpegstudio`),密钥 `android/signing/ffmpeg-studio.keystore` + `android/keystore.properties`(不入库,务必备份,丢失后无法给老用户发更新)
- 正式包命令 `gradle assembleRelease`(自动用 keystore.properties 签名)

## v0.5.1

- release 开启 **R8 混淆 + 资源收缩**:dex 从 ~44MB(material-icons-extended 全量图标)降到 ~2.4MB,arm64 包 ~45MB;混淆映射表在 `app/build/outputs/mapping/release/mapping.txt`(解崩溃堆栈用,随包存档)

## v0.4.0

- **接入真实转码引擎**:[ffmpeg-kit 社区分支](https://github.com/AntonKarpenko/ffmpeg-kit) `full-gpl`(FFmpeg n8.1.2,内置 libx264 / libx265 / libvpx-vp9 / libmp3lame 等),APK 按 ABI 拆分:arm64-v8a(手机)/ x86_64(模拟器)
- 输入:SAF `content://` 通过 FFmpegKit 的 SAF 协议直接读取,无需复制
- 输出:通过 MediaStore 写入系统媒体库(视频→`Movies/FFmpegStudio`,音频→`Music/FFmpegStudio`,GIF→`Pictures/FFmpegStudio`),带 IS_PENDING 原子落盘;Android 10 以下回退应用专属目录
- 「完成后删除源文件」= 对源 URI 执行 delete;「覆盖源文件」= 成功后把结果写回源文件
- 进度:MediaMetadataRetriever 取总时长 + FFmpegKit Statistics 回调实时计算百分比
- 空格路径:执行前用 ffmpeg-kit 官方 `FFmpegKitConfig.parseArguments` 切分参数(与 shell 一致,单/双引号均可分组),带空格的路径/参数加引号即可;参数模式下输入输出走 SAF 协议(URL 编码,天然无空格问题),输出文件名中的空格原样保留

## v0.4.2

- **无任何内置示例数据**:全新安装时历史、最近文件为空,「本周概览」显示真实统计 0(已处理/成功/成功率);文件选择使用 SAF 系统文件选择器(真实文件,最近使用自动记录)

## v0.4.3

- 执行日志页顶部**实时易读摘要**(正在处理 · 23% · 已编码 2.4 秒 / 10.0 秒 · 11 帧/秒 · 速度 0.37x),完成态显示用时/输出体积/平均帧率/平均速度,摘要自动换行

## v0.4.4

- 所有黑框区域(执行日志、历史日志、完整命令/报错、AI 命令块、命令预览条)**长按可选中复制**,单个容器内支持跨行选择,点空白处清除选中
