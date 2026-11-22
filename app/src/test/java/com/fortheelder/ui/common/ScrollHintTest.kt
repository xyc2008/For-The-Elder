package com.fortheelder.ui.common

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 滚动兜底提示判据的**纯 JVM** 守卫（第 58 轮）。
 *
 * ## 这一轮为什么要抽出一个纯函数
 *
 * 这条判据从第 51 轮起就活在 `ElderScreen` 的可组合体里，只能是**设备侧**用例隔着渲染去问
 * ——要造一屏 40 行的内容、真的滑一遍，才知道规则对不对。第 58 轮把规则从
 * 「下面还有内容就一直盖着」改成「下面还有内容、**且停在顶端**才盖着」，
 * 借这次改动把它挪到 [shouldShowScrollHint]：那个文件**不 import 任何 Compose 类型**，
 * 于是规则本身第一次能在这一层被逐格钉住——不用设备、不用渲染、几毫秒跑完。
 *
 * ## 这里钉的是表达式，不是行为
 *
 * 三列输入（视口量没量出来、滚到哪了、下面还有没有内容）与一列输出的**真值表**在这里是穷举的：
 * [全部八种组合都按真值表走] 把 8 行全跑一遍，另外几条用例把每一行**为什么**是那个值写清楚。
 * 它钉不住的是 `ScrollState` 自己算错（`value` / `canScrollForward` / `viewportSize` 报得不对）
 * ——那是 framework 的事；也钉不住「老人因此少漏看内容」，那要用户测试，本项目至今没有。
 * 渲染层那半由设备侧的 `ScrollMoreHintTest`（自造屏）与 `ScrollOverflowExpectationTest`（六个真屏）守着。
 *
 * ## 最后两条是**结构**守卫
 *
 * 纯函数最容易的坏法有两种，肉眼都看不出来：**没人调用**（规则改了、界面照旧），
 * 与**偷偷依赖了 Compose**（单测就再也钉不住它了，可上面的用例照样绿）。
 * 两种都在 [它得被界面真的用上] 里堵掉。
 */
class ScrollHintTest {

    // ---- 逐格：每一行真值表都有一条用例，失败信息写清那一格在问什么 ----

    @Test
    fun 内容放得下时不出现() {
        assertFalse(
            "内容一屏放得下（canScrollForward 为假）却要出提示：这一格是第 51 轮就有的老规则，" +
                "短内容屏上冒出一个「下面还有内容」是彻头彻尾的假话",
            shouldShowScrollHint(viewportSize = 1200, scrollValue = 0, canScrollForward = false),
        )
    }

    @Test
    fun 下面还有内容而且停在顶端时出现() {
        assertTrue(
            "下面确实还有内容、滚动区就停在顶端，提示却不出现：这条规则唯一的正向用例，" +
                "它红了说明整个提示再也不会出现",
            shouldShowScrollHint(viewportSize = 1200, scrollValue = 0, canScrollForward = true),
        )
    }

    @Test
    fun 已经往下滑过就不出现() {
        // 第 58 轮新加的这条：一滑动浮标就退场，把盖住的 64dp（本机 168 px）还给内容。
        assertFalse(
            "用户已经在往下滑（value=500）了，提示还盖在底部：这正是第 58 轮要治的那件事——" +
                "被判据单边化成「只要下面还有内容就一直盖着」的那一版",
            shouldShowScrollHint(viewportSize = 1200, scrollValue = 500, canScrollForward = true),
        )
        assertFalse(
            "滚动位置哪怕只挪了 1 px 也该退场（判据是 value == 0，不是 value < 某个阈值）",
            shouldShowScrollHint(viewportSize = 1200, scrollValue = 1, canScrollForward = true),
        )
    }

    @Test
    fun 回到顶端以后又出现() {
        // 「滑走 / 滑回」这条对称性是主动选的（取舍见 ADR-054）：滑回顶端它就该回来。
        // 真值表里这一格与「停在顶端」是同一行，但两个用例问的是两件事，留两条。
        val 滑走时 = shouldShowScrollHint(viewportSize = 1200, scrollValue = 500, canScrollForward = true)
        val 滑回后 = shouldShowScrollHint(viewportSize = 1200, scrollValue = 0, canScrollForward = true)
        assertFalse("前置：滑走之后提示应当已经退场（实际 $滑走时）", 滑走时)
        assertTrue(
            "滑回顶端之后提示没回来：对称性断了，用户一次误触就再也找不回这个提示",
            滑回后,
        )
    }

