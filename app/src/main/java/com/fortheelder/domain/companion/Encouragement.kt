package com.fortheelder.domain.companion

/**
 * 做完一件事之后的回话。
 *
 * 三条边界都是机械可判的：
 * - 回话必须带上刚做完的那件事本身（AC-10.1），所以 [forAction] 只有这一个入口，没有「通用的好话」入口；
 * - 不许出现积分、等级、徽章、排行榜这一套（AC-10.2）；
 * - 不许夹带任何要花钱的内容（AC-10.3）。
 *
 * 后两条规则要扫的词表在 [CopyRules] 里，与起点目录、语音出口共用同一张：本对象不再自己持有词表。
 *
 * 这些话**只在内存里存在**，不落库。库里没有完成记录，所以「连续 N 天」既写不出来，也没处可算。
 */
object Encouragement {

    /**
     * 做完一件事之后的那句话。
     *
     * [action] 是起点卡上那一步的**原文**（例如「把常用的那支笔放在手边。」），它自己就带句号，
     * 所以后面那句之前的标点必须是条件式的：模板无条件补一个句号的话，屏幕上会出现「。。」，
     * 这是第 16 轮修掉的真实缺陷（`records/01-迭代记录.md` 第十六轮）。
     *
     * 修法只让**标点**变成条件，不动 [action] 本身：`violations` 要求回话里包含那件事的原文，
     * 把原句末尾的标点削掉会让这条断言落空——为了少一个句号而丢掉「它真的指的是这件事」的检查，
     * 是拿能保的东西去换好看。
     */
    fun forAction(action: String): String =
        "您刚做完的是：$action${terminator(action)}这件事是做完了的，不是想想而已。"

    /** [action] 已经自带句末标点时返回空串，否则补一个句号。 */
    private fun terminator(action: String): String =
        if (sentenceEnds.any { action.endsWith(it) }) "" else "。"

    /** 中文里能收住一句话的标点。少一个，屏幕上就会出现「。，」这类拼接痕迹。 */
    private val sentenceEnds: List<String> = listOf("。", "！", "？", "…")

    /**
     * 返回所有违反项的说明文字，空列表表示这句可以用。带上 [action] 时，还要求回话指的是那件事。
     *
     * 前面三条只有本入口判得了（空句、太短、有没有指出是哪件事），词表判不了；后面的六类一律交给
     * [CopyRules]，本文件不再持有任何词表——第 37 轮之前这里自己有一份，与起点侧那份对不上。
     */
    fun violations(text: String, action: String? = null): List<String> {
        val found = mutableListOf<String>()
        if (text.isBlank()) found += "回话是空的"
        if (text.length < 10) found += "回话太短，看不出指的是哪件事"
        if (action != null && !text.contains(action)) found += "回话里没有那件事本身"

        found += CopyRules.violations(text)

        return found
    }
}
