package com.fortheelder.domain.voice

/**
 * 全 App 都不许出现的说法。
 *
 * 这个应用给不出任何医疗或功效承诺，所以凡是容易被读成承诺的词，一处都不该有。
 * 这张表原先有两份：`domain/play/PlayWords.kt` 里 15 个词，`StarterRules.medicalWords`
 * 里 9 个词，两份重叠 8 个。同一个规则有两个出处，早晚会出现「按 A 表写是合规的、
 * 按 B 表写是违规的」这种自相矛盾的局面，所以第 12 轮把词表提到这里，两处都引用这一份
 * （原 `PlayWords` 已删除，不留转发壳）。**保留的是更严的那一版**：15 个词。
 *
 * 用法上有两点必须说清：
 * 1. **扫的是朴素子串。** 因此自己也不能写否定式：一句「这不叫练脑子」照样会被扫出来。
 *    这是存心的——顺着读的人只会记住那几个字，而扫的代价只是换个说法。
 * 2. 允许说 [allowedWords] 里那几个（「玩」「试试」「大脑体操」）。把它们单列出来，
 *    是为了让后来者别顺手把「大脑」二字加进 [banned]——那会把允许的说法一起扫掉。
 */
object ClaimWords {

    /** 医疗与功效宣称。出现了就是 violated。 */
    val banned: List<String> = listOf(
        "训练",
        "改善",
        "提升",
        "延缓",
        "评估",
        "认知能力",
        "疗效",
        "疗程",
        "治疗",
        "预防",
        "诊断",
        "康复",
        "大脑退化",
        "记忆力",
        "变聪明",
    )

    /** 允许的说法。列在这里是给后来者看的，不参与判定。 */
    val allowedWords: List<String> = listOf("玩", "试试", "大脑体操")

    fun checkWords(text: String, words: List<String> = banned): List<String> =
        words.filter { text.contains(it) }

    /** 违反项的说明。空列表表示这句话可以用。 */
    fun violations(text: String): List<String> {
        val found = mutableListOf<String>()
        if (text.isBlank()) found += "这句话是空的"
        checkWords(text).forEach { found += "出现医疗或功效宣称「$it」" }
        return found
    }
}
