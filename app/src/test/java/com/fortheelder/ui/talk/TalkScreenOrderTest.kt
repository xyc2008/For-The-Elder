package com.fortheelder.ui.talk

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「五句现成的话」摆在页面哪个位置的机械守卫（第 61 轮）。
 *
 * 这一轮只做了一件事：把 `TalkExamples.sentences` 那五句从「整页最后一张卡」搬到
 * 「说一句」那张卡里、输入框下面。搬它的理由是可达性——它要解决的场景是**用户一时想不出
 * 说什么**，而那一刻人最容易做的动作是放弃，不是往下滑。它原来的位置上头还压着「我听见的」
 * 「常伴记住的事」「起点卡」「锚点卡」，用户记下的任何一件事都能把它顶到好几屏之外。
 *
 * **位置这种事最容易在下一次改动里悄悄漂回去**：谁把新卡片插在中间，或者顺手把它挪到最后，
 * 编译和既有用例都还是绿——屏幕上看不见的东西，渲染级用例也不会替它说话（`TalkInputTest`
 * 全篇都在 `performScrollTo()` 之后点，滚到哪儿都点得中）。所以位置要在源码形态上钉住：
 *
 * 1. 这五句**只有一处**渲染，而且必须落在输入框之下、「我听见的／这句我没听明白」之上；
 * 2. 输入框与它之间**不许再开一张卡**——两者同处 `TalkVoice.saySection` 那张卡，这才是
 *    「想不出就走这几句、想得出就在上面打字」这句设计原话的字面形式。
 *
 * 第 62 轮给 `Heard` / `NotUnderstood` 两个分支各套了一层 `BringResponseIntoView`（自动滚），
 * 卡片的构造调用因此退到了下一行，本条守卫的锚点也跟着从「分支与构造调用同一行」收回
 * 「分支关键字那一行」。锚点形状会随重构一起漂，这正是这类形态守卫最脆的地方——
 * 挪动这两张卡的外形时，必须顺手回来看一眼下面那两条 `firstIndexOf`。
 *
 * 它只管**页面的骨架形态**，不管屏幕上量出来的像素——「不用上滑就看得见」是设备上量的，
 * 那是 `TalkFirstScreenTest`（androidTest）与 `records/04` 里的坐标读数的事。
 *
 * 源码目录由 `app/build.gradle.kts` 里的系统属性 `fortheelder.mainSrc` 提供；属性缺失或文件
 * 不存在时这条用例**直接失败**而不是跳过：守卫悄悄失效，比守卫报错难发现得多。
 */
class TalkScreenOrderTest {

