package com.ffmpegstudio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Videocam
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ffmpegstudio.data.AppViewModel
import com.ffmpegstudio.data.Sheet
import com.ffmpegstudio.data.StdValues
import com.ffmpegstudio.logic.CommandBuilder
import com.ffmpegstudio.ui.BlockCard
import com.ffmpegstudio.ui.BtnSmall
import com.ffmpegstudio.ui.FieldColumn
import com.ffmpegstudio.ui.FieldHead
import com.ffmpegstudio.ui.FieldHint
import com.ffmpegstudio.ui.LocalStudioColors
import com.ffmpegstudio.ui.LogBlock
import com.ffmpegstudio.ui.MediaThumb
import com.ffmpegstudio.ui.R_CARD
import com.ffmpegstudio.ui.R_FIELD
import com.ffmpegstudio.ui.SectionTitle
import com.ffmpegstudio.ui.SegRow
import com.ffmpegstudio.ui.SelectField
import com.ffmpegstudio.ui.SelectOption
import com.ffmpegstudio.ui.SliderField
import com.ffmpegstudio.ui.StudioInput
import com.ffmpegstudio.ui.SwitchTiny

@Composable
fun WorkScreen(vm: AppViewModel, pick: () -> Unit) {
    val ws = vm.ws
    val fm = LocalFocusManager.current
    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { fm.clearFocus() } }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        FileCard(vm, pick)
        // 与下方参数块（BlockCard bottom 10dp）保持一致的间距
        Spacer(Modifier.height(10.dp))

        // ---- 预设 ----
        BlockCard(
            icon = Icons.Outlined.Tune,
            title = "预设",
            summary = presetSummary(vm),
            expanded = vm.blockOpen["presets"] ?: true,
            onToggle = { vm.toggleBlock("presets") },
        ) {
            FieldColumn {
                PresetGrid(vm)
                PresetToolbar(vm)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BtnSmall("保存为预设", { vm.openSheet(Sheet.PRESET_SAVE_CHOICE) }, Modifier.weight(1f), kind = "primary")
                    BtnSmall("查看详情", {
                        vm.presetInfoId = ws.activePresetId
                        vm.openSheet(Sheet.PRESET_INFO)
                    }, Modifier.weight(1f))
                }
            }
        }

        // ---- 原始命令（原始命令模式时显示） ----
        if (ws.isRawMode) {
            BlockCard(
                icon = Icons.Outlined.Terminal,
                title = "原始命令",
                summary = rawCmdSummary(ws.rawCmd),
                expanded = vm.blockOpen["rawcmd"] ?: true,
                onToggle = { vm.toggleBlock("rawcmd") },
            ) {
                FieldColumn {
                    FieldHead("完整命令") {
                        BtnSmall("展开编辑", { vm.openSheet(Sheet.RAWCMD) })
                    }
                    Spacer(Modifier.height(8.dp))
                    StudioInput(
                        value = ws.rawCmd,
                        onValueChange = { v ->
                            vm.update { it.copy(rawCmd = v) }
                            vm.updateRawPreset(v)
                        },
                        placeholder = "ffmpeg -i <input> -c:v libx264 -crf 23 -preset medium -c:a aac -b:a 192k <output>",
                        mono = true, minLines = 3, maxLines = 6,
                    )
                    FieldHint("可在此直接输入；点「展开编辑」打开大窗。<input> / <output> 会替换为实际路径。")
                }
            }
        }

        // ---- 视频参数 ----
        if (!ws.isRawMode) {
            BlockCard(
                icon = Icons.Outlined.Videocam,
                title = "视频参数",
                summary = videoSummary(vm),
                expanded = vm.blockOpen["video"] ?: false,
                onToggle = { vm.toggleBlock("video") },
            ) {
                Column {
                    SwitchFieldColumn("移除视频", "输出仅音频流（-vn）", ws.noVideo) { vm.setNoVideo(it) }
                    if (!ws.noVideo) VideoCodecFields(vm) else FieldColumn { FieldHint("已开启移除视频，编码器与质量参数已隐藏。") }
                }
            }

            // ---- 音频参数 ----
            BlockCard(
                icon = Icons.Outlined.MusicNote,
                title = "音频参数",
                summary = audioSummary(vm),
                expanded = vm.blockOpen["audio"] ?: false,
                onToggle = { vm.toggleBlock("audio") },
            ) {
                Column {
                    SwitchFieldColumn("移除音频", "输出仅视频流（-an）", ws.noAudio) { vm.setNoAudio(it) }
                    if (!ws.noAudio) AudioCodecFields(vm) else FieldColumn { FieldHint("已开启移除音频，编码器与码率已隐藏。") }
                }
            }

            // ---- 裁剪时间 ----
            BlockCard(
                icon = Icons.Outlined.ContentCut,
                title = "裁剪时间",
                summary = trimSummary(vm),
                expanded = vm.blockOpen["trim"] ?: false,
                onToggle = { vm.toggleBlock("trim") },
            ) { TrimFields(vm) }

            // ---- 输出 ----
            BlockCard(
                icon = Icons.Outlined.Folder,
                title = "输出",
                summary = outputSummary(vm),
                expanded = vm.blockOpen["output"] ?: false,
                onToggle = { vm.toggleBlock("output") },
            ) { OutputFields(vm) }

            // ---- 高级 ----
            BlockCard(
                icon = Icons.Outlined.Settings,
                title = "高级",
                summary = advSummary(vm),
                expanded = vm.blockOpen["advanced"] ?: false,
                onToggle = { vm.toggleBlock("advanced") },
            ) {
                FieldColumn {
                    FieldHead("追加自定义参数") {
                        BtnSmall("编辑", { vm.openSheet(Sheet.PARAMS) }, kind = "primary")
                    }
                    Spacer(Modifier.height(8.dp))
                    LogBlock(vm.ws.custom.ifEmpty { "（空）在 -i 输入之后追加" })
                }
            }
        }
        Spacer(Modifier.height(200.dp))
    }
}

