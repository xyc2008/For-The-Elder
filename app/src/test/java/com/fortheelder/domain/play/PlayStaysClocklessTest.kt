package com.fortheelder.domain.play

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 游戏这一层不许认识时钟（AC-11.2、AC-12.4 的机械形式）。
 *
 * 「一局不报用时」这句话，前两个游戏是靠评审确认的：读一遍 [SubtractRound] 这个值，
 * 数一数它的字段里没有能放秒数的地方。评审能过，但会退：下一局谁顺手加一个
 * `startedAt: Long`，读代码的人不一定拦得住，而**报出一个用时**是这个应用最不能犯的错
 * （「算五道用了多久」就成了一句心算测评，AC-11.1 明禁说出口）。
 *
 * 所以这一轮把它变成机械守卫：扫 `domain/play` 下每一个 `.kt`，**不允许**出现任何取当前
 * 时刻的写法或时钟类型。这样一来，「这一层不认时钟」就不是一句评语，而是一条会失败的用例。
 *
 * 两处豁免，都写在代码里而不是写在记录里：
 * - 整行注释（以注释符号起头的行）不看——[PlayRound] 的 KDoc 里正当地提到了时钟的名字
 *   （说的是「这一层不用它」），那不是依赖；
 * - 该行含 `copy-ok:` 的跳过，给将来真的有理由时留一个必须写理由的口子。
 *
 * 源码目录由 `app/build.gradle.kts` 里的系统属性 `fortheelder.mainSrc` 提供；属性缺失时
 * 这条用例**直接失败**而不是跳过（照 [com.fortheelder.ui.UiCopyPlacementTest] 的写法）：
 * 守卫悄悄失效，比守卫报错难发现得多。
 */
class PlayStaysClocklessTest {

    private val playRoot: File = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 fortheelder.mainSrc，无法定位 main 源码目录"),
        "com/fortheelder/domain/play",
    )

    /** 一旦出现就说明这一层开始读时间了。 */
    private val clockWays = listOf(
        "AppClock",
        "currentTimeMillis",
        "nanoTime",
        "System.currentTimeMillis",
        "java.time",
        "Instant.now",
        "LocalDateTime",
        "LocalTime",
        "LocalDate",
        "SystemClock",
        "elapsedRealtime",
        "Date(",
    )

    @Test
    fun thePlayLayerNeverReachesForAClock() {
        assertTrue("游戏层的源码目录不存在：$playRoot", playRoot.isDirectory)

        val files = playRoot.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .sortedBy { it.path }
            .toList()

        // 防止「扫了个空目录于是零违规」这种假绿。
        assertTrue("游戏层源码文件太少（${files.size} 个），守卫形同虚设", files.size >= 4)
        assertTrue(
            "没扫到 SubtractRound 所在的那个文件，守卫会漏掉第三个游戏",
            files.any { it.name == "SubtractSeven.kt" },
        )

        val offenders = mutableListOf<String>()
        var scannedLines = 0
        for (file in files) {
            file.readLines(Charsets.UTF_8).forEachIndexed { index, raw ->
                val trimmed = raw.trimStart()
                if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) {
                    return@forEachIndexed
                }
                if (raw.contains("copy-ok:")) return@forEachIndexed
                scannedLines++
                val code = raw.substringBefore("//")
                for (way in clockWays) {
                    if (code.contains(way)) {
                        offenders += "${file.relativeTo(playRoot).path}:${index + 1}  「$way」  ${raw.trim()}"
                        break
                    }
                }
            }
        }

        assertTrue(
            "扫描到的代码行数异常（$scannedLines 行），守卫可能没真的读到源码",
            scannedLines > 150,
        )

        assertTrue(
            "游戏层有 ${offenders.size} 处伸手去拿时钟了。一局的用时只能来自界面那一层的实测，" +
                "领域层一旦能读时间，就会有人把秒数算进结果里（AC-11.2、AC-12.4）：\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }
}
