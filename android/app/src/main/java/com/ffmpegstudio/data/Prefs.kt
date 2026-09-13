package com.ffmpegstudio.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 基于SharedPreferences的本地持久化，对应Web版localStorage:
 * ff-theme / ff-presets-v3 / ff-ai / 历史 / 输出目录 / 保留日志。
 */
class Prefs(context: Context) {
    private val appContext = context.applicationContext
    private val sp: SharedPreferences = context.getSharedPreferences("ff-studio", Context.MODE_PRIVATE)

    // ---------- Theme ----------
    var theme: String
        get() = sp.getString("ff-theme", "system") ?: "system"
        set(v) = sp.edit().putString("ff-theme", v).apply()

    // ---------- Output dir ----------
    var outDir: String
        get() = sp.getString("ff-outdir", Defaults.DEFAULT_OUT_DIR) ?: Defaults.DEFAULT_OUT_DIR
        set(v) = sp.edit().putString("ff-outdir", v).apply()

    var keepLog: Boolean
        get() = sp.getBoolean("ff-keeplog", true)
        set(v) = sp.edit().putBoolean("ff-keeplog", v).apply()

    // ---------- 工作台预设网格：每行数量 / 收起时显示行数 ----------
    var presetCols: Int
        get() = sp.getInt("ff-preset-cols", 2)
        set(v) = sp.edit().putInt("ff-preset-cols", v).apply()

    var presetRows: Int
        get() = sp.getInt("ff-preset-rows", 1)
        set(v) = sp.edit().putInt("ff-preset-rows", v).apply()

    // ---------- 会话状态（退出重进恢复：工作台 + 折叠状态；页面固定从主页开始） ----------
    fun loadBlockOpen(): Map<String, Boolean>? {
        val raw = sp.getString("ff-blocks", null) ?: return null
        return try {
            val o = JSONObject(raw)
            o.keys().asSequence().map { it to o.optBoolean(it) }.toMap()
        } catch (_: Exception) { null }
    }

