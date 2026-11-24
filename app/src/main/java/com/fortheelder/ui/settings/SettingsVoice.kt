package com.fortheelder.ui.settings

/**
 * 设置页上的全部界面文案（NFR-17 的「集中常量」，取舍见 ADR-017）。
 *
 * 这一页是应用里文字最多的地方——它得把每一件"常伴替您做了什么"摊开讲清楚，
 * 所以文案也是全项目改动最频繁的一处。集中到这里之后，改一句话只需要改一行，
 * 不需要在读 300 行布局代码的时候顺手找字符串。
 *
 * ## 两条写作纪律
 *
 * 1. **说明写在开关旁边，不写在别处**。「作息数据」那张卡里的每一句都紧挨着它描述的那件事：
 *    开关上面说的是"记了什么"，开关下面说的是"没打开会怎样"。文案挪了位置就等于失效。
 * 2. **不承诺没做出来的事**。这一页出现的每个数字（条数、保留天数、间隔）都来自真实存储或
 *    真实配置：条数来自 [com.fortheelder.data.rhythm.RhythmRepository.signalCount]，
 *    保留天数来自 `SIGNAL_RETENTION_DAYS`，间隔来自
 *    [com.fortheelder.domain.engine.intervention.InterventionConfig.gapMinutes]。
 *    没有一个是为了好听写上去的。
 *
 * 共用的那几句（清空弹窗、称呼占位提示）在 [com.fortheelder.ui.common.CommonVoice]。
 */
object SettingsVoice {

    const val title: String = "设置"

    // ---- 怎么称呼您 ----

    const val saveAddress: String = "保存称呼"
    const val addressSaved: String = "称呼已保存。"
    const val addressHint: String = "留空就只问候，不带称呼。"

    // ---- 字的大小 ----

    const val fontHint: String = "点一下立刻生效，不用按确定。"

    /** 改字号之后的那句回执。[label] 是档位自己的说法。 */
    fun fontSizeChanged(label: String): String = "字号已改成「$label」。"

    /**
     * 系统字号会叠加这件事必须说出来。用户按了「更大」却发现字大得离谱时，
     * 唯一会怀疑的是这个应用坏了——而它其实是照着系统设置再放大了一档。
     */
    const val fontStackNote: String = "这里的字号会和手机系统的字号叠在一起变大。"

    // ---- 关于常伴 ----

    const val aboutSection: String = "关于常伴"

    /** 版本号走 [com.fortheelder.BuildConfig]，所以屏幕上显示的永远是这一版真的版本。 */
    fun versionLine(name: String): String = "版本 $name"

    const val noUpload: String = "不联网上传您的任何内容。"
    const val noAds: String = "没有广告，没有积分，没有要每天完成的任务。"

    const val localOnlyNote: String =
        "您的称呼、字号这些设置只存在这台手机里。卸载应用时它们会一起消失，我们这边不会留有副本。"

    // ---- 重新来一遍 ----

    const val resetSection: String = "重新来一遍"

    /**
     * 「再看一遍开头那几页」这一行（第 47 轮加）。
     *
     * 在这一轮之前，设置页上唯一能重新见到引导的入口是下面那颗「清空设置」，而那一步会把
     * 称呼和字号一起清掉：想复习一句「它不会做什么」得先付出丢掉设置的成本。这一行补的就是
     * 这个落差——它只读，一个字节都不写。
     *
     * 两行同在这张卡里，各自的说明紧挨着自己那颗按钮（本对象注释第 1 条那条纪律）。
     */
    const val reviewGuideButton: String = "再看一遍开头那几页"
    const val reviewGuideNote: String = "只是重新看一遍，称呼、字号和作息设置都不会变。"

