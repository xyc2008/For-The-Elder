package com.fortheelder.domain.engine.intent

/** 一句话里被听出来的意愿属于哪一类。三类要引出的回应完全不同，所以分开。 */
enum class IntentKind {
    /** 想做某件具体的事，例如「想学书法」。 */
    INTEREST,

    /** 当下的状态，例如「睡不着」。是事实陈述，不是愿望。 */
    STATE,

    /** 直接提出的请求，例如「帮我找找」。 */
    REQUEST,
}

/**
 * 一条命中。
 *
 * [term] 是规范名（`书法`），[cue] 是触发这次识别的词（`一直想`），
 * [evidence] 是**原文逐字片段**——不改写、不补全、不摘要，用户要能拿它对照自己说过的话。
 *
 * 不变式：`text.substring(start, endExclusive) == evidence`，且 `start >= 0`。
 * 这条不变式由单测守着：只要有人以后往这段逻辑里塞进「润色一下原文」，测试立刻红。
 *
 * [start] 与 [endExclusive] 是**相对整段输入的偏移**（不是相对某一句），
 * 所以多条命中可以直接按 start 排出先后。
 */
data class IntentHit(
    val kind: IntentKind,
    val term: String,
    val cue: String,
    val evidence: String,
    val start: Int,
    val endExclusive: Int,
)

/**
 * 识别的结果。
 *
 * 刻意做成「要么给出命中，要么明说没识别」的两态，而**不是**一个可能为空的列表：
 * 需求 AC-06.2 要求无法判断时输出「不识别」，那就得有一个能表达「不识别」的类型。
 * 返回空列表时调用方很容易顺手写成「空就是没有，继续往下走」，而这里不行——
 * 类型会逼着调用方处理「没听懂」这条分支。
 */
sealed interface IntentScan {
    data class Recognized(val hits: List<IntentHit>) : IntentScan

    data object NotRecognized : IntentScan
}

/**
 * 意愿识别。
 *
 * **全部离线**：只用词表做字符串匹配，没有模型、没有网络、不碰任何统计数据
 * （AC-06.1、AC-06.4）。这个类所在的包里不出现 `android`、`androidx`、`java.net`
 * 任何一个符号，这一点由构建产物和静态检查两边守着，记在 records/04。
 *
 * 整体流程：先把输入切成句子，再逐句匹配，最后按 (类别, 规范词条) 去重并排序。
 * 切句的理由是**提示词和主题必须在同一句**——不切句的话「想学点东西。书法是好事」
 * 会被当成一句话，凭空造出一个兴趣。
 *
 * 它是无状态的 `object`：没有任何依赖，所以不进 AppContainer，界面直接调用即可。
 */
object IntentScanner {

    /**
     * @return 整段文本的识别结果。去重后没有任何命中就是 [IntentScan.NotRecognized]。
     */
    fun scan(text: String): IntentScan {
        val hits = mutableListOf<IntentHit>()
        forEachSentence(text) { start, end ->
            hits += scanSentence(text, start, end)
        }
        val unique = hits
            .distinctBy { it.kind to it.term }
            .sortedWith(compareBy({ it.start }, { it.endExclusive }, { it.kind.ordinal }))
        return if (unique.isEmpty()) IntentScan.NotRecognized else IntentScan.Recognized(unique)
    }

    /** 逐句扫描。偏移都换成相对 [text] 的绝对值后再产出。 */
    private fun scanSentence(text: String, start: Int, end: Int): List<IntentHit> {
        val sentence = text.substring(start, end)
        val found = mutableListOf<IntentHit>()

        // 否定只作用在兴趣上：「睡不着」「吃不下」里的「不」是构词成分，不是否定。
        if (IntentLexicon.negationMarkers.none { sentence.contains(it) }) {
            val cues = occurrences(sentence, IntentLexicon.interestCues)
            for (topic in IntentLexicon.interestTopics) {
                for (match in occurrences(sentence, topic.phrases)) {
                    val cue = nearestCue(cues, match) ?: continue
                    found += interestHit(text, start, topic.term, cue, match)
                }
            }
        }

        for (entry in IntentLexicon.statePhrases) {
            for (match in occurrences(sentence, entry.phrases)) {
                found += phraseHit(text, start, IntentKind.STATE, entry.term, match)
            }
        }

        for (entry in IntentLexicon.requestPhrases) {
            for (match in occurrences(sentence, entry.phrases)) {
                found += phraseHit(text, start, IntentKind.REQUEST, entry.term, match)
            }
        }

        return found
    }