    @Test
    fun 视口还没量出来时不出现() {
        // `ScrollState.maxValue` 的初值是 Int.MAX_VALUE（反编译 compose-foundation 1.7.5 的构造器
        // 确认过），第一次布局量完之前 canScrollForward 在**任何**屏上都是真；viewportSize 的初值是 0。
        // 少了这个门槛，短内容屏的首帧会先闪一下提示（设备侧那条 `首帧不会先闪一下滚动提示` 守着渲染）。
        assertFalse(
            "视口还没量（viewportSize=0）、另外两条却都是真值的样子：首帧门槛掉了，" +
                "短内容屏会先闪一下提示",
            shouldShowScrollHint(viewportSize = 0, scrollValue = 0, canScrollForward = true),
        )
    }

    @Test
    fun 已经滑到底时不出现() {
        assertFalse(
            "已经到了底（下面没内容了）却还要出提示：滑到底提示仍在，就是第 50 轮那条缺陷的原样",
            shouldShowScrollHint(viewportSize = 1200, scrollValue = 5000, canScrollForward = false),
        )
    }

    /**
     * 8 行真值表穷举。逐条用例写的是「为什么」，这一条写的是「有没有漏」：
     * 三列输入的所有组合都过一遍，期望值直接手写在下表里，不由被测函数推出来。
     */
    @Test
    fun 全部八种组合都按真值表走() {
        data class 行(
            val 视口: Int,
            val 位置: Int,
            val 下面还有: Boolean,
            val 期望: Boolean,
            val 这是什么: String,
        )

        val 表 = listOf(
            行(0, 0, true, false, "首帧：还没量版式"),
            行(0, 0, false, false, "首帧且内容放得下"),
            行(1200, 0, true, true, "长内容停在顶端"),
            行(1200, 0, false, false, "短内容停在顶端"),
            行(1200, 1, true, false, "滑走了 1 px"),
            行(1200, 1, false, false, "滑走了且到了底"),
            行(1200, 5000, true, false, "滑到中段"),
            行(1200, 5000, false, false, "滑到底"),
        )

        val 不符 = 表.filter {
            shouldShowScrollHint(it.视口, it.位置, it.下面还有) != it.期望
        }.map {
            "${it.这是什么}：视口=${it.视口} 位置=${it.位置} 下面还有=${it.下面还有} " +
                "期望=${it.期望} 实际=${shouldShowScrollHint(it.视口, it.位置, it.下面还有)}"
        }

        assertTrue("真值表有 ${不符.size} 行不符：\n" + 不符.joinToString("\n"), 不符.isEmpty())
    }

    // ---- 结构守卫：纯函数不许变成死代码，也不许偷偷把 Compose 引进来 ----

    /** main 源码目录，与 `A11yPlacementTest` 同一个来源（`app/build.gradle.kts` 里的系统属性）。 */
    private val 判据文件 = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 fortheelder.mainSrc，无法定位 main 源码目录"),
        "com/fortheelder/ui/common/ScrollHint.kt",
    )

    private val 外壳文件 = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 fortheelder.mainSrc，无法定位 main 源码目录"),
        "com/fortheelder/ui/common/ElderComponents.kt",
    )

    @Test
    fun 它得被界面真的用上() {
        val 判据源码 = 判据文件.readText(Charsets.UTF_8)
        val 外壳源码 = 外壳文件.readText(Charsets.UTF_8)

        val 依赖了安卓 = 判据源码.lines()
            .map { it.trim() }
            .filter { it.startsWith("import ") && !it.startsWith("import kotlin.") }

        assertTrue(
            "ScrollHint.kt 引入了 Compose / Android 的依赖：${依赖了安卓.joinToString("、")}。" +
                "这个文件必须是纯 Kotlin，否则它就没法被这一层的 JVM 用例钉住——" +
                "抽它出来的一半理由就没了",
            依赖了安卓.isEmpty(),
        )

        assertTrue(
            "ElderComponents.kt 里没有 `shouldShowScrollHint(` 这一句：规则抽出来了却没人用，" +
                "上面那些用例全在钉一个界面不认的函数（要改判定就改 ScrollHint.kt，别在屏里另写一份）",
            外壳源码.contains("shouldShowScrollHint("),
        )
    }
}
