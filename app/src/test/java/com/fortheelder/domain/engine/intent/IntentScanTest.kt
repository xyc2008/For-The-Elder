package com.fortheelder.domain.engine.intent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 意愿识别引擎的单元测试（JVM，不需要设备）。
 *
 * 这里花的力气主要在**证明它会说「不知道」**：识别对了不算难得，难得的是在听不明白的时候
 * 老实输出「不识别」，而不是硬凑一个兴趣出来。所以一半用例是各种应当**什么都不产出**的输入。
 */
class IntentScanTest {

    /** 断了识别的输入上取值时给出能看懂的错误，不用在每个用例里重复写类型判断。 */
    private fun hitsOf(text: String): List<IntentHit> {
        val scan = IntentScanner.scan(text)
        return when (scan) {
            is IntentScan.Recognized -> scan.hits
            IntentScan.NotRecognized ->
                throw AssertionError("期望识别出内容，实际是「不识别」：$text")
        }
    }

    private fun assertNotRecognized(text: String) {
        val scan = IntentScanner.scan(text)
        assertFalse("期望「不识别」，实际是 $scan：$text", scan is IntentScan.Recognized)
    }

    // ---------- 不识别：宁可漏，不猜 ----------

    @Test
    fun `空串不识别`() {
        assertNotRecognized("")
    }

    @Test
    fun `纯空白不识别`() {
        assertNotRecognized("   \n\t  ")
    }

    @Test
    fun `只有标点不识别`() {
        assertNotRecognized("。。！？")
    }

    @Test
    fun `只有主题没有提示词不识别`() {
        // 「书法挺好的」是评价，不是他自己想做。当作兴趣就是替他做主。
        assertNotRecognized("书法挺好的")
    }

    @Test
    fun `只有提示词没有主题不识别`() {
        assertNotRecognized("我想学点什么")
    }

    @Test
    fun `句内有否定就整句不产出`() {
        assertNotRecognized("我不想学书法")
    }

    @Test
    fun `否定只作用在自己那句话上`() {
        val hits = hitsOf("我不想学书法。想学摄影")

        assertEquals(1, hits.size)
        assertEquals("摄影", hits[0].term)
    }

    @Test
    fun `句内出现否定词时宁可漏掉整句`() {
        // 已知代价：句尾一个「没时间」会把前半句的「想学书法」一并带走。取舍见 records/04。
        assertNotRecognized("我一直想学书法，可惜没时间")
    }

    @Test
    fun `词表外的主题不识别`() {
        // 口琴不在词表里。不收就是不收，不靠猜。
        assertNotRecognized("想学口琴")
    }

    @Test
    fun `提示词与主题跨句不算共现`() {
        assertNotRecognized("想学点东西。书法是好事")
    }

    @Test
    fun `特别里的别不当作否定词`() {
        // 单独一个「别」不收进否定词表，否则「特别」会把整句误杀。
        val hits = hitsOf("我特别想学书法")

        assertEquals(1, hits.size)
        assertEquals("书法", hits[0].term)
    }

    @Test
    fun `睡不着里的不是构词成分不是否定`() {
        val hits = hitsOf("睡不着")

        assertEquals(1, hits.size)
        assertEquals(IntentKind.STATE, hits[0].kind)
    }

    // ---------- 兴趣 ----------

    @Test
    fun `同义写法归到规范词条`() {
        val hits = hitsOf("想学毛笔字")

        assertEquals(1, hits.size)
        assertEquals(IntentKind.INTEREST, hits[0].kind)
        assertEquals("书法", hits[0].term)
        assertTrue("证据里应保留用户的原话：${hits[0].evidence}", hits[0].evidence.contains("毛笔字"))
    }

    @Test
    fun `提示词重叠时取更长的那个`() {
        val hits = hitsOf("我一直想学书法")

        assertEquals(1, hits.size)
        assertEquals("一直想", hits[0].cue)
        assertEquals("一直想学书法", hits[0].evidence)
    }

    @Test
    fun `同一句里两个主题产出两条`() {
        val hits = hitsOf("想学书法和摄影")

        assertEquals(2, hits.size)
        assertEquals(setOf("书法", "摄影"), hits.map { it.term }.toSet())
    }

    @Test
    fun `重复说同一个主题只算一条`() {
        val hits = hitsOf("想学书法，想学书法")

        assertEquals(1, hits.size)
        assertEquals("书法", hits[0].term)
    }

    @Test
    fun `逗号不当作句末`() {
        val hits = hitsOf("我一直想学书法，您看行吗")

        assertEquals(1, hits.size)
        assertEquals("书法", hits[0].term)
    }