    /**
     * 兴趣命中的证据片段。
     *
     * 取**提示词与主题合起来的最短片段**（两端分别取更靠外的那个），例如
     * 「我一直想学书法」里 `一直想` 在 2..5、`书法` 在 5..7，片段就是 2..7 的「一直想学书法」。
     * 之所以不整句照抄：一句话里可能有两个主题（「想学书法和摄影」），整句照抄的话
     * 两条命中的证据会一模一样，用户看不出是哪半句触发的。
     */
    private fun interestHit(
        text: String,
        offset: Int,
        term: String,
        cue: Occurrence,
        topic: Occurrence,
    ): IntentHit {
        val from = minOf(cue.start, topic.start)
        val to = maxOf(cue.endExclusive, topic.endExclusive)
        return IntentHit(
            kind = IntentKind.INTEREST,
            term = term,
            cue = cue.word,
            evidence = text.substring(offset + from, offset + to),
            start = offset + from,
            endExclusive = offset + to,
        )
    }

    /** 状态与请求命中的证据片段：命中的词本身就是证据，提示词与证据是同一个东西。 */
    private fun phraseHit(
        text: String,
        offset: Int,
        kind: IntentKind,
        term: String,
        match: Occurrence,
    ): IntentHit = IntentHit(
        kind = kind,
        term = term,
        cue = match.word,
        evidence = text.substring(offset + match.start, offset + match.endExclusive),
        start = offset + match.start,
        endExclusive = offset + match.endExclusive,
    )

    /** 一个词在句子里的一次出现。 */
    private data class Occurrence(val start: Int, val endExclusive: Int, val word: String) {
        val length: Int get() = endExclusive - start
    }

    /**
     * 找出 [words] 在句子里所有**互不重叠**的出现，按出现位置排序。
     *
     * 按词长从长到短扫，并记下已经被吃掉的字符位置：这样 `下象棋` 不会被拆成 `象棋`，
     * `一直想` 也不会再被 `想` 重复算一次。长度相同的词按词表里的先后，结果可复现。
     */
    private fun occurrences(sentence: String, words: List<String>): List<Occurrence> {
        if (sentence.isEmpty()) return emptyList()
        val taken = BooleanArray(sentence.length)
        val found = mutableListOf<Occurrence>()
        for (word in words.sortedByDescending { it.length }) {
            if (word.isEmpty()) continue
            var i = sentence.indexOf(word)
            while (i >= 0) {
                val endExclusive = i + word.length
                if ((i until endExclusive).none { taken[it] }) {
                    found += Occurrence(i, endExclusive, word)
                    for (k in i until endExclusive) taken[k] = true
                }
                i = sentence.indexOf(word, i + 1)
            }
        }
        return found.sortedBy { it.start }
    }

    /**
     * 离主题最近的那个提示词。
     *
     * 距离为 0 表示两者挨着或重叠。距离相同取更长的词（信息量更大），再相同取更靠前的，
     * 这样同一个输入永远得到同一个结果。一个提示词都没有时返回 null——那就不是兴趣。
     */
    private fun nearestCue(cues: List<Occurrence>, topic: Occurrence): Occurrence? =
        cues.minWithOrNull(
            compareBy({ distance(it, topic) }, { -it.length }, { it.start }),
        )

    /** 两个片段之间隔着多少个字符；重叠算 0。 */
    private fun distance(a: Occurrence, b: Occurrence): Int = when {
        a.endExclusive <= b.start -> b.start - a.endExclusive
        b.endExclusive <= a.start -> a.start - b.endExclusive
        else -> 0
    }

    /**
     * 按句末标点把文本切成句子，逐句回调**相对原文的绝对偏移**。
     *
     * 两端的空白先剃掉：剃掉之后偏移仍然指向原文，所以证据片段里不会出现首尾空格。
     * 空白句（连着的标点、只有换行的行）直接跳过。
     */
    private fun forEachSentence(text: String, action: (start: Int, end: Int) -> Unit) {
        var sentenceStart = 0
        var i = 0
        while (i <= text.length) {
            val isBreak = i == text.length || IntentLexicon.sentenceBreaks.contains(text[i])
            if (isBreak) {
                var start = sentenceStart
                var end = i
                while (start < end && text[start].isWhitespace()) start++
                while (end > start && text[end - 1].isWhitespace()) end--
                if (start < end) action(start, end)
                sentenceStart = i + 1
            }
            i++
        }
    }
}