@Composable
private fun SwitchFieldColumn(title: String, desc: String, on: Boolean, onChange: (Boolean) -> Unit) {
    FieldColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LocalStudioColors.current.ink)
                Text(desc, fontSize = 12.sp, color = LocalStudioColors.current.muted, lineHeight = 16.sp)
            }
            SwitchTiny(on) { onChange(it) }
        }
    }
}

@Composable
private fun FileCard(vm: AppViewModel, pick: () -> Unit) {
    val c = LocalStudioColors.current
    val ws = vm.ws
    Row(
        Modifier
            .fillMaxWidth()
            .clip(R_CARD)
            .background(c.surface)
            .border(1.dp, c.line, R_CARD)
            .clickable { pick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 已选文件显示视频首帧预览图（取不到帧回落胶片图标）
        if (ws.input != null) {
            MediaThumb(listOf(ws.input.path))
        } else {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.surface2),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Movie, contentDescription = null, tint = c.info, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(ws.input?.name ?: "未选择文件", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
            Text(
                ws.input?.path ?: "点击选择输入媒体文件",
                fontSize = 12.sp, color = c.muted, lineHeight = 16.sp,
            )
        }
    }
}

private fun presetSummary(vm: AppViewModel): String {
    val p = vm.presets.find { it.id == vm.ws.activePresetId } ?: return "—"
    return if (p.type == "raw") p.name + " · 原始" else p.name
}

private fun rawCmdSummary(raw: String): String {
    val t = raw.trim()
    return when {
        t.isEmpty() -> "未填写"
        t.length > 22 -> t.take(22) + "…"
        else -> t.replace(Regex("\\s+"), " ")
    }
}

private fun videoSummary(vm: AppViewModel): String {
    val ws = vm.ws
    return when {
        ws.noVideo -> "已移除视频"
        ws.vcodec == "copy" -> "复制视频流"
        else -> "CRF ${ws.crf} · ${ws.preset}" + (if (ws.res.isNotEmpty()) " · ${ws.res}" else "")
    }
}

private fun audioSummary(vm: AppViewModel): String {
    val ws = vm.ws
    return when {
        ws.noAudio -> "已移除音频"
        ws.acodec == "copy" -> "复制音频流"
        else -> "${ws.acodec} ${ws.abitrate}"
    }
}

private fun trimSummary(vm: AppViewModel): String {
    val ws = vm.ws
    val toMode = ws.trimEndMode == "to"
    return when (ws.trimMode) {
        "off" -> "不设置"
        "together" -> {
            if (ws.trimStart.isEmpty() && ws.trimEnd.isEmpty()) "一起 · 未填"
            else {
                val a = ws.trimStart.ifEmpty { "0" }
                val b = if (ws.trimEnd.isNotEmpty()) (if (toMode) "→ " + ws.trimEnd else "时长 " + ws.trimEnd) else "→ 结尾"
                "一起 · $a $b"
            }
        }
        else -> {
            val v = if (ws.vTrimStart.isNotEmpty() || ws.vTrimEnd.isNotEmpty()) "有" else "—"
            val a = if (ws.aTrimStart.isNotEmpty() || ws.aTrimEnd.isNotEmpty()) "有" else "—"
            "分开 · V:$v A:$a"
        }
    }
}

private fun outputSummary(vm: AppViewModel): String =
    if (vm.ws.overwriteSource) "覆盖源（先临时）" else vm.ws.outputPath.take(28).ifEmpty { "默认" }

private fun advSummary(vm: AppViewModel): String {
    val ws = vm.ws
    val opts = listOfNotNull(
        if (ws.forceY) "-y" else null,
        if (ws.deleteSource) "删源" else null,
        if (ws.overwriteSource) "覆盖源" else null,
        ws.custom.ifEmpty { null },
    )
    return opts.joinToString(" ").ifEmpty { "无" }
}

// ---------- 预设网格 ----------
@Composable
private fun PresetGrid(vm: AppViewModel) {
    val c = LocalStudioColors.current
    val ws = vm.ws
    val cols = vm.presetCols
    // 收起时显示「默认展开行数」× 每行数量（在预设管理页设置）
    val visible = if (ws.presetExpanded) vm.presets else vm.presets.take(cols * vm.presetRows)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        visible.chunked(cols).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { p ->
                    val active = p.id == ws.activePresetId
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (active) c.accent else c.surface)
                            .border(1.dp, if (active) c.accent else c.line, RoundedCornerShape(12.dp))
                            .clickable { vm.applyPresetById(p.id) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .height(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            p.name, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            color = if (active) Color.White else c.ink,
                            maxLines = 1, modifier = Modifier.weight(1f, fill = false),
                        )
                        if (p.type == "raw") {
                            Spacer(Modifier.width(4.dp))
                            Text("RAW", fontSize = 10.sp, color = if (active) Color.White.copy(alpha = 0.75f) else c.muted)
                        }
                    }
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PresetToolbar(vm: AppViewModel) {
    val c = LocalStudioColors.current
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (vm.ws.presetExpanded) "收起" else "展开全部",
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.info,
            modifier = Modifier
                .clickable { vm.update { it.copy(presetExpanded = !it.presetExpanded) } }
                .padding(vertical = 6.dp),
        )
        Spacer(Modifier.weight(1f))
        Text(
            "管理", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.info,
            modifier = Modifier
                .clickable { vm.goPage("presets") }
                .padding(vertical = 6.dp),
        )
    }
}

