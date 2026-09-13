package com.ffmpegstudio.data

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.provider.MediaStore
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.FFmpegKitConfig
import com.antonkarpenko.ffmpegkit.FFmpegSessionCompleteCallback
import com.antonkarpenko.ffmpegkit.LogCallback
import com.antonkarpenko.ffmpegkit.StatisticsCallback
import com.antonkarpenko.ffmpegkit.ReturnCode
import com.ffmpegstudio.logic.CommandBuilder
import com.ffmpegstudio.logic.TimeUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------- 工作台状态（对应 Web 版 state 对象） ----------------
data class WsState(
    val input: MediaFile? = null,
    // 与「视频压缩」出厂预设一致，压缩是主场景
    val crf: Int = 26,
    val preset: String = "medium",
    val vcodec: String = "libx264",
    val acodec: String = "aac",
    val abitrate: String = "192k",
    val res: String = "",
    val fps: Int = 0,
    val fpsMode: String = "default",
    val format: String = "auto",
    // format = "custom" 时的输出后缀名
    val formatCustom: String = "",
    // 各下拉框是否处于「自定义输入」模式（点「自定义…」进入，选标准项退出）
    val vcodecCustom: Boolean = false,
    val presetCustom: Boolean = false,
    val resCustom: Boolean = false,
    val acodecCustom: Boolean = false,
    val outputPath: String = Defaults.DEFAULT_OUT_TPL,
    val custom: String = "",
    val rawCmd: String = "",
    // 默认不裁剪，填了时间才切到一起/分开
    val trimMode: String = "off",
    val trimFmt: String = "sec",
    val trimStart: String = "",
    val trimEnd: String = "",
    val trimEndMode: String = "to",
    val vTrimStart: String = "",
    val vTrimEnd: String = "",
    val vTrimEndMode: String = "to",
    val aTrimStart: String = "",
    val aTrimEnd: String = "",
    val aTrimEndMode: String = "to",
    val noAudio: Boolean = false,
    val noVideo: Boolean = false,
    val forceY: Boolean = false,
    val deleteSource: Boolean = false,
    val overwriteSource: Boolean = false,
    // UI 专属
    val activePresetId: String? = "compress",
    val isRawMode: Boolean = false,
    val presetExpanded: Boolean = false,
    val cmdCollapsed: Boolean = false,
)

data class LogLine(val cls: String, val text: String)

data class RunState(
    val running: Boolean = false,
    val hasLog: Boolean = false,
    val ok: Boolean = true,
    val lines: List<LogLine> = emptyList(),
    val progress: Int = 0,
    val elapsed: Double = 0.0,
    // 实时统计（Statistics 回调，用于日志页顶部的易读摘要）
    val frame: Int = 0,
    val fps: Float = 0f,
    val speed: Double = 0.0,
    val processedMs: Long = 0,
    val outSizeB: Long = 0,
    val totalMs: Long = -1,
    // 成功后的输出路径（覆盖源文件时为源路径），供「打开」按钮使用
    val outPath: String = "",
)

enum class Sheet { LOG, CONFIRM, FULLCMD, ERR, PARAMS, RAWCMD, PRESET_INFO, PRESET_SAVE_CHOICE, AI_CHAT }

// 预设编辑器状态
data class EditorState(
    val editingId: String? = null,
    val fromWorkspace: Boolean = false,
    val name: String = "",
    val note: String = "",
    val type: String = "params", // params | raw
    val cmdText: String = "",
    val rawText: String = "",
    val home: Boolean = false,
)