    /**
     * 「清空全部数据」那一行的说明（第 71 轮改写，口径见 `docs/02` 的 RC-034）。
     *
     * 改写的理由一句话：这颗按钮原先叫「清空设置」，做的也确实是清设置——可它对另外四样
     * 东西（作息记录、您说过的意愿、记下的日常、说话的时机）一个字都没说。用户按下之后会
     * 以为这台手机上常伴记的东西都没了，而实际上它们一件都没少。AC-14.3 的口径是
     * 「清空**全部**本地数据」，第 71 轮把行为补齐之后，这句话才有了如实的内容。
     *
     * 逐项列举而不是省略成「全部数据」四个字：老人要能**数得出**自己会失去什么，才谈得上
     * 同意。这也是本对象注释第 1 条那条纪律（说明紧挨着它说明的那颗按钮）。
     */
    const val resetNote: String =
        "会把称呼、字号、作息记录、您说过的意愿、记下的日常，还有说话的时机一起清回初始状态，然后重新显示第一次打开时的说明。"
    const val resetButton: String = "清空全部数据"
    const val resetConfirmText: String =
        "这台手机上常伴记下的东西会全部清掉：称呼、字号、作息记录、您说过的意愿、记下的日常，还有说话的时机。这一步不能撤销。"

    /**
     * 清空之前，把「现在到底存着什么、各有多少条」摆出来。
     *
     * 三个数字都是数据库里的**真实条数**（[com.fortheelder.data.rhythm.RhythmRepository.signalCount]
     * 与两张表的 `Flow`），不是估算值：清空之后它们会立刻变成 0，用户自己看得见这一步真的
     * 生效了——与「作息数据」卡显示条数同一条纪律（第 22 轮立下的：界面上出现的数字要能
     * 在库里查到出处）。
     *
     * 节流状态（说话的时机）不进这个列表：它不是「有几条」的东西，而是一组时刻，
     * 报一个条数只会让人以为存了别的什么。
     */
    fun resetCounts(wishes: Int, anchors: Int, signals: Int): String =
        "现在存着：您说过的意愿 $wishes 条，记下的日常 $anchors 条，作息记录 $signals 条。"

    // ---- 作息数据 ----

    const val rhythmSection: String = "作息数据"

    /** 采集到的条数。[count] 直接来自数据库计数，不是估算。 */
    fun rhythmCount(count: Int): String = "已记录 $count 条。"

    const val rhythmOnNoData: String = "已经打开了，还没记到东西。"
    const val rhythmNone: String = "还没有记录。"

    const val rhythmSwitch: String = "按我的作息说话"

    const val rhythmOnNote: String =
        "记的是手机什么时候亮屏、解锁、插电。常伴活着的时候才记得下来——" +
            "从后台把它划掉之后，那段时间的动作就记不到了。"

    const val rhythmOffNote: String =
        "没有打开。常伴照样能用，只是说话的时机按普通人的作息来猜，可能正好撞上您午睡。"

    const val noPermissionNote: String = "不读您看了哪个应用，也不要额外权限，更不上传。"

    /** 保留天数由 `SIGNAL_RETENTION_DAYS` 提供，写死在这里迟早会和实际清理逻辑对不上。 */
    fun retentionNote(days: Int): String = "只保留最近 $days 天，过期的会自动删掉。"

    const val clearRhythm: String = "清空作息数据"
    const val rhythmConfirmText: String =
        "已经记下的作息记录会全部删掉，作息画像也会一起清空。这一步不能撤销。"

    // ---- 说话的时机 ----

    const val timingSection: String = "说话的时机"
    const val timingLoading: String = "正在看您最近的作息。"

    /** 已经学出一份画像时。[summary] 来自 `wakeSleepSummary`，[sampleDays] 是这份画像看过的天数。 */
    fun timingReady(summary: String, sampleDays: Int): String =
        "按您自己的作息：$summary，是从最近 $sampleDays 天的记录里看出来的。"

    fun timingGathering(validDays: Int, requiredDays: Int): String =
        "作息还在了解中：已经看了 $validDays 天，满 $requiredDays 天就能用上。"

