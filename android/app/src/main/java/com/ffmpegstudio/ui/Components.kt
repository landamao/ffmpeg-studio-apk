package com.ffmpegstudio.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.interaction.MutableInteractionSource

val R_CARD = RoundedCornerShape(14.dp)
val R_FIELD = RoundedCornerShape(10.dp)

/** 圆角分区标题 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = text,
            color = LocalStudioColors.current.muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
        )
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

/** 表面卡片 */
@Composable
fun CardBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(R_CARD)
            .border(1.dp, LocalStudioColors.current.line, R_CARD),
        color = LocalStudioColors.current.surface,
    ) {
        Column { content() }
    }
}

/** 可折叠参数块（对应 .block） */
@Composable
fun BlockCard(
    icon: ImageVector,
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    visible: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (!visible) return
    val c = LocalStudioColors.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(R_CARD)
            .border(1.dp, c.line, R_CARD),
        color = c.surface,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .heightIn(min = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.accentDim),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    Text(
                        summary, fontSize = 11.sp, color = c.muted,
                        textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth(), lineHeight = 14.sp,
                    )
                }
                Spacer(Modifier.width(6.dp))
                val rot by animateFloatAsState(if (expanded) 0f else -90f, tween(200), label = "chev")
                Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = c.muted, modifier = Modifier.size(16.dp).rotate(rot))
            }
            if (expanded) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(c.line)
                )
                content()
            }
        }
    }
}

/**
 * 可清除的文本选择容器：内容整体可长按选中/跨项选择；
 * 仅「原地轻点」（350ms 内、无位移、未被按钮消费）清除当前选中。
 * 滚动/滑动手势一律不重建容器，否则 key() 重组会打断惯性滚动。
 */
@Composable
fun ClearableSelection(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var selEpoch by remember { mutableIntStateOf(0) }
    Box(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(pass = PointerEventPass.Initial)
                val downAt = System.currentTimeMillis()
                var last: PointerInputChange? = null
                var moved = false
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Final)
                    last = e.changes.firstOrNull()
                    if (last == null) break
                    if (last.positionChanged()) moved = true
                    if (!last.pressed) break
                }
                // 原地轻点且没被子按钮消费 = 点了空白 → 重建容器以清除选中
                if (last != null && !moved && !last.isConsumed && System.currentTimeMillis() - downAt < 350) {
                    selEpoch++
                }
            }
        },
    ) {
        key(selEpoch) { SelectionContainer { content() } }
    }
}

/** 表单字段容器（对应 .field，带底部分隔线） */
@Composable
fun FieldColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = LocalStudioColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .heightIn(min = 1.dp),
    ) { content() }
}

@Composable
fun FieldHead(label: String, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LocalStudioColors.current.ink)
        if (trailing != null) trailing()
    }
}

@Composable
fun FieldHint(text: String, color: Color? = null, modifier: Modifier = Modifier) {
    if (text.isEmpty()) return
    Text(
        text, fontSize = 11.sp, lineHeight = 15.sp,
        color = color ?: LocalStudioColors.current.muted,
        modifier = modifier.padding(top = 6.dp),
    )
}

/** 小号开关（对应 .switch 46x28） */
@Composable
fun SwitchTiny(on: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    val c = LocalStudioColors.current
    val bg by animateColorAsState(if (on) c.accent else c.line, tween(180), label = "swbg")
    Box(
        modifier = modifier
            .width(46.dp)
            .height(28.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onChange(!on) }
            .padding(start = 3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val tx by animateFloatAsState(if (on) 18f else 0f, tween(180), label = "swx")
        Box(
            Modifier
                .size(22.dp)
                .graphicsLayer { translationX = tx.dp.toPx() }
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/** 分段控件（对应 .seg） */
@Composable
fun SegRow(
    options: List<Pair<String, String>>, // value to label
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalStudioColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.surface2)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (active) c.surface else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (active) c.ink else c.muted,
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp,
                )
            }
        }
    }
}

data class SelectOption(val value: String, val label: String)

/** 下拉选择框（模拟 HTML select）：紧凑、贴着字段展开（即手指位置）、点外面自动关闭。
 *  选项多时内部可滚动；当前项高亮打勾；文案单行不折行。 */
