package com.fortheelder.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 界面文案摆放位置的机械守卫（NFR-17）。
 *
 * 需求原文是「所有用户可见文案必须进资源文件或集中常量，不散落硬编码」。本版本的取舍是
 * **不进 `res/`，而进同包的 `*Voice.kt`**（理由见 `records/03-架构决策记录.md` 的 ADR-017），
 * 所以这条规则的机械形式就是：
 *
 * 1. `ui/` 下名为 `*Voice.kt` 的文件可以自由写中文文案——那是它们的职责；
 * 2. 其余界面文件里，**不允许出现含中文的字符串字面量**；
 * 3. 确有理由就地写在界面代码里的（例如示例句子表这种本来就是数据表的东西），
 *    必须在该行留下 `copy-ok: 理由`，把理由写在代码里而不是写在记录里。
 *
 * 这条用例**不检查 `domain/`**。领域层的文案落点已经过逐文件盘点：它们都在具名常量
 * （枚举构造器实参、`object` 的属性）或单一格式化函数里，符合 NFR-17 的「集中常量」，
 * 但没有机械形式可判——这一层是评审项，不是守卫项，账记在 `docs/01` GAP-04 里。
 *
 * 源码目录由 `app/build.gradle.kts` 里的系统属性 `fortheelder.mainSrc` 提供；属性缺失时
 * 这条用例**直接失败**而不是跳过：守卫悄悄失效，比守卫报错难发现得多。
 */
class UiCopyPlacementTest {

    private val cjk = Regex("[\\u4e00-\\u9fff]")

    /** 宽松的字符串字面量：双引号到下一个双引号，不跨行、不做转义处理。够用即可。 */
    private val literal = Regex("\"([^\"\\n]*)\"")

    private val uiRoot: File = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 fortheelder.mainSrc，无法定位 main 源码目录"),
        "com/fortheelder/ui",
    )

    @Test
    fun uiCopyOnlyLivesInVoiceModules() {
        assertTrue("界面源码目录不存在：$uiRoot", uiRoot.isDirectory)

        val uiFiles = uiRoot.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .sortedBy { it.path }
            .toList()

        // 防止「扫了个空目录于是零违规」这种假绿。
        assertTrue("界面层源码文件太少（${uiFiles.size} 个），守卫形同虚设", uiFiles.size >= 10)
        assertTrue(
            "一个 *Voice.kt 都没扫到，规则的第一半会失效",
            uiFiles.any { it.name.endsWith("Voice.kt") },
        )

        val offenders = mutableListOf<String>()
        var scannedLines = 0
        for (file in uiFiles) {
            val isVoiceModule = file.name.endsWith("Voice.kt")
            file.readLines(Charsets.UTF_8).forEachIndexed { index, raw ->
                scannedLines++
                if (isVoiceModule) return@forEachIndexed
                if (raw.contains("copy-ok:")) return@forEachIndexed
                val trimmed = raw.trimStart()
                if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) {
                    return@forEachIndexed
                }
                for (found in literal.findAll(raw.substringBefore("//"))) {
                    if (cjk.containsMatchIn(found.groupValues[1])) {
                        offenders += "${file.relativeTo(uiRoot).path}:${index + 1}  「${found.groupValues[1]}」"
                        break
                    }
                }
            }
        }

        assertTrue("扫描到的界面源码行数异常（$scannedLines 行），守卫可能没真的读到源码", scannedLines > 500)

        assertTrue(
            "界面代码里还有 ${offenders.size} 处中文文案就地写着。" +
                "把它们挪进同包的 *Voice.kt；确有理由留在原处的，在该行加「copy-ok: 理由」：\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }
}