    private val screen: File = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 fortheelder.mainSrc，无法定位 main 源码目录"),
        "com/fortheelder/ui/talk/TalkScreen.kt",
    )

    /** 去掉行尾注释后的源码行；下标 0 对应第 1 行，报错信息里统一用 `+1` 换回行号。 */
    private fun source(): List<String> {
        assertTrue("「说两句」的界面源码不在预期位置：$screen", screen.isFile)
        return screen.readLines(Charsets.UTF_8).map { it.substringBefore("//") }
    }

    private fun indicesOf(source: List<String>, needle: String): List<Int> =
        source.indices.filter { source[it].contains(needle) }

    /** 找不到锚点就当场失败——守卫失去锚点会变成假绿，这是最坏的一种绿。 */
    private fun firstIndexOf(source: List<String>, needle: String): Int {
        val found = indicesOf(source, needle)
        assertTrue(
            "${screen.name} 里找不到预期的那一行「$needle」，这条守卫的锚点没了",
            found.isNotEmpty(),
        )
        return found.first()
    }

    @Test
    fun 五句现成的话在页面上只渲染一处() {
        val rows = indicesOf(source(), "viewModel.hear(sentence)")
        assertEquals(
            "示例句的渲染行应当只有一处（现在有 ${rows.size} 处）——两处就等于同一句话在屏上" +
                "出现两次，用户不知道该点哪一个：" + rows.joinToString { "第 ${it + 1} 行" },
            1,
            rows.size,
        )
    }

    @Test
    fun 五句现成的话落在输入框之后与本页回应卡片之前() {
        val source = source()
        val field = firstIndexOf(source, "OutlinedTextField(")
        val examples = firstIndexOf(source, "viewModel.hear(sentence)")
        val heard = firstIndexOf(source, "is TalkUiState.Heard ->")
        val notUnderstood = firstIndexOf(source, "is TalkUiState.NotUnderstood ->")

        assertTrue(
            "示例句跑到输入框上面去了（输入框第 ${field + 1} 行、示例句第 ${examples + 1} 行）：" +
                "这一页的主干动作是「自己写一句」，现成的句子只是它的退路",
            field < examples,
        )
        assertTrue(
            "示例句落到本页的回应卡片下面去了（「我听见的」第 ${heard + 1} 行、" +
                "「这句我没听明白」第 ${notUnderstood + 1} 行、示例句第 ${examples + 1} 行）：" +
                "那样用户一说出话就得再往下找它",
            examples < minOf(heard, notUnderstood),
        )
    }

    @Test
    fun 输入框与示例句之间不再另开一张卡() {
        val source = source()
        val field = firstIndexOf(source, "OutlinedTextField(")
        val examples = firstIndexOf(source, "viewModel.hear(sentence)")

        val cardsBetween = indicesOf(source, "SectionCard(").filter { it > field && it < examples }
        assertTrue(
            "输入框（第 ${field + 1} 行）与示例句（第 ${examples + 1} 行）之间被插进了新卡片：" +
                cardsBetween.joinToString { "第 ${it + 1} 行" } +
                "。两件事必须同处一张卡，否则「说得再具体也还是得往下找」这件事就回来了",
            cardsBetween.isEmpty(),
        )
    }

    @Test
    fun 输入框与示例句同处说一句那张卡() {
        val source = source()
        val field = firstIndexOf(source, "OutlinedTextField(")
        val examples = firstIndexOf(source, "viewModel.hear(sentence)")

        val cardOfField = indicesOf(source, "SectionCard(title = ").lastOrNull { it < field }
        assertTrue("输入框上面一张具名卡片都没有，这一页的骨架变了", cardOfField != null)
        assertEquals(
            "输入框与示例句被分在了两张不同的卡里",
            cardOfField,
            indicesOf(source, "SectionCard(title = ").lastOrNull { it < examples },
        )
        assertTrue(
            "包住输入框的那张卡不再是「说一句」那张",
            source[cardOfField!!].contains("TalkVoice.saySection"),
        )
    }

    @Test
    fun 示例句上面那一句说明仍在且紧挨着() {
        val source = source()
        val titles = indicesOf(source, "TalkVoice.examplesSection")
        assertEquals("「不知道说什么，点一句试试」这句话应当在界面上出现一次", 1, titles.size)

        val examples = firstIndexOf(source, "viewModel.hear(sentence)")
        val title = titles.single()
        // 第 63 轮：五句现成的话外面多包了一层 TargetStack（间距从那以后按 ElderDimens.minTouchGap
        // 排开，而不是卡片内层的 8dp）。所以「紧挨着」现在要跨过这一层看，跨过的**只许**是它。
        val 说明下面的语句 = source.drop(title + 1).filter { it.isNotBlank() && !it.trim().startsWith("//") }
        assertTrue(
            "这句说明下面应当依次是 TargetStack 与那五句的循环，现在头两条语句是" +
                "「${说明下面的语句[0].trim()}」「${说明下面的语句[1].trim()}」：" +
                "中间插进别的东西，说明就指不到那几句话上了",
            说明下面的语句[0].contains("TargetStack {") &&
                说明下面的语句[1].contains("for (sentence in TalkExamples.sentences)"),
        )
        assertEquals(
            "这句说明必须紧贴在示例句上方（现在隔了 ${examples - title} 行，期望 3 行 = " +
                "TargetStack 那一行 + 循环那一行 + 循环体那一行）",
            3,
            examples - title,
        )
    }

    // ---- 措辞里的方位词必须与上面钉住的位置一致 ----

    @Test
    fun 开头那句不能再叫用户去页面底下找() {
        assertTrue(
            "示例句已经不在页尾，开头这句却还写着「页面底下」：${TalkVoice.introLine}",
            !TalkVoice.introLine.contains("页面底下"),
        )
        assertTrue(
            "示例句就在输入框下面，这一句应当说得出「下面」在哪儿：${TalkVoice.introLine}",
            TalkVoice.introLine.contains("下面"),
        )
    }

    @Test
    fun 没听明白那句叫人看的方向是朝上的() {
        // 「这句我没听明白」这张卡排在示例句**下面**（上面那条位置守卫钉住了这一顺序），
        // 所以它只能叫用户往上找；说「下面」就是把手指到没有东西的地方。
        assertTrue(
            "没听明白的退路指向了反方向：${TalkVoice.notUnderstoodLine}",
            !TalkVoice.notUnderstoodLine.contains("下面"),
        )
        assertTrue(
            "示例句在它的上方，这句应当说得出「上面」：${TalkVoice.notUnderstoodLine}",
            TalkVoice.notUnderstoodLine.contains("上面"),
        )
    }
}
