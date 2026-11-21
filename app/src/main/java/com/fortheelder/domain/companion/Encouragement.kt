package com.fortheelder.domain.companion

/**
 * 做完一件事之后的回话。
 *
 * 三条边界都是机械可判的：
 * - 回话必须带上刚做完的那件事本身（AC-10.1），所以 [forAction] 只有这一个入口，没有「通用的好话」入口；
 * - 不许出现积分、等级、徽章、排行榜这一套（AC-10.2）；
 * - 不许夹带任何要花钱的内容（AC-10.3）。
 *
 * 这些话**只在内存里存在**，不落库。库里没有完成记录，所以「连续 N 天」既写不出来，也没处可算。
 */
object Encouragement {

    fun forAction(action: String): String =
        "您刚做完的是：$action。这件事是做完了的，不是想想而已。"

    val genericWords: List<String> = listOf("你真棒", "真棒", "加油", "继续努力", "厉害", "了不起", "点赞")

    val gameWords: List<String> = listOf("打卡", "签到", "连续", "坚持", "积分", "等级", "徽章", "排行", "奖励", "勋章")

    val moneyWords: List<String> = listOf("优惠", "折扣", "购买", "下单", "推荐商品", "特价", "套餐", "收费", "付费")

    /** 返回所有违反项的说明文字，空列表表示这句可以用。带上 [action] 时，还要求回话指的是那件事。 */
    fun violations(text: String, action: String? = null): List<String> {
        val found = mutableListOf<String>()
        if (text.isBlank()) found += "回话是空的"
        if (text.length < 10) found += "回话太短，看不出指的是哪件事"
        if (action != null && !text.contains(action)) found += "回话里没有那件事本身"

        checkWords(text, genericWords).forEach { found += "出现放到哪件事上都成立的空话「$it」" }
        checkWords(text, gameWords).forEach { found += "出现累计或施压的说法「$it」" }
        checkWords(text, moneyWords).forEach { found += "出现要花钱的内容「$it」" }

        return found
    }

    fun checkWords(text: String, words: List<String>): List<String> = words.filter { text.contains(it) }
}
