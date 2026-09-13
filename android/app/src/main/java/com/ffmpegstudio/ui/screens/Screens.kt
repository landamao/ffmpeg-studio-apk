package com.ffmpegstudio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ffmpegstudio.data.AppViewModel
import com.ffmpegstudio.data.AiConfig
import com.ffmpegstudio.data.HistoryEntry
import com.ffmpegstudio.data.Preset
import com.ffmpegstudio.logic.CommandBuilder
import com.ffmpegstudio.logic.TimeUtil
import com.ffmpegstudio.ui.Badge
import com.ffmpegstudio.ui.Btn
import com.ffmpegstudio.ui.BtnSmall
import com.ffmpegstudio.ui.ClearableSelection
import com.ffmpegstudio.ui.CardBox
import com.ffmpegstudio.ui.FieldColumn
import com.ffmpegstudio.ui.FieldHead
import com.ffmpegstudio.ui.FieldHint
import com.ffmpegstudio.ui.LocalStudioColors
import com.ffmpegstudio.ui.LogBlock
import com.ffmpegstudio.ui.MediaThumb
import com.ffmpegstudio.ui.RowItem
import com.ffmpegstudio.ui.SectionTitle
import com.ffmpegstudio.ui.SegRow
import com.ffmpegstudio.ui.SelectField
import com.ffmpegstudio.ui.SelectOption
import com.ffmpegstudio.ui.StudioInput
import com.ffmpegstudio.ui.SwitchTiny
import com.ffmpegstudio.ui.rememberDragGridState

// ==================== 历史 ====================
@Composable
fun HistoryScreen(vm: AppViewModel) {
    val c = LocalStudioColors.current
    val clipboard = LocalClipboardManager.current
    var expandedId by remember { mutableStateOf<Long?>(null) }
    // 滚动状态必须放在 ClearableSelection 之外：
    // 点按清除选中会 key() 重建子树，状态在内部的话每次点按都跳回顶部
    val scroll = rememberScrollState()

    // 整页共用一个选择容器：日志可跨行选中复制，点页面空白处清除选中
    ClearableSelection {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all" to "全部", "ok" to "成功", "fail" to "失败").forEach { (v, label) ->
                val active = vm.currentFilter() == v
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (active) c.accent else c.surface)
                        .border(1.dp, if (active) c.accent else c.line, RoundedCornerShape(999.dp))
                        .clickable { vm.setFilter(v) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (active) Color.White else c.ink)
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        val f = vm.currentFilter()
        val items = vm.history.filter { f == "all" || (f == "ok" && it.ok) || (f == "fail" && !it.ok) }
        if (items.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Outlined.Movie, contentDescription = null, tint = c.muted, modifier = Modifier.size(26.dp))
                Spacer(Modifier.height(12.dp))
                Text("暂无处理记录", fontSize = 13.sp, color = c.muted)
            }
        }
        items.forEach { h ->
            HistItem(vm, h, expandedId == h.id, clipboard) { expandedId = if (expandedId == h.id) null else h.id }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(88.dp))
    }
    }
}

