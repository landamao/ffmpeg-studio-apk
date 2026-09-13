package com.ffmpegstudio.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ffmpegstudio.data.AppViewModel
import com.ffmpegstudio.data.MediaFile
import com.ffmpegstudio.data.Sheet
import com.ffmpegstudio.ui.screens.AiSettingsScreen
import com.ffmpegstudio.ui.screens.HistoryScreen
import com.ffmpegstudio.ui.screens.HomeScreen
import com.ffmpegstudio.ui.screens.PresetEditScreen
import com.ffmpegstudio.ui.screens.PresetsManageScreen
import com.ffmpegstudio.ui.screens.SettingsScreen
import com.ffmpegstudio.ui.screens.WorkScreen
import com.ffmpegstudio.ui.sheets.AiChatSheet
import com.ffmpegstudio.ui.sheets.ConfirmSheet
import com.ffmpegstudio.ui.sheets.ErrSheet
import com.ffmpegstudio.ui.sheets.FullCmdSheet
import com.ffmpegstudio.ui.sheets.LogSheet
import com.ffmpegstudio.ui.sheets.ParamsSheet
import com.ffmpegstudio.ui.sheets.PresetInfoSheet
import com.ffmpegstudio.ui.sheets.PresetSaveChoiceSheet
import com.ffmpegstudio.ui.sheets.RawCmdSheet

private val NAV_H = 64.dp

@Composable
fun AppRoot(vm: AppViewModel) {
    val colors = studioThemeColors(vm.themeMode)
    CompositionLocalProvider(LocalStudioColors provides colors) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val showSheet = vm.sheet

        // SAF 文件选择（对应 Web 版选择输入文件）
        val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                } catch (_: Exception) {}
                val (name, size) = queryFileMeta(context, uri)
                vm.setInput(MediaFile(name = name, path = uri.toString(), size = humanSize(size), sizeB = size))
                vm.goPage("work")
                vm.showToast("已选择 $name")
            }
        }
        val pick: () -> Unit = { pickLauncher.launch(arrayOf("video/*", "audio/*", "image/*")) }

        // 返回键：先关弹层，再返回上一级
        BackHandler(enabled = vm.sheet != null || vm.page != "home") {
            if (vm.sheet != null) vm.closeSheet()
            else vm.back()
        }

        val nested = vm.page == "ai" || vm.page == "presets" || vm.page == "preset-edit"
        val logReopen = vm.page == "work" && vm.run.hasLog && !vm.run.running

        Column(
            Modifier
                .fillMaxSize()
                .background(colors.bg)
                .statusBarsPadding(),
        ) {
            // ---- 顶栏 ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (nested) {
                    IconBtn(Icons.AutoMirrored.Outlined.ArrowBack) { vm.back() }
                    Spacer(Modifier.width(8.dp))
                }
                Column(Modifier.weight(1f)) {
                    val (t, s) = pageTitle(vm.page, vm)
                    Text(t, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.ink)
                    Text(s, fontSize = 12.sp, color = colors.muted)
                }
                if (logReopen) {
                    IconBtn(Icons.Outlined.Description) { vm.openSheet(Sheet.LOG) }
                    Spacer(Modifier.width(8.dp))
                }
                if (vm.page != "ai" && vm.page != "settings") {
                    IconBtn(Icons.Outlined.AutoAwesome) { vm.openSheet(Sheet.AI_CHAT) }
                    Spacer(Modifier.width(8.dp))
                }
                if (vm.page == "work") {
                    IconBtn(
                        if (vm.run.running) Icons.Outlined.Stop else Icons.Filled.PlayArrow,
                        accent = true,
                    ) {
                        if (vm.run.running) vm.openSheet(Sheet.LOG) else vm.tryRun(pick)
                    }
                }
            }

            // ---- 主内容 ----
            Box(Modifier.weight(1f)) {
                PageVisibility(visible = vm.page == "home") { HomeScreen(vm, pick) }
                PageVisibility(visible = vm.page == "work") { WorkScreen(vm, pick) }
                PageVisibility(visible = vm.page == "history") { HistoryScreen(vm) }
                PageVisibility(visible = vm.page == "settings") { SettingsScreen(vm) }
                PageVisibility(visible = vm.page == "ai") { AiSettingsScreen(vm) }
                PageVisibility(visible = vm.page == "presets") { PresetsManageScreen(vm) }
                PageVisibility(visible = vm.page == "preset-edit") { PresetEditScreen(vm) }

                // 命令预览收起后的角标按钮（右下，对应 .cmd-corner）
                if (vm.page == "work" && vm.ws.input != null && vm.ws.cmdCollapsed) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 10.dp, bottom = 8.dp)
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.monoBg)
                            .clickable { vm.update { it.copy(cmdCollapsed = false) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.ExpandLess, contentDescription = null, tint = colors.monoInk, modifier = Modifier.size(14.dp))
                    }
                }

                // 日志 FAB（对应 .fab-log）
                if (vm.page == "work" && vm.run.hasLog && !vm.run.running && showSheet != Sheet.LOG) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 72.dp)
                            .size(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.monoBg)
                            .clickable { vm.openSheet(Sheet.LOG) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Description, contentDescription = null, tint = colors.monoInk, modifier = Modifier.size(20.dp))
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.accent)
                        )
                    }
                }
            }

            // ---- 命令预览条 + 底部导航 ----
            if (vm.page == "work" && vm.ws.input != null && !vm.ws.cmdCollapsed) {
                CommandBar(vm)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.line)
            )
            Nav(vm)
        }

        // ---- 弹层 ----
        when (showSheet) {
            Sheet.LOG -> LogSheet(vm)
            Sheet.CONFIRM -> ConfirmSheet(vm)
            Sheet.FULLCMD -> FullCmdSheet(vm)
            Sheet.ERR -> ErrSheet(vm)
            Sheet.PARAMS -> ParamsSheet(vm)
            Sheet.RAWCMD -> RawCmdSheet(vm)
            Sheet.PRESET_INFO -> PresetInfoSheet(vm)
            Sheet.PRESET_SAVE_CHOICE -> PresetSaveChoiceSheet(vm)
            Sheet.AI_CHAT -> AiChatSheet(vm)
            null -> {}
        }

        // ---- Toast ----
        vm.toast?.let { msg -> ToastOverlay(msg) }
    }
}

