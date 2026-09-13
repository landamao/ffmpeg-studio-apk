package com.ffmpegstudio.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex

/**
 * 长按拖动排序（对应 Web 版 makeSortable）：手动布局的网格/列表通用，带实时目标位置预览。
 *
 * 关键约束：**拖动期间绝不重组列表**（跨父容器的项在换位重组时会被销毁重建、
 * 手势检测器随之中断——主页分行网格此前「拖一下就没了」的根因）。
 * 所以预览全部在绘制层完成：
 * - 被拖项：手指增量累计进 dragPosition，graphicsLayer 平移跟手；
 * - 其它项：中心越过邻居时维护一份「视觉顺序」visualOrder，用 graphicsLayer
 *   位移把邻居挪到让位后的槽位（不碰 VM 数据、不触发布局）；
 * - 命中判定用其它项的「有效矩形」（布局矩形 + 让位偏移），中心必须真正
 *   越进邻居当前所在的位置才换，停在两项之间不会来回抖；
 * - 松手把 visualOrder 一次性提交 onReorder；没落进任何项则原样弹回。
 * 提交后的偏移清理放在 onGloballyPositioned：布局一旦落到新槽位就清掉对应偏移。
 */
class DragGridState(
    val currentIds: () -> List<String>,
    val onReorder: (List<String>) -> Unit,
    val onFinished: (() -> Unit)? = null,
) {
    var draggingId by mutableStateOf<String?>(null)
    /** 手指拖动累计增量 = 被拖项相对布局槽位的视觉偏移 */
    var dragPosition by mutableStateOf(Offset.Zero)
    /** 拖动期间演化的视觉顺序；null = 尚未越过任何邻居 */
    var visualOrder: List<String>? = null
    /** 其它项的让位偏移（绘制层消费） */
    val offsets = mutableStateMapOf<String, Offset>()
    val rects = mutableStateMapOf<String, Rect>()
    /** 滚动容器（视口）矩形：verticalScroll 会裁剪超出视口的内容，
     *  被拖项必须钳制在视口内，否则往上拖就「消失」 */
    var containerRect by mutableStateOf<Rect?>(null)
    var moved = false

    /** 由 rememberDragGridState 注入的手势修饰符工厂 */
    var gestureHook: (String) -> Modifier = { Modifier }

    /** 挂到滚动容器上，记录视口边界 */
    fun containerModifier(): Modifier = Modifier.onGloballyPositioned {
        containerRect = it.boundsInWindow()
    }

    fun itemModifier(id: String): Modifier = Modifier
        .onGloballyPositioned {
            val new = it.boundsInWindow()
            val old = rects[id]
            // 提交换序后布局落到新槽位，对应偏移使命完成，清掉避免双重位移
            if (draggingId != id && old != null && old.topLeft != new.topLeft) {
                offsets.remove(id)
            }
            rects[id] = new
        }
        .zIndex(if (draggingId == id) 1f else 0f)
        .graphicsLayer {
            if (draggingId == id) {
                translationX = dragPosition.x
                translationY = dragPosition.y
                scaleX = 1.04f
                scaleY = 1.04f
            } else {
                val o = offsets[id] ?: Offset.Zero
                translationX = o.x
                translationY = o.y
            }
        }
        .then(gestureHook(id))
}

@Composable
fun rememberDragGridState(
    currentIds: () -> List<String>,
    onReorder: (List<String>) -> Unit,
    onFinished: (() -> Unit)? = null,
): DragGridState {
    val haptics = LocalHapticFeedback.current
    val st = remember { DragGridState(currentIds, onReorder, onFinished) }

    /** 中心落在哪个其他项的「有效矩形」（布局矩形 + 让位偏移）内 */
    fun hitTest(dragId: String, center: Offset): String? {
        st.rects.forEach { (id, r) ->
            if (id == dragId) return@forEach
            val o = st.offsets[id] ?: Offset.Zero
            if (center.x >= r.left + o.x && center.x <= r.right + o.x &&
                center.y >= r.top + o.y && center.y <= r.bottom + o.y
            ) return id
        }
        return null
    }

    st.gestureHook = { id ->
        Modifier.pointerInput(id) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    st.draggingId = id
                    st.moved = false
                    st.dragPosition = Offset.Zero
                    st.offsets.clear()
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onDrag = { change, amount ->
                    change.consume()
                    st.dragPosition += amount
                    // 钳制在滚动视口内（留 24px 边距），防止被 verticalScroll 裁剪「消失」；
                    // 行宽与容器同宽时水平方向没有移动余地，跳过水平钳制
                    val cont = st.containerRect
                    val self = st.rects[id]
                    if (cont != null && self != null) {
                        val m = 24f
                        var dx = st.dragPosition.x
                        var dy = st.dragPosition.y
                        if (self.top + dy < cont.top + m) dy = cont.top + m - self.top
                        if (self.bottom + dy > cont.bottom - m) dy = cont.bottom - m - self.bottom
                        if (self.width < cont.width - 2 * m) {
                            if (self.left + dx < cont.left + m) dx = cont.left + m - self.left
                            if (self.right + dx > cont.right - m) dx = cont.right - m - self.right
                        }
                        st.dragPosition = Offset(dx, dy)
                    }
                    // ---- 实时预览：中心越进邻居的有效位置就演化视觉顺序 ----
                    val layout = st.rects[id]
                    if (layout != null) {
                        val center = Offset(
                            layout.center.x + st.dragPosition.x,
                            layout.center.y + st.dragPosition.y,
                        )
                        val hit = hitTest(id, center)
                        if (hit != null) {
                            val order = st.visualOrder ?: st.currentIds()
                            val from = order.indexOf(id)
                            val to = order.indexOf(hit)
                            if (from >= 0 && to >= 0 && from != to) {
                                val newOrder = order.toMutableList().apply {
                                    removeAt(from); add(to, id)
                                }
                                st.visualOrder = newOrder
                                // 重算其它项的让位偏移：从自己的布局槽位挪到视觉槽位
                                val base = st.currentIds()
                                newOrder.forEachIndexed { slotIdx, mid ->
                                    if (mid == id) return@forEachIndexed
                                    val fromRect = st.rects[mid]
                                    val toRect = base.getOrNull(slotIdx)?.let { st.rects[it] }
                                    if (fromRect != null && toRect != null) {
                                        st.offsets[mid] = Offset(
                                            toRect.left - fromRect.left,
                                            toRect.top - fromRect.top,
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                onDragEnd = {
                    val vo = st.visualOrder
                    if (vo != null && vo != st.currentIds()) {
                        st.moved = true
                        st.onReorder(vo)
                    }
                    st.draggingId = null
                    st.dragPosition = Offset.Zero
                    st.visualOrder = null
                    if (!st.moved) st.offsets.clear() // 未提交：所有项弹回原位
                    if (st.moved) st.onFinished?.invoke()
                },
                onDragCancel = {
                    st.draggingId = null
                    st.dragPosition = Offset.Zero
                    st.visualOrder = null
                    st.offsets.clear()
                },
            )
        }
    }
    return st
}
