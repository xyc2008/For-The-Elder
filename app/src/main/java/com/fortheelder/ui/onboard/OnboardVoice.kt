package com.fortheelder.ui.onboard

/**
 * 引导三页上的全部文案（NFR-17 的「集中常量」，取舍见 ADR-017）。
 *
 * 引导是用户见到这个应用的第一分钟，也是唯一一次"无法被跳过"的阅读机会。三页各说一件事：
 *
 * 1. **它不做什么**。建立信任靠先划边界，不靠罗列功能——所以 `wontBullets` 和
 *    `doesBullets` 一样是三条以上，而且排在更显眼的位置（见 [doesSection] 与
 *    [wontSection] 的排布）。
 * 2. **称呼与字号**。两样都能空着，都能以后再改，所以这一页没有任何必填项。
 * 3. **先问再采**（AC-04.4）。这一页把"为什么要知道您什么时候用手机"讲清楚，
 *    再由用户点同意或不同意；两个按钮都往下走，选「先不用」不会把人卡在引导里。
 *
 * ## 一个词都不能改
 *
 * 前两页的句子在 `androidTest`（`OnboardingTest`）里有按原文的断言，`docs/09-用户手册.md`
 * 也逐句引用了它们。搬进这里时是逐字复制，没有顺手润色——润色会让文档、用例和屏幕三者对不上，
 * 而后两者是这一版唯一能证明"屏幕上是这么写的"的东西。
 *
 * 注意 [retentionNote] 与设置页那句**故意不一样**：这里说"过期的自动删掉"，
 * 设置页说"过期的会自动删掉"。两处都有各自的用例盯着，不要统一。
 */
object OnboardVoice {

    // ---- 第一页：它现在会做的 / 不会做的 ----

    const val subtitle: String = "一个懂节制的陪伴者"

    const val doesSection: String = "它现在会做的"

    /**
     * 「会做的」五条。
     *
     * 每一条都对应这一版真的存在的能力：称呼（设置页）、按时段问候（问候引擎）、
     * 应用内字号（四档排版表）、说两句（对话页）、三个小游戏（游戏页）。
     * 加第六条的代价不是多一行字，而是必须真的做出来——所以这份列表跟着功能长，不跟着文案长。
     */
    val doesBullets: List<String> = listOf(
        "记得您希望被怎么称呼。",
        "按一天里的时段，跟您问一声好；这会儿不合适就不出声。",
        "按您的需要放大字，不用去改手机设置。",
        "想说话的时候，跟它说两句。",
        "手上闲不住的时候，有三个小游戏，不算分也不排名次。",
    )

    const val wontSection: String = "它不会做的"

    /** 三条边界。第一条对应「零权限」这个事实：安装时确实什么都不用授权。 */
    val wontBullets: List<String> = listOf(
        "不会向您推销任何东西。",
        "不会催您、不会记您几天没来。",
        "不会替您判断身体状况。",
    )

    const val closingNote: String =
        "上面这些都在这一版里，不用再打开什么开关，它也不会弹窗通知您。想做点什么、或者什么都不做，都行。"

    const val nextButton: String = "我看看怎么用"

    // ---- 第二页：称呼与字号 ----

    const val page2Title: String = "您希望被怎么称呼"
    const val addressHint: String = "空着也行，以后在设置里随时能补。"
    const val fontPickNote: String = "选一个看着最舒服的。下面这句话会立刻变成您选的大小。"
    const val previewSection: String = "您会先看到这样一句话"

    /** 预览卡末尾的一句。用来说明"只预览这一块"，免得用户以为整页都跟着变。 */
    const val previewNote: String = "字号预览就到这里，下面的按钮不受影响。"

    const val nextStep: String = "下一步"
    const val backStep: String = "回去看上一页"

    // ---- 第三页：先问再采 ----

    const val page3Title: String = "想先跟您说件事"
    const val askFirst: String = "这一步得先问过您。"
    const val whySection: String = "为什么要知道您什么时候用手机"

    const val whyText: String =
        "因为「该跟您说句话了」这件事得按您的作息来。常伴不看闹钟——" +
            "有人六点就起了，有人九点才醒。知道您什么时候醒着、什么时候在歇着，" +
            "它才不会挑您午睡的时候开口。"

    const val whereSection: String = "记在哪里、记多久"
    const val whereLocalOnly: String = "只记在这台手机上，不上传，常伴也不联网。"
    const val whereNoPermission: String = "不读您看了哪个应用、跟谁聊了，也不要任何额外权限。"

    /** 保留天数由 `SIGNAL_RETENTION_DAYS` 提供，与设置页那句同源不同措辞。 */
    fun retentionNote(days: Int): String = "只保留最近 $days 天，过期的自动删掉。"

    const val whereForgotten: String = "常伴从后台被划掉之后，那期间就记不到了。"

    const val accept: String = "可以，按我的作息来"
    const val decline: String = "先不用"
    const val bothChoiceNote: String = "两个按钮都能选。以后在设置里随时能改，也能一键把记下的东西全删掉。"

    /**
     * 引导进度。**刻意不做成可见文字**：设计上就不让用户看见「还剩几页」这件事
     * （见 `OnboardingScreen` 的类注释），所以这句话只挂在表头的无障碍状态里，
     * 由屏幕朗读说出来，版式一字不动。
     */
    fun pageIndicator(page: Int, total: Int): String = "第 $page 页，共 $total 页"
}
