package com.ffmpegstudio.logic

import com.ffmpegstudio.data.HistoryEntry
import com.ffmpegstudio.data.MediaFile
import com.ffmpegstudio.data.PresetParams
import com.ffmpegstudio.data.Defaults

/**
 * FFmpeg 命令构建器，严格对应 Web 版 buildVf / trimArgsFrom / paramsToCmdFragment /
 * parseCmdFragment / buildCmd / expandOutTemplate / validateOutExpanded 等。
 */
object CommandBuilder {

    // ---------- 视频滤镜链 ----------
    fun buildVf(pr: PresetParams): String {
        val chains = mutableListOf<String>()
        if (pr.fps > 0) {
            when (pr.fpsMode) {
                "smooth" -> chains.add("minterpolate=fps=${pr.fps}:mi_mode=mci:mc_mode=aobmc:vsbmc=1")
                "blend" -> chains.add("framerate=fps=${pr.fps}")
                "drop" -> chains.add("fps=${pr.fps}")
            }
        }
        if (pr.res.isNotEmpty()) chains.add("scale=${pr.res}")
        return chains.joinToString(",")
    }

    // ---------- 裁剪参数 ----------
    data class TrimResult(val args: List<String> = emptyList(), val maps: List<String>? = null)

    fun trimArgsFrom(pr: PresetParams): TrimResult {
        val fmt = if (pr.trimFmt == "hms") "hms" else "sec"
        val mode = if (pr.trimMode == "split" || pr.trimMode == "together") pr.trimMode else "off"
        if (mode == "off") return TrimResult()
        if (mode == "together") {
            val ss = TimeUtil.formatTimeForCmd(pr.trimStart, fmt)
            val te = TimeUtil.formatTimeForCmd(pr.trimEnd, fmt)
            val args = mutableListOf<String>()
            if (ss != null) args.addAll(listOf("-ss", ss))
            if (te != null) args.addAll(listOf(if (pr.trimEndMode == "t") "-t" else "-to", te))
            return TrimResult(args)
        }
        // split: trim/atrim 滤镜
        val vss = TimeUtil.formatTimeForCmd(pr.vTrimStart, fmt)
        val vte = TimeUtil.formatTimeForCmd(pr.vTrimEnd, fmt)
        val ass = TimeUtil.formatTimeForCmd(pr.aTrimStart, fmt)
        val ate = TimeUtil.formatTimeForCmd(pr.aTrimEnd, fmt)
        val noV = pr.noVideo
        val noA = pr.noAudio
        val useV = !noV && (vss != null || vte != null)
        val useA = !noA && (ass != null || ate != null)
        if (!useV && !useA) return TrimResult()
        val chains = mutableListOf<String>()
        if (useV) {
            val p = mutableListOf<String>()
            if (vss != null) p.add("start=$vss")
            if (vte != null) p.add(if (pr.vTrimEndMode == "t") "duration=$vte" else "end=$vte")
            chains.add("[0:v]trim=${p.joinToString(":")},setpts=PTS-STARTPTS[vtrim]")
        }
        if (useA) {
            val p = mutableListOf<String>()
            if (ass != null) p.add("start=$ass")
            if (ate != null) p.add(if (pr.aTrimEndMode == "t") "duration=$ate" else "end=$ate")
            chains.add("[0:a]atrim=${p.joinToString(":")},asetpts=PTS-STARTPTS[atrim]")
        }
        val maps = mutableListOf<String>()
        maps.add(if (useV) "[vtrim]" else if (noV) "" else "0:v")
        maps.add(if (useA) "[atrim]" else if (noA) "" else "0:a")
        return TrimResult(listOf("-filter_complex", chains.joinToString(";")), maps.filter { it.isNotEmpty() })
    }