// ---------- 视频字段 ----------
// 标准选项定义在 StdValues（AppViewModel 的 applyParams 也要用），这里取引用
private val STD_VCODEC = StdValues.VCODEC
private val STD_ACODEC = StdValues.ACODEC
private val STD_PRESET = StdValues.PRESET
private val STD_RES = StdValues.RES

@Composable
private fun VideoCodecFields(vm: AppViewModel) {
    val ws = vm.ws
    val isCopy = ws.vcodec == "copy"
    Column {
        FieldColumn {
            FieldHead("视频编码器")
            Spacer(Modifier.height(8.dp))
            SelectField(
                options = listOf(
                    SelectOption("libx264", "H.264（libx264）"),
                    SelectOption("libx265", "H.265（libx265）"),
                    SelectOption("libvpx-vp9", "VP9（libvpx-vp9）"),
                    SelectOption("copy", "复制视频流"),
                    SelectOption("__custom", "自定义…"),
                ),
                value = if (!ws.vcodecCustom && ws.vcodec in STD_VCODEC) ws.vcodec else "__custom",
                onSelect = { sel ->
                    if (sel == "__custom") vm.update { it.copy(vcodecCustom = true) }
                    else vm.update { it.copy(vcodecCustom = false, vcodec = sel) }
                },
            )
            if (ws.vcodecCustom || ws.vcodec !in STD_VCODEC) {
                Spacer(Modifier.height(8.dp))
                StudioInput(
                    value = ws.vcodec,
                    onValueChange = { v -> vm.update { it.copy(vcodec = v.trim()) } },
                    placeholder = "例如 libaom-av1 / h264_mediacodec",
                )
            }
        }
        if (!isCopy) {
            FieldColumn {
                SliderField(
                    label = "CRF 质量",
                    value = ws.crf, min = 0, max = 51,
                    onIntChange = { v -> vm.update { it.copy(crf = v) } },
                    textValue = ws.crf.toString(),
                    onTextChange = { s -> s.toIntOrNull()?.let { vm.update { st -> st.copy(crf = it.coerceIn(0, 51)) } } },
                    hint = "0 无损 / 18 视觉无损 / 28 可接受 / 51 最差。数值越低质量越高、文件越大。",
                )
            }
            FieldColumn {
                FieldHead("编码预设 Preset")
                Spacer(Modifier.height(8.dp))
                SelectField(
                    options = STD_PRESET.map {
                        SelectOption(it, it + when (it) {
                            "medium" -> "（推荐）"
                            "ultrafast" -> "（最快，文件大）"
                            "veryslow" -> "（最慢，文件小）"
                            else -> ""
                        })
                } + SelectOption("__custom", "自定义…"),
                value = if (!ws.presetCustom && ws.preset in STD_PRESET) ws.preset else "__custom",
                onSelect = { sel ->
                    if (sel == "__custom") vm.update { it.copy(presetCustom = true) }
                    else vm.update { it.copy(presetCustom = false, preset = sel) }
                },
            )
            if (ws.presetCustom || ws.preset !in STD_PRESET) {
                    Spacer(Modifier.height(8.dp))
                    StudioInput(
                        value = ws.preset,
                        onValueChange = { v -> vm.update { it.copy(preset = v.trim()) } },
                        placeholder = "填入自定义 preset 名称",
                    )
                }
            }
            FieldColumn {
                FieldHead("分辨率")
                Spacer(Modifier.height(8.dp))
                SelectField(
                    options = listOf(
                        SelectOption("", "保持原始"),
                        SelectOption("1920:1080", "1080p"),
                        SelectOption("1280:720", "720p"),
                        SelectOption("854:480", "480p"),
                        SelectOption("__custom", "自定义 W:H"),
                    ),
                    value = if (!ws.resCustom && ws.res in STD_RES) ws.res else "__custom",
                    onSelect = { sel ->
                        if (sel == "__custom") vm.update { it.copy(resCustom = true) }
                        else vm.update { it.copy(resCustom = false, res = sel) }
                    },
                )
                if (ws.resCustom || ws.res !in STD_RES) {
                    Spacer(Modifier.height(8.dp))
                    StudioInput(
                        value = ws.res,
                        onValueChange = { v -> vm.update { it.copy(res = v.trim()) } },
                        placeholder = "例如 1920:1080",
                    )
                }
            }
            FieldColumn {
                SliderField(
                    label = "帧率 FPS",
                    value = ws.fps, min = 0, max = 240,
                    onIntChange = { v -> vm.update { it.copy(fps = v) } },
                    textValue = if (ws.fps == 0) "" else ws.fps.toString(),
                    onTextChange = { s ->
                        val v = s.trim().toIntOrNull() ?: 0
                        vm.update { it.copy(fps = v.coerceIn(0, 240)) }
                    },
                    ticks = listOf("0 保持", "30", "60", "120", "240"),
                )
                FieldHint("0 保持 / 30 / 60 / 120 / 240 · 点数字可手改")
                if (ws.fps > 0) {
                    Spacer(Modifier.height(12.dp))
                    FieldHead("调整方式")
                    Spacer(Modifier.height(8.dp))
                    SelectField(
                        options = listOf(
                            SelectOption("default", "默认（-r，最近邻丢/重帧）"),
                            SelectOption("drop", "精确丢帧（fps 滤镜）"),
                            SelectOption("blend", "混合补帧（framerate，顺滑）"),
                            SelectOption("smooth", "平滑插帧（minterpolate，最顺最慢）"),
                        ),
                        value = ws.fpsMode,
                        onSelect = { sel -> vm.update { it.copy(fpsMode = sel) } },
                    )
                    FieldHint("144→60 这类非整数比，丢帧会一顿一顿。顺滑用「混合补帧」；最自然用「平滑插帧」。")
                }
            }
        }
    }
}

