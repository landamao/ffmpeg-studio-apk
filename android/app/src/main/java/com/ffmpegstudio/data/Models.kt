package com.ffmpegstudio.data

/** 参数预设中可映射到工作台控件的全部字段（对应 Web 版 preset.params） */
data class PresetParams(
    val crf: Int = 23,
    val preset: String = "medium",
    val vcodec: String = "libx264",
    val acodec: String = "aac",
    val abitrate: String = "192k",
    val res: String = "",
    val fps: Int = 0,
    val fpsMode: String = "default",
    val format: String = "mp4",
    // format = "custom" 时的输出后缀名
    val formatCustom: String = "",
    val noAudio: Boolean = false,
    val noVideo: Boolean = false,
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
    val custom: String = "",
    val outTpl: String = "<raw_name>_out.<ext>",
)

data class Preset(
    val id: String,
    val name: String,
    val note: String = "",
    val builtin: Boolean = false,
    val home: Boolean = false,
    val type: String = "params", // "params" | "raw"
    val raw: String = "",
    val params: PresetParams = PresetParams(),
)

data class MediaFile(
    val name: String,
    val path: String,
    val size: String,
    val sizeB: Long,
)

data class HistoryEntry(
    val id: Long,
    val ok: Boolean,
    val name: String,
    val input: String,
    val out: String,
    val inSize: String,
    val outSize: String,
    val dur: String,
    // 完成时刻（epoch ms），展示时算相对时间
    val atMs: Long = 0,
    // 平均编码帧率（帧/秒）与平均速度（相对实时倍数），0 = 无
    val avgFps: Int = 0,
    val avgSpeed: Double = 0.0,
    val cmd: String,
    val log: String,
)

data class AiConfig(
    val enabled: Boolean = true,
    val base: String = "https://api.openai.com/v1",
    val key: String = "",
    val model: String = "gpt-4o-mini",
)

data class ChatMsg(
    val role: String, // "user" | "ai" | "err"
    val text: String,
    val cmd: String = "",
    val errFull: String = "",
)

/** 工作台各下拉框的「标准选项」；值不在列表内即视为自定义 */
object StdValues {
    val VCODEC = listOf("libx264", "libx265", "libvpx-vp9", "copy")
    val ACODEC = listOf("aac", "libmp3lame", "flac", "pcm_s16le", "copy")
    val PRESET = listOf("ultrafast", "superfast", "veryfast", "faster", "fast", "medium", "slow", "slower", "veryslow")
    val RES = listOf("", "1920:1080", "1280:720", "854:480")
}

object Defaults {
    val DEFAULT_OUT_TPL = "<raw_name>_out.<ext>"

    val DEFAULT_PRESETS = listOf(
        Preset(
            id = "compress", name = "视频压缩", note = "通用体积压缩", builtin = true, home = true,
            // crf 26：压缩预设以减小体积为目标，比通用默认 23 更省空间
            params = PresetParams(crf = 26, preset = "medium", vcodec = "libx264", acodec = "aac", abitrate = "192k"),
        ),
        Preset(
            id = "mp4", name = "转 MP4", note = "兼容社交平台", builtin = true, home = true,
            params = PresetParams(crf = 20),
        ),
        Preset(
            id = "audio", name = "提取音频", note = "复制音频流，容器自动", builtin = true, home = true,
            // copy + 自动容器：按源音频编码选（FLAC→flac、AAC→m4a、MP3→mp3、未知→mka）
            params = PresetParams(crf = 0, vcodec = "copy", acodec = "copy", format = "auto", noVideo = true),
        ),
        Preset(
            id = "gif", name = "转 GIF", note = "小体积动图", builtin = true, home = false,
            params = PresetParams(crf = 0, acodec = "copy", res = "640:-1", fps = 10, format = "gif", noAudio = true),
        ),
        Preset(
            id = "trim", name = "裁剪片段", note = "高质量片段导出", builtin = true, home = false,
            params = PresetParams(crf = 18, preset = "fast"),
        ),
        Preset(
            id = "raw", name = "原始命令", note = "不套预设，直接执行完整命令", builtin = true, home = true,
            type = "raw",
            raw = "ffmpeg -i <input> -c:v libx264 -crf 23 -preset medium -c:a aac -b:a 192k <output>",
        ),
    )

    val DEFAULT_OUT_DIR = "/storage/emulated/0/Movies/FFmpegStudio"
}