    @Test
    fun `证据片段的首尾空白被剃掉`() {
        val hits = hitsOf("  想学书法  ")

        assertEquals(1, hits.size)
        assertEquals("想学书法", hits[0].evidence)
    }

    // ---------- 状态与请求 ----------

    @Test
    fun `一条状态`() {
        val hits = hitsOf("最近老是睡不着")

        assertEquals(1, hits.size)
        assertEquals(IntentKind.STATE, hits[0].kind)
        assertEquals("睡不好", hits[0].term)
        assertEquals("睡不着", hits[0].evidence)
    }

    @Test
    fun `一条请求`() {
        val hits = hitsOf("帮我找找附近能下棋的地方")

        assertEquals(1, hits.size)
        assertEquals(IntentKind.REQUEST, hits[0].kind)
        assertEquals("想打听一件事", hits[0].term)
    }

    @Test
    fun `请求与兴趣可以同时出现`() {
        val hits = hitsOf("我想知道怎么用手机")

        assertEquals(setOf(IntentKind.REQUEST, IntentKind.INTEREST), hits.map { it.kind }.toSet())
    }

    // ---------- 切句与排序 ----------

    @Test
    fun `换行也切句`() {
        val hits = hitsOf("想学书法\n睡不着")

        assertEquals(2, hits.size)
        assertEquals(IntentKind.INTEREST, hits[0].kind)
        assertEquals(IntentKind.STATE, hits[1].kind)
    }

    @Test
    fun `感叹号和问号都切句`() {
        val hits = hitsOf("想学书法！能帮我找找吗？")

        assertEquals(2, hits.size)
        assertEquals(IntentKind.INTEREST, hits[0].kind)
        assertEquals(IntentKind.REQUEST, hits[1].kind)
    }

    @Test
    fun `结果按在原文里出现的先后排序`() {
        val hits = hitsOf("睡不着。想学书法")

        assertEquals(IntentKind.STATE, hits[0].kind)
        assertEquals(IntentKind.INTEREST, hits[1].kind)
        assertTrue("偏移应当递增：${hits.map { it.start }}", hits[0].start < hits[1].start)
    }

    @Test
    fun `同一个输入两次结果相同`() {
        val text = "想学书法。睡不着"

        assertEquals(IntentScanner.scan(text), IntentScanner.scan(text))
    }

    // ---------- 证据片段的不变式 ----------

    @Test
    fun `证据片段与偏移严格对应原文`() {
        val text = "想学书法！能帮我找找吗？"

        for (hit in hitsOf(text)) {
            assertTrue("偏移越界：$hit", hit.start >= 0 && hit.endExclusive <= text.length)
            assertEquals(
                "证据必须与原文一致（不得改写）：$hit",
                text.substring(hit.start, hit.endExclusive),
                hit.evidence,
            )
            assertTrue("词条与提示词都不应为空：$hit", hit.term.isNotBlank() && hit.cue.isNotBlank())
        }
    }

    // ---------- 词表自身的完整性 ----------

    /** 词表里所有词条的所有写法。 */
    private fun allTerms(): List<IntentTerm> =
        IntentLexicon.interestTopics + IntentLexicon.statePhrases + IntentLexicon.requestPhrases

    @Test
    fun `词表里没有跨词条重复的写法`() {
        val seen = mutableMapOf<String, String>()
        val clashes = mutableListOf<String>()
        for (entry in allTerms()) {
            for (phrase in entry.phrases) {
                val previous = seen.put(phrase, entry.term)
                if (previous != null && previous != entry.term) {
                    clashes += "$phrase 同时属于 $previous 与 ${entry.term}"
                }
            }
        }

        assertTrue("同一句话的写法只能归到一个词条：$clashes", clashes.isEmpty())
    }

    @Test
    fun `词条里的写法既不重复也不为空`() {
        for (entry in allTerms()) {
            assertEquals("${entry.term} 的写法里有重复：${entry.phrases}", entry.phrases.size, entry.phrases.toSet().size)
            for (phrase in entry.phrases) {
                assertTrue("${entry.term} 的写法为空或含空白：「$phrase」", phrase.isNotBlank() && phrase.none { it.isWhitespace() })
            }
        }
    }

    @Test
    fun `提示词与否定词都不为空`() {
        for (cue in IntentLexicon.interestCues) {
            assertTrue("提示词为空或含空白：「$cue」", cue.isNotBlank() && cue.none { it.isWhitespace() })
        }
        for (marker in IntentLexicon.negationMarkers) {
            assertTrue("否定词为空或含空白：「$marker」", marker.isNotBlank() && marker.none { it.isWhitespace() })
        }
    }
}