// ---------- 音频字段 ----------
@Composable
private fun AudioCodecFields(vm: AppViewModel) {
    val ws = vm.ws
    val hideBitrate = ws.acodec == "copy" || ws.acodec == "flac" || ws.acodec == "pcm_s16le"
    Column {
        FieldColumn {
            FieldHead("音频编码器")
            Spacer(Modifier.height(8.dp))
            SelectField(
                options = listOf(
                    SelectOption("aac", "AAC"),
                    SelectOption("libmp3lame", "MP3"),
                    SelectOption("flac", "FLAC"),
                    SelectOption("pcm_s16le", "WAV（PCM）"),
                    SelectOption("copy", "复制音频流"),
                    SelectOption("__custom", "自定义…"),
                ),
                value = if (!ws.acodecCustom && ws.acodec in STD_ACODEC) ws.acodec else "__custom",
                onSelect = { sel ->
                    if (sel == "__custom") vm.update { it.copy(acodecCustom = true) }
                    else vm.update { it.copy(acodecCustom = false, acodec = sel) }
                },
            )
            if (ws.acodecCustom || ws.acodec !in STD_ACODEC) {
                Spacer(Modifier.height(8.dp))
                StudioInput(
                    value = ws.acodec,
                    onValueChange = { v -> vm.update { it.copy(acodec = v.trim()) } },
                    placeholder = "例如 libopus / ac3 / libfdk_aac",
                )
            }
        }
        if (!hideBitrate) {
            FieldColumn {
                FieldHead("音频码率") { AbitrateInput(vm) }
                Spacer(Modifier.height(8.dp))
                SegRow(
                    options = listOf("96k" to "96k", "128k" to "128k", "192k" to "192k", "256k" to "256k", "320k" to "320k"),
                    selected = ws.abitrate,
                    onSelect = { sel ->
                        vm.update { it.copy(abitrate = sel) }
                    },
                )
                FieldHint("可点数字手改，例如 160k / 96k / 1.5M。下方为常用档位。")
            }
        }
    }
}