    // ---------- 预设 → 命令片段 / 命令片段 → 预设 ----------
    fun paramsToCmdFragment(pr: PresetParams): String {
        val parts = mutableListOf<String>()
        if (pr.noVideo) parts.add("-vn")
        else if (pr.vcodec == "copy") parts.add("-c:v copy")
        else if (pr.vcodec.isNotEmpty()) {
            parts.addAll(listOf("-c:v", pr.vcodec, "-crf", pr.crf.toString(), "-preset", pr.preset))
            val vf = buildVf(pr)
            if (vf.isNotEmpty()) parts.addAll(listOf("-vf", vf))
            if (pr.fps > 0 && pr.fpsMode == "default") parts.addAll(listOf("-r", pr.fps.toString()))
        }
        if (pr.noAudio) parts.add("-an")
        else if (pr.acodec.isNotEmpty()) {
            parts.addAll(listOf("-c:a", pr.acodec))
            if (pr.acodec != "copy" && pr.acodec != "flac" && pr.acodec != "pcm_s16le")
                parts.addAll(listOf("-b:a", pr.abitrate))
        }
        val tr = trimArgsFrom(pr)
        parts.addAll(tr.args)
        if (pr.custom.isNotEmpty()) parts.add(pr.custom)
        return parts.joinToString(" ")
    }

    data class ParsedFragment(val params: PresetParams, val custom: String)

    fun parseCmdFragment(text: String): ParsedFragment {
        val tokens = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        var i = 0
        val leftover = mutableListOf<String>()
        var pr = PresetParams()
        while (i < tokens.size) {
            val t = tokens[i]
            when {
                t == "-c:v" && i + 1 < tokens.size -> { pr = pr.copy(vcodec = tokens[++i]) }
                t == "-crf" && i + 1 < tokens.size -> { pr = pr.copy(crf = tokens[++i].toDoubleOrNull()?.toInt() ?: 23) }
                t == "-preset" && i + 1 < tokens.size -> { pr = pr.copy(preset = tokens[++i]) }
                t == "-vf" && i + 1 < tokens.size -> {
                    val vf = tokens[++i]
                    val segs = vf.split(",").filter { it.isNotEmpty() }
                    val keep = mutableListOf<String>()
                    for (seg in segs) {
                        var m = Regex("^minterpolate=fps=([\\d.]+)").find(seg)
                        if (m != null) { pr = pr.copy(fpsMode = "smooth", fps = m.groupValues[1].toDoubleOrNull()?.toInt() ?: 0); continue }
                        m = Regex("^framerate=fps=([\\d.]+)").find(seg)
                        if (m != null) { pr = pr.copy(fpsMode = "blend", fps = m.groupValues[1].toDoubleOrNull()?.toInt() ?: 0); continue }
                        m = Regex("^fps=([\\d.]+)").find(seg)
                        if (m != null) { pr = pr.copy(fpsMode = "drop", fps = m.groupValues[1].toDoubleOrNull()?.toInt() ?: 0); continue }
                        m = Regex("^scale=([^,]+)").find(seg)
                        if (m != null) { pr = pr.copy(res = m.groupValues[1]); continue }
                        keep.add(seg)
                    }
                    if (keep.isNotEmpty()) leftover.addAll(listOf("-vf", keep.joinToString(",")))
                }
                t == "-r" && i + 1 < tokens.size -> {
                    val v = tokens[++i].toDoubleOrNull()?.toInt() ?: 0
                    pr = pr.copy(fps = v, fpsMode = if (pr.fpsMode == "default") "default" else pr.fpsMode)
                }
                t == "-c:a" && i + 1 < tokens.size -> { pr = pr.copy(acodec = tokens[++i]) }
                t == "-b:a" && i + 1 < tokens.size -> { pr = pr.copy(abitrate = tokens[++i]) }
                t == "-an" -> { pr = pr.copy(noAudio = true) }
                t == "-vn" -> { pr = pr.copy(noVideo = true) }
                t == "-ss" && i + 1 < tokens.size -> {
                    val v = tokens[++i]
                    pr = pr.copy(trimStart = v, trimFmt = if (v.contains(":")) "hms" else "sec")
                }
                t == "-to" && i + 1 < tokens.size -> {
                    pr = pr.copy(trimEnd = tokens[++i], trimEndMode = "to")
                }
                t == "-t" && i + 1 < tokens.size -> {
                    pr = pr.copy(trimEnd = tokens[++i], trimEndMode = "t")
                }
                else -> leftover.add(t)
            }
            i++
        }
        return ParsedFragment(pr, leftover.joinToString(" "))
    }

