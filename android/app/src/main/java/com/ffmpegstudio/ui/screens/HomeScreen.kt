package com.ffmpegstudio.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Gif
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ffmpegstudio.data.AppViewModel
import com.ffmpegstudio.data.Preset
import com.ffmpegstudio.logic.CommandBuilder
import com.ffmpegstudio.ui.CardBox
import com.ffmpegstudio.ui.LocalStudioColors
import com.ffmpegstudio.ui.RowItem
import com.ffmpegstudio.ui.SectionTitle
import com.ffmpegstudio.ui.rememberDragGridState
import kotlinx.coroutines.launch

private fun presetIcon(id: String): ImageVector = when (id) {
    "compress" -> Icons.Outlined.FileDownload
    "mp4" -> Icons.Outlined.Movie
    "audio" -> Icons.Outlined.MusicNote
    "gif" -> Icons.Outlined.Gif
    "trim" -> Icons.Outlined.ContentCut
    "raw" -> Icons.Outlined.Terminal
    else -> Icons.Outlined.AutoAwesome
}

@Composable
fun HomeScreen(vm: AppViewModel, pick: () -> Unit) {
    val c = LocalStudioColors.current
    val homePresets = vm.presets.filter { it.home }.take(6)
    val drag = rememberDragGridState(
        currentIds = { vm.presets.filter { it.home }.take(6).map { it.id } },
        onReorder = { order -> vm.reorderPresets(order) },
        onFinished = { vm.showToast("主页顺序已更新") },
    )

    Column(
        Modifier
            .fillMaxSize()
            .then(drag.containerModifier())
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        SectionTitle("快速开始")

        // 快速开始网格（2 列，长按拖动排序）
        // key(id)：节点身份跟随数据；行级 zIndex：拖动行要浮在最上（2），
        // 有让位偏移的行其次（1）——否则向上拖时让位卡片被下一行盖住「消失」
        homePresets.chunked(2).forEach { rowPresets ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .zIndex(
                        when {
                            rowPresets.any { it.id == drag.draggingId } -> 2f
                            rowPresets.any { drag.offsets.containsKey(it.id) } -> 1f
                            else -> 0f
                        }
                    ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowPresets.forEach { p ->
                    key(p.id) {
                        QuickCard(
                            vm = vm,
                            p = p,
                            modifier = Modifier
                                .weight(1f)
                                .then(drag.itemModifier(p.id)),
                        )
                    }
                }
                if (rowPresets.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }

        SectionTitle("本周概览")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(vm.statTotal().toString(), "已处理", Modifier.weight(1f))
            StatCard(vm.statOk().toString(), "成功", Modifier.weight(1f))
            StatCard(vm.statSuccessRate(), "成功率", Modifier.weight(1f))
        }

        if (vm.recent.isNotEmpty()) {
            SectionTitle("最近文件")
            CardBox {
                vm.recent.take(3).forEach { f ->
                    key(f.path) {
                        RevealRow(
                            onDelete = { vm.deleteRecent(f) },
                            onClick = {
                                vm.setInput(f)
                                vm.goPage("work")
                            },
                        ) {
                            RowItem(
                                title = f.name,
                                desc = f.path,
                                value = f.size,
                                icon = Icons.Outlined.Movie,
                            )
                        }
                    }
                }
            }
        }

        SectionTitle("从文件选择")
        CardBox {
            RowItem(
                title = "选择媒体文件",
                desc = "从相册、文件管理器或分享入口导入",
                icon = Icons.Outlined.Folder,
                showChevron = true,
                onClick = pick,
            )
        }
        Spacer(Modifier.height(88.dp))
    }
}

@Composable
private fun QuickCard(vm: AppViewModel, p: Preset, modifier: Modifier = Modifier) {
    val c = LocalStudioColors.current
    val desc = if (p.type == "raw") (p.note.ifEmpty { "自定义命令" })
    else (p.note.ifEmpty { CommandBuilder.paramsToCmdFragment(p.params).take(24) })
    Column(
        modifier
            .heightIn(min = 96.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(14.dp))
            .clickable {
                vm.applyPresetById(p.id)
                vm.goPage("work")
                vm.showToast("已加载预设")
            }
            .padding(14.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.accentDim),
            contentAlignment = Alignment.Center,
        ) {
            Icon(presetIcon(p.id), contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(p.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(desc, fontSize = 12.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StatCard(n: String, label: String, modifier: Modifier = Modifier) {
    val c = LocalStudioColors.current
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(12.dp))
            .padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(n, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.ink)
        Text(label, fontSize = 11.sp, color = c.muted)
    }
}

/**
 * iOS 风格左滑露按钮：内容层随手势向左平移，右侧只露出固定宽度的
 * 删除按钮（不是整行红底）；滑开一半以上吸附到展开位，点击「移除」才删除，
 * 点内容或滑回则收起。
 */
@Composable
private fun RevealRow(
    onDelete: () -> Unit,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val c = LocalStudioColors.current
    val density = LocalDensity.current
    val revealPx = with(density) { REVEAL_WIDTH.toPx() }
    val offset = remember { Animatable(0f) }
    var revealed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxWidth()) {
        // 底层：右侧固定宽度删除按钮（浅红露出区 + 实色按钮）
        Box(Modifier.matchParentSize()) {
            Box(Modifier.fillMaxSize().background(c.danger.copy(alpha = 0.08f)))
            Row(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(REVEAL_WIDTH)
                    .background(c.danger)
                    .clickable { onDelete() },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = "移除", tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("移除", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
            }
        }
        // 内容层：横向拖动平移，最多露出按钮宽度
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = -offset.value }
                .background(c.surface)
                .clickable {
                    if (offset.value > 0f) {
                        revealed = false
                        scope.launch { offset.animateTo(0f) }
                    } else onClick()
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                offset.snapTo((offset.value - dragAmount).coerceIn(0f, revealPx))
                            }
                        },
                        onDragEnd = {
                            val open = offset.value >= revealPx * 0.5f
                            revealed = open
                            scope.launch { offset.animateTo(if (open) revealPx else 0f) }
                        },
                    )
                },
        ) { content() }
    }
}

private val REVEAL_WIDTH = 86.dp