/** 码率输入：失焦时规范化（对应 Web 版 change 事件 normalizeAbitrate） */
@Composable
private fun AbitrateInput(vm: AppViewModel) {
    val c = LocalStudioColors.current
    var text by remember { mutableStateOf(vm.ws.abitrate) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(vm.ws.abitrate) { if (!focused && text != vm.ws.abitrate) text = vm.ws.abitrate }
    Box(
        Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(c.accentDim),
    ) {
        androidx.compose.material3.TextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                color = c.accent, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            ),
            colors = androidx.compose.material3.TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = c.accent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged {
                    focused = it.isFocused
                    if (!it.isFocused) {
                        val norm = CommandBuilder.normalizeAbitrate(text)
                        text = norm
                        vm.update { s -> s.copy(abitrate = norm) }
                    }
                },
        )
    }
}

// ---------- 裁剪字段 ----------
@Composable
private fun TrimFields(vm: AppViewModel) {
    val ws = vm.ws
    val hms = ws.trimFmt == "hms"
    Column {
        FieldColumn {
            FieldHead("裁剪范围")
            Spacer(Modifier.height(8.dp))
            SegRow(
                options = listOf("together" to "一起", "split" to "分开", "off" to "不设置"),
                selected = ws.trimMode,
                onSelect = { sel -> vm.update { it.copy(trimMode = sel) } },
            )
            FieldHint("一起：音视频同一时间窗；分开：各自起止；不设置：不裁剪。")
        }
        if (ws.trimMode != "off") {
            FieldColumn {
                FieldHead("输入格式")
                Spacer(Modifier.height(8.dp))
                SegRow(
                    options = listOf("sec" to "秒数", "hms" to "时:分:秒"),
                    selected = ws.trimFmt,
                    onSelect = { vm.setTrimFmt(it) },
                )
                FieldHint("秒数：12.345 · 时分秒：00:00:12.345。支持毫秒，小数位能写多少算多少。")
            }
        }
        when (ws.trimMode) {
            "together" -> {
                val toMode = ws.trimEndMode == "to"
                FieldColumn {
                    FieldHead("起点 -ss")
                    Spacer(Modifier.height(8.dp))
                    StudioInput(
                        value = ws.trimStart,
                        onValueChange = { v -> vm.update { it.copy(trimStart = v) } },
                        placeholder = if (hms) "例如 00:00:10.250（留空从开头）" else "例如 10.25（留空从开头）",
                    )
                }
                FieldColumn {
                    FieldHead("终点方式")
                    Spacer(Modifier.height(8.dp))
                    SegRow(
                        options = listOf("to" to "结束时刻 -to", "t" to "时长 -t"),
                        selected = ws.trimEndMode,
                        onSelect = { sel -> vm.update { it.copy(trimEndMode = sel) } },
                    )
                }
                FieldColumn {
                    FieldHead(if (toMode) "结束时刻 -to" else "时长 -t")
                    Spacer(Modifier.height(8.dp))
                    StudioInput(
                        value = ws.trimEnd,
                        onValueChange = { v -> vm.update { it.copy(trimEnd = v) } },
                        placeholder = if (hms) (if (toMode) "例如 00:01:30.500" else "例如 00:00:20.250")
                        else (if (toMode) "例如 90.5" else "例如 20.25"),
                    )
                    FieldHint(if (toMode) "相对片源的时间点，留空则不添加 -to。" else "从起点算起的持续时长，留空则不添加 -t。")
                }
            }
            "split" -> {
                FieldColumn { FieldHint("分开裁剪时将使用 trim/atrim 滤镜；与「复制流」同用时需重编码。") }
                SectionTitle("视频")
                SplitTrimFields(vm, isVideo = true)
                SectionTitle("音频")
                SplitTrimFields(vm, isVideo = false)
            }
            else -> {
                FieldColumn { FieldHint("当前不裁剪，命令不会包含 -ss / -t / -to。") }
            }
        }
    }
}