    fun saveBlockOpen(m: Map<String, Boolean>) {
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k, v) }
        sp.edit().putString("ff-blocks", o.toString()).apply()
    }

    fun loadWs(): WsState? {
        val raw = sp.getString("ff-ws", null) ?: return null
        return try {
            val o = JSONObject(raw)
            val input = o.optJSONObject("input")?.let { io ->
                MediaFile(
                    name = io.optString("name"), path = io.optString("path"),
                    size = io.optString("size"), sizeB = io.optLong("sizeB"),
                )
            }
            WsState(
                input = input,
                crf = o.optInt("crf", 26),
                preset = o.optString("preset", "medium"),
                vcodec = o.optString("vcodec", "libx264"),
                acodec = o.optString("acodec", "aac"),
                abitrate = o.optString("abitrate", "192k"),
                res = o.optString("res"),
                fps = o.optInt("fps", 0),
                fpsMode = o.optString("fpsMode", "default"),
                format = o.optString("format", "auto"),
                formatCustom = o.optString("formatCustom"),
                vcodecCustom = o.optBoolean("vcodecCustom"),
                presetCustom = o.optBoolean("presetCustom"),
                resCustom = o.optBoolean("resCustom"),
                acodecCustom = o.optBoolean("acodecCustom"),
                outputPath = o.optString("outputPath", Defaults.DEFAULT_OUT_TPL),
                custom = o.optString("custom"),
                rawCmd = o.optString("rawCmd"),
                trimMode = o.optString("trimMode", "off"),
                trimFmt = o.optString("trimFmt", "sec"),
                trimStart = o.optString("trimStart"),
                trimEnd = o.optString("trimEnd"),
                trimEndMode = o.optString("trimEndMode", "to"),
                vTrimStart = o.optString("vTrimStart"),
                vTrimEnd = o.optString("vTrimEnd"),
                vTrimEndMode = o.optString("vTrimEndMode", "to"),
                aTrimStart = o.optString("aTrimStart"),
                aTrimEnd = o.optString("aTrimEnd"),
                aTrimEndMode = o.optString("aTrimEndMode", "to"),
                noAudio = o.optBoolean("noAudio"),
                noVideo = o.optBoolean("noVideo"),
                forceY = o.optBoolean("forceY"),
                deleteSource = o.optBoolean("deleteSource"),
                overwriteSource = o.optBoolean("overwriteSource"),
                activePresetId = if (o.has("activePresetId")) (if (o.isNull("activePresetId")) null else o.optString("activePresetId")) else "compress",
                isRawMode = o.optBoolean("isRawMode"),
                presetExpanded = o.optBoolean("presetExpanded"),
                cmdCollapsed = o.optBoolean("cmdCollapsed"),
            )
        } catch (_: Exception) { null }
    }

    fun saveWs(s: WsState) {
        val o = JSONObject()
        s.input?.let { f ->
            o.put("input", JSONObject()
                .put("name", f.name).put("path", f.path)
                .put("size", f.size).put("sizeB", f.sizeB))
        }
        o.put("crf", s.crf).put("preset", s.preset)
        o.put("vcodec", s.vcodec).put("acodec", s.acodec).put("abitrate", s.abitrate)
        o.put("res", s.res).put("fps", s.fps).put("fpsMode", s.fpsMode)
        o.put("format", s.format).put("formatCustom", s.formatCustom)
        o.put("vcodecCustom", s.vcodecCustom).put("presetCustom", s.presetCustom)
        o.put("resCustom", s.resCustom).put("acodecCustom", s.acodecCustom)
        o.put("outputPath", s.outputPath).put("custom", s.custom).put("rawCmd", s.rawCmd)
        o.put("trimMode", s.trimMode).put("trimFmt", s.trimFmt)
        o.put("trimStart", s.trimStart).put("trimEnd", s.trimEnd).put("trimEndMode", s.trimEndMode)
        o.put("vTrimStart", s.vTrimStart).put("vTrimEnd", s.vTrimEnd).put("vTrimEndMode", s.vTrimEndMode)
        o.put("aTrimStart", s.aTrimStart).put("aTrimEnd", s.aTrimEnd).put("aTrimEndMode", s.aTrimEndMode)
        o.put("noAudio", s.noAudio).put("noVideo", s.noVideo)
        o.put("forceY", s.forceY).put("deleteSource", s.deleteSource).put("overwriteSource", s.overwriteSource)
        if (s.activePresetId != null) o.put("activePresetId", s.activePresetId) else o.put("activePresetId", JSONObject.NULL)
        o.put("isRawMode", s.isRawMode).put("presetExpanded", s.presetExpanded).put("cmdCollapsed", s.cmdCollapsed)
        sp.edit().putString("ff-ws", o.toString()).apply()
    }

    // ---------- AI ----------
    fun loadAi(): AiConfig {
        val raw = sp.getString("ff-ai", null) ?: return AiConfig()
        return try {
            val o = JSONObject(raw)
            AiConfig(
                enabled = o.optBoolean("enabled", true),
                base = o.optString("base", "https://api.openai.com/v1"),
                key = o.optString("key", ""),
                model = o.optString("model", "gpt-4o-mini"),
            )
        } catch (_: Exception) { AiConfig() }
    }

    fun saveAi(cfg: AiConfig) {
        val o = JSONObject()
            .put("enabled", cfg.enabled)
            .put("base", cfg.base)
            .put("key", cfg.key)
            .put("model", cfg.model)
        sp.edit().putString("ff-ai", o.toString()).apply()
    }

    // ---------- Presets ----------
    fun loadPresets(): List<Preset> {
        var list: List<Preset>? = null
        try {
            val raw = sp.getString("ff-presets-v3", null)
            if (raw != null) list = parsePresets(raw)
        } catch (_: Exception) {}
        var result = list ?: Defaults.DEFAULT_PRESETS.map { it.copy(params = it.params) }
        // 迁移：旧「提取音频」用 vcodec:copy，应改为移除视频（-vn）；补充默认 outTpl / noVideo
        result = result.map { p ->
            if (p.type == "params") {
                var np = p.params
                if (np.format == "mp3" && np.vcodec == "copy" && !np.noVideo)
                    np = np.copy(noVideo = true, vcodec = "libx264")
                p.copy(params = np)
            } else p
        }
        // 原始命令预设强制保留
        if (result.none { it.type == "raw" }) {
            val def = Defaults.DEFAULT_PRESETS.first { it.type == "raw" }
            result = listOf(def.copy(params = def.params)) + result
        }
        return result
    }

    fun savePresets(presets: List<Preset>) {
        val arr = JSONArray()
        presets.forEach { p ->
            val o = JSONObject()
                .put("id", p.id).put("name", p.name).put("note", p.note)
                .put("builtin", p.builtin).put("home", p.home).put("type", p.type)
                .put("raw", p.raw)
            val pr = JSONObject()
                .put("crf", p.params.crf).put("preset", p.params.preset)
                .put("vcodec", p.params.vcodec).put("acodec", p.params.acodec)
                .put("abitrate", p.params.abitrate).put("res", p.params.res)
                .put("fps", p.params.fps).put("fpsMode", p.params.fpsMode)
                .put("format", p.params.format)
                .put("formatCustom", p.params.formatCustom)
                .put("noAudio", p.params.noAudio).put("noVideo", p.params.noVideo)
                .put("trimMode", p.params.trimMode).put("trimFmt", p.params.trimFmt)
                .put("trimStart", p.params.trimStart).put("trimEnd", p.params.trimEnd)
                .put("trimEndMode", p.params.trimEndMode)
                .put("vTrimStart", p.params.vTrimStart).put("vTrimEnd", p.params.vTrimEnd)
                .put("vTrimEndMode", p.params.vTrimEndMode)
                .put("aTrimStart", p.params.aTrimStart).put("aTrimEnd", p.params.aTrimEnd)
                .put("aTrimEndMode", p.params.aTrimEndMode)
                .put("custom", p.params.custom).put("outTpl", p.params.outTpl)
            o.put("params", pr)
            arr.put(o)
        }
        sp.edit().putString("ff-presets-v3", arr.toString()).apply()
    }

    private fun parsePresets(raw: String): List<Preset> {
        val arr = JSONArray(raw)
        val list = mutableListOf<Preset>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val pr = o.optJSONObject("params") ?: JSONObject()
            val p = Preset(
                id = o.optString("id"),
                name = o.optString("name"),
                note = o.optString("note"),
                builtin = o.optBoolean("builtin"),
                home = o.optBoolean("home"),
                type = o.optString("type", "params"),
                raw = o.optString("raw"),
                params = PresetParams(
                    crf = pr.optInt("crf", 23),
                    preset = pr.optString("preset", "medium"),
                    vcodec = pr.optString("vcodec", "libx264"),
                    acodec = pr.optString("acodec", "aac"),
                    abitrate = pr.optString("abitrate", "192k"),
                    res = pr.optString("res"),
                    fps = pr.optInt("fps", 0),
                    fpsMode = pr.optString("fpsMode", "default"),
                    format = pr.optString("format", "mp4"),
                    formatCustom = pr.optString("formatCustom"),
                    noAudio = pr.optBoolean("noAudio"),
                    noVideo = pr.optBoolean("noVideo"),
                    trimMode = pr.optString("trimMode", "off"),
                    trimFmt = pr.optString("trimFmt", "sec"),
                    trimStart = pr.optString("trimStart"),
                    trimEnd = pr.optString("trimEnd"),
                    trimEndMode = pr.optString("trimEndMode", "to"),
                    vTrimStart = pr.optString("vTrimStart"),
                    vTrimEnd = pr.optString("vTrimEnd"),
                    vTrimEndMode = pr.optString("vTrimEndMode", "to"),
                    aTrimStart = pr.optString("aTrimStart"),
                    aTrimEnd = pr.optString("aTrimEnd"),
                    aTrimEndMode = pr.optString("aTrimEndMode", "to"),
                    custom = pr.optString("custom"),
                    outTpl = pr.optString("outTpl", Defaults.DEFAULT_OUT_TPL),
                ),
            )
            list.add(p)
        }
        return list
    }

    // ---------- History：每条记录一个 JSON 文件，存 filesDir/history/ ----------
    private val historyDir: java.io.File get() = java.io.File(appContext.filesDir, "history")

    fun loadHistory(): List<HistoryEntry> {
        // 旧版把全部历史塞在 SharedPreferences 一个键里，直接丢弃
        if (sp.contains("ff-history")) sp.edit().remove("ff-history").apply()
        val dir = historyDir
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.mapNotNull { f ->
                try { parseHistoryEntry(JSONObject(f.readText())) } catch (_: Exception) { null }
            }
            ?.sortedByDescending { it.id }
            ?: emptyList()
    }

    fun saveHistoryEntry(h: HistoryEntry) {
        try {
            historyDir.mkdirs()
            java.io.File(historyDir, "${h.id}.json").writeText(historyJson(h).toString())
        } catch (_: Exception) {}
    }

    fun deleteHistoryFile(id: Long) {
        try { java.io.File(historyDir, "$id.json").delete() } catch (_: Exception) {}
    }

    private fun historyJson(h: HistoryEntry) = JSONObject()
        .put("id", h.id).put("ok", h.ok).put("name", h.name)
        .put("in", h.input).put("out", h.out)
        .put("inSize", h.inSize).put("outSize", h.outSize)
        .put("dur", h.dur).put("atMs", h.atMs)
        .put("avgFps", h.avgFps).put("avgSpeed", h.avgSpeed)
        .put("cmd", h.cmd).put("log", h.log)

    private fun parseHistoryEntry(o: JSONObject) = HistoryEntry(
        id = o.optLong("id"),
        ok = o.optBoolean("ok"),
        name = o.optString("name"),
        input = o.optString("in"),
        out = o.optString("out"),
        inSize = o.optString("inSize"),
        outSize = o.optString("outSize"),
        dur = o.optString("dur"),
        atMs = o.optLong("atMs"),
        avgFps = o.optInt("avgFps"),
        avgSpeed = o.optDouble("avgSpeed", 0.0),
        cmd = o.optString("cmd"),
        log = o.optString("log"),
    )

    // ---------- Recent picked files ----------
    fun loadRecent(): List<MediaFile> {
        val raw = sp.getString("ff-recent", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                MediaFile(
                    name = o.optString("name"),
                    path = o.optString("path"),
                    size = o.optString("size"),
                    sizeB = o.optLong("sizeB"),
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    fun saveRecent(list: List<MediaFile>) {
        val arr = JSONArray()
        list.take(12).forEach { f ->
            arr.put(JSONObject().put("name", f.name).put("path", f.path)
                .put("size", f.size).put("sizeB", f.sizeB))
        }
        sp.edit().putString("ff-recent", arr.toString()).apply()
    }
}