    // ---------- 码率规范化 ----------
    fun normalizeAbitrate(raw: String?): String {
        var v = (raw ?: "").trim().lowercase()
        if (v.isEmpty()) return "192k"
        if (Regex("^\\d+(\\.\\d+)?$").matches(v)) v += "k"
        v = v.replace(Regex("\\s+"), "").replace(Regex("bps$"), "").replace(Regex("bit$"), "")
        if (!Regex("^\\d+(\\.\\d+)?[km]?$").matches(v)) return "192k"
        if (!Regex("[km]$").containsMatchIn(v)) v += "k"
        return v
    }

    // ---------- 容器选择（自动 / 跟随原文件 / 自定义） ----------
    /**
     * 把容器选择模式解析成实际后缀。
     * - auto：按流与编码推断——仅输出音频时按音频编码选容器（FLAC→flac、AAC→m4a、
     *   未知编码兜底 mka）；重编码视频默认 MP4，音频是 FLAC/PCM 或编码未知时用 MKV；
     *   复制视频流跟随源后缀；GIF 编码出 GIF。
     * - source：直接用源文件后缀。
     * - custom：用用户填的后缀名（ffmpeg 按后缀选 muxer）。
     */
    fun resolveFormat(
        format: String,
        formatCustom: String,
        isRawMode: Boolean,
        noVideo: Boolean,
        noAudio: Boolean,
        vcodec: String,
        acodec: String,
        src: SrcCodecs,
    ): String = when (format) {
        "source" -> src.ext.ifEmpty { "mkv" }
        "custom" -> formatCustom.trim().lowercase().removePrefix(".").ifEmpty { "mp4" }
        "auto" -> when {
            // 原始命令拿不到意图，跟随源后缀最稳
            isRawMode -> src.ext.ifEmpty { "mkv" }
            noVideo && noAudio -> "mkv"
            noVideo -> audioContainer(acodec, src.audio)
            else -> videoContainer(vcodec, acodec, src, wantGif = vcodec.contains("gif"))
        }
        else -> format
    }

    private fun audioContainer(acodec: String, srcAudio: String?): String = when {
        acodec == "copy" -> when (srcAudio) {
            "aac" -> "m4a"; "mp3" -> "mp3"; "flac" -> "flac"
            "opus" -> "ogg"; "vorbis" -> "ogg"; "pcm" -> "wav"
            else -> "mka" // 未知/探测失败：matroska 什么都能装
        }
        acodec.contains("mp3lame") || acodec == "mp3" -> "mp3"
        acodec == "aac" -> "m4a"
        acodec.contains("flac") -> "flac"
        acodec.startsWith("pcm") -> "wav"
        acodec.contains("opus") -> "ogg"
        acodec.contains("vorbis") -> "ogg"
        else -> "mka"
    }

    private fun videoContainer(vcodec: String, acodec: String, src: SrcCodecs, wantGif: Boolean): String {
        val base = when {
            wantGif -> "gif"
            vcodec == "copy" -> src.ext.ifEmpty { "mkv" } // 复制视频流跟随源容器
            vcodec.contains("vpx") -> "webm"
            vcodec.startsWith("libx") || vcodec.contains("264") || vcodec.contains("265") ||
                vcodec.contains("hevc") || vcodec.startsWith("mpeg4") || vcodec.contains("xvid") -> "mp4"
            else -> "mkv" // 其它/未知视频编码：matroska 兼容性最好
        }
        // MP4 装不下 FLAC/PCM 音频，落 MKV
        val flacOrPcm = acodec == "flac" || acodec.startsWith("pcm") ||
            (acodec == "copy" && (src.audio == "flac" || src.audio == "pcm"))
        return if (base == "mp4" && flacOrPcm) "mkv" else base
    }