@Composable
private fun SplitTrimFields(vm: AppViewModel, isVideo: Boolean) {
    val ws = vm.ws
    val hms = ws.trimFmt == "hms"
    val start = if (isVideo) ws.vTrimStart else ws.aTrimStart
    val end = if (isVideo) ws.vTrimEnd else ws.aTrimEnd
    val endMode = if (isVideo) ws.vTrimEndMode else ws.aTrimEndMode
    val toMode = endMode == "to"
    val prefix = if (isVideo) "视频" else "音频"
    val setStart: (String) -> Unit = { v -> vm.update { if (isVideo) it.copy(vTrimStart = v) else it.copy(aTrimStart = v) } }
    val setEnd: (String) -> Unit = { v -> vm.update { if (isVideo) it.copy(vTrimEnd = v) else it.copy(aTrimEnd = v) } }
    val setEndMode: (String) -> Unit = { sel -> vm.update { if (isVideo) it.copy(vTrimEndMode = sel) else it.copy(aTrimEndMode = sel) } }
    Column {
        FieldColumn {
            FieldHead("$prefix 起点 -ss")
            Spacer(Modifier.height(8.dp))
            StudioInput(
                value = start,
                onValueChange = setStart,
                placeholder = if (hms) "例如 00:00:10.250（留空从开头）" else "例如 10.25（留空从开头）",
            )
        }
        FieldColumn {
            FieldHead("$prefix 终点方式")
            Spacer(Modifier.height(8.dp))
            SegRow(
                options = listOf("to" to "结束时刻 -to", "t" to "时长 -t"),
                selected = endMode,
                onSelect = setEndMode,
            )
        }
        FieldColumn {
            FieldHead(if (toMode) "$prefix 结束时刻 -to" else "$prefix 时长 -t")
            Spacer(Modifier.height(8.dp))
            StudioInput(
                value = end,
                onValueChange = setEnd,
                placeholder = if (hms) (if (toMode) "例如 00:01:30.500" else "例如 00:00:20.250")
                else (if (toMode) "例如 90.5" else "例如 20.25"),
            )
            FieldHint(if (toMode) "相对片源的时间点，留空则不添加。" else "从${prefix}起点算起的时长，留空则不添加。")
        }
    }
}

// ---------- 输出字段 ----------
private val FORMATS = listOf(
    "auto" to "自动", "source" to "跟随原文件",
    "mp4" to "MP4", "mkv" to "MKV", "mov" to "MOV", "webm" to "WebM", "mp3" to "MP3",
    "m4a" to "M4A", "wav" to "WAV", "flac" to "FLAC", "gif" to "GIF",
    "custom" to "自定义",
)

