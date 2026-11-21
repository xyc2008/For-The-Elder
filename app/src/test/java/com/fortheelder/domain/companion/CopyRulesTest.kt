package com.fortheelder.domain.companion

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 词表收成单一来源之后的**行为等价**检查（JVM）。
 *
 * 词的名单由 `CopyRulesPlacementTest` 对着规格逐字比；这个类管的是另一半：三个入口拿同一句话去扫，
 * 给出的理由必须一模一样，而且六类规矩一类都不能哑火。分开的理由是两边都能独立红：
 * 只改词表会红规格那一条，只改调用方（比如某一处忘了改指向、或者自己又写了一遍判断）会红这一条。
 *
 * 收口前的实测差异就是这一条要防的事：「连续」在鼓励语侧算违规、在起点侧也算，「报名」「加微信」
 * 却只在起点侧算——同一句「现在报名，加微信留个电话」交给两个入口，会得到两份不同的理由。
 */
class CopyRulesTest {

    /** 一句话同时踩中六类规矩，且每一类只踩一个词。 */
    private val probe = "连续做同一件事，现在下单就行，加微信就能找到，了解一下，参加一次训练，您真棒。"

    @Test
    fun everyRuleFiresOnASentenceThatBreaksAllSix() {
        assertEquals(
            listOf(
                "出现累计或施压的说法「连续」",
                "出现要花钱的说法「下单」",
                "出现要留联系方式的说法「加微信」",
                "出现没法照做的空话「了解一下」",
                "出现放到哪件事上都成立的空话「真棒」",
                "出现医疗或功效宣称「训练」",
            ),
            CopyRules.violations(probe),
        )
    }

    @Test
    fun theThreeCallersGiveTheSameReasonsForTheSameSentence() {
        val fromTable = CopyRules.violations(probe)
        assertEquals("鼓励语入口的理由与词表不一致", fromTable, Encouragement.violations(probe))
        assertEquals("语音出口的理由与词表不一致", fromTable, CompanionVoice.violations(probe))

        // 起点侧多一段字段前缀（「标题」「第一步」「要准备什么」），剥掉前缀后理由应当一模一样。
        // term 用目录里真有的「书法」、耗时用合规的 10 分钟，免得结构检查混进来。
        val option = StarterOption(
            id = "probe",
            term = "书法",
            title = probe,
            firstStep = probe,
            minutes = 10,
            prep = probe,
        )
        val fromStarter = StarterRules.violations(option)
        // 3 个字段 × 6 类规矩，一条不多一条不少：多了说明结构检查或别的表也报了警，少了说明某一类没扫。
        assertEquals("起点入口的理由条数不对：$fromStarter", 18, fromStarter.size)
        assertEquals(
            "起点入口的理由与词表不一致",
            fromTable.toSet(),
            fromStarter.map { it.substringAfter(' ') }.toSet(),
        )
    }

    @Test
    fun aSentenceThatBreaksNothingDrawsNoReason() {
        val option = StarterCatalog.all[0]
        val text = Encouragement.forAction(option.firstStep)
        assertEquals(emptyList<String>(), CopyRules.violations(text))
        assertEquals(emptyList<String>(), Encouragement.violations(text, option.firstStep))
        assertEquals(emptyList<String>(), CompanionVoice.violations(text))
        assertEquals(emptyList<String>(), StarterRules.violations(option))
    }
}
