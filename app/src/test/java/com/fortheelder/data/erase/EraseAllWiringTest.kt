package com.fortheelder.data.erase

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「清空全部数据」的**接线**守卫（FR-14 / AC-14.3）。
 *
 * [AllDataEraserTest] 钉的是擦除器自己的覆盖面和顺序，但**它证不了擦除器真的接对了东西**：
 * 五个 lambda 全都传 `{}`（空实现）它照样全绿。这条用例补的就是那半边——对着 main 源码，
 * 逐条查这五个入口是不是接到了各自该接的仓库方法上。
 *
 * 为什么查源码而不是跑起来：接线用的五个入口分住在 Room 与两个 DataStore 里，跑起来要设备；
 * 而这里要问的问题非常窄——「这五句话写在一起了吗」——源码文本就是这件事的全部证据。
 * 同一套做法在 [com.fortheelder.ui.CopyCountGuardTest] 与
 * [com.fortheelder.ui.DocScreenCopyGuardTest] 里已经用了好几轮。
 *
 * 还查一条**反向**的：设置页不许再直接调 `settings.resetToDefaults()`。那是第七十一轮之前
 * 的老路——它只清 `settings` 一个文件，却顶着「清空」的名字。有人把它改回去，这条就红。
 *
 * 源码目录由 `app/build.gradle.kts` 的系统属性 `fortheelder.mainSrc` 提供；属性缺失时
 * **直接失败**而不是跳过（跳过等于没有守卫）。
 */
class EraseAllWiringTest {

    private val mainSrc: File = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 fortheelder.mainSrc，无法定位 main 源码目录"),
    )

    private val appContainer: File = File(mainSrc, "com/fortheelder/core/AppContainer.kt")
    private val settingsScreen: File = File(mainSrc, "com/fortheelder/ui/settings/SettingsScreen.kt")

    /**
     * 五个入口各自要接上的那一句。文本取的是源码里逐字的样子（含空格），
     * 改了名字或换了方法就先改这里——这条守卫的用途正是让这种改动**必须**在这里被看见。
     */
    private val 接线 = listOf(
        "val allDataEraser: AllDataEraser by lazy" to "擦除器本身没有被容器建出来",
        "clearWishes = { wishRepository.forgetAll() }" to
            "wish 表没接上：说过的意愿清不掉",
        "clearAnchors = { anchorRepository.clearAll() }" to
            "anchor 表没接上：记下的日常清不掉",
        "clearRhythm = { rhythmRepository.clearAll() }" to
            "作息两张表没接上：作息记录清不掉",
        "resetThrottle = { interventionStore.reset() }" to
            "intervention 这个 DataStore 没接上：说话的时机清不掉",
        "resetSettings = { settings.resetToDefaults() }" to
            "settings 这个 DataStore 没接上：称呼、字号、开关都清不掉",
    )

    @Test
    fun `五个入口各自接在自己的仓库上`() {
        assertTrue("main 源码目录不存在：$mainSrc", mainSrc.isDirectory)
        assertTrue("找不到 AppContainer.kt：$appContainer", appContainer.isFile)

        val 文本 = appContainer.readText(Charsets.UTF_8)
        assertTrue(
            "AppContainer.kt 短得离谱（${文本.length} 字），守卫可能读错了文件",
            文本.length > 3_000,
        )

        val 没接上的 = 接线.filterNot { (片段, _) -> 文本.contains(片段) }
        assertTrue(
            "「清空全部数据」的接线断了 ${没接上的.size} 处，每一处都意味着有一类数据清不掉；" +
                "改 Wiring 就该顺手改这里，两边必须一样：\n" +
                没接上的.joinToString("\n") { (片段, 说明) -> "  $说明\n    期望源码里有：$片段" },
            没接上的.isEmpty(),
        )
    }

    @Test
    fun `设置页走的是擦除器 不是只清设置`() {
        assertTrue("找不到 SettingsScreen.kt：$settingsScreen", settingsScreen.isFile)
        val 文本 = settingsScreen.readText(Charsets.UTF_8)
        assertTrue(
            "SettingsScreen.kt 短得离谱（${文本.length} 字），守卫可能读错了文件",
            文本.length > 3_000,
        )

        assertTrue(
            "设置页没有调用 allDataEraser.erase()：那颗按钮要么没接上，要么被改回了老路",
            文本.contains("container.allDataEraser.erase()"),
        )
        assertTrue(
            "设置页又直接调用 settings.resetToDefaults() 了：那条路只清 settings 一个文件，" +
                "顶着「清空全部数据」的名字会漏掉 wish / anchor / rhythm / intervention 四样",
            !文本.contains("container.settings.resetToDefaults()"),
        )
    }
}
