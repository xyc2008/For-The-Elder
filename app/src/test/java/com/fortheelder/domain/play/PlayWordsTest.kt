package com.fortheelder.domain.play

import com.fortheelder.ui.play.PlayVoice
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 宣称词词表的单元测试（JVM）。
 *
 * AC-11.1 说不许出现「训练」「改善」「提升」这类话。这条在 `records/04` 里是靠 grep 扫
 * `app/src/main/java` 全文验的，但扫描只能证明「扫过的那些行里没有」，证明不了
 * 「以后新写的也没有」。所以这里把这一块**所有会被用户读到的字符串**枚举出来逐条过词表：
 * 枚举出来的取值一个都跑不掉，比正则更难糊弄过去。
 *
 * 枚举的清单本身也是断言的一部分：`PlayHint` 用枚举、`PlayVoice` 用纯字符串函数、
 * 玩法说明写在目录项上，都是为了能在这里被列全。散在 Compose 里的字面量列不全，
 * 所以 `PlayScreen.kt` 里一个用户可见的字面量都没有。
 */
class PlayWordsTest {

    /** 这一块会出现在屏幕上的每一句话。 */
    private val spokenTexts: List<String> = buildList {
        add(PlayVoice.title)
        add(PlayVoice.startLabel)
        add(PlayVoice.leaveLabel)
        add(PlayVoice.doneNote)
        addAll(PlayHint.entries.map { it.text }.filter { it.isNotBlank() })
        for (game in PlayCatalog.games) {
            add(game.name)
            add(game.howTo)
            val round = game.deal(Random(1))
            add(PlayVoice.rule(game))
            add(PlayVoice.promise(game))
            add(PlayVoice.nextTarget(round))
            add(PlayVoice.doneLine(round, 43))
            add(PlayVoice.doneLine(round, 60))
            add(PlayVoice.doneLine(round, 125))
            for (seconds in listOf(0, 7, 59, 60, 61, 180)) add(PlayVoice.duration(seconds))
        }
    }

    @Test
    fun everySpokenLineIsOnTheList() {
        assertTrue("枚举出来的句子太少，这条用例就没在验什么", spokenTexts.size >= 15)
    }

    @Test
    fun noSpokenLineUsesClaimWords() {
        for (text in spokenTexts) {
            assertEquals(
                "「$text」有问题：${PlayWords.violations(text)}",
                emptyList<String>(),
                PlayWords.violations(text),
            )
        }
    }

    @Test
    fun everyHintSaysSomething() {
        for (hint in PlayHint.entries) {
            if (hint == PlayHint.NONE) continue
            assertTrue("$hint 是要给用户看的一句话，不该是空的", hint.text.isNotBlank())
            assertEquals(emptyList<String>(), PlayWords.violations(hint.text))
        }
        assertEquals("NONE 表示没话说，必须是空串", "", PlayHint.NONE.text)
    }

    @Test
    fun everyBannedWordIsActuallyDetected() {
        assertTrue("禁用词表是空的，等于没有约束", PlayWords.banned.isNotEmpty())
        for (word in PlayWords.banned) {
            assertEquals("词表里的「$word」拿它自己去扫都扫不出来", listOf(word), PlayWords.checkWords(word))
        }
    }

    @Test
    fun theScannerReallyCatchesClaimWords() {
        assertTrue("「认知能力」没被扫出来", PlayWords.violations("做一套认知能力评估").isNotEmpty())
        assertTrue("「延缓」没被扫出来", PlayWords.violations("每天玩一玩可以延缓记性变差").isNotEmpty())
        assertTrue("「训练」没被扫出来", PlayWords.violations("注意力训练").isNotEmpty())
        assertTrue("空句子没被扫出来", PlayWords.violations("").isNotEmpty())
    }

    @Test
    fun theAllowedWordsAreNotOnTheBannedList() {
        for (word in PlayWords.allowedWords) {
            assertFalse("允许说的「$word」被列进了禁用词", PlayWords.banned.contains(word))
        }
        assertEquals(
            "这几种说法是允许的，不该被扫出来",
            emptyList<String>(),
            PlayWords.violations("手上有事做的时候，玩一会儿，试试这个大脑体操。"),
        )
    }
}
