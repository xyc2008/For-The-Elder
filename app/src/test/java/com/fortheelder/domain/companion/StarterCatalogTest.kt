package com.fortheelder.domain.companion

import com.fortheelder.domain.engine.intent.IntentKind
import com.fortheelder.domain.engine.intent.IntentLexicon
import com.fortheelder.domain.wish.WishStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 起点目录的单元测试（JVM，纯数据）。
 *
 * 这是 FR-08 的四条验收标准里能机械判的部分：每个起点都给出具体的第一步（AC-08.1）、
 * 耗时不超过 20 分钟（AC-08.2）、不出现要花钱或留联系方式的说法（AC-08.3）、
 * 不出现累计与施压的说法（AC-08.4）。
 *
 * 最后一条靠 [StarterRules.violations] 的词表扫，条数不设上限：多一条起点就多扫一条，
 * 不需要人来复核。
 */
class StarterCatalogTest {

    private val interestTerms: List<String> = IntentLexicon.interestTopics.map { it.term }
    private val stateTerms: List<String> = IntentLexicon.statePhrases.map { it.term }
    private val requestTerms: List<String> = IntentLexicon.requestPhrases.map { it.term }

    // ---- 目录的覆盖面 ----

    @Test
    fun everyInterestTopicHasAtLeastTwoOptions() {
        for (term in interestTerms) {
            assertTrue(
                "「$term」只给了 ${StarterCatalog.byTerm(term).size} 个起点，凑不出「2~3 个选择」",
                StarterCatalog.byTerm(term).size >= 2,
            )
        }
    }

    @Test
    fun everyOptionBelongsToAnInterestTopic() {
        for (option in StarterCatalog.all) {
            assertTrue("起点「${option.id}」挂在不认识的 term「${option.term}」上", option.term in interestTerms)
        }
    }

    @Test
    fun catalogCoversExactlyTheInterestTopics() {
        assertEquals(interestTerms.toSet(), StarterCatalog.coveredTerms)
    }

    @Test
    fun noOptionsForStatesOrRequests() {
        // RC-009 收窄之一：睡不着、身上疼这类状态不该被推销任何活动；请求类也不给起点。
        for (term in stateTerms + requestTerms) {
            assertEquals("「$term」不该有起点", emptyList<StarterOption>(), StarterCatalog.byTerm(term))
        }
    }

