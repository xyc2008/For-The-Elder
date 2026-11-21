package com.fortheelder.domain.play

/**
 * 这一块里不许出现的说法。
 *
 * 小游戏是最容易滑向那套话术的地方：只要在说明里写上一句「动动脑」，下一句就顺理成章地
 * 变成「对脑子有好处」。可这个应用给不出这种承诺——没有任何一款小游戏能证明自己让人变好，
 * 把话说大了，用户真会拿它当一回事，而这正是这个应用处处躲开的东西。
 *
 * 所以 AC-11.1 点名了一批词，这里做成一张表，测试拿它去扫全仓库的文案
 * （`records/04-质量与验证记录.md` 记着实测命中数）。
 *
 * 用法上有两点必须说清：
 * 1. **扫的是朴素子串。** 因此自己也不能写否定式：一句「这不叫练脑子」照样会被扫出来。
 *    这是存心的——顺着读的人只会记住那几个字，而扫的代价只是换个说法。
 * 2. 允许说 [allowedWords] 里那几个（「玩」「试试」「大脑体操」）。把它们单列出来，
 *    是为了让后来者别顺手把「大脑」二字加进 [banned]——那会把允许的说法一起扫掉。
 */
object PlayWords {

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