    /** 文件名没有后缀时补上容器后缀（ffmpeg 靠后缀猜 muxer，缺了会报 Unable to find a suitable output format） */
    fun ensureExt(path: String, format: String): String {
        val name = path.substringAfterLast('/')
        return if (format.isEmpty() || name.contains('.')) path else "$path.$format"
    }

    // ---------- 输出路径模板 ----------
    fun expandOutTemplate(tpl: String?, input: MediaFile?, format: String): String {
        val rawDir = input?.path?.replace(Regex("/[^/]+$"), "") ?: ""
        val rawName = input?.name?.replace(Regex("\\.[^.]+$"), "") ?: "output"
        var s = (tpl ?: "").trim().ifEmpty { Defaults.DEFAULT_OUT_TPL }
        s = s.replace("<raw_dir>", rawDir)
        s = s.replace("<raw_name>", rawName)
        s = s.replace("<ext>", format.ifEmpty { "mp4" })
        s = Regex("<T,val=([^>]+)>").replace(s) { m -> TimeUtil.formatTimestamp(m.groupValues[1]) }
        return s
    }

    fun pathTaken(p: String?, history: List<HistoryEntry>, input: MediaFile?): Boolean {
        if (p.isNullOrEmpty()) return true
        if (history.any { it.out == p }) return true
        if (input != null && input.path == p) return true
        return false
    }

    fun resolveUniqueN(path: String, history: List<HistoryEntry>, input: MediaFile?): String {
        if (!path.contains("<N>")) return path
        val plain = path.replace("<N>", "")
        if (!pathTaken(plain, history, input)) return plain
        for (i in 1..9999) {
            val p = path.replace("<N>", i.toString())
            if (!pathTaken(p, history, input)) return p
        }
        return path.replace("<N>", System.currentTimeMillis().toString())
    }

    /** 返回 null = 校验通过，否则错误信息 */
    fun validateOutExpanded(p: String?): String? {
        var s = (p ?: "")
        if (s.trim().isEmpty()) return "输出路径不能为空"
        if (Regex("<[^>]*$").containsMatchIn(s)) return "占位符未写完整（如 <T,val=yyyy-MM-dd_HH-mm-ss>）"
        val leftovers = Regex("<[^>]+>").findAll(s).map { it.value }.toList()
        val ok = Regex("^(<raw_dir>|<raw_name>|<ext>|<N>|<T,val=[^>]+>)$")
        for (t in leftovers) {
            if (!ok.matches(t)) return "未知占位符 $t"
        }
        s = s
            .replace("<raw_dir>", "dir")
            .replace("<raw_name>", "name")
            .replace("<ext>", "ext")
            .replace("<N>", "")
            .replace(Regex("<T,val=[^>]+>"), "t")
        val segs = s.split("/", "\\")
        for (i in segs.indices) {
            val seg = segs[i]
            val isLast = i == segs.size - 1
            if (!isLast) {
                if (seg.isEmpty() && i > 0) continue
                if (Regex("[<>:\"|?*]").containsMatchIn(seg)) return "目录名含非法字符：${seg.ifEmpty { "/" }}"
            } else {
                if (seg.isEmpty()) return "文件名不能为空"
                if (Regex("[<>:\"|?*\\\\/]").containsMatchIn(seg)) return "文件名不能包含 \\ / : * ? \" < > |"
                if (Regex("[.\\s]$").containsMatchIn(seg)) return "文件名不能以 . 或空格结尾"
            }
        }
        return null
    }

    // ---------- 完整命令 ----------
    data class BuildContext(
        val exec: Boolean = false,
        val isRawMode: Boolean,
        val rawCmd: String,
        val input: MediaFile?,
        val forceY: Boolean,
        val custom: String,
        val params: PresetParams,
        val noVideo: Boolean,
        val noAudio: Boolean,
        val vcodec: String,
        val acodec: String,
        val crf: Int,
        val preset: String,
        val abitrate: String,
        val format: String,
        val fps: Int,
        val fpsMode: String,
        val res: String,
        val overwriteSource: Boolean,
        val outDir: String,
        val outputPath: String,
        val history: List<HistoryEntry>,
    )