@Composable
fun SelectField(
    options: List<SelectOption>,
    value: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalStudioColors.current
    var open by remember { mutableStateOf(false) }
    val label = options.find { it.value == value }?.label ?: value
    Box(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(R_FIELD)
                .background(c.surface2)
                .border(1.dp, c.line, R_FIELD)
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 14.sp, color = c.ink, modifier = Modifier.weight(1f), maxLines = 1)
            Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = c.muted, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Column(
                Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                options.forEach { opt ->
                    val active = opt.value == value
                    DropdownMenuItem(
                        text = {
                            Text(
                                opt.label,
                                fontSize = 14.sp,
                                maxLines = 1,
                                color = if (active) c.accent else c.ink,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                        trailingIcon = if (active) {
                            { Icon(Icons.Rounded.Check, contentDescription = null, tint = c.accent, modifier = Modifier.size(16.dp)) }
                        } else null,
                        onClick = {
                            open = false
                            onSelect(opt.value)
                        },
                    )
                }
            }
        }
    }
}

/** 带标签 + 数值输入的滑条字段 */
@Composable
fun SliderField(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onIntChange: (Int) -> Unit,
    textValue: String,
    onTextChange: (String) -> Unit,
    ticks: List<String> = emptyList(),
    hint: String = "",
    textWidth: Int = 56,
) {
    val c = LocalStudioColors.current
    FieldHead(label) {
        Box(
            modifier = Modifier
                .width(textWidth.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(c.accentDim)
                .border(1.dp, Color.Transparent, R_FIELD),
        ) {
            TextField(
                value = textValue,
                onValueChange = onTextChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                    color = c.accent, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = c.accent,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp),
            )
        }
    }
    Slider(
        value = value.toFloat(),
        onValueChange = { onIntChange(it.toInt()) },
        valueRange = min.toFloat()..max.toFloat(),
        colors = SliderDefaults.colors(
            thumbColor = c.accent,
            activeTrackColor = c.accent,
            inactiveTrackColor = c.surface2,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    if (ticks.isNotEmpty()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ticks.forEach {
                Text(it, fontSize = 10.sp, color = c.muted, fontFamily = FontFamily.Monospace)
            }
        }
    }
    if (hint.isNotEmpty()) FieldHint(hint)
}

/** 通用行（对应 .row） */
@Composable
fun RowItem(
    title: String,
    desc: String? = null,
    value: String? = null,
    icon: ImageVector? = null,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = LocalStudioColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.surface2),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = c.ink)
            if (!desc.isNullOrEmpty()) {
                Text(desc, fontSize = 12.sp, color = c.muted, lineHeight = 16.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (!value.isNullOrEmpty()) {
            Text(value, fontSize = 13.sp, color = c.muted, textAlign = TextAlign.End, modifier = Modifier.padding(start = 12.dp))
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
        if (showChevron) {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = c.muted, modifier = Modifier.size(16.dp).rotate(-90f))
        }
    }
}

/** 状态徽标 */
@Composable
fun Badge(kind: String, text: String) {
    val c = LocalStudioColors.current
    val (bg, fg) = when (kind) {
        "ok" -> c.accentDim to c.accent
        "fail" -> c.dangerDim to c.danger
        else -> c.info.copy(alpha = 0.15f) to c.info
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(fg))
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = fg)
    }
}

/** 等宽日志块 */
@Composable
fun LogBlock(text: String, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    val c = LocalStudioColors.current
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(c.monoBg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        // 长按可选中复制，单个容器内支持跨行选择
        SelectionContainer {
            Text(
                text, fontSize = 10.sp, lineHeight = 15.sp, maxLines = maxLines,
                color = c.monoInk, fontFamily = FontFamily.Monospace,
            )
        }
    }
}

/** 底部弹层外壳（自绘：遮罩 + 底部面板 + 上滑动画，替代 ModalBottomSheet） */
@Composable
fun SheetDialog(
    title: String,
    onDismiss: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    footer: (@Composable RowScope.() -> Unit)? = null,
    tall: Boolean = false,
    content: @Composable () -> Unit,
) {
    val c = LocalStudioColors.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(if (shown) 1f else 0f, tween(240), label = "sheet")
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f * progress))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer { translationY = (1f - progress) * 600.dp.toPx() }
                .background(
                    c.surface,
                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { }
                .navigationBarsPadding()
                .then(if (tall) Modifier.height(620.dp) else Modifier.heightIn(max = 640.dp)),
        ) {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(c.line)
                    .align(Alignment.CenterHorizontally)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.ink, modifier = Modifier.weight(1f))
                if (trailing != null) trailing()
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(c.line)
            )
            Column(
                Modifier
                    .weight(1f, fill = tall)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
            ) { content() }
            if (footer != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) { footer() }
            }
        }
    }
}

