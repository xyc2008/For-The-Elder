package com.fortheelder.domain.play

import com.fortheelder.domain.voice.ClaimWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「找同类」用字表的纪律（JVM，纯值）。
 *
 * 这一份表是第 48 轮新开的，所以要回答的问题不是「游戏能不能玩」（那是 [SameKindTest] 的事），
 * 而是**这批字本身立不立得住**。四条，一条都不能松：
 *
 * 1. **够挑**。至少 [SameKind.KINDS_ON_BOARD] 类、每类恰好 [SameKind.PER_KIND] 个——
 *    少一个都摆不满四列一整行。
 * 2. **两个字、彼此不成子串、全表无重**。与 [CardWords] 同源的三条标准：三字词在窄屏上折行；
 *    一个是另一个的一部分会让「看着像」变成真的分不清；重复的词会让「找同类」的判据出岔子。
 * 3. **与牌面表零重叠**。同一个词既当牌面又是某类成员，两份表就不再是各管一件事，
 *    而「谁在管」这个问题一旦有两个答案，就没人会去测它们对不上。
 * 4. **过词表**。类别名和词都会被念或写到屏幕上，所以都要过 [ClaimWords]。
 */
class KindWordsTest {

    private val allWords: List<String> = KindWords.all

    // ---- 够挑 ----

    @Test
    fun thereAreEnoughKindsAndEachKindIsFull() {
        assertTrue(
            "只有 ${KindWords.kinds.size} 类，摆不满一局（要找 ${SameKind.KINDS_ON_BOARD} 类）",
            KindWords.kinds.size >= SameKind.KINDS_ON_BOARD,
        )
        for (kind in KindWords.kinds) {
            assertEquals(
                "「${kind.label}」这一类有 ${kind.words.size} 个词，四列摆不满一整行",
                SameKind.PER_KIND,
                kind.words.size,
            )
            assertEquals("「${kind.label}」这一类里有重复的词：${kind.words}", kind.words.size, kind.words.toSet().size)
        }
    }

    @Test
    fun everyWordIsTwoCharacters() {
        for (word in allWords) {
            assertEquals(
                "「$word」不是两个字——三字词在窄屏上会折行，一行高度不一样看着就乱",
                2,
                word.length,
            )
        }
    }

    // ---- 彼此分得开 ----

    @Test
    fun noWordIsASubstringOfAnother() {
        for (a in allWords) {
            for (b in allWords) {
                if (a === b) continue
                assertFalse(
                    "「$a」是「$b」的一部分：两张牌看起来都像是要找的那张",
                    b.contains(a),
                )
            }
        }
    }

    @Test
    fun noTwoKindsShareAWord() {
        for ((i, a) in KindWords.kinds.withIndex()) {
            for (b in KindWords.kinds.drop(i + 1)) {
                val both = a.words.intersect(b.words.toSet())
                assertTrue(
                    "「${a.label}」与「${b.label}」共用了 $both：反查类别的时候会把不相干的格子也算进去",
                    both.isEmpty(),
                )
            }
        }
        assertEquals("全表有重复的词：$allWords", allWords.size, allWords.toSet().size)
    }

    @Test
    fun noWordIsAlsoACardFace() {
        // 两份表**刻意零重叠**：一份管「牌面上摆什么字」，一份管「这些字属于哪一类」。
        // 各管一件事，所以同一个词不该同时出现在两边（理由见 KindWords 的类 KDoc）。
        val shared = allWords.intersect(CardWords.all.toSet())
        assertTrue("这两个词既是牌面又是某一类的成员：$shared", shared.isEmpty())
    }

    // ---- 类别名 ----

    @Test
    fun everyLabelIsDistinctAndNotBlank() {
        val labels = KindWords.kinds.map { it.label }
        assertTrue("有类别没名字：$labels", labels.all { it.isNotBlank() })
        assertEquals("有两个类别同名：$labels", labels.size, labels.toSet().size)
    }

    @Test
    fun noWordOrLabelMakesAMedicalClaim() {
        for (kind in KindWords.kinds) {
            val text = (listOf(kind.label) + kind.words).joinToString(" ")
            val hits = ClaimWords.checkWords(text)
            assertTrue("「${kind.label}」这一类里出现了宣称词 $hits", hits.isEmpty())
        }
    }
}
