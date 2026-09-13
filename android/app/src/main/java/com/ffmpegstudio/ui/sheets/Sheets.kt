package com.ffmpegstudio.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ffmpegstudio.data.AppViewModel
import com.ffmpegstudio.data.ChatMsg
import com.ffmpegstudio.data.Sheet
import com.ffmpegstudio.ui.Badge
import com.ffmpegstudio.ui.Btn
import com.ffmpegstudio.ui.BtnSmall
import com.ffmpegstudio.ui.FieldHint
import com.ffmpegstudio.ui.IconBtn
import com.ffmpegstudio.ui.LocalStudioColors
import com.ffmpegstudio.ui.LogBlock
import com.ffmpegstudio.ui.SheetDialog
import com.ffmpegstudio.ui.StudioInput

// ==================== 执行日志 ====================
@Composable
fun LogSheet(vm: AppViewModel) {
    val c = LocalStudioColors.current
    val run = vm.run
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    LaunchedEffect(run.lines.size) {
        if (run.lines.isNotEmpty()) listState.animateScrollToItem(run.lines.size - 1)
    }
    SheetDialog(
        title = "执行日志",
        onDismiss = { vm.closeSheet() },
        trailing = {
            Badge(
                when {
                    run.running -> "run"
                    run.ok -> "ok"
                    else -> "fail"
                },
                when {
                    run.running -> "运行中"
                    run.ok -> "成功"
                    else -> "失败"
                },
            )
        },
        footer = {
            Btn("停止", { vm.stopRun() }, Modifier.weight(1f), enabled = run.running)
            Btn("收起", { vm.closeSheet() }, Modifier.weight(1f), kind = "primary")
        },
        tall = true,
    ) {
        // 日志列表不能套 SelectionContainer / ClearableSelection：
        // 选中后滚动，LazyColumn 回收离屏项会直接崩溃；改为「复制全部」一键复制
        Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${run.progress}%", fontSize = 12.sp, color = c.muted)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(999.dp)).background(c.surface2)) {
                        Box(
                            Modifier
                                .fillMaxWidth(run.progress / 100f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(c.accent)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(String.format(java.util.Locale.US, "%.1fs", run.elapsed), fontSize = 12.sp, color = c.muted)
                }
                Spacer(Modifier.height(10.dp))
                // 实时摘要：把执行统计翻译成易读信息（进度/已编码时长/帧率/速度）
                val LU = java.util.Locale.US
                val summary = when {
                    run.running -> buildString {
                        append("正在处理")
                        if (run.progress > 0) append(" · ").append(run.progress).append("%")
                        if (run.processedMs > 0) {
                            append(" · 已编码 ").append(fmtDur(run.processedMs))
                            if (run.totalMs > 0) append(" / ").append(fmtDur(run.totalMs))
                        }
                        if (run.fps >= 1f) append(" · ").append(run.fps.toInt()).append(" 帧/秒")
                        if (run.speed > 0) append(" · 速度 ").append(String.format(LU, "%.2f", run.speed)).append("x")
                    }
                    run.ok -> buildString {
                        append("处理完成 · 用时 ").append(String.format(LU, "%.1f 秒", run.elapsed))
                        if (run.outSizeB > 0) append(" · 输出 ").append(vm.humanSize(run.outSizeB))
                        // 平均帧率/速度在历史记录卡片里查看
                    }
                    else -> "处理失败 · 用时 " + String.format(LU, "%.1f 秒", run.elapsed)
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (run.ok || run.running) c.accentDim else c.dangerDim)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        summary,
                        fontSize = 12.sp, lineHeight = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (run.ok || run.running) c.accent else c.danger,
                    )
                }
                // 完成后：输出文件路径 + 一键打开
                if (!run.running && run.ok && run.outPath.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("输出文件", fontSize = 11.sp, color = c.muted)
                            Text(
                                run.outPath,
                                fontSize = 10.sp, lineHeight = 14.sp,
                                color = c.ink, fontFamily = FontFamily.Monospace,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        BtnSmall("打开", { vm.openFile(run.outPath) }, kind = "primary")
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("日志", fontSize = 11.sp, color = c.muted, modifier = Modifier.weight(1f))
                    BtnSmall("复制全部", {
                        clipboard.setText(AnnotatedString(vm.logText()))
                        vm.showToast("日志已复制")
                    })
                }
                Spacer(Modifier.height(6.dp))
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 380.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.monoBg)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (run.lines.isEmpty()) {
                        item { Text("等待执行…", fontSize = 11.sp, color = c.monoMuted, fontFamily = FontFamily.Monospace) }
                    }
                    items(run.lines) { line ->
                        val color = when (line.cls) {
                            "dim" -> c.monoMuted
                            "ok" -> c.logOk
                            "err" -> c.logErr
                            "info" -> c.logInfo
                            else -> c.monoInk
                        }
                        Text(
                            line.text, fontSize = 11.sp, lineHeight = 17.sp,
                            color = color, fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
    }
}

/** 毫秒 → 易读时长（21.3 秒 / 1 分 08 秒） */
private fun fmtDur(ms: Long): String {
    val s = ms / 1000.0
    return if (s >= 60) "${(s / 60).toInt()} 分 ${String.format(java.util.Locale.US, "%.0f", s % 60)} 秒"
    else String.format(java.util.Locale.US, "%.1f 秒", s)
}

/** 容器选择的展示标签（auto/source/custom/固定后缀） */
private fun formatLabel(f: String, custom: String): String = when (f) {
    "auto" -> "自动"
    "source" -> "跟随原文件"
    "custom" -> "自定义 ." + custom.ifEmpty { "mp4" }
    else -> f.uppercase()
}

// ==================== 确认执行 ====================
@Composable
fun ConfirmSheet(vm: AppViewModel) {
    val c = LocalStudioColors.current
    val ws = vm.ws
    val ap = vm.presets.find { it.id == ws.activePresetId }
    val video = if (ws.isRawMode) "原始命令"
    else if (ws.noVideo) "移除"
    else if (ws.vcodec == "copy") "复制"
    else "${ws.vcodec} CRF ${ws.crf} ${ws.preset}"
    val audio = if (ws.isRawMode) "—"
    else if (ws.noAudio) "移除"
    else ws.acodec + (if (ws.acodec == "copy") "" else " " + ws.abitrate)
    val opts = listOfNotNull(
        if (ws.forceY) "覆盖已有文件" else null,
        if (ws.deleteSource) "完成后删除源" else null,
        if (ws.overwriteSource) "覆盖源文件" else null,
    ).joinToString(" · ").ifEmpty { "无" }

    SheetDialog(
        title = "确认执行",
        onDismiss = { vm.closeSheet() },
        footer = {
            Btn("取消", { vm.closeSheet() }, Modifier.weight(1f))
            Btn("确认执行", { vm.closeSheet(); vm.startRun() }, Modifier.weight(1f), kind = "primary")
        },
    ) {
        KvRow("输入", ws.input?.path ?: "—")
        KvRow("输出", vm.finalOutputPath())
        KvRow("预设", ap?.name ?: "自定义")
        KvRow("视频", video)
        KvRow("音频", audio)
        KvRow("选项", opts)
        FieldHint("完整命令", modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
        LogBlock(vm.buildCmd(), maxLines = 8)
    }
}

@Composable
private fun KvRow(k: String, v: String) {
    val c = LocalStudioColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(k, fontSize = 13.sp, color = c.muted)
        Spacer(Modifier.width(12.dp))
        Text(v, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.ink, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End, lineHeight = 18.sp)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
}

// ==================== 完整命令 ====================
@Composable
fun FullCmdSheet(vm: AppViewModel) {
    val clipboard = LocalClipboardManager.current
    SheetDialog(
        title = "完整命令",
        onDismiss = { vm.closeSheet() },
        trailing = {
            IconBtn(Icons.Outlined.ContentCopy) {
                clipboard.setText(AnnotatedString(vm.buildCmd())); vm.showToast("命令已复制")
            }
        },
        footer = { Btn("关闭", { vm.closeSheet() }, Modifier.weight(1f)) },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(LocalStudioColors.current.monoBg)
                .padding(12.dp),
        ) {
            SelectionContainer {
                Text(
                    vm.buildCmd(), fontSize = 12.sp, lineHeight = 19.sp,
                    color = LocalStudioColors.current.monoInk, fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

// ==================== 完整报错/日志 ====================
@Composable
fun ErrSheet(vm: AppViewModel) {
    val clipboard = LocalClipboardManager.current
    SheetDialog(
        title = vm.errTitle,
        onDismiss = { vm.closeSheet() },
        trailing = {
            IconBtn(Icons.Outlined.ContentCopy) {
                clipboard.setText(AnnotatedString(vm.lastErr.ifEmpty { vm.errBody })); vm.showToast("完整信息已复制")
            }
        },
        footer = { Btn("关闭", { vm.closeSheet() }, Modifier.weight(1f)) },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(LocalStudioColors.current.monoBg)
                .padding(12.dp),
        ) {
            SelectionContainer {
                Text(
                    vm.errBody, fontSize = 12.sp, lineHeight = 19.sp,
                    color = LocalStudioColors.current.monoInk, fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

// ==================== 自定义参数 ====================
@Composable
fun ParamsSheet(vm: AppViewModel) {
    var text by remember { mutableStateOf(vm.ws.custom) }
    SheetDialog(
        title = "自定义参数",
        onDismiss = { vm.closeSheet() },
        footer = {
            Btn("取消", { vm.closeSheet() }, Modifier.weight(1f))
            Btn("保存", {
                vm.update { it.copy(custom = text.trim()) }
                vm.closeSheet()
                vm.showToast("已保存自定义参数")
            }, Modifier.weight(1f), kind = "primary")
        },
    ) {
        FieldHint("将在输入文件之后追加", modifier = Modifier.padding(bottom = 10.dp))
        StudioInput(
            value = text,
            onValueChange = { text = it },
            placeholder = "-vf scale=1280:-2",
            mono = true, minLines = 5, maxLines = 8,
        )
    }
}

// ==================== 原始命令编辑 ====================
@Composable
fun RawCmdSheet(vm: AppViewModel) {
    var text by remember { mutableStateOf(vm.ws.rawCmd) }
    SheetDialog(
        title = "编辑原始命令",
        onDismiss = { vm.closeSheet() },
        footer = {
            Btn("取消", { vm.closeSheet() }, Modifier.weight(1f))
            Btn("保存", {
                vm.saveRawCmd(text)
                vm.closeSheet()
            }, Modifier.weight(1f), kind = "primary")
        },
        tall = true,
    ) {
        StudioInput(
            value = text,
            onValueChange = { text = it },
            placeholder = "ffmpeg -i <input> -c:v libx264 -crf 23 -preset medium -c:a aac -b:a 192k <output>",
            mono = true, minLines = 8, maxLines = 14,
        )
        FieldHint("可粘贴完整 ffmpeg 命令。用 <input> 占位输入文件，<output> 占位输出路径；也可直接写死路径。")
    }
}

// ==================== 预设详情 ====================
@Composable
fun PresetInfoSheet(vm: AppViewModel) {
    val c = LocalStudioColors.current
    val p = vm.presets.find { it.id == vm.presetInfoId } ?: return
    SheetDialog(
        title = p.name,
        onDismiss = { vm.closeSheet() },
        footer = {
            Btn("编辑", {
                vm.closeSheet()
                vm.openPresetEditor(p.id, fromWorkspace = false)
            }, Modifier.weight(1f))
            Btn("使用", {
                vm.applyPresetById(p.id)
                vm.closeSheet()
                vm.showToast("已应用预设")
            }, Modifier.weight(1f), kind = "primary")
        },
    ) {
        if (p.type == "raw") {
            KvRow("备注", p.note.ifEmpty { "—" })
            KvRow("类型", "原始命令")
            FieldHint("命令", modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
            LogBlock(p.raw)
        } else {
            val pr = p.params
            KvRow("备注", p.note.ifEmpty { "—" })
            KvRow("类型", if (p.builtin) "内置" else "自定义")
            KvRow("CRF", pr.crf.toString())
            KvRow("Preset", pr.preset)
            KvRow("视频", if (pr.noVideo) "移除" else pr.vcodec)
            KvRow("分辨率", pr.res.ifEmpty { "原始" })
            KvRow("音频", if (pr.noAudio) "移除" else "${pr.acodec} ${pr.abitrate}")
            KvRow("格式", formatLabel(pr.format, pr.formatCustom))
        }
    }
}

// ==================== 保存预设（新建 / 覆盖） ====================
@Composable
fun PresetSaveChoiceSheet(vm: AppViewModel) {
    val c = LocalStudioColors.current
    val overwritable = vm.presets.filter { it.type != "raw" }
    SheetDialog(
        title = "保存预设",
        onDismiss = { vm.closeSheet() },
        footer = { Btn("取消", { vm.closeSheet() }, Modifier.weight(1f)) },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, c.line, RoundedCornerShape(12.dp))
                .clickable { vm.openPresetEditor(null, fromWorkspace = true) }
                .padding(16.dp),
        ) {
            Column {
                Text("保存为新预设", fontSize = 15.sp, color = c.ink)
                Text("创建一条新的自定义预设", fontSize = 12.sp, color = c.muted)
            }
        }
        SectionTitleLocal("覆盖已有预设")
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, c.line, RoundedCornerShape(14.dp)),
        ) {
            overwritable.forEach { p ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.openPresetEditor(p.id, fromWorkspace = true) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Column {
                        Text(p.name, fontSize = 15.sp, color = c.ink)
                        Text(p.note.ifEmpty { if (p.builtin) "内置" else "自定义" }, fontSize = 12.sp, color = c.muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitleLocal(text: String) {
    com.ffmpegstudio.ui.SectionTitle(text, modifier = Modifier.padding(top = 8.dp))
}

// ==================== AI 聊天 ====================
@Composable
fun AiChatSheet(vm: AppViewModel) {
    val c = LocalStudioColors.current
    val clipboard = LocalClipboardManager.current
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(vm.chat.messages.size) {
        if (vm.chat.messages.isNotEmpty()) listState.animateScrollToItem(vm.chat.messages.size - 1)
    }
    SheetDialog(
        title = "AI 助手",
        onDismiss = { vm.closeSheet() },
        trailing = { Badge("n", "未实现") },
        footer = {
            StudioInput(
                value = input,
                onValueChange = { input = it },
                placeholder = "描述你的转码需求…",
                modifier = Modifier.weight(1f),
            )
            Btn("发送", {
                if (input.isNotBlank()) {
                    vm.sendAi(input)
                    input = ""
                }
            }, kind = "primary")
        },
        tall = true,
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp, max = 420.dp)) {
            items(vm.chat.messages) { m -> ChatBubble(vm, m, clipboard) }
        }
    }
}

@Composable
private fun ChatBubble(vm: AppViewModel, m: ChatMsg, clipboard: androidx.compose.ui.platform.ClipboardManager) {
    val c = LocalStudioColors.current
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalAlignment = if (m.role == "user") Alignment.End else Alignment.Start) {
        val bg = when (m.role) {
            "user" -> c.accent
            else -> c.surface2
        }
        val fg = if (m.role == "user") Color.White else c.ink
        Column(
            Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(12.dp))
                .background(bg)
                .padding(12.dp),
        ) {
            Text(m.text, fontSize = 13.sp, lineHeight = 20.sp, color = fg)
            if (m.cmd.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.monoBg)
                        .padding(10.dp),
                ) {
                    // 不能套 SelectionContainer：在 LazyColumn 里选中后滚动会崩
                    Text(m.cmd, fontSize = 11.sp, color = c.monoInk, fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BtnSmall("复制命令", { clipboard.setText(AnnotatedString(m.cmd)); vm.showToast("命令已复制") })
                    BtnSmall("写入原始命令", { vm.applyAiRaw(m.cmd) }, kind = "primary")
                    BtnSmall("追加参数", { vm.applyAiParams(extractParams(m.cmd)) })
                }
            }
        }
    }
}

private fun extractParams(fullCmd: String): String =
    fullCmd.removePrefix("ffmpeg -i <input> ").removeSuffix(" <output>").trim()