    @Test
    fun idsAreUnique() {
        val ids = StarterCatalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun byIdFindsEveryOptionAndReturnsNullForUnknown() {
        for (option in StarterCatalog.all) {
            assertEquals(option, StarterCatalog.byId(option.id))
        }
        assertNull(StarterCatalog.byId("没有这个起点"))
    }

    // ---- AC-08.1：具体的第一步 ----

    @Test
    fun everyOptionHasTitleAndFirstStep() {
        for (option in StarterCatalog.all) {
            assertTrue("${option.id} 没有标题", option.title.isNotBlank())
            assertTrue("${option.id} 没有第一步动作", option.firstStep.isNotBlank())
        }
    }

    @Test
    fun firstStepIsLongEnoughToFollow() {
        for (option in StarterCatalog.all) {
            assertTrue("${option.id} 的第一步只有 ${option.firstStep.length} 个字，照着做不出来", option.firstStep.length >= 10)
        }
    }

    @Test
    fun firstStepNeverSoundsLikeAnUnanswerableSuggestion() {
        for (option in StarterCatalog.all) {
            for (word in StarterRules.vagueWords) {
                assertFalse("${option.id} 的第一步出现空话「$word」", option.firstStep.contains(word))
            }
        }
    }

    @Test
    fun howLongToPrepareIsAlwaysAnswered() {
        for (option in StarterCatalog.all) {
            val prep = option.prep
            assertTrue("${option.id} 没有回答「要准备什么」", prep.isNotBlank())
            val answered = prep == "什么都不用准备" || prep.contains("才用得上") || prep.contains("就行")
            assertTrue("${option.id} 的「要准备什么」既没说不必准备，也没说清前提：$prep", answered)
        }
    }

    // ---- AC-08.2：耗时上限 ----

    @Test
    fun everyOptionFitsInsideTheTwentyMinuteCeiling() {
        assertEquals(20, StarterRules.maxMinutes)
        for (option in StarterCatalog.all) {
            assertTrue("${option.id} 要 ${option.minutes} 分钟，超过了 ${StarterRules.maxMinutes} 分钟", option.minutes in 1..StarterRules.maxMinutes)
        }
    }

    // ---- AC-08.3 / AC-08.4：文案纪律 ----

    @Test
    fun noOptionViolatesTheWordLists() {
        for (option in StarterCatalog.all) {
            assertEquals("起点「${option.id}」的文案有问题：${StarterRules.violations(option)}", emptyList<String>(), StarterRules.violations(option))
        }
    }

    @Test
    fun violationsCatchMoneyAndContactTalk() {
        val broken = sample(firstStep = "先报名交费，加微信领资料。")
        assertTrue(StarterRules.violations(broken).toString().contains("报名"))
        assertTrue(StarterRules.violations(broken).toString().contains("加微信"))
    }

    @Test
    fun violationsCatchStreakTalk() {
        val broken = sample(firstStep = "每天打卡，坚持连续三十天会得到奖励。")
        val found = StarterRules.violations(broken).toString()
        assertTrue(found.contains("打卡"))
        assertTrue(found.contains("连续") || found.contains("坚持"))
        assertTrue(found.contains("奖励"))
    }

    @Test
    fun violationsCatchMedicalTalk() {
        val broken = sample(firstStep = "做一套认知能力训练，可以延缓记性变差。")
        val found = StarterRules.violations(broken).toString()
        assertTrue(found.contains("训练"))
        assertTrue(found.contains("认知能力"))
    }

    @Test
    fun violationsCatchOverlongAndTooShortOptions() {
        assertTrue(StarterRules.violations(sample(minutes = 45)).toString().contains("45"))
        assertTrue(StarterRules.violations(sample(minutes = 0)).toString().contains("0"))
        assertTrue(StarterRules.violations(sample(firstStep = "了解一下")).toString().contains("了解一下"))
        assertTrue(StarterRules.violations(sample(firstStep = "看看就好")).toString().contains("太短"))
    }

    @Test
    fun violationsCatchTermOutsideTheCatalog() {
        val broken = sample(term = "发大财")
        // term 不在目录里时，coveredTerms 里当然没有它，所以这条一定会被标出来。
        assertTrue(StarterRules.violations(broken).toString().contains("发大财"))
    }

    // ---- 放行判据：三样缺一样都不递 ----

    @Test
    fun offerableOnlyForConfirmedInterestWithAnOption() {
        for (kind in IntentKind.entries) {
            for (status in WishStatus.entries) {
                for (term in interestTerms) {
                    val expected = kind == IntentKind.INTEREST && status == WishStatus.CONFIRMED
                    assertEquals("($kind, $term, $status) 的放行判断错了", expected, StarterCatalog.offerable(kind, term, status))
                }
            }
        }
    }

    @Test
    fun offerableIsFalseForEveryOtherKindAndLabel() {
        for (kind in IntentKind.entries) {
            for (status in WishStatus.entries) {
                for (term in stateTerms + requestTerms + "没听说过的事") {
                    assertFalse("($kind, $term, $status) 不该放行", StarterCatalog.offerable(kind, term, status))
                }
            }
        }
    }

    @Test
    fun offerableIsFalseWhenTheTopicHasNoOptionAtAll() {
        // 目录是唯一的内容来源：一件没有起点的事，就算用户同意了也不递，宁可不提。
        assertFalse(StarterCatalog.offerable(IntentKind.INTEREST, "踢毽子", WishStatus.CONFIRMED))
        assertFalse(StarterCatalog.offerable(IntentKind.INTEREST, "书法", WishStatus.PENDING))
        assertFalse(StarterCatalog.offerable(IntentKind.STATE, "睡不好", WishStatus.CONFIRMED))
    }

    @Test
    fun byTermKeepsCatalogOrderAndOnlyMatchingTerm() {
        for (term in interestTerms) {
            val options = StarterCatalog.byTerm(term)
            assertNotNull(options)
            for (option in options) assertEquals(term, option.term)
        }
        assertEquals(emptyList<StarterOption>(), StarterCatalog.byTerm(""))
    }

    private fun sample(
        id: String = "sample-option",
        term: String = "书法",
        title: String = "样例",
        firstStep: String = "找一张白纸，从上往下写满它。",
        minutes: Int = 10,
        prep: String = "什么都不用准备",
    ) = StarterOption(id = id, term = term, title = title, firstStep = firstStep, minutes = minutes, prep = prep)
}
