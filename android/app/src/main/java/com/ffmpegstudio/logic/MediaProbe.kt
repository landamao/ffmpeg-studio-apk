package com.ffmpegstudio.logic

import android.content.Context

/** 源文件流信息：编码名（小写）+ 后缀；探测失败时字段为 null */
data class SrcCodecs(
    val video: String? = null,
    val audio: String? = null,
    val ext: String = "",
)

/**
 * 用 MediaExtractor 探测输入文件的视频/音频编码，供「自动」容器选择使用。
 * content:// 与本地路径都支持；失败返回空信息（调用方走兜底容器）。
 */
object MediaProbe {
    private val cache = androidx.collection.LruCache<String, SrcCodecs>(48)

    fun probe(ctx: Context, path: String): SrcCodecs {
        if (path.isEmpty()) return SrcCodecs()
        cache.get(path)?.let { return it }
        val ext = path.substringAfterLast('.', "").lowercase()
        var video: String? = null
        var audio: String? = null
        try {
            val ex = android.media.MediaExtractor()
            try {
                if (path.startsWith("content://")) ex.setDataSource(ctx, android.net.Uri.parse(path), null)
                else ex.setDataSource(path)
                for (i in 0 until ex.trackCount) {
                    val mime = ex.getTrackFormat(i).getString(android.media.MediaFormat.KEY_MIME) ?: continue
                    when {
                        mime.startsWith("video/") && video == null ->
                            video = mime.removePrefix("video/").substringBefore(';').lowercase()
                        mime.startsWith("audio/") && audio == null ->
                            audio = normalizeAudio(mime.removePrefix("audio/"))
                    }
                }
            } finally {
                runCatching { ex.release() }
            }
        } catch (_: Throwable) { }
        val r = SrcCodecs(video, audio, ext)
        cache.put(path, r)
        return r
    }

    /** MediaExtractor 的音频 MIME → 常见编码名（mp4a-latm→aac、mpeg→mp3、raw→pcm） */
    private fun normalizeAudio(s: String): String {
        val t = s.substringBefore(';').lowercase()
        return when {
            t.startsWith("mp4a") -> "aac"
            t.startsWith("mpeg") -> "mp3"
            t.startsWith("raw") -> "pcm"
            t.startsWith("3gpp") || t.startsWith("amr") -> "amr"
            else -> t
        }
    }
}