data class ChatState(val messages: List<ChatMsg> = listOf(
    ChatMsg("ai", "AI 功能未实现。")
))

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)

    // ---------- 导航 ----------
    // 启动固定进主页；只恢复工作台状态（ws / blockOpen）
    var page by mutableStateOf("home")
        private set
    var presetInfoId by mutableStateOf<String?>(null)
    var lastErr by mutableStateOf("")
    var errTitle by mutableStateOf("完整信息")
    var errBody by mutableStateOf("")

    // ---------- 持久化设置 ----------
    var themeMode by mutableStateOf(prefs.theme)
    var outDir by mutableStateOf(prefs.outDir)
    var keepLog by mutableStateOf(prefs.keepLog)
    // 工作台预设网格：每行数量 / 收起时显示行数（设置在预设管理页）
    var presetCols by mutableStateOf(prefs.presetCols)
        private set
    var presetRows by mutableStateOf(prefs.presetRows)
        private set
    var aiCfg by mutableStateOf(prefs.loadAi())
        private set

    fun setGridCols(n: Int) { presetCols = n.coerceIn(2, 4); prefs.presetCols = presetCols }
    fun setGridRows(n: Int) { presetRows = n.coerceIn(1, 3); prefs.presetRows = presetRows }

    // ---------- 主数据 ----------
    var presets by mutableStateOf(prefs.loadPresets())
        private set
    var history by mutableStateOf(prefs.loadHistory())
        private set
    var recent by mutableStateOf(prefs.loadRecent())
        private set
    var ws by mutableStateOf(prefs.loadWs() ?: WsState())
        private set
    /** 源文件流信息（异步探测），供「自动」容器选择 */
    var srcCodecs by mutableStateOf(com.ffmpegstudio.logic.SrcCodecs())
        private set
    var run by mutableStateOf(RunState())
        private set
    var sheet by mutableStateOf<Sheet?>(null)
        private set
    var editor by mutableStateOf(EditorState())
    var chat by mutableStateOf(ChatState())
        private set

    /** 工作台各块折叠状态（presets 默认展开，对应 Web 版初始 collapsed 类）；随会话持久化 */
    val blockOpen = androidx.compose.runtime.mutableStateMapOf<String, Boolean>().also { m ->
        val saved = prefs.loadBlockOpen()
        if (saved != null) m.putAll(saved)
        else m.putAll(mapOf(
            "presets" to true, "rawcmd" to true, "video" to false,
            "audio" to false, "trim" to false, "output" to false, "advanced" to false,
        ))
    }
    fun toggleBlock(k: String) { blockOpen[k] = !(blockOpen[k] ?: false) }

    /** 退出应用前保存工作台状态（MainActivity ON_STOP 调用），下次启动恢复；页面固定从主页开始 */
    fun persistSession() {
        prefs.saveWs(ws)
        prefs.saveBlockOpen(blockOpen.toMap())
    }

    private var runJob: Job? = null
    private var historyFilter by mutableStateOf("all")
    fun currentFilter() = historyFilter
    fun setFilter(f: String) { historyFilter = f }

    // ---------- Toast ----------
    var toast by mutableStateOf<String?>(null)
        private set
    private var toastJob: Job? = null
    fun showToast(msg: String) {
        toast = msg
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            delay(1800)
            toast = null
        }
    }

    // ---------- 导航 ----------
    fun goPage(p: String) {
        page = p
        sheet = null
    }
    fun back() {
        when (page) {
            "ai" -> goPage("settings")
            "preset-edit" -> goPage("presets")
            "presets" -> goPage("work")
            else -> goPage("settings")
        }
    }

    // ---------- Sheets ----------
    fun openSheet(s: Sheet) { sheet = s }
    fun closeSheet() { sheet = null }
    fun showError(title: String, body: String) {
        errTitle = title; errBody = body; lastErr = body
        sheet = Sheet.ERR
    }

    // ---------- 参数构建上下文 ----------
    private fun ctx(exec: Boolean = false) = CommandBuilder.BuildContext(
        exec = exec,
        isRawMode = ws.isRawMode,
        rawCmd = ws.rawCmd,
        input = ws.input,
        forceY = ws.forceY,
        custom = ws.custom,
        params = currentParams(),
        noVideo = ws.noVideo,
        noAudio = ws.noAudio,
        vcodec = ws.vcodec,
        acodec = ws.acodec,
        crf = ws.crf,
        preset = ws.preset,
        abitrate = ws.abitrate,
        format = resolvedFormat(),
        fps = ws.fps,
        fpsMode = ws.fpsMode,
        res = ws.res,
        overwriteSource = ws.overwriteSource,
        outDir = outDir,
        outputPath = ws.outputPath,
        history = history,
    )

    fun buildCmd(exec: Boolean = false): String = CommandBuilder.buildCmd(ctx(exec))

    /** 容器选择模式 → 实际输出后缀（auto/source/custom 的解析结果） */
    fun resolvedFormat(): String = CommandBuilder.resolveFormat(
        format = ws.format,
        formatCustom = ws.formatCustom,
        isRawMode = ws.isRawMode,
        noVideo = ws.noVideo,
        noAudio = ws.noAudio,
        vcodec = ws.vcodec,
        acodec = ws.acodec,
        src = srcCodecs,
    )

    fun resolveOutput(): String = CommandBuilder.resolveOutput(ctx())

    /** 最终落盘路径（覆盖源成功后移回） */
    fun finalOutputPath(): String {
        val input = ws.input
        return if (ws.overwriteSource && input != null) input.path else resolveOutput()
    }

    fun expandedOut(): String =
        CommandBuilder.expandOutTemplate(ws.outputPath.ifBlank { Defaults.DEFAULT_OUT_TPL }, ws.input, resolvedFormat())

    fun validateOut(): String? = CommandBuilder.validateOutExpanded(expandedOut())

    fun currentParams(): PresetParams = PresetParams(
        crf = ws.crf, preset = ws.preset, vcodec = ws.vcodec, acodec = ws.acodec,
        abitrate = ws.abitrate, res = ws.res, fps = ws.fps, fpsMode = ws.fpsMode,
        format = ws.format, formatCustom = ws.formatCustom, noAudio = ws.noAudio, noVideo = ws.noVideo,
        trimMode = ws.trimMode, trimFmt = ws.trimFmt,
        trimStart = ws.trimStart, trimEnd = ws.trimEnd, trimEndMode = ws.trimEndMode,
        vTrimStart = ws.vTrimStart, vTrimEnd = ws.vTrimEnd, vTrimEndMode = ws.vTrimEndMode,
        aTrimStart = ws.aTrimStart, aTrimEnd = ws.aTrimEnd, aTrimEndMode = ws.aTrimEndMode,
        custom = ws.custom, outTpl = ws.outputPath.ifBlank { Defaults.DEFAULT_OUT_TPL },
    )

    /** 将参数集应用到工作台控件（对应 Web 版 applyParams） */
    fun applyParams(p: PresetParams) {
        val stdPreset = listOf("ultrafast","superfast","veryfast","faster","fast","medium","slow","slower","veryslow")
        val stdRes = listOf("", "1920:1080", "1280:720", "854:480")
        ws = ws.copy(
            crf = p.crf,
            preset = p.preset,
            vcodec = p.vcodec,
            acodec = p.acodec,
            abitrate = p.abitrate,
            res = p.res,
            fps = p.fps,
            fpsMode = if (p.fpsMode in listOf("drop", "smooth", "blend")) p.fpsMode else "default",
            format = p.format,
            formatCustom = p.formatCustom,
            vcodecCustom = p.vcodec !in StdValues.VCODEC,
            presetCustom = p.preset !in StdValues.PRESET,
            resCustom = p.res !in StdValues.RES,
            acodecCustom = p.acodec !in StdValues.ACODEC,
        noAudio = p.noAudio,
            noVideo = p.noVideo,
            trimStart = p.trimStart, trimEnd = p.trimEnd,
            trimEndMode = if (p.trimEndMode == "t") "t" else "to",
            trimFmt = if (p.trimFmt == "hms") "hms" else "sec",
            trimMode = if (p.trimMode == "split" || p.trimMode == "together") p.trimMode else "off",
            vTrimStart = p.vTrimStart, vTrimEnd = p.vTrimEnd, vTrimEndMode = if (p.vTrimEndMode == "t") "t" else "to",
            aTrimStart = p.aTrimStart, aTrimEnd = p.aTrimEnd, aTrimEndMode = if (p.aTrimEndMode == "t") "t" else "to",
            custom = p.custom,
            outputPath = p.outTpl.ifBlank { Defaults.DEFAULT_OUT_TPL },
        )
    }

    fun applyPresetById(id: String?) {
        val p = presets.find { it.id == id } ?: return
        ws = ws.copy(activePresetId = p.id, isRawMode = p.type == "raw")
        if (p.type == "raw") {
            ws = ws.copy(rawCmd = p.raw)
        } else {
            applyParams(p.params)
        }
    }

    // ---------- 工作台 setter ----------
    fun update(f: (WsState) -> WsState) { ws = f(ws) }

    fun setInput(f: MediaFile?) {
        ws = ws.copy(input = f, cmdCollapsed = false)
        if (f != null && recent.none { it.path == f.path }) {
            recent = listOf(f) + recent
            prefs.saveRecent(recent)
        }
        // 异步探测源文件编码（自动容器选择用）
        srcCodecs = com.ffmpegstudio.logic.SrcCodecs(ext = f?.name?.substringAfterLast('.', "")?.lowercase() ?: "")
        if (f != null) {
            val app = getApplication<Application>()
            val path = f.path
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                srcCodecs = com.ffmpegstudio.logic.MediaProbe.probe(app, path)
            }
        }
    }

    fun setNoAudio(v: Boolean) {
        var s = ws.copy(noAudio = v)
        if (v && s.noVideo) s = s.copy(noVideo = false) // 音视频不要同时移除
        ws = s
    }

    fun setNoVideo(v: Boolean) {
        var s = ws.copy(noVideo = v)
        if (v && s.noAudio) s = s.copy(noAudio = false)
        ws = s
    }

    fun setOverwriteSource(v: Boolean) {
        ws = ws.copy(overwriteSource = v)
        showToast(if (v) "将先输出到默认目录，成功后再移回原路径" else "已关闭覆盖源文件")
    }

    fun setTrimFmt(newFmt: String) {
        val oldFmt = ws.trimFmt
        ws = ws.copy(
            trimFmt = newFmt,
            trimStart = TimeUtil.reformatTimeValue(ws.trimStart, oldFmt, newFmt),
            trimEnd = TimeUtil.reformatTimeValue(ws.trimEnd, oldFmt, newFmt),
            vTrimStart = TimeUtil.reformatTimeValue(ws.vTrimStart, oldFmt, newFmt),
            vTrimEnd = TimeUtil.reformatTimeValue(ws.vTrimEnd, oldFmt, newFmt),
            aTrimStart = TimeUtil.reformatTimeValue(ws.aTrimStart, oldFmt, newFmt),
            aTrimEnd = TimeUtil.reformatTimeValue(ws.aTrimEnd, oldFmt, newFmt),
        )
    }

    fun setAbitrateRaw(raw: String) {
        val v = CommandBuilder.normalizeAbitrate(raw)
        ws = ws.copy(abitrate = v)
    }

    // ---------- 原始命令模式 ----------
    /** 工作台内联编辑原始命令时同步到原始命令预设 */
    fun updateRawPreset(text: String) {
        val p = presets.find { it.id == ws.activePresetId }
        if (p != null && p.type == "raw") {
            presets = presets.map { if (it.id == p.id) it.copy(raw = text) else it }
            savePresets()
        }
    }

    fun syncRawModeVisibility(): List<String> =
        if (ws.isRawMode) listOf("video", "audio", "trim", "advanced") else emptyList()

    fun saveRawCmd(text: String) {
        ws = ws.copy(rawCmd = text)
        val p = presets.find { it.id == ws.activePresetId }
        if (p != null && p.type == "raw") {
            presets = presets.map { if (it.id == p.id) it.copy(raw = text) else it }
            savePresets()
        }
        showToast("原始命令已保存")
    }

    // ---------- 预设管理 ----------
    private fun savePresets() = prefs.savePresets(presets)

    fun togglePresetHome(id: String) {
        presets = presets.map { if (it.id == id) it.copy(home = !it.home) else it }
        savePresets()
        val p = presets.find { it.id == id }
        showToast(if (p?.home == true) "已显示在主页" else "已从主页隐藏")
    }

    fun deletePreset(id: String) {
        val p = presets.find { it.id == id } ?: return
        if (p.type == "raw") { showToast("原始命令预设不可删除"); return }
        presets = presets.filter { it.id != id }
        if (ws.activePresetId == id) ws = ws.copy(activePresetId = presets.firstOrNull()?.id)
        savePresets()
        showToast("预设已删除")
    }

    /** 按新顺序写回可重排的项（主页卡片 / 管理列表），未参与重排的项保持原槽位 */
    fun reorderPresets(newOrder: List<String>) {
        val idSet = newOrder.toSet()
        val byId = presets.associateBy { it.id }
        val original = presets
        var i = 0
        presets = original.map { p ->
            if (p.id in idSet) byId[newOrder[i++]] ?: p else p
        }
        savePresets()
    }

    fun openPresetEditor(id: String?, fromWorkspace: Boolean) {
        val p = id?.let { pid -> presets.find { it.id == pid } }
        editor = EditorState(
            editingId = id,
            fromWorkspace = fromWorkspace,
            name = p?.name ?: "",
            note = p?.note ?: "",
            type = if (p?.type == "raw") "raw" else "params",
            cmdText = CommandBuilder.paramsToCmdFragment(
                if (fromWorkspace) currentParams() else (p?.params ?: currentParams())
            ),
            rawText = p?.raw ?: "",
            home = p?.home ?: false,
        )
        goPage("preset-edit")
    }

    /** 保存预设（对应 Web 版 peSave，含 fromWorkspace 细节） */
    fun saveEditor() {
        val name = editor.name.trim()
        if (name.isEmpty()) { showToast("请填写名称"); return }
        val type = editor.type
        var raw: String? = null
        var params: PresetParams
        if (type == "raw") {
            val r = editor.rawText.trim()
            if (r.isEmpty()) { showToast("请填写原始命令"); return }
            raw = r
            params = if (editor.editingId != null)
                presets.find { it.id == editor.editingId }?.params ?: currentParams()
            else currentParams()
        } else {
            val parsed = CommandBuilder.parseCmdFragment(editor.cmdText)
            val base = if (editor.fromWorkspace || editor.editingId == null) currentParams()
                else (presets.find { it.id == editor.editingId }?.params ?: currentParams())
            params = base.copy(
                crf = parsed.params.crf.takeIf { editor.cmdText.contains("-crf") } ?: base.crf,
                preset = parsed.params.preset.takeIf { editor.cmdText.contains("-preset") } ?: base.preset,
                vcodec = parsed.params.vcodec.takeIf { editor.cmdText.contains("-c:v") } ?: base.vcodec,
                acodec = parsed.params.acodec.takeIf { editor.cmdText.contains("-c:a") } ?: base.acodec,
                abitrate = parsed.params.abitrate.takeIf { editor.cmdText.contains("-b:a") } ?: base.abitrate,
                res = parsed.params.res.takeIf { editor.cmdText.contains("scale=") } ?: base.res,
                fps = parsed.params.fps.takeIf { editor.cmdText.contains("-r ") || editor.cmdText.contains("fps=") || editor.cmdText.contains("framerate=") || editor.cmdText.contains("minterpolate=") } ?: base.fps,
                fpsMode = if (parsed.params.fpsMode != "default") parsed.params.fpsMode else base.fpsMode,
                noAudio = parsed.params.noAudio || base.noAudio,
                noVideo = parsed.params.noVideo || base.noVideo,
                trimStart = parsed.params.trimStart.ifEmpty { base.trimStart },
                trimEnd = parsed.params.trimEnd.ifEmpty { base.trimEnd },
                trimEndMode = parsed.params.trimEndMode.takeIf { editor.cmdText.contains("-to") || editor.cmdText.contains("-t ") } ?: base.trimEndMode,
                trimFmt = parsed.params.trimFmt.takeIf { editor.cmdText.contains("-ss") } ?: base.trimFmt,
                custom = parsed.custom.ifEmpty { base.custom },
            )
            if (editor.fromWorkspace) {
                params = params.copy(
                    format = ws.format,
                    formatCustom = ws.formatCustom,
                    custom = ws.custom,
                    outTpl = ws.outputPath.ifBlank { Defaults.DEFAULT_OUT_TPL },
                    trimMode = ws.trimMode, trimFmt = ws.trimFmt,
                    trimStart = ws.trimStart, trimEnd = ws.trimEnd, trimEndMode = ws.trimEndMode,
                    vTrimStart = ws.vTrimStart, vTrimEnd = ws.vTrimEnd, vTrimEndMode = ws.vTrimEndMode,
                    aTrimStart = ws.aTrimStart, aTrimEnd = ws.aTrimEnd, aTrimEndMode = ws.aTrimEndMode,
                    fpsMode = ws.fpsMode, fps = ws.fps,
                )
            }
            params = params.copy(
                crf = params.crf, preset = params.preset.ifEmpty { "medium" },
                vcodec = params.vcodec.ifEmpty { "libx264" }, acodec = params.acodec.ifEmpty { "aac" },
                abitrate = params.abitrate.ifEmpty { "192k" },
                fpsMode = if (params.fpsMode in listOf("drop","smooth","blend")) params.fpsMode else "default",
                format = params.format.ifEmpty { if (editor.fromWorkspace) ws.format else "mp4" },
                trimMode = if (params.trimMode == "split" || params.trimMode == "together") params.trimMode else "off",
                outTpl = params.outTpl.ifEmpty { Defaults.DEFAULT_OUT_TPL },
            )
        }
        if (editor.editingId != null) {
            presets = presets.map {
                if (it.id == editor.editingId) it.copy(name = name, note = editor.note.trim(), type = type, home = editor.home, params = params, raw = raw ?: it.raw)
                else it
            }
            ws = ws.copy(activePresetId = editor.editingId)
            showToast("预设已更新")
        } else {
            val id = "p" + System.currentTimeMillis()
            presets = presets + Preset(id = id, name = name, note = editor.note.trim(), type = type, home = editor.home, builtin = false, params = params, raw = raw ?: "")
            ws = ws.copy(activePresetId = id)
            showToast("预设已保存")
        }
        savePresets()
        if (type != "raw") applyParams(params)
        goPage("presets")
    }

    /** 恢复内置预设（对应 Web 版 rowRestorePresets） */
    fun restoreBuiltinPresets() {
        val list = presets.toMutableList()
        Defaults.DEFAULT_PRESETS.forEach { def ->
            val idx = list.indexOfFirst { it.id == def.id }
            val restored = def.copy(builtin = true, params = def.params)
            if (idx >= 0) {
                if (list[idx].builtin) list[idx] = restored
            } else list.add(restored)
        }
        if (list.none { it.type == "raw" }) {
            val def = Defaults.DEFAULT_PRESETS.first { it.type == "raw" }
            list.add(0, def.copy(params = def.params))
        }
        presets = list
        savePresets()
        val ap = presets.find { it.id == ws.activePresetId }
        if (ap != null && ap.type != "raw" && !ws.isRawMode) applyParams(ap.params)
        showToast("内置预设已恢复")
    }

    // ---------- 模拟执行引擎（对应 Web 版 MOCK_LOGS + startRun） ----------
    /** 执行按钮（对应 Web 版 btnRun）：校验输入/输出/原始命令后弹出确认 */
    fun tryRun(openPicker: () -> Unit) {
        if (ws.input == null) { showToast("请先选择输入文件"); openPicker(); return }
        if (!ws.overwriteSource) {
            val err = validateOut()
            if (err != null) { showToast(err); return }
        }
        if (run.running) { sheet = Sheet.LOG; return }
        if (ws.isRawMode && ws.rawCmd.isBlank()) { showToast("请先填写原始命令"); sheet = Sheet.RAWCMD; return }
        sheet = Sheet.CONFIRM
    }

    // ---------- 真实执行引擎（FFmpegKit） ----------
    private var sessionId: Long? = null

    /** 本次执行的完整原始日志（供「复制全部」用；滚动列表套选中容器会崩，改为按钮复制） */
    private val logBuf = StringBuilder()
    fun logText(): String = synchronized(logBuf) { logBuf.toString() }

    var ffmpegVersion by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            ffmpegVersion = try { FFmpegKitConfig.getFFmpegVersion() } catch (_: Throwable) { null }
        }
    }

    /**
     * 按引号规则把命令串切成参数数组。用 ffmpeg-kit 官方解析器（与 shell 一致：
     * 单/双引号均可分组、反斜杠转义），带空格的路径加引号即可正确切分。
     */
    private fun tokenize(cmd: String): List<String> = try {
        FFmpegKitConfig.parseArguments(cmd).toList()
    } catch (_: Throwable) {
        // 兜底：仅双引号分组的手工切分
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuote = false
        for (ch in cmd) {
            when {
                ch == '"' -> inQuote = !inQuote
                ch.isWhitespace() && !inQuote -> {
                    if (cur.isNotEmpty()) { out.add(cur.toString()); cur.clear() }
                }
                else -> cur.append(ch)
            }
        }
        if (cur.isNotEmpty()) out.add(cur.toString())
        out
    }

    private fun mimeFor(format: String) = when (format) {
        "mp4" -> "video/mp4"; "mkv" -> "video/x-matroska"; "mov" -> "video/quicktime"
        "webm" -> "video/webm"; "mp3" -> "audio/mpeg"; "m4a" -> "audio/mp4"
        "wav" -> "audio/x-wav"; "flac" -> "audio/flac"; "gif" -> "image/gif"
        "ogg" -> "audio/ogg"; "opus" -> "audio/opus"; "ts" -> "video/mp2t"
        "mka" -> "audio/x-matroska"
        else -> "application/octet-stream"
    }

    /** 输出落点：媒体集合目录 / 子目录 / 文件名 */
    private fun outputTarget(): Triple<String, String, String> {
        val fmt = resolvedFormat()
        val sub = outDir.trim('/').substringAfterLast('/').ifBlank { "FFmpegStudio" }
            .replace(Regex("[^A-Za-z0-9_\\- ]"), "")
        val folder = when (fmt) {
            "mp3", "m4a", "wav", "flac", "ogg", "opus" -> "Music"
            "gif" -> "Pictures"
            else -> "Movies"
        }
        var name = CommandBuilder.expandOutTemplate(
            ws.outputPath.ifBlank { Defaults.DEFAULT_OUT_TPL }, ws.input, fmt
        ).substringAfterLast('/').ifBlank { "output.$fmt" }
        name = name.replace(Regex("[<>:\"|?*\\\\/]"), "_")
        // 没写后缀的话 ffmpeg 靠后缀猜 muxer 会直接报错，这里兜底补上
        if (!name.contains('.')) name += ".$fmt"
        return Triple(folder, sub, name)
    }

    private fun classifyLog(msg: String) = when {
        msg.contains("Error", ignoreCase = true) || msg.contains("Invalid data", true) -> "err"
        msg.startsWith("frame=") && msg.contains("Lsize=") -> "ok"
        msg.startsWith("ffmpeg version") -> "dim"
        else -> ""
    }

    private fun cleanupDoc(ctx: Application, uri: android.net.Uri?) {
        if (uri == null) return
        try { ctx.contentResolver.delete(uri, null, null) } catch (_: Throwable) {}
    }

    private fun failRun(msg: String) {
        run = run.copy(running = false, ok = false, lines = run.lines + LogLine("err", msg))
        showToast("处理失败")
    }

    fun startRun() {
        if (run.running) return
        val cmdTemplate = buildCmd(exec = true)
        run = RunState(
            running = true, hasLog = true, ok = true,
            lines = listOf(LogLine("info", "\$ $cmdTemplate"), LogLine("dim", "")),
        )
        logBuf.setLength(0)
        synchronized(logBuf) { logBuf.appendLine("$ $cmdTemplate") }
        sheet = Sheet.LOG
        runJob = viewModelScope.launch { executeReal(cmdTemplate) }
    }

    private suspend fun executeReal(cmdTemplate: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        val ctx = getApplication<Application>()
        val startedAt = System.currentTimeMillis()
        val deleteSource = ws.deleteSource
        val overwriteSource = ws.overwriteSource
        val inputUri = ws.input?.path?.let { runCatching { android.net.Uri.parse(it) }.getOrNull() }
        val inputPath = ws.input?.path ?: ""
        val inSize = ws.input?.size ?: "—"
        val inputName = ws.input?.name ?: "未命名"
        val cmdDisplay = buildCmd()

        // 总时长（用于进度）
        var totalMs = -1L
        if (inputUri != null) {
            totalMs = try {
                val mmr = android.media.MediaMetadataRetriever()
                mmr.setDataSource(ctx, inputUri)
                val d = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: -1L
                runCatching { mmr.release() }
                d
            } catch (_: Throwable) { -1L }
        }

        if (totalMs > 0) run = run.copy(totalMs = totalMs)

        // 输入参数：content:// → SAF 参数，普通路径直接用
        var inputParam: String? = null
        if (inputUri != null && inputPath.startsWith("content://")) {
            inputParam = try { FFmpegKitConfig.getSafParameterForRead(ctx, inputUri) } catch (e: Throwable) {
                failRun("无法读取输入文件: ${e.message}"); return@withContext
            }
        } else if (inputPath.isNotEmpty()) inputParam = inputPath

        // 输出：API29+ 走 MediaStore（IS_PENDING），旧版本走应用外部目录
        val (folder, sub, outName) = outputTarget()
        val legacyFile: java.io.File?
        var outUri: android.net.Uri? = null
        var outParam: String
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            try {
                val collection = when (folder) {
                    "Music" -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    "Pictures" -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    else -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                }
                val cv = android.content.ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, outName)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/$sub")
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeFor(resolvedFormat()))
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                outUri = ctx.contentResolver.insert(collection, cv) ?: throw IllegalStateException("系统拒绝创建文件")
                outParam = FFmpegKitConfig.getSafParameterForWrite(ctx, outUri)
            } catch (e: Throwable) {
                cleanupDoc(ctx, outUri)
                failRun("创建输出文件失败: ${e.message}"); return@withContext
            }
            legacyFile = null
        } else {
            legacyFile = java.io.File(ctx.getExternalFilesDir(null), "$sub/$outName")
            legacyFile.parentFile?.mkdirs()
            if (legacyFile.exists()) legacyFile.delete()
            outParam = legacyFile.absolutePath
        }

        // executeWithArguments 的参数数组不含程序名 "ffmpeg"
        val tokens = tokenize(cmdTemplate).let { if (it.firstOrNull() == "ffmpeg") it.drop(1) else it }
        val args = tokens.map { t ->
            t.replace("<input>", inputParam ?: "<input>").replace("<output>", outParam)
        }

        val collected = StringBuilder()
        val done = kotlinx.coroutines.CompletableDeferred<Boolean>()
        val session = FFmpegKit.executeWithArgumentsAsync(
            args.toTypedArray(),
            FFmpegSessionCompleteCallback { s ->
                done.complete(ReturnCode.isSuccess(s.returnCode))
            },
            LogCallback { log ->
                val msg = log.message?.trimEnd('\n') ?: return@LogCallback
                if (msg.isBlank()) return@LogCallback
                collected.appendLine(msg)
                synchronized(logBuf) { logBuf.appendLine(msg) }
                run = run.copy(lines = (run.lines + LogLine(classifyLog(msg), msg)).takeLast(400))
            },
            StatisticsCallback { stats ->
                val t = stats.time.toLong()
                var r = run.copy(
                    frame = stats.videoFrameNumber,
                    fps = stats.videoFps,
                    speed = stats.speed,
                    processedMs = t,
                    outSizeB = stats.size,
                    elapsed = (System.currentTimeMillis() - startedAt) / 1000.0,
                )
                if (t > 0 && totalMs > 0) r = r.copy(progress = (t * 100 / totalMs).toInt().coerceIn(0, 99))
                run = r
            },
        )
        sessionId = session.sessionId

        val ok = done.await()
        sessionId = null
        val elapsedSec = (System.currentTimeMillis() - startedAt) / 1000.0
        run = run.copy(elapsed = elapsedSec)
        if (!ok) {
            cleanupDoc(ctx, outUri)
            legacyFile?.delete()
            run = run.copy(running = false, ok = false)
            showToast("处理失败")
            return@withContext
        }

        // 成功：结束 IS_PENDING
        if (outUri != null) {
            try {
                ctx.contentResolver.update(outUri, android.content.ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }, null, null)
            } catch (_: Throwable) {}
        }

        // 覆盖源文件：把结果写回源文件，再清掉临时输出
        if (overwriteSource && inputUri != null) {
            try {
                ctx.contentResolver.openOutputStream(inputUri, "wt")?.use { os ->
                    if (outUri != null) ctx.contentResolver.openInputStream(outUri)?.use { it.copyTo(os) }
                    else legacyFile?.inputStream()?.use { it.copyTo(os) }
                }
                cleanupDoc(ctx, outUri); legacyFile?.delete()
                run = run.copy(lines = run.lines + LogLine("ok", "已移回覆盖原文件: $inputPath"))
            } catch (e: Throwable) {
                run = run.copy(lines = run.lines + LogLine("err", "覆盖源文件失败: ${e.message}"))
            }
        }
        // 完成后删除源文件
        if (deleteSource && !overwriteSource && inputUri != null) {
            val del = try { ctx.contentResolver.delete(inputUri, null, null) > 0 } catch (_: Throwable) { false }
            run = run.copy(lines = run.lines + LogLine(if (del) "info" else "err", if (del) "源文件已删除" else "源文件删除失败（该位置不允许删除）"))
        }

        // 输出大小
        val outSize = if (outUri != null) {
            try {
                ctx.contentResolver.openAssetFileDescriptor(outUri, "r")?.use { humanSize(it.length) } ?: "—"
            } catch (_: Throwable) { "—" }
        } else legacyFile?.let { humanSize(it.length()) } ?: "—"

        val displayOut = if (overwriteSource) inputPath else if (outUri != null) "/storage/emulated/0/$folder/$sub/$outName" else (legacyFile?.absolutePath ?: "")
        run = run.copy(
            running = false, ok = true, progress = 100, outPath = displayOut,
            lines = run.lines + LogLine("ok", "\n输出文件已写入") + LogLine("dim", displayOut),
        )
        val avgFps = if (run.frame > 0 && elapsedSec > 0) (run.frame / elapsedSec).toInt() else 0
        val avgSpeed = if (run.totalMs > 0 && elapsedSec > 0) run.totalMs / 1000.0 / elapsedSec else 0.0
        val entry = HistoryEntry(
            id = System.currentTimeMillis(), ok = true, name = inputName,
            input = inputPath, out = displayOut,
            inSize = inSize, outSize = outSize, dur = TimeUtil.humanDuration(elapsedSec.toLong()),
            atMs = System.currentTimeMillis(), avgFps = avgFps, avgSpeed = avgSpeed,
            cmd = cmdDisplay,
            // 「完成后保留日志」关闭时不把日志写进历史
            log = if (keepLog) collected.toString().take(20000) else "",
        )
        val newHistory = listOf(entry) + history
        prefs.saveHistoryEntry(entry)
        // 只保留最近 100 条，超出的删掉对应文件
        newHistory.drop(100).forEach { prefs.deleteHistoryFile(it.id) }
        history = newHistory.take(100)
        showToast("处理完成")
    }

    fun humanSize(bytes: Long): String = when {
        bytes < 0 -> "—"
        bytes >= 1024L * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f GB", bytes / 1073741824.0)
        bytes >= 1024L * 1024 -> String.format(java.util.Locale.US, "%.0f MB", bytes / 1048576.0)
        bytes >= 1024 -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    fun stopRun() {
        if (!run.running) return
        sessionId?.let { FFmpegKit.cancel(it) }
        sessionId = null
        runJob?.cancel()
        runJob = null
        run = run.copy(running = false, ok = false, lines = run.lines + LogLine("err", "用户已停止任务"))
        showToast("已停止")
    }

    // ---------- 历史 ----------
    fun deleteHistory(id: Long) {
        history = history.filter { it.id != id }
        prefs.deleteHistoryFile(id)
        showToast("已删除记录")
    }

    /** 从「最近文件」移除一条记录（不删除文件本身），主页左滑触发 */
    fun deleteRecent(f: MediaFile) {
        recent = recent.filterNot { it.path == f.path }
        prefs.saveRecent(recent)
        showToast("已从最近文件移除")
    }

    /**
     * 调系统应用打开一个文件/URI（历史记录与日志窗口的「打开」按钮）。
     * content:// 直接透传；本地路径经 FileProvider 转成可授权的 URI。
     */
    fun openFile(target: String) {
        val ctx = getApplication<Application>()
        val t = target.trim()
        if (t.isEmpty()) { showToast("路径为空"); return }
        val uri: android.net.Uri = if (t.startsWith("content://")) {
            android.net.Uri.parse(t)
        } else {
            val f = java.io.File(t)
            if (!f.exists()) { showToast("文件不存在或已被移动"); return }
            try {
                androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
            } catch (_: Throwable) {
                showToast("无法访问该文件"); return
            }
        }
        val mime = if (t.startsWith("content://")) {
            try { ctx.contentResolver.getType(uri) } catch (_: Throwable) { null }
        } else null
        val ext = t.substringAfterLast('.', "").lowercase()
        val type = mime?.takeIf { it.isNotEmpty() }
            ?: android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: "application/octet-stream"
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
            .setDataAndType(uri, type)
            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // 弹系统选择器，每次都可以选用哪个应用打开
        val chooser = android.content.Intent.createChooser(intent, "打开文件")
        // ViewModel 持有 Application 上下文，非 Activity 环境启动必须带 NEW_TASK，
        // 否则抛异常被误报成「没有应用可打开」
        chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(chooser) } catch (_: Throwable) { showToast("打开失败：没有应用支持此文件") }
    }

    /** 用浏览器打开一个链接（关于页的 GitHub / QQ 群 / Telegram） */
    fun openUrl(url: String) {
        val ctx = getApplication<Application>()
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(intent) } catch (_: Throwable) { showToast("无法打开链接") }
    }

    fun retryHistory(h: HistoryEntry) {
        val f = recent.find { it.path == h.input }
            ?: MediaFile(name = h.name, path = h.input, size = h.inSize, sizeB = 0)
        setInput(f)
        goPage("work")
        showToast("已加载到工作台")
    }

    // ---------- AI（未实现：不模拟请求与回复） ----------
    fun saveAi(cfg: AiConfig) {
        aiCfg = cfg
        prefs.saveAi(cfg)
        showToast("AI 设置已保存")
    }

    fun testAi(base: String, key: String) {
        showToast("AI 功能未实现")
    }

    fun sendAi(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        chat = chat.copy(messages = chat.messages + ChatMsg("user", t) + ChatMsg("ai", "AI 功能未实现。"))
    }

    /** AI 建议 → 写入原始命令 */
    fun applyAiRaw(fullCmd: String) {
        ws = ws.copy(isRawMode = true, rawCmd = fullCmd)
        val rawP = presets.find { it.type == "raw" }
        if (rawP != null) {
            ws = ws.copy(activePresetId = rawP.id)
            presets = presets.map { if (it.id == rawP.id) it.copy(raw = fullCmd) else it }
            savePresets()
        }
        sheet = null
        goPage("work")
        showToast("已写入原始命令")
    }

    /** AI 建议 → 追加自定义参数 */
    fun applyAiParams(params: String) {
        ws = ws.copy(custom = if (ws.custom.isEmpty()) params else ws.custom + " " + params)
        sheet = null
        goPage("work")
        showToast("已追加自定义参数")
    }

    // ---------- 设置 ----------
    fun setTheme(mode: String) {
        themeMode = mode
        prefs.theme = mode
    }

    fun updateOutDir(v: String) {
        outDir = v
        prefs.outDir = v
        showToast("输出目录已更新")
    }

    fun updateKeepLog(v: Boolean) {
        keepLog = v
        prefs.keepLog = v
    }

    // ---------- 统计 ----------
    fun statTotal() = history.size
    fun statOk() = history.count { it.ok }

    /** 成功率：无记录时显示 — */
    fun statSuccessRate(): String {
        val t = history.size
        return if (t == 0) "—" else "${statOk() * 100 / t}%"
    }
}