@Composable
private fun HistItem(
    vm: AppViewModel,
    h: HistoryEntry,
    open: Boolean,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    onToggle: () -> Unit,
) {
    val c = LocalStudioColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(14.dp)),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MediaThumb(listOf(h.input, h.out))
                Spacer(Modifier.width(10.dp))
                Text(h.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.weight(1f))
                Badge(if (h.ok) "ok" else "fail", if (h.ok) "成功" else "失败")
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(c.surface2)
                        .clickable { vm.deleteHistory(h.id) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = c.danger, modifier = Modifier.size(15.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                MetaCol("耗时", h.dur, Modifier.weight(1f))
                MetaCol("", TimeUtil.formatAgo(h.atMs), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                MetaCol("原文件", h.inSize, Modifier.weight(1f))
                MetaCol("输出", h.outSize, Modifier.weight(1f))
            }
            if (h.avgFps > 0 || h.avgSpeed > 0) {
                Row(Modifier.fillMaxWidth()) {
                    MetaCol(
                        "平均",
                        buildString {
                            if (h.avgFps > 0) append(h.avgFps).append(" 帧/秒")
                            if (h.avgSpeed > 0) {
                                if (isNotEmpty()) append(" · ")
                                append(String.format(java.util.Locale.US, "%.2fx", h.avgSpeed))
                            }
                        },
                        Modifier.weight(1f),
                    )
                }
            }
        }
        if (open) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
            Column(Modifier.fillMaxWidth().background(c.surface2).padding(16.dp)) {
                CopyRow("输入路径", h.input, clipboard, onOpen = { vm.openFile(h.input) }) { vm.showToast("已复制") }
                CopyRow("输出路径", h.out, clipboard, onOpen = { vm.openFile(h.out) }) { vm.showToast("已复制") }
                CopyRow("完整命令", h.cmd, clipboard) { vm.showToast("已复制") }
                if (h.log.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("完整日志", fontSize = 11.sp, color = c.muted, modifier = Modifier.weight(1f))
                        BtnSmall("复制", {
                            clipboard.setText(AnnotatedString(h.log)); vm.showToast("已复制")
                        })
                        if (!h.ok) {
                            Spacer(Modifier.width(8.dp))
                            BtnSmall("查看完整日志", { vm.showError("完整日志", h.log) })
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    LogBlock(h.log)
                    Spacer(Modifier.height(10.dp))
                }
                BtnSmall("加载到工作台", { vm.retryHistory(h) }, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun MetaCol(label: String, value: String, modifier: Modifier = Modifier) {
    val c = LocalStudioColors.current
    Row(modifier.padding(vertical = 3.dp)) {
        if (label.isNotEmpty()) {
            Text("$label ", fontSize = 11.sp, color = c.muted)
        }
        Text(value, fontSize = 11.sp, color = c.ink, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CopyRow(
    label: String,
    text: String,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    onOpen: (() -> Unit)? = null,
    onCopied: () -> Unit,
) {
    val c = LocalStudioColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 11.sp, color = c.muted, modifier = Modifier.weight(1f))
        if (onOpen != null && text.isNotEmpty()) {
            BtnSmall("打开", onOpen)
            Spacer(Modifier.width(8.dp))
        }
        BtnSmall("复制", {
            clipboard.setText(AnnotatedString(text)); onCopied()
        })
    }
    Spacer(Modifier.height(6.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        Text(text, fontSize = 10.sp, lineHeight = 14.sp, color = c.muted, fontFamily = FontFamily.Monospace)
    }
    Spacer(Modifier.height(8.dp))
}

// ==================== 设置 ====================
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val c = LocalStudioColors.current
    var showOutDir by remember { mutableStateOf(false) }
    var showRestore by remember { mutableStateOf(false) }
    // 版本号从包信息读取，跟随 build.gradle 的 versionName，避免手写不同步
    val appVersion = remember {
        runCatching {
            val app = vm.getApplication<android.app.Application>()
            app.packageManager.getPackageInfo(app.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }
    var outDirText by remember(showOutDir) { mutableStateOf(vm.outDir) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        SectionTitle("输出")
        CardBox {
            RowItem(
                title = "默认输出目录",
                desc = vm.outDir,
                icon = Icons.Outlined.Folder,
                showChevron = true,
                onClick = { showOutDir = true },
            )
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("完成后保留日志", fontSize = 15.sp, color = c.ink)
                    Text("写入历史以便排查", fontSize = 12.sp, color = c.muted)
                }
                SwitchTiny(vm.keepLog) { vm.updateKeepLog(it) }
            }
        }

        SectionTitle("外观")
        CardBox {
            FieldColumn {
                FieldHead("主题")
                Spacer(Modifier.height(8.dp))
                SegRow(
                    options = listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色"),
                    selected = vm.themeMode,
                    onSelect = { vm.setTheme(it) },
                )
            }
        }

        SectionTitle("预设")
        CardBox {
            RowItem(
                title = "恢复内置预设",
                desc = "找回已删除的内置项并重置为出厂内容",
                icon = Icons.Outlined.RestartAlt,
                showChevron = true,
                onClick = { showRestore = true },
            )
        }

        SectionTitle("AI")
        CardBox {
            RowItem(
                title = "AI 助手",
                desc = aiSummary(vm),
                icon = Icons.Outlined.AutoAwesome,
                showChevron = true,
                onClick = { vm.goPage("ai") },
            )
        }

        SectionTitle("关于")
        CardBox {
            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(c.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Movie, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text("FFmpeg Studio", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.ink)
                Text("v$appVersion", fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(top = 4.dp))
            }
            RowItem(title = "FFmpeg 版本", value = vm.ffmpegVersion ?: "加载中…")
            RowItem(
                title = "开源许可",
                desc = "github.com/landamao/ffmpeg-studio-apk",
                showChevron = true,
                onClick = { vm.openUrl("https://github.com/landamao/ffmpeg-studio-apk") },
            )
            RowItem(title = "隐私说明", showChevron = true, onClick = { vm.showToast("本地处理，密钥仅存本机") })
            RowItem(
                title = "GitHub 主页",
                desc = "github.com/landamao",
                showChevron = true,
                onClick = { vm.openUrl("https://github.com/landamao") },
            )
            RowItem(
                title = "项目地址",
                desc = "github.com/landamao/ffmpeg-studio-apk",
                showChevron = true,
                onClick = { vm.openUrl("https://github.com/landamao/ffmpeg-studio-apk") },
            )
            RowItem(
                title = "QQ 群",
                desc = "1103659691",
                showChevron = true,
                onClick = { vm.openUrl("https://qm.qq.com/q/2ilvmVy3DC") },
            )
            RowItem(
                title = "Telegram",
                desc = "@landamaogroup",
                showChevron = true,
                onClick = { vm.openUrl("https://t.me/landamaogroup") },
            )
            Text(
                "版权所有 · landamao",
                fontSize = 11.sp, color = c.muted,
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        Spacer(Modifier.height(88.dp))
    }

    if (showOutDir) {
        AlertDialog(
            onDismissRequest = { showOutDir = false },
            title = { Text("默认输出目录") },
            text = {
                StudioInput(value = outDirText, onValueChange = { outDirText = it })
            },
            confirmButton = {
                TextButton(onClick = {
                    if (outDirText.isNotBlank()) vm.updateOutDir(outDirText.trim())
                    showOutDir = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showOutDir = false }) { Text("取消") } },
        )
    }
    if (showRestore) {
        AlertDialog(
            onDismissRequest = { showRestore = false },
            title = { Text("恢复内置预设") },
            text = { Text("· 已删除的内置预设会重新出现\n· 内置项内容重置为出厂设置\n· 自定义预设会保留") },
            confirmButton = {
                TextButton(onClick = { vm.restoreBuiltinPresets(); showRestore = false }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showRestore = false }) { Text("取消") } },
        )
    }
}

private fun aiSummary(vm: AppViewModel): String = "未实现"

// ==================== AI 设置 ====================
@Composable
fun AiSettingsScreen(vm: AppViewModel) {
    val c = LocalStudioColors.current
    val fm = LocalFocusManager.current
    var enabled by remember { mutableStateOf(vm.aiCfg.enabled) }
    var base by remember { mutableStateOf(vm.aiCfg.base) }
    var key by remember { mutableStateOf(vm.aiCfg.key) }
    var model by remember { mutableStateOf(vm.aiCfg.model) }

    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { fm.clearFocus() } }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        CardBox {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("启用 AI 生成命令", fontSize = 15.sp, color = c.ink)
                    Text("自然语言 → FFmpeg 参数", fontSize = 12.sp, color = c.muted)
                }
                SwitchTiny(enabled) { enabled = it }
            }
            FieldColumn {
                FieldHead("接口协议")
                Spacer(Modifier.height(8.dp))
                SelectField(
                    options = listOf(SelectOption("openai", "OpenAI 兼容")),
                    value = "openai",
                    onSelect = {},
                )
            }
            FieldColumn {
                FieldHead("Base URL")
                Spacer(Modifier.height(8.dp))
                StudioInput(value = base, onValueChange = { base = it }, placeholder = "https://api.openai.com/v1")
            }
            FieldColumn {
                FieldHead("API Key")
                Spacer(Modifier.height(8.dp))
                StudioInput(
                    value = key, onValueChange = { key = it },
                    placeholder = "sk-...", password = true,
                )
            }
            FieldColumn {
                FieldHead("模型")
                Spacer(Modifier.height(8.dp))
                StudioInput(value = model, onValueChange = { model = it }, placeholder = "gpt-4o-mini")
            }
        }
        SectionTitle("操作")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("测试连接", { vm.testAi(base.trim(), key.trim()) }, Modifier.weight(1f))
            Btn("保存设置", {
                vm.saveAi(AiConfig(enabled, base.trim(), key.trim(), model.trim()))
            }, Modifier.weight(1f), kind = "primary")
        }
        FieldHint("未实现", modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(88.dp))
    }
}

// ==================== 预设管理 ====================
@Composable
fun PresetsManageScreen(vm: AppViewModel) {
    val c = LocalStudioColors.current
    var deleteTarget by remember { mutableStateOf<Preset?>(null) }
    val drag = rememberDragGridState(
        currentIds = { vm.presets.map { it.id } },
        onReorder = { order -> vm.reorderPresets(order) },
        onFinished = { vm.showToast("预设顺序已更新") },
    )

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        SectionTitle("显示")
        CardBox {
            FieldColumn {
                FieldHead("每行数量")
                Spacer(Modifier.height(8.dp))
                SegRow(
                    options = listOf("2" to "2", "3" to "3", "4" to "4"),
                    selected = vm.presetCols.toString(),
                    onSelect = { vm.setGridCols(it.toInt()) },
                )
            }
            FieldColumn {
                FieldHead("默认展开行数")
                Spacer(Modifier.height(8.dp))
                SegRow(
                    options = listOf("1" to "1", "2" to "2", "3" to "3"),
                    selected = vm.presetRows.toString(),
                    onSelect = { vm.setGridRows(it.toInt()) },
                )
            }
        }
        SectionTitle("全部预设")
        // 钳制容器 = 这张卡片本身：Surface 会裁剪内容，被拖行滑出卡片顶/底就会「消失」
        CardBox(Modifier.then(drag.containerModifier())) {
            // key(id)：换位重组时节点跟着数据走，拖拽手势不会因 pointerInput 重建而中断
            vm.presets.forEach { p ->
                key(p.id) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        // background 必须在 itemModifier（平移层）之内，
                        // 否则拖拽让位时背景留在原地、行内容平移出去被卡片裁掉
                        .then(drag.itemModifier(p.id))
                        .background(c.surface)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.DragHandle, contentDescription = null,
                        tint = c.muted, modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    // 星标
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { vm.togglePresetHome(p.id) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (p.home) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                            contentDescription = null,
                            tint = if (p.home) c.warn else c.muted,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f).clickable { vm.openPresetEditor(p.id, fromWorkspace = false) }) {
                        Text(p.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                        Text(p.note.ifEmpty { "无备注" }, fontSize = 12.sp, color = c.muted)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (p.type == "raw") "原始命令" else "参数预设", fontSize = 10.sp, color = c.muted)
                            Text(if (p.builtin) "内置" else "自定义", fontSize = 10.sp, color = c.muted)
                        }
                    }
                    // 使用
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.surface2)
                            .border(1.dp, c.line, RoundedCornerShape(10.dp))
                            .clickable {
                                vm.applyPresetById(p.id)
                                vm.goPage("work")
                                vm.showToast("已应用预设")
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.ArrowForward, contentDescription = null, tint = c.ink, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    // 编辑
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.surface2)
                            .border(1.dp, c.line, RoundedCornerShape(10.dp))
                            .clickable { vm.openPresetEditor(p.id, fromWorkspace = false) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, tint = c.ink, modifier = Modifier.size(16.dp))
                    }
                    if (p.type != "raw") {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(c.surface2)
                                .border(1.dp, c.line, RoundedCornerShape(10.dp))
                                .clickable { deleteTarget = p },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = c.danger, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                }
            }
        }
        FieldHint("长按可拖动排序（工作台与主页同步）。点亮星标可显示在主页快速开始。点击名称可编辑。", modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(88.dp))
    }
    // 右下角新建预设
    Box(
        Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 16.dp, bottom = 16.dp)
            .size(52.dp)
            .clip(CircleShape)
            .background(c.accent)
            .clickable { vm.openPresetEditor(null, fromWorkspace = true) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = "新建预设", tint = Color.White, modifier = Modifier.size(26.dp))
    }
    }

    deleteTarget?.let { p ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除预设") },
            text = { Text("删除预设「${p.name}」？") },
            confirmButton = {
                TextButton(onClick = { vm.deletePreset(p.id); deleteTarget = null }) { Text("删除", color = c.danger) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

// ==================== 预设编辑 ====================
@Composable
fun PresetEditScreen(vm: AppViewModel) {
    val c = LocalStudioColors.current
    val fm = LocalFocusManager.current
    var showDelete by remember { mutableStateOf(false) }
    val ed = vm.editor

    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { fm.clearFocus() } }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        CardBox {
            FieldColumn {
                FieldHead("名称")
                Spacer(Modifier.height(8.dp))
                StudioInput(value = ed.name, onValueChange = { vm.editor = vm.editor.copy(name = it) }, placeholder = "例如 朋友圈压缩")
            }
            FieldColumn {
                FieldHead("备注")
                Spacer(Modifier.height(8.dp))
                StudioInput(value = ed.note, onValueChange = { vm.editor = vm.editor.copy(note = it) }, placeholder = "用途说明，可选")
            }
            FieldColumn {
                FieldHead("类型")
                Spacer(Modifier.height(8.dp))
                SegRow(
                    options = listOf("params" to "参数预设", "raw" to "原始命令"),
                    selected = ed.type,
                    onSelect = { vm.editor = vm.editor.copy(type = it) },
                )
            }
            if (ed.type == "params") {
                FieldColumn {
                    FieldHead("参数摘要 / 命令片段")
                    Spacer(Modifier.height(8.dp))
                    StudioInput(
                        value = ed.cmdText,
                        onValueChange = { vm.editor = vm.editor.copy(cmdText = it) },
                        placeholder = "-crf 23 -preset medium",
                        mono = true, minLines = 4, maxLines = 8,
                    )
                    FieldHint("保存后映射到工作台控件；无法映射的片段进入自定义参数。")
                }
            } else {
                FieldColumn {
                    FieldHead("完整原始命令")
                    Spacer(Modifier.height(8.dp))
                    StudioInput(
                        value = ed.rawText,
                        onValueChange = { vm.editor = vm.editor.copy(rawText = it) },
                        placeholder = "ffmpeg -i <input> -c:v libx264 -crf 23 out.mp4",
                        mono = true, minLines = 5, maxLines = 10,
                    )
                    FieldHint("使用 <input> 作为输入占位符；也可直接写完整命令。")
                }
            }
            FieldColumn {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("显示在主页", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                        Text("出现在主页快速开始", fontSize = 12.sp, color = c.muted)
                    }
                    SwitchTiny(ed.home) { vm.editor = vm.editor.copy(home = it) }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("取消", { vm.goPage("presets") }, Modifier.weight(1f))
            Btn("保存", { vm.saveEditor() }, Modifier.weight(1f), kind = "primary")
        }
        if (ed.editingId != null && ed.type != "raw") {
            Spacer(Modifier.height(10.dp))
            Btn("删除此预设", { showDelete = true }, Modifier.fillMaxWidth(), kind = "danger")
        }
        Spacer(Modifier.height(88.dp))
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除预设") },
            text = { Text("删除预设「${ed.name}」？") },
            confirmButton = {
                TextButton(onClick = { vm.deletePreset(ed.editingId!!); showDelete = false }) {
                    Text("删除", color = c.danger)
                }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("取消") } },
        )
    }
}