@Composable
private fun OutputFields(vm: AppViewModel) {
    val ws = vm.ws
    val c = LocalStudioColors.current
    var pathField by remember { mutableStateOf(TextFieldValue(ws.outputPath)) }
    LaunchedEffect(ws.outputPath) {
        if (pathField.text != ws.outputPath) pathField = TextFieldValue(ws.outputPath)
    }
    Column {
        FieldColumn {
            FieldHead("容器格式")
            Spacer(Modifier.height(8.dp))
            SelectField(
                options = FORMATS.map { SelectOption(it.first, it.second) },
                value = ws.format,
                onSelect = { sel -> vm.update { it.copy(format = sel) } },
            )
            if (ws.format == "custom") {
                Spacer(Modifier.height(8.dp))
                StudioInput(
                    value = ws.formatCustom,
                    onValueChange = { v -> vm.update { it.copy(formatCustom = v) } },
                    placeholder = "输出后缀名，例如 ts、mkv、opus",
                    mono = true,
                )
            }
            FieldHint(
                when (ws.format) {
                    "auto" -> "按流和编码自动选容器：仅音频时按音频编码（FLAC→flac、AAC→m4a、MP3→mp3、未知→mka）；" +
                        "重编码视频默认 MP4，音频为 FLAC/PCM 或编码未知时用 MKV；复制流跟随源。" +
                        "当前解析：." + vm.resolvedFormat()
                    "source" -> "输出与源文件同后缀。当前解析：." + vm.resolvedFormat()
                    "custom" -> "ffmpeg 按后缀名选容器。当前解析：." + vm.resolvedFormat()
                    else -> ""
                }
            )
        }
        FieldColumn {
            FieldHead("输出路径模板")
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.TextField(
                value = pathField,
                onValueChange = {
                    pathField = it
                    vm.update { s -> s.copy(outputPath = it.text) }
                },
                placeholder = { Text("<raw_name>_out.<ext>", fontSize = 14.sp, color = c.muted) },
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, color = c.ink),
                shape = R_FIELD,
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = c.surface2,
                    unfocusedContainerColor = c.surface2,
                    focusedIndicatorColor = c.accent,
                    unfocusedIndicatorColor = c.line,
                    cursorColor = c.accent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            val insert: (String) -> Unit = { tpl ->
                val selStart = pathField.selection.start.coerceAtLeast(0).coerceAtMost(pathField.text.length)
                val selEnd = pathField.selection.end.coerceAtLeast(0).coerceAtMost(pathField.text.length)
                val newText = pathField.text.substring(0, selStart) + tpl + pathField.text.substring(selEnd)
                val nv = TextFieldValue(newText, TextRange(selStart + tpl.length))
                pathField = nv
                vm.update { it.copy(outputPath = newText) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TplChip("<raw_dir>") { insert(it) }
                TplChip("<raw_name>") { insert(it) }
                TplChip("<ext>") { insert(it) }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TplChip("<N>") { insert(it) }
                TplChip("<T,val=yyyy-MM-dd_HH-mm-ss>") { insert(it) }
            }
            val err = if (ws.overwriteSource) null else vm.validateOut()
            if (err != null) FieldHint(err, color = c.danger)
            val resolved = vm.resolveOutput()
            val dest = vm.finalOutputPath()
            Text(
                if (ws.overwriteSource) "临时 → $resolved\n完成 → $dest" else "→ $resolved",
                fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = c.info,
                lineHeight = 15.sp, modifier = Modifier.padding(top = 6.dp),
            )
        }
        SwitchFieldColumn("覆盖已存在文件", "添加 -y 参数", ws.forceY) { v -> vm.update { it.copy(forceY = v) } }
        SwitchFieldColumn("完成后删除源文件", "成功后删除输入文件", ws.deleteSource) { v -> vm.update { it.copy(deleteSource = v) } }
        SwitchFieldColumn("覆盖源文件", "先输出到默认目录，成功后再移回原文件路径。避免处理中原文件被占用或损坏。", ws.overwriteSource) { vm.setOverwriteSource(it) }
    }
}

@Composable
private fun TplChip(tpl: String, onInsert: (String) -> Unit) {
    val c = LocalStudioColors.current
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(10.dp))
            .clickable { onInsert(tpl) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(tpl, fontSize = 12.sp, color = c.ink, fontWeight = FontWeight.Medium)
    }
}