    const val timingInconsistent: String = "这段时间的作息不太固定，先按普通的作息来说话。"

    const val collectOffNote: String =
        "采集没有打开，上面这些是以前记下的。以后说话的时机就按普通的作息来。"

    /** 现在的开口间隔。[gap] 已经过 `asHumanGap()` 格式化。 */
    fun gapLine(gap: String): String = "现在打招呼的间隔：$gap"

    const val gapExplainNote: String =
        "它打招呼的间隔会随您接话的多少自己调整：接话少就往后放，最多一天一次。不会越叫越勤。"

    // ---- 说话的时机 · 用户自己那条安静指令 ----

    /*
     * 这一段补的是这张卡自己写下的理由：它把 FR-05 的判定依据摆到台面上，而 `quietOn` 恰恰是
     * 判定窗口的五个入参之一。首页那颗「今天先安静一会儿」按下去之后，用户在设置页原本一个字
     * 都看不到——想核「它今天为什么不来搭话」就无处可查（AC-05.4）。
     */

    /**
     * 今天正处在「用户自己要求安静」时摆在卡上的那句话。
     *
     * **刻意不说恢复到哪一刻。** 这条指令只到当天为止，可明天起说不说话仍由作息与间隔说了算，
     * 不保证明早一开口就来招呼。写成「明天几点就恢复」会是一句应用兑现不了的承诺。
     */
    const val quietTodayLine: String =
        "今天您说过先安静一会儿，我不迎上来说话。明天起就回到上面那套时机。"

    /**
     * 撤销那颗按钮。
     *
     * 措辞故意与首页那粒「那就说话吧」不同：那一粒按下之后首页当场重判、换一句问候，
     * 这一粒只是把用户自己那条理由撤掉。两处同字面量也会给几条数文案的守卫添麻烦。
     */
    const val quietCancel: String = "取消今天的安静"

    /**
     * 撤销之后的回执。
     *
     * **不能写成「现在起就说话」**：取消只去掉了那一条理由，紧接着的重判通常仍会落到
     * 「离上次开口太近」而不说话。回执只报「安静取消了」这一件已经发生的事。
     */
    const val quietCancelled: String = "好，今天的安静已经取消。"

    // ---- 说话的时机 · 间隔被拉长之后的出口 ----

    /*
     * 这一段补的是同一张卡上另一扇**只进不出**的门。上一段那扇门（安静指令）有出口，是因为
     * 用户自己按下的东西自己能撤；这一段这扇门更糟：用户一次都没接话，间隔就被
     * `InterventionConfig.gapMinutes` 一路退到最长 24 小时，而把它拉回来的唯一途径又是
     * 「接到一次招呼」——那正是 24 小时才发生一次的事。他明明就坐在手机前面，却只能干等。
     */

    /**
     * 摆在出口按钮上面的一句说明。
     *
     * 说的是「上面那个数为什么这么大」，不是「您多久没理我了」——后者是在替用户记账，而这一张
     * 卡只该讲常伴自己的行为。措辞里不写次数与小时数：具体几次退到多少是配置的事，
     * 写死在这里迟早会与 `InterventionConfig` 对不上（本对象注释第 2 条那条纪律）。
     */
    const val gapStretchedNote: String = "您有一阵子没接着说话，间隔就退到了上面那个数。"

    /**
     * 出口按钮。
     *
     * 用「我现在就在这儿」而不是「重置间隔」：用户要说的是**自己在场**这件事，而「重置」这类词
     * 是站在配置的角度说话，不该出现在给老人看的界面上。
     */
    const val presentNow: String = "我现在就在这儿"

    /**
     * 按下之后的回执。
     *
     * 只说「间隔回到最短了」这一件已经发生的事，**不承诺马上说话**：紧接着的重判通常仍会落到
     * 「离上次开口太近」而不说话，这与 [quietCancelled] 是同一条纪律。
     */
    const val presentNowDone: String = "好，间隔回到最短了。"
}