@Composable
private fun PageVisibility(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(120)),
    ) { content() }
}

private fun pageTitle(page: String, vm: AppViewModel): Pair<String, String> = when (page) {
    "home" -> "FFmpeg Studio" to "媒体转码工作台"
    "work" -> "工作台" to "调整参数并执行"
    "presets" -> "预设管理" to "编辑 / 排序 / 主页显示"
    "preset-edit" -> (if (vm.editor.editingId == null) "新建预设" else "编辑预设") to "名称 / 备注 / 命令"
    "history" -> "历史" to "处理记录与日志"
    "settings" -> "设置" to "应用偏好"
    "ai" -> "AI 助手" to "模型提供商设置"
    else -> "FFmpeg Studio" to "媒体转码工作台"
}

fun queryFileMeta(context: android.content.Context, uri: android.net.Uri): Pair<String, Long> {
    var name = uri.lastPathSegment ?: "未命名"
    var size = -1L
    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val ni = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            val si = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (ni >= 0) name = c.getString(ni) ?: name
            if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
        }
    }
    return name to size
}

fun humanSize(bytes: Long): String = when {
    bytes < 0 -> "—"
    bytes >= 1024L * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f GB", bytes / 1073741824.0)
    bytes >= 1024L * 1024 -> String.format(java.util.Locale.US, "%.0f MB", bytes / 1048576.0)
    bytes >= 1024 -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

@Composable
fun ToastOverlay(msg: String) {
    val c = LocalStudioColors.current
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = NAV_H + 24.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(c.toastBg)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text(msg, fontSize = 13.sp, color = c.toastInk, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun IconBtn(icon: ImageVector, accent: Boolean = false, onClick: () -> Unit) {
    val c = LocalStudioColors.current
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (accent) c.accent else c.surface)
            .border(1.dp, if (accent) c.accent else c.line, RoundedCornerShape(12.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (accent) Color.White else c.ink, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun CommandBar(vm: AppViewModel) {
    val c = LocalStudioColors.current
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.monoBg),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("命令预览", fontSize = 10.sp, color = c.monoMuted, letterSpacing = 0.5.sp)
                Spacer(Modifier.height(4.dp))
                SelectionContainer {
                    Text(
                        vm.buildCmd(),
                        fontSize = 11.sp, lineHeight = 16.sp, color = c.monoInk,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MiniCmdBtn(Icons.Outlined.KeyboardArrowDown) { vm.update { it.copy(cmdCollapsed = true) } }
                MiniCmdBtn(Icons.Outlined.OpenInFull) { vm.openSheet(Sheet.FULLCMD) }
                MiniCmdBtn(Icons.Outlined.ContentCopy) {
                    clipboard.setText(AnnotatedString(vm.buildCmd()))
                    vm.showToast("命令已复制")
                }
            }
        }
    }
}

@Composable
private fun MiniCmdBtn(icon: ImageVector, onClick: () -> Unit) {
    val c = LocalStudioColors.current
    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(c.monoInk.copy(alpha = 0.1f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = c.monoInk, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun Nav(vm: AppViewModel) {
    val c = LocalStudioColors.current
    val activePage = when (vm.page) {
        "ai" -> "settings"
        "presets", "preset-edit" -> "work"
        else -> vm.page
    }
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(NAV_H)
            .background(c.surface.copy(alpha = 0.97f)),
    ) {
        NavItem("主页", Icons.Outlined.Home, "home", activePage) { vm.goPage("home") }
        NavItem("工作台", Icons.Outlined.Dashboard, "work", activePage) { vm.goPage("work") }
        NavItem("历史", Icons.Outlined.History, "history", activePage) { vm.goPage("history") }
        NavItem("设置", Icons.Outlined.Settings, "settings", activePage) { vm.goPage("settings") }
    }
}

@Composable
private fun RowScope.NavItem(
    label: String,
    icon: ImageVector,
    key: String,
    active: String,
    onClick: () -> Unit,
) {
    val c = LocalStudioColors.current
    val on = key == active
    Column(
        Modifier
            .weight(1f)
            .fillMaxSize()
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (on) {
            Box(
                Modifier
                    .width(28.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                    .background(c.accent)
            )
            Spacer(Modifier.height(2.dp))
        }
        Icon(icon, contentDescription = null, tint = if (on) c.accent else c.muted, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 10.sp, color = if (on) c.accent else c.muted, fontWeight = FontWeight.Medium)
    }
}
