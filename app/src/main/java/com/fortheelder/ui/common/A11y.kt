package com.fortheelder.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

/**
 * 无障碍语义。这些修饰符不发可见文字，只把「这一页叫什么、这是第几页、这块内容会变、
 * 这是几行几列的第几格」交给系统语义树，屏幕朗读（TalkBack）读的就是这棵树。
 *
 * 之所以集中放这里：语义标签一旦散落在各屏里，改文案时很容易漏掉一处，而漏掉的后果
 * （朗读读不出当前位置）在线性版式里肉眼完全看不出来。
 *
 * 下面每个修饰符都只挂**一个** `semantics {}` 块。`heading()` 与 `stateDescription` 合成
 * 一块，是为了让标题与页码只有一处入口；同一节点叠两个 `semantics` 会怎样，本项目没有
 * 实测过，这里选择不碰它（见 records/03 的 ADR-019 第 2 条）。
 */

/**
 * 页面标题。加在每屏表头的标题 `Text` 上。
 *
 * @param state 需要朗读出来、但**不显示**在屏幕上的补充状态（例如「第 2 页，共 3 页」）。
 *   传 `null` 表示没有额外状态。
 */
fun Modifier.asScreenTitle(state: String? = null): Modifier = semantics {
    heading()
    if (state != null) stateDescription = state
}

/** 卡片／小节标题。用于在同一屏内部建立层级，让朗读可以按标题跳读。 */
fun Modifier.asSectionTitle(): Modifier = semantics { heading() }

/**
 * 内容原地变化、值得主动播报的区域。只用在「同一节点占据固定位置、文字被替换」的地方；
 * 新出现的节点请求朗读的行为本项目没有实测过，因此不用它（见 ADR-019）。
 */
fun Modifier.announcesWhenItChanges(): Modifier =
    semantics { liveRegion = LiveRegionMode.Polite }

/** 网格整体：告诉朗读「这是一张 rows 行 columns 列的表」。 */
fun Modifier.gridOf(rows: Int, columns: Int): Modifier =
    semantics { collectionInfo = CollectionInfo(rows, columns) }

/**
 * 网格中的一格。行列均从 0 开始，每格跨度为 1。
 *
 * 有行列信息，朗读才会说出「第 3 行第 2 列」，否则老人听完一串数字不知道自己在哪。
 */
fun Modifier.gridCell(row: Int, column: Int): Modifier = semantics {
    collectionItemInfo = CollectionItemInfo(row, 1, column, 1)
}
