package com.fortheelder.domain.companion

import com.fortheelder.domain.voice.ClaimWords

/**
 * 回话纪律的**唯一**一张词表。
 *
 * 这些规矩本来是逐条落在各个产出方身上的：鼓励语（AC-10.2 / AC-10.3）自己一份，
 * 起点目录（AC-08.3 / AC-08.4）自己一份，语音出口（FR-12）又自己一份。同一句规矩抄三遍的结果是
 * **同一句规矩对不上自己**：第 36 轮实测，「不许出现累计或施压的说法」在 `Encouragement` 与
 * `StarterRules` 各有一张 10 词的表，重叠 9 个词，`Encouragement` 多「勋章」、起点侧多「可得」；
 * 「不许夹带要花钱的内容」两张表 9 + 14 词只重合 2 个，鼓励语侧完全没管「报名」「会员」「加微信」，
 * 起点侧也完全没管「下单」「优惠」「折扣」。按产出方分表这件事本身，就是漏检的来源——不是谁写漏了。
 *
 * 所以这里按**规矩**分表，不按产出方：把文案交给它扫的只有 `Encouragement`、`StarterOptions.violations`、`CompanionVoice` 三处，都拿同一张表、同一个 [violations]；这份名单由 `CopyRulesPlacementTest.onlyTheRegisteredProducersSubmitCopyToTheTable` 核着。
 * 加一句新文案时，六个类别一起生效；哪一类漏了，`CopyRulesPlacementTest` 当场红。
 *
 * 表是**并集**，不是交集：并集之后旧的每一张表都是它的子集，任何原来会被拦下的句子现在仍旧被拦下，
 * 只会拦得更全。`ClaimWords.banned` 不搬过来——它已经在 [com.fortheelder.domain.voice.ClaimWords]
 * 里是单一来源，这里只引用。
 */
object CopyRules {

    /** AC-08.4 / AC-10.2：不出现「坚持 XX 天后可得」这类累计、排行、施压的机制。 */
    val streakWords: List<String> = listOf(
        "打卡", "签到", "连续", "坚持", "积分", "等级", "徽章", "排行", "奖励", "可得", "勋章",
    )

    /** AC-08.3 / AC-10.3：不出现要花钱的说法。 */
    val spendWords: List<String> = listOf(
        "报名", "付费", "收费", "交费", "学费", "入会", "会员", "续费", "办卡", "押金",
        "优惠", "折扣", "购买", "下单", "推荐商品", "特价", "套餐",
    )

    /** AC-08.3：不出现要留联系方式才能继续的说法。 */
    val contactWords: List<String> = listOf("加微信", "公众号", "留个电话", "留下联系")

    /** AC-08.1 的反面：听着客气、但用户照着做不出来的空话。 */
    val vagueWords: List<String> = listOf("了解一下", "了解一下就好", "感兴趣的话", "有机会可以")

    /** AC-10.1 的反面：放到哪件事上都成立的空话，等于没说指的是哪件事。 */
    val emptyPraiseWords: List<String> = listOf(
        "你真棒", "真棒", "加油", "继续努力", "厉害", "了不起", "点赞",
    )

    /**
     * 返回所有违反项的说明文字，空列表表示这句可以用。
     *
     * 六类的顺序固定：累计、花钱、联系方式、空话、通用好话、医疗。产出方自己的结构检查
     * （空句、太短、没指出是哪件事）排在 [violations] 的结果**前面**，因为那些检查是各自的入口才知道的事。
     */
    fun violations(text: String): List<String> {
        val found = mutableListOf<String>()
        hit(text, streakWords).forEach { found += "出现累计或施压的说法「$it」" }
        hit(text, spendWords).forEach { found += "出现要花钱的说法「$it」" }
        hit(text, contactWords).forEach { found += "出现要留联系方式的说法「$it」" }
        hit(text, vagueWords).forEach { found += "出现没法照做的空话「$it」" }
        hit(text, emptyPraiseWords).forEach { found += "出现放到哪件事上都成立的空话「$it」" }
        // 医疗这一类不调 ClaimWords.violations：那边会额外判一次「空句」，空句该由产出方自己说。
        hit(text, ClaimWords.banned).forEach { found += "出现医疗或功效宣称「$it」" }
        return found
    }

    /** [words] 里出现在 [text] 中的那些，保持 [words] 的顺序。 */
    fun hit(text: String, words: List<String>): List<String> = words.filter { text.contains(it) }
}