    /** 命令实际写入的路径（覆盖源时写到默认目录临时文件） */
    fun resolveOutput(ctx: BuildContext): String {
        if (ctx.overwriteSource && ctx.input != null) {
            val base = ctx.input.name.replace(Regex("\\.[^.]+$"), "")
            return ctx.outDir.trimEnd('/') + "/" + base + "_tmp." + ctx.format
        }
        var p = expandOutTemplate(ctx.outputPath, ctx.input, ctx.format)
        p = resolveUniqueN(p, ctx.history, ctx.input)
        if (p.startsWith("/") || p.contains(":\\") || p.startsWith("content://")) return ensureExt(p, ctx.format)
        return ensureExt(ctx.outDir.trimEnd('/') + "/" + p, ctx.format)
    }

    fun buildCmd(ctx: BuildContext): String {
        // exec 模式：保留占位符，由真实执行引擎替换为 SAF 参数
        val out = if (ctx.exec) "<output>" else resolveOutput(ctx)
        if (ctx.isRawMode) {
            if (ctx.exec) return ctx.rawCmd.trim().ifEmpty { "ffmpeg -i <input> <output>" }
            val raw = ctx.rawCmd.trim()
            val input = ctx.input?.path ?: "<input>"
            return raw
                .replace("<input>", input)
                .replace("<output>", out)
                .ifEmpty { "ffmpeg -i $input $out" }
        }
        val parts = mutableListOf("ffmpeg")
        if (ctx.forceY) parts.add("-y")
        parts.addAll(listOf("-i", if (ctx.exec) "<input>" else ctx.input?.path ?: "<input>"))
        if (ctx.custom.isNotEmpty()) parts.addAll(ctx.custom.split(Regex("\\s+")).filter { it.isNotEmpty() })
        val tr = trimArgsFrom(ctx.params)
        parts.addAll(tr.args)
        val maps = tr.maps
        if (ctx.format == "gif") {
            parts.addAll(listOf("-vf", "fps=${if (ctx.fps > 0) ctx.fps else 10}"))
            if (ctx.res.isNotEmpty()) parts.addAll(listOf("-s", ctx.res))
            parts.addAll(listOf("-loop", "0", "-an"))
        } else {
            if (maps != null) maps.forEach { parts.addAll(listOf("-map", it)) }
            if (ctx.noVideo) parts.add("-vn")
            else if (ctx.vcodec == "copy") parts.addAll(listOf("-c:v", "copy"))
            else if (ctx.vcodec.isNotEmpty()) {
                parts.addAll(listOf("-c:v", ctx.vcodec, "-crf", ctx.crf.toString()))
                if (ctx.preset.isNotEmpty()) parts.addAll(listOf("-preset", ctx.preset))
                val vf = buildVf(ctx.params)
                if (vf.isNotEmpty() && maps == null) parts.addAll(listOf("-vf", vf))
                if (ctx.fps > 0 && ctx.fpsMode == "default") parts.addAll(listOf("-r", ctx.fps.toString()))
            } else {
                // 自定义编码器文本被清空：不指定编码，仅保留滤镜/帧率
                val vf = buildVf(ctx.params)
                if (vf.isNotEmpty() && maps == null) parts.addAll(listOf("-vf", vf))
                if (ctx.fps > 0 && ctx.fpsMode == "default") parts.addAll(listOf("-r", ctx.fps.toString()))
            }
            if (ctx.noAudio) parts.add("-an")
            else if (ctx.acodec.isNotEmpty()) {
                parts.addAll(listOf("-c:a", ctx.acodec))
                if (ctx.acodec != "copy" && ctx.acodec != "flac" && ctx.acodec != "pcm_s16le")
                    parts.addAll(listOf("-b:a", ctx.abitrate))
            }
        }
        parts.add(out)
        return parts.filter { it.isNotEmpty() }.joinToString(" ")
    }
}
