package com.ffmpegstudio.logic

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 裁剪时间解析与格式化，严格对应 Web 版 parseTimeToSec / formatSec / secToHms /
 * formatTimeForCmd / reformatTimeValue。
 */
object TimeUtil {

    /** 返回 null = 空值；NaN = 解析失败；否则秒数 */
    fun parseTimeToSec(str: String?, fmt: String): Double? {
        val s = (str ?: "").trim()
        if (s.isEmpty()) return null
        if (fmt == "hms" || s.contains(":")) {
            val m = Regex("^(?:(\\d+):)?(\\d{1,3}):(\\d{1,2}(?:\\.\\d+)?)$").find(s) ?: return Double.NaN
            val h = m.groupValues[1].ifEmpty { "0" }.toDouble()
            val min = m.groupValues[2].toDouble()
            val sec = m.groupValues[3].toDouble()
            return h * 3600 + min * 60 + sec
        }
        if (!Regex("^\\d+(\\.\\d+)?$").matches(s)) return Double.NaN
        return s.toDouble()
    }

    fun formatSec(n: Double): String {
        // 保留 6 位小数并去掉尾零
        var s = String.format(Locale.US, "%.6f", n)
        s = s.replace(Regex("(\\.\\d*?)0+$"), "$1").replace(Regex("\\.$"), "")
        return s
    }

    fun secToHms(n0: Double): String {
        val neg = n0 < 0
        val n = kotlin.math.abs(n0)
        val h = (n / 3600).toInt()
        val m = ((n % 3600) / 60).toInt()
        val sec = n % 60
        var sStr = formatSec(sec)
        sStr = if (!sStr.contains(".")) {
            sStr.padStart(2, '0')
        } else {
            val parts = sStr.split(".")
            parts[0].padStart(2, '0') + "." + parts[1]
        }
        return (if (neg) "-" else "") + h.toString().padStart(2, '0') + ":" +
            m.toString().padStart(2, '0') + ":" + sStr
    }

    /** null = 不添加；NaN/负数 = 无效不添加 */
    fun formatTimeForCmd(raw: String?, fmt: String): String? {
        val sec = parseTimeToSec(raw, fmt) ?: return null
        if (sec.isNaN() || sec < 0) return null
        return if (fmt == "hms" || (raw ?: "").contains(":")) secToHms(sec) else formatSec(sec)
    }

    fun reformatTimeValue(raw: String, oldFmt: String, newFmt: String): String {
        val sec = parseTimeToSec(raw, oldFmt)
        if (sec == null || sec.isNaN()) return raw
        return if (newFmt == "hms") secToHms(sec) else formatSec(sec)
    }

    private val pad2: (Int) -> String = { String.format(Locale.US, "%02d", it) }

    /** 对应 Web 版 formatTimestamp：yyyy yy MM dd HH mm ss SSS */
    fun formatTimestamp(fmt: String): String {
        val d = Date()
        val map = mapOf(
            "yyyy" to SimpleDateFormat("yyyy", Locale.US).format(d),
            "yy" to SimpleDateFormat("yy", Locale.US).format(d),
            "MM" to SimpleDateFormat("MM", Locale.US).format(d),
            "dd" to SimpleDateFormat("dd", Locale.US).format(d),
            "HH" to SimpleDateFormat("HH", Locale.US).format(d),
            "mm" to SimpleDateFormat("mm", Locale.US).format(d),
            "ss" to SimpleDateFormat("ss", Locale.US).format(d),
            "SSS" to SimpleDateFormat("SSS", Locale.US).format(d),
        )
        return Regex("yyyy|yy|MM|dd|HH|mm|ss|SSS").replace(fmt) { m -> map[m.value] ?: m.value }
    }

    /** 「x 分 y 秒」耗时展示 */
    fun humanDuration(sec: Long): String {
        val m = sec / 60
        val s = sec % 60
        return if (m > 0) "$m 分 $s 秒" else "$s 秒"
    }

    /** 相对时间：刚刚 / n 分钟前 / n 小时前，超过一天显示日期时刻 */
    fun formatAgo(atMs: Long): String {
        if (atMs <= 0) return "—"
        val s = (System.currentTimeMillis() - atMs) / 1000
        return when {
            s < 60 -> "刚刚"
            s < 3600 -> "${s / 60} 分钟前"
            s < 86400 -> "${s / 3600} 小时前"
            else -> SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(atMs))
        }
    }
}
