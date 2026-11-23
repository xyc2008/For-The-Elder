package com.fortheelder.ui.talk

import kotlin.math.roundToInt

/**
 * 「刚冒出来的那张卡，得自己走进看得见的地方」（第 62 轮）。
 *
 * 起因是一条量出来的事实：第 61 轮在设备上点了一句现成的话之后，新出现的「我听见的」
 * 在首屏上**整张都在滚动区下沿以下**——按探针报出的滚动位移倒推，它的上沿落在 y2822，
 * 而滚动区是 `[0,338][1080,2337]`，**低了 485 像素**；语义树里那一屏连它的节点都没有
 * （读数在 `build/r61/probe61_out4.txt` 第 162 行）。也就是说用户点了一句话，
 * 屏幕上**一点变化都看不到**，还得自己想起来往上滑。「点一句就有人应声」这条承诺，
 * 在真实的那一屏上不成立。
 *
 * 这一层只做两件事：**算出该滚到哪里**、**只滚这一次**。
 *
 * 1. 判定与算术抽成纯函数（[isFullyVisibleInViewport] / [revealScrollTarget]），
 *    好处是每一档边界都能在 JVM 上逐条钉住，不必靠设备反复试才有结论；
 *    设备上那一层只负责回答「这根绳子接对了没有」。
 * 2. 「只滚这一次」是产品决定而不是实现细节：用户点完之后自己往下滑，
 *    常伴**不许把他拽回来**。所以滚动由一次性标记驱动，不做持续跟随。
 *
 * 一条取舍写在明处：[revealScrollTarget] 把卡对齐到**视口上沿**，而不是「让它多露一点」。
 * 第 61 轮就是栽在「露一点」上——实测里那张卡只露 32 像素、更早一次只露 1 像素，
 * 等于没露。对齐上沿是唯一不必再赌一次像素的写法。
 */

/**
 * 这张卡是不是**已经整张**在视口里了。
 *
 * 两个条件缺一不可：上沿不能跑到视口上边（`>= 0`），下沿不能超出视口底。
 * 只看上沿会放过「比视口还高、下半截被切掉」的卡，只看下沿会放过「已经滑过头顶」的卡。
 *
 * [viewportHeight] 为 0 一律当作「看不见」：0 是 `ScrollState.viewportSize` 的初值
 * （第 58 轮反编译 compose-foundation 1.7.5 的构造器确认过），表示滚动容器的第一次布局
 * 还没量完。这时宁可让调用方下一帧再来，也不能拿一个 0 高的视口算出荒唐的目标值。
 */
internal fun isFullyVisibleInViewport(
    cardTopInViewport: Float,
    cardHeight: Int,
    viewportHeight: Int,
): Boolean =
    viewportHeight > 0 &&
        cardTopInViewport >= 0f &&
        cardTopInViewport + cardHeight <= viewportHeight

/**
 * 该滚到哪一像素。
 *
 * 坐标口径：[cardTopInViewport] 是卡片**上沿在滚动视口里的位置**，视口上沿记 0，
 * 被滑到视口上方时为负。于是这张卡在内容里的位置 = `cardTopInViewport + currentScroll`，
 * 把它挪到视口上沿就是滚到那个数——这正是下面那个加法。
 *
 * 三种不动的情形，各有各的理由：整张已经看得见（用户自己停的位置不该被动）；
 * 视口没量完；内容本来就不用滚（[maxScroll] 非正，短内容屏）。算出来的目标越界时
 * 夹回 `[0, maxScroll]`，不去追一个滚动区到不了的值。
 */
internal fun revealScrollTarget(
    cardTopInViewport: Float,
    cardHeight: Int,
    viewportHeight: Int,
    currentScroll: Int,
    maxScroll: Int,
): Int {
    if (viewportHeight <= 0 || maxScroll <= 0) return currentScroll
    if (isFullyVisibleInViewport(cardTopInViewport, cardHeight, viewportHeight)) return currentScroll
    return (currentScroll + cardTopInViewport).roundToInt().coerceIn(0, maxScroll)
}
