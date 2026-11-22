package com.fortheelder.ui.common

/**
 * 滚动兜底提示该不该出现的**唯一**判据（第 58 轮从 [ElderScreen] 的可组合体里抽出来）。
 *
 * ## 现在的规则
 *
 * **下面确实还有没露出来的内容**，并且**滚动区停在顶端**——两条同时成立才出现，缺一条就不出现。
 * 三个入参各自的来路与初值：
 *
 * - [viewportSize]：滚动容器的已量高度（`ScrollState.viewportSize`），初值 0。拿它当「第一次布局
 *   量过了吗」的门槛：[canScrollForward] 在第一帧**任何**屏上都是真——`ScrollState.maxValue` 的
 *   初值是 `Int.MAX_VALUE`（反编译 compose-foundation 1.7.5 的构造器确认过），要等第一次布局量完
 *   才落回真值；少这个门槛，短内容屏也会先闪一下提示。
 * - [canScrollForward]：下面还有内容（`ScrollState.canScrollForward`）。
 * - [scrollValue]：当前滚动位置（`ScrollState.value`）。停在顶端就是 0。
 *
 * ## 被它替掉的旧规则
 *
 * 第 51 轮上的头一版只看后两条（`viewportSize > 0 && canScrollForward`）：只要下面还有内容，
 * 浮标就**一直**盖在滚动区底部。那是一条第 50 轮缺陷驱动的单边判据——它解决了「用户不知道下面
 * 还有内容」，代价是把滚动区底部 64dp（本机 168 px、屏高 2400 px）**永久**押在那里：
 * 用户一路往下滑，那一条带始终盖着同一句话，而被它盖住的内容早就换过好几屏了（见 ADR-047 / ADR-054）。
 *
 * ## 为什么是「停在顶端」而不是「滑过一次就永久消失」
 *
 * 这条规则是**对称**的：一滑动浮标就退场，把那 64dp 还给内容；滑回顶端它再回来。
 * 对称比单向好用——用户不必记住「那句话刚才说过一次了」，任何时候回到开头都能再看到它；
 * 而「滑过一次就永远不再提示」对 55-65 岁人群更危险：一次误触之后就再也找不回这个提示。
 *
 * ## 这个函数钉得住什么、钉不住什么
 *
 * 它钉的是**表达式**：给定上面三个量，该不该出现。`ScrollState` 自己算错
 * （`value` / `canScrollForward` / `viewportSize` 报得不对）不在它的范围内，那是 framework 的事。
 * 它也不代表「老人因此少漏看内容」——那要用户测试，本项目至今没有。
 *
 * 抽出来的直接好处是**可被纯 JVM 用例钉住**：这个文件不 import 任何 Compose 类型，
 * 所以 `app/src/test` 那一侧的 `ScrollHintTest` 能逐条锁住上面每一句话；
 * 抽出来之前这条判定只活在可组合体里，只能靠设备侧用例隔着渲染去问（代价见 ADR-054）。
 */
internal fun shouldShowScrollHint(
    viewportSize: Int,
    scrollValue: Int,
    canScrollForward: Boolean,
): Boolean = viewportSize > 0 && canScrollForward && scrollValue == 0
