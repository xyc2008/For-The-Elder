package com.fortheelder.domain.companion

/**
 * 第 10 轮新增的界面文案集中在这里。
 *
 * 集中的理由是可测：所有会显示给用户的句子都从一个地方取，词表扫描一次就能覆盖全部，
 * 不需要人去翻每个 composable。
 */
object CompanionVoice {

    fun optionsCardTitle(): String = "可以这样开始"

    fun optionsIntro(term: String): String = "「$term」可以这样开始"

    fun optionsNote(): String = "这几步都是按「现在就能做、不用花钱、不用跑一趟」挑的。"

    fun minutesLine(minutes: Int): String = "大概 $minutes 分钟"

    fun prepLine(prep: String): String = "要准备：$prep"

    fun doneLabel(): String = "这一步做完了"

    fun rememberLabel(): String = "把这一步记成日常"

    fun anchorAsk(action: String): String =
        "把「$action」记成日常？记下来之后，您在这个时段打开应用，我会把它摆出来。您随时能删。"

    fun cancelAnchorLabel(): String = "先不记"

    fun anchorCardTitle(): String = "常伴记着的日常"

    fun anchorEmpty(): String =
        "现在还没有。您在上面某一步上点「${rememberLabel()}」，它就出现在这里。"

    fun anchorForgetLabel(): String = "不用记了"

    /** 卡片上常驻的一行静态说明。它不是删除之后的追问，删掉之后不会再冒出来。 */
    fun anchorNote(): String =
        "这些只在您打开应用的时候才出现，我不会另外吵您。${anchorForgetLabel()}就是直接删掉，随时可以再记一次。"

    /**
     * 已经记过的那一步不再给「[rememberLabel]」，换成一句说清现状的话。
     *
     * 第 86 轮：这句话里复述的是 [confirmSlotLabel] 里那一个字不差的措辞，也就是用户刚按下的
     * 那个按钮上的字。此前复述的是 [anchorSlotLabel]（[AnchorSlot] 自己的时段名），四档里
     * 只有 ANYTIME 对不上：按钮写「想起来就做」，回执却写「记在想起来的时候」，用户按下的是
     * 一个词，听到的是另一个词，还得自己在心里把它们对上。其余三档两者本来同形，屏幕文字不变。
     */
    fun anchorAlreadyLine(slot: AnchorSlot): String =
        "这一步已经记成日常了，${confirmSlotLabel(slot)}。"

    fun anchorSlotLabel(slot: AnchorSlot): String = slot.label

    fun anchorActionLine(anchor: Anchor): String = "「${anchor.action}」"

    /** 时段按钮本身就是确认动作：[confirmSlotLabel] 点下去即建立锚点。 */
    fun confirmSlotLabel(slot: AnchorSlot): String = when (slot) {
        AnchorSlot.MORNING -> "记在上午"
        AnchorSlot.AFTERNOON -> "记在下午"
        AnchorSlot.EVENING -> "记在晚上"
        AnchorSlot.ANYTIME -> "想起来就做"
    }

    val confirmableSlots: List<AnchorSlot> = listOf(
        AnchorSlot.MORNING,
        AnchorSlot.AFTERNOON,
        AnchorSlot.EVENING,
        AnchorSlot.ANYTIME,
    )

    /** 首页出现的那一行：只在一件事真的被用户记成日常之后才用得上。 */
    fun dueLine(anchor: Anchor): String =
        "您自己记下的一件事：${anchor.action}想做就做，不做也没关系。"

    /** 本版本刻意不做的通道，说话时不能拿它们许诺任何事。 */
    val absentChannelWords: List<String> = listOf("提醒", "通知", "推送", "闹钟", "到点")

    /** 卡片上的静态句子，供词表扫描一次性覆盖。 */
    fun staticLines(): List<String> = listOf(
        optionsCardTitle(),
        optionsIntro("书法"),
        optionsNote(),
        minutesLine(StarterRules.maxMinutes),
        prepLine("什么都不用准备"),
        doneLabel(),
        rememberLabel(),
        anchorAsk("写满一张纸"),
        anchorAlreadyLine(AnchorSlot.MORNING),
        anchorAlreadyLine(AnchorSlot.AFTERNOON),
        anchorAlreadyLine(AnchorSlot.EVENING),
        anchorAlreadyLine(AnchorSlot.ANYTIME),
        cancelAnchorLabel(),
        anchorCardTitle(),
        anchorEmpty(),
        anchorForgetLabel(),
        anchorNote(),
        dueLine(sampleAnchor),
        confirmSlotLabel(AnchorSlot.MORNING),
        confirmSlotLabel(AnchorSlot.AFTERNOON),
        confirmSlotLabel(AnchorSlot.EVENING),
        confirmSlotLabel(AnchorSlot.ANYTIME),
        anchorSlotLabel(AnchorSlot.MORNING),
        anchorSlotLabel(AnchorSlot.ANYTIME),
    )

    private val sampleAnchor = Anchor(
        term = "书法",
        optionId = "calligraphy-paper",
        action = "找一张白纸，用自己顺手的笔，从上往下写满它，写什么字都行。",
        slot = AnchorSlot.MORNING,
        createdAtEpochMilli = 0L,
    )

    /**
     * 返回所有违反项的说明文字，空列表表示这句可以用。
     *
     * 「花钱 / 累计 / 医疗」这六类词表在 [CopyRules] 里，和鼓励语、起点目录共用同一张；
     * 只有 [absentChannelWords] 是本入口自己的——它说的不是用词，是本版本确实没有的通道。
     */
    fun violations(text: String): List<String> {
        val found = mutableListOf<String>()
        if (text.isBlank()) found += "文案是空的"

        found += CopyRules.violations(text)
        absentChannelWords.filter { text.contains(it) }
            .forEach { found += "拿本版本没有的通道「$it」许诺了事情" }

        return found
    }
}