/** 主按钮 / 次按钮 */
@Composable
fun Btn(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, kind: String = "normal", enabled: Boolean = true) {
    val c = LocalStudioColors.current
    val (bg, fg, border) = when (kind) {
        "primary" -> Triple(c.accent, Color.White, BorderStroke(1.dp, c.accent))
        "danger" -> Triple(c.danger, Color.White, BorderStroke(1.dp, c.danger))
        "dangerGhost" -> Triple(Color.Transparent, c.danger, BorderStroke(1.dp, c.danger.copy(alpha = 0.35f)))
        else -> Triple(c.surface, c.ink, BorderStroke(1.dp, c.line))
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(border.width, border.brush, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() }
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = fg)
    }
}

@Composable
fun BtnSmall(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, kind: String = "normal") {
    val c = LocalStudioColors.current
    val (bg, fg, border) = when (kind) {
        "primary" -> Triple(c.accent, Color.White, BorderStroke(1.dp, c.accent))
        "on" -> Triple(c.accent, Color.White, BorderStroke(1.dp, c.accent))
        "danger" -> Triple(c.surface, c.danger, BorderStroke(1.dp, c.danger.copy(alpha = 0.35f)))
        else -> Triple(c.surface, c.ink, BorderStroke(1.dp, c.line))
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(border.width, border.brush, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = fg)
    }
}

/** 输入框（对应 CSS input 样式：surface2 底 + 1px 边框） */
@Composable
fun StudioInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    mono: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (minLines > 1) minLines else 1,
    password: Boolean = false,
) {
    val c = LocalStudioColors.current
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, fontSize = 14.sp, color = c.muted) },
        singleLine = maxLines == 1,
        minLines = minLines,
        maxLines = maxLines,
        visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        textStyle = TextStyle(
            fontSize = 14.sp, color = c.ink,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            lineHeight = 20.sp,
        ),
        shape = R_FIELD,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = c.surface2,
            unfocusedContainerColor = c.surface2,
            focusedIndicatorColor = c.accent,
            unfocusedIndicatorColor = c.line,
            disabledIndicatorColor = c.line,
            cursorColor = c.accent,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

// ---------- 媒体缩略图 ----------
/** 进程内 LRU，避免滚动/重组时反复取帧 */
private object ThumbCache {
    private val map = androidx.collection.LruCache<String, android.graphics.Bitmap>(128)
    fun get(k: String): android.graphics.Bitmap? = if (k.isEmpty()) null else map.get(k)
    fun put(k: String, b: android.graphics.Bitmap) { if (k.isNotEmpty()) map.put(k, b) }
}

/** 取视频首帧，缩到最大边 256px；音频/不支持格式返回 null（显示占位图标） */
private fun extractThumb(ctx: android.content.Context, path: String): android.graphics.Bitmap? {
    if (path.isEmpty()) return null
    return try {
        val mmr = android.media.MediaMetadataRetriever()
        try {
            if (path.startsWith("content://")) mmr.setDataSource(ctx, android.net.Uri.parse(path))
            else mmr.setDataSource(path)
            val frame = mmr.frameAtTime ?: return null
            if (frame.width <= 0 || frame.height <= 0) null
            else if (frame.width > 256) {
                android.graphics.Bitmap.createScaledBitmap(
                    frame, 256, (frame.height * 256f / frame.width).toInt().coerceAtLeast(1), true,
                )
            } else frame
        } finally {
            runCatching { mmr.release() }
        }
    } catch (_: Throwable) { null }
}

/**
 * 媒体缩略图：依次尝试 paths 里的文件取首帧（IO 线程 + LRU 缓存），
 * 全部取不到显示胶片占位图标。工作台文件卡与历史记录共用。
 */
@Composable
fun MediaThumb(paths: List<String>, modifier: Modifier = Modifier) {
    val c = LocalStudioColors.current
    val ctx = LocalContext.current
    var bmp by remember(paths) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(paths) {
        val cached = paths.firstNotNullOfOrNull { p -> ThumbCache.get(p) }
        if (cached != null) { bmp = cached; return@LaunchedEffect }
        bmp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            paths.firstNotNullOfOrNull { p -> extractThumb(ctx, p)?.also { ThumbCache.put(p, it) } }
        }
    }
    Box(
        modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(c.surface2),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) {
            androidx.compose.foundation.Image(
                bitmap = b.asImageBitmap(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Outlined.Movie, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp))
        }
    }
}
