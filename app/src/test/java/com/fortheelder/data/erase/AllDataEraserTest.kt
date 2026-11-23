package com.fortheelder.data.erase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * [AllDataEraser] 的单元测试：**覆盖面**与**顺序**这两件事。
 *
 * 这一层被设计成一串挂起函数（而不是五个仓库类型），就是为了让这两件事在 JVM 上钉死——
 * 五个记录调用的 lambda 就够，不需要 Room、不需要 DataStore、不需要设备（理由写在
 * [AllDataEraser] 的类注释里；接线是否正确由 `EraseAllWiringTest` 对着 `AppContainer.kt` 守）。
 *
 * 它固定三件事：
 *
 * 1. 五类数据**每一类都被调到，各恰好一次**——少一类就有一条数据清不掉，
 *    多调一次说明这一步被写了两遍（例如把 `clearAnchors` 顺手写在 `clearRhythm` 里）；
 * 2. 顺序是「用户数据 → 说话的时机 → 设置」，最后一步必须是设置：设置里那个
 *    `onboardingDone` 一翻，整棵导航树立刻换回引导页，它要是跑在前面，用户会在数据还没
 *    删完时就被送走（[AllDataEraser] 类注释的 ①②）；
 * 3. **不吞异常**：任何一步抛了，后面的步骤不再执行，异常原样往外抛。界面因此停在设置页、
 *    设置也没被清掉——宁可让用户再点一次，也不要「看起来成功了」却留着一库旧数据。
 *
 * 不测的部分：各仓库自己的清空语义（`WishRepository.forgetAll`、`RhythmRepository.clearAll`
 * 等）由各自的用例守；这里只测「调用」这件事。
 */
class AllDataEraserTest {

    /** 记下每一步被调用了几次、以及调用的先后。 */
    private class 调用记录 {
        val 顺序 = mutableListOf<String>()

        fun 记(名字: String): suspend () -> Unit = { 顺序 += 名字 }

        fun 次数(名字: String): Int = 顺序.count { it == 名字 }
    }

    @Test
    fun `五类数据各清一次 一步都不少`() = runTest {
        val 记录 = 调用记录()
        val eraser = AllDataEraser(
            clearWishes = 记录.记("wishes"),
            clearAnchors = 记录.记("anchors"),
            clearRhythm = 记录.记("rhythm"),
            resetThrottle = 记录.记("throttle"),
            resetSettings = 记录.记("settings"),
        )

        eraser.erase()

        assertEquals(
            "清空的覆盖面变了：五类数据必须每类都清到（FR-14 / AC-14.3）",
            listOf("wishes", "anchors", "rhythm", "throttle", "settings"),
            记录.顺序,
        )
        for (名字 in listOf("wishes", "anchors", "rhythm", "throttle", "settings")) {
            assertEquals("「$名字」被调了不止一次，说明这一步写重了", 1, 记录.次数(名字))
        }
    }

    @Test
    fun `设置必须留在最后一步`() = runTest {
        // 单独立一条：上面那条整表相等已经覆盖了顺序，但它是「顺序全对」的断言，
        // 红了不容易一眼看出坏在哪。这条把「设置最后」单独钉住，红的时候直接指出原因。
        val 记录 = 调用记录()
        val eraser = AllDataEraser(
            clearWishes = 记录.记("wishes"),
            clearAnchors = 记录.记("anchors"),
            clearRhythm = 记录.记("rhythm"),
            resetThrottle = 记录.记("throttle"),
            resetSettings = 记录.记("settings"),
        )

        eraser.erase()

        assertEquals(
            "设置不是最后清的：`onboardingDone` 一翻导航就换回引导页，数据会在还没删完时失去入口",
            "settings",
            记录.顺序.last(),
        )
    }

    @Test
    fun `中途出错时后面的步骤不再执行 异常也不吞`() = runTest {
        // 三个存储之间没有共同事务，这里做不到原子（已登记 docs/07 的 R-15）。
        // 能做的是：不假装成功。第二步抛出去之后，第三步起一概不碰。
        val 记录 = 调用记录()
        val 出事了 = IllegalStateException("第二步写盘失败")
        val eraser = AllDataEraser(
            clearWishes = 记录.记("wishes"),
            clearAnchors = { 记录.顺序 += "anchors"; throw 出事了 },
            clearRhythm = 记录.记("rhythm"),
            resetThrottle = 记录.记("throttle"),
            resetSettings = 记录.记("settings"),
        )

        var 抛出来的: Throwable? = null
        try {
            eraser.erase()
        } catch (错: Throwable) {
            抛出来的 = 错
        }

        assertSame("抛出来的是原来那个异常，不是包了一层的新异常", 出事了, 抛出来的)
        assertEquals(
            "出错之后还在往下清：那会留下「设置已清、库里还满着」的一半状态",
            listOf("wishes", "anchors"),
            记录.顺序,
        )
    }
}
