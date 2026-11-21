package com.fortheelder.domain.companion

import com.fortheelder.domain.engine.intent.IntentKind
import com.fortheelder.domain.wish.WishGate
import com.fortheelder.domain.wish.WishStatus

/**
 * 一个「现在就能做」的起点。
 *
 * [minutes] 与 [prep] 不是装饰：FR-08 要求每个起点都写明「大概要花多久」「需要准备什么」，
 * 少了任何一个，用户就没法判断自己此刻做不做得到。
 */
data class StarterOption(
    val id: String,
    val term: String,
    val title: String,
    val firstStep: String,
    val minutes: Int,
    val prep: String,
)

/**
 * 起点目录。纯数据 + 纯函数，不碰数据库、不碰界面。
 *
 * 目录只覆盖 [com.fortheelder.domain.engine.intent.IntentLexicon.interestTopics] 里的 13 件事：
 * 那是「想做的事」，给它们递一个起点是帮忙。状态类与请求类的话不给起点——
 * 睡不着的时候被推销一项活动，是打扰不是陪伴（RC-009 收窄之一）。
 */
object StarterCatalog {

    val all: List<StarterOption> = listOf(
        // 书法
        StarterOption(
            id = "calligraphy-paper",
            term = "书法",
            title = "写满一张纸",
            firstStep = "找一张白纸，用自己顺手的笔，从上往下写满它，写什么字都行。",
            minutes = 10,
            prep = "什么都不用准备",
        ),
        StarterOption(
            id = "calligraphy-pen",
            term = "书法",
            title = "挑一支顺手的笔",
            firstStep = "把家里能写字的笔都摊开，一支一支在纸上画一道，留下最顺手的那一支。",
            minutes = 5,
            prep = "家里有能写字的笔才用得上；没有就先跳过这一条",
        ),
        StarterOption(
            id = "calligraphy-trace",
            term = "书法",
            title = "照着喜欢的字描一遍",
            firstStep = "找一张自己看着顺眼的字，铺在纸旁边，照着它描一遍。",
            minutes = 15,
            prep = "手边有字帖或字画才用得上；没有就先跳过这一条",
        ),

        // 下棋
        StarterOption(
            id = "chess-rules",
            term = "下棋",
            title = "看一遍走法",
            firstStep = "把一种棋的走法从头看一遍，只看到「每一步怎么走」就够了。",
            minutes = 15,
            prep = "什么都不用准备",
        ),
        StarterOption(
            id = "chess-solo",
            term = "下棋",
            title = "自己摆一局",
            firstStep = "把棋子摆到棋盘上，自己走一步、再替对面走一步，把这一局走完。",
            minutes = 10,
            prep = "家里有棋和棋盘才用得上；没有就先跳过这一条",
        ),
        StarterOption(
            id = "chess-watch",
            term = "下棋",
            title = "看别人下一局",
            firstStep = "找一段别人下棋的影像，完整看完一局，看不懂的地方先放着。",
            minutes = 15,
            prep = "什么都不用准备",
        ),

        // 摄影
        StarterOption(
            id = "photo-window",
            term = "摄影",
            title = "拍一张窗外的样子",
            firstStep = "拿起手机，对着窗外拍一张，拍完看一眼就够。",
            minutes = 5,
            prep = "什么都不用准备",
        ),
        StarterOption(
            id = "photo-three",
            term = "摄影",
            title = "给屋里三样东西各拍一张",
            firstStep = "在屋里走动，挑三样自己看着顺眼的东西，每样拍一张。",
            minutes = 10,
            prep = "什么都不用准备",
        ),
        StarterOption(
            id = "photo-look",
            term = "摄影",
            title = "翻一遍自己拍过的照片",
            firstStep = "把手机里自己拍过的照片往前翻一段，翻到哪张停下来都行。",
            minutes = 15,
            prep = "什么都不用准备",
        ),

        // 唱歌
        StarterOption(
            id = "sing-one",
            term = "唱歌",
            title = "唱完一首自己会的老歌",
            firstStep = "挑一首自己会唱的老歌，从头到尾唱一遍，跑调也不要紧。",
            minutes = 5,
            prep = "什么都不用准备",
        ),
        StarterOption(
            id = "sing-hum",
            term = "唱歌",
            title = "哼一段调子",
            firstStep = "随便哼一段调子，想起哪段就哼哪段，声音大小随意。",
            minutes = 5,
            prep = "什么都不用准备",
        ),
        StarterOption(
            id = "sing-lyric",
            term = "唱歌",
            title = "把一首歌的唱词念出来",
            firstStep = "找一首老歌的唱词，像念诗那样从头念一遍。",
            minutes = 10,
            prep = "什么都不用准备",
        ),

        // 戏曲
        StarterOption(
            id = "opera-watch",
            term = "戏曲",
            title = "看完一段自己喜欢的戏",
            firstStep = "挑一段自己爱看的戏，从头看到尾，中间停下来也不碍事。",
            minutes = 20,
            prep = "什么都不用准备",
        ),
        StarterOption(
            id = "opera-line",
            term = "戏曲",
            title = "跟着念一句唱词",
            firstStep = "挑一句听着顺耳的唱词，跟着念一遍，念不准也没关系。",
            minutes = 10,
            prep = "什么都不用准备",
        ),
        StarterOption(
            id = "opera-listen",
            term = "戏曲",
            title = "闭上眼听一段唱腔",
            firstStep = "找一段唱腔，闭上眼睛听完整。",
            minutes = 10,
            prep = "什么都不用准备",
        ),

        // 跳舞
        StarterOption(
            id = "dance-song",
            term = "跳舞",
            title = "跟着一首歌动一动",
            firstStep = "放一首自己喜欢的歌，跟着节奏动动手脚，动到歌结束。",
            minutes = 10,
            prep = "什么都不用准备",
        ),
        StarterOption(
            id = "dance-steps",
            term = "跳舞",
            title = "踩着自己记得的拍子走两步",
            firstStep = "在屋里空一点的地方站好，踩着自己记得的拍子走几步。",
            minutes = 5,
            prep = "什么都不用准备",
        ),
        StarterOption(
            id = "dance-video",
            term = "跳舞",
            title = "跟着一段影像学两个动作",
            firstStep = "找一段教动作的影像，跟着学两个动作，学到哪算哪。",
            minutes = 15,
            prep = "什么都不用准备",
        ),

        // 养花
        StarterOption(
            id = "plant-water",
            term = "养花",
            title = "给家里的绿植浇一次水",
            firstStep = "把家里每一盆绿植都浇一次水，浇到盆底见湿为止。",
            minutes = 10,
            prep = "家里有绿植才用得上；没有就先跳过这一条",
        ),
        StarterOption(
            id = "plant-leaf",
            term = "养花",
            title = "擦一遍叶子上的灰",
            firstStep = "拿一块湿布，把几盆绿植的叶子正面擦一遍。",
            minutes = 15,
            prep = "家里有绿植和一块布才用得上；没有就先跳过这一条",
        ),
        StarterOption(
            id = "plant-look",
            term = "养花",
            title = "挨个看一遍每盆绿植",
            firstStep = "把家里的绿植挨个看一遍，看看哪一盆冒了新叶。",
            minutes = 5,
            prep = "家里有绿植才用得上；没有就先跳过这一条",
        ),

        // 钓鱼
        StarterOption(
            id = "fish-gear",
            term = "钓鱼",
            title = "把渔具摊开过一遍手",
            firstStep = "把渔具都摊在桌上，一件一件过手，看线有没有打结。",
            minutes = 15,
            prep = "家里有渔具才用得上；没有就先跳过这一条",
        ),
        StarterOption(
            id = "fish-knot",
            term = "钓鱼",
            title = "练一次绑钩",
            firstStep = "拿一小段线和一枚钩，绑一次，绑完剪掉也行。",
            minutes = 10,
            prep = "家里有线和钩才用得上；没有就先跳过这一条",
        ),
        StarterOption(
            id = "fish-place",
            term = "钓鱼",
            title = "想一处想去的水边",
            firstStep = "在纸上写下一处自己想去的水边，写完收起来。",
            minutes = 5,
            prep = "什么都不用准备",
        ),

        // 打太极
        StarterOption(
            id = "taichi-one",
            term = "打太极",
            title = "把一式从头到尾打一遍",
            firstStep = "挑一式自己会的，从头到尾打一遍，慢一点更好。",
            minutes = 10,
            prep = "什么都不用准备",
        ),
        StarterOption(
            id = "taichi-open",
            term = "打太极",
            title = "站定，把起势做几遍",
            firstStep = "在屋里站定，把起势做几遍，动作之间停多久都行。",
            minutes = 5,
            prep = "什么都不用准备",
        ),
        StarterOption(
            id = "taichi-slow",
            term = "打太极",
            title = "只管把动作放慢",
            firstStep = "打一遍自己熟的那一段，别的不管，只管把动作放慢。",
            minutes = 15,
            prep = "什么都不用准备",
        ),

        // 出去走走
        StarterOption(
            id = "walk-door",
            term = "出去走走",
            title = "出门走到楼下再回来",
            firstStep = "出门走到楼下，站一会儿，然后回来。",
            minutes = 10,
            prep = "什么都不用准备",
        ),
        StarterOption(
            id = "walk-quarter",
            term = "出去走走",
            title = "沿一条常走的路走满一刻钟",
            firstStep = "出门沿一条常走的路往前走，走到十五分钟就往回。",
            minutes = 20,
            prep = "穿一双走得舒服的鞋就行",
        ),
        StarterOption(
            id = "walk-five",
            term = "出去走走",
            title = "路上挑五样以前没留意的",
            firstStep = "出门走一段路，路上挑五样以前没留意过的东西记下来。",
            minutes = 15,
            prep = "什么都不用准备",
        ),

        // 做饭
        StarterOption(
            id = "cook-dish",
            term = "做饭",
            title = "做一道自己拿手的老菜",
            firstStep = "挑一道自己做惯了的菜，从头到尾做一遍，做完就吃。",
            minutes = 20,
            prep = "家里有食材才用得上；没有就先跳过这一条",
        ),
        StarterOption(
            id = "cook-soup",
            term = "做饭",
            title = "熬一锅简单的汤",
            firstStep = "把两三样食材放进锅里熬一锅汤，熬到熟就行。",
            minutes = 20,
            prep = "家里有食材才用得上；没有就先跳过这一条",
        ),
        StarterOption(
            id = "cook-plan",
            term = "做饭",
            title = "想一遍今天想吃什么",
            firstStep = "坐下来，把今天想吃的一两样写在纸上。",
            minutes = 5,
            prep = "什么都不用准备",
        ),

        // 看书
        StarterOption(
            id = "read-two-pages",
            term = "看书",
            title = "把手边那本书读两页",
            firstStep = "把手边那本书翻开，读两页，读完就合上。",
            minutes = 10,
            prep = "手边有书才用得上；没有就先跳过这一条",
        ),
        StarterOption(
            id = "read-poem",
            term = "看书",
            title = "念一首自己记得的诗",
            firstStep = "挑一首自己还记着的诗，出声念一遍。",
            minutes = 5,
            prep = "什么都不用准备",
        ),
        StarterOption(
            id = "read-copy",
            term = "看书",
            title = "把读到的一句话抄下来",
            firstStep = "读到哪一句觉得好，就把它抄在纸上。",
            minutes = 10,
            prep = "手边有书才用得上；没有就先跳过这一条",
        ),

        // 用手机
        StarterOption(
            id = "phone-photo",
            term = "用手机",
            title = "用手机拍一张照片存下来",
            firstStep = "用手机拍一张照片，拍完看一眼，存着就行。",
            minutes = 5,
            prep = "什么都不用准备",
        ),
        StarterOption(
            id = "phone-message",
            term = "用手机",
            title = "给熟人发一句话",
            firstStep = "挑一个熟人，发一句话过去，发什么都行。",
            minutes = 10,
            prep = "什么都不用准备",
        ),
        StarterOption(
            id = "phone-font",
            term = "用手机",
            title = "把手机上的字调大一点",
            firstStep = "在手机的设置里，把字号往上调一档，看看顺不顺眼。",
            minutes = 10,
            prep = "什么都不用准备",
        ),
    )

    private val byTermIndex: Map<String, List<StarterOption>> = all.groupBy { it.term }
    private val byIdIndex: Map<String, StarterOption> = all.associateBy { it.id }

    fun byTerm(term: String): List<StarterOption> = byTermIndex[term].orEmpty()

    fun byId(id: String): StarterOption? = byIdIndex[id]

    /** 目录里真的有起点的那些「想做的事」。 */
    val coveredTerms: Set<String> = byTermIndex.keys

    /**
     * 唯一一处放行判据：只有「想做的事」、只有用户已经确认过愿意了解的、只有目录里确实有起点的，
     * 才允许递出起点。三样缺一样都不递。
     */
    fun offerable(kind: IntentKind, term: String, status: WishStatus): Boolean =
        kind == IntentKind.INTEREST && WishGate.mayOffer(status) && byTerm(term).isNotEmpty()
}

/**
 * 目录的机械可判边界。文案纪律在这里落成词表，测试直接扫，不靠人工评审。
 */
object StarterRules {

    /** AC-08.2：预计耗时不超过 20 分钟。 */
    const val maxMinutes: Int = 20

    val payToJoinWords: List<String> = listOf(
        "报名", "付费", "收费", "交费", "学费", "入会", "会员", "续费",
        "办卡", "押金", "加微信", "公众号", "留个电话", "留下联系",
    )

    /** AC-08.4：不出现「坚持 XX 天后可得」这类机制。 */
    val streakWords: List<String> = listOf(
        "打卡", "签到", "连续", "坚持", "积分", "等级", "徽章", "排行", "奖励", "可得",
    )

    /** AC-11.1 的禁用词表提前用到起点文案上：起点也不许有医疗口吻。 */
    val medicalWords: List<String> = listOf(
        "训练", "改善", "提升", "延缓", "评估", "认知能力", "治疗", "预防", "疗效",
    )

    /** AC-08.1 的反面：不可执行的空话。 */
    val vagueWords: List<String> = listOf("了解一下", "了解一下就好", "感兴趣的话", "有机会可以")

    fun fieldsOf(option: StarterOption): List<Pair<String, String>> = listOf(
        "标题" to option.title,
        "第一步" to option.firstStep,
        "要准备什么" to option.prep,
    )

    /** 返回所有违反项的说明文字，空列表表示这条起点可以用。 */
    fun violations(option: StarterOption): List<String> {
        val found = mutableListOf<String>()

        if (option.id.isBlank()) found += "缺少 id"
        if (option.term.isBlank()) found += "缺少 term"
        if (option.title.isBlank()) found += "缺少标题"
        if (option.firstStep.isBlank()) found += "缺少第一步动作"
        if (option.prep.isBlank()) found += "缺少「要准备什么」"
        if (option.firstStep.length < 10) found += "第一步动作太短，用户照着做不出来"
        if (option.minutes !in 1..maxMinutes) found += "耗时 ${option.minutes} 分钟超出 1~$maxMinutes"
        if (StarterCatalog.coveredTerms.isEmpty() || option.term !in StarterCatalog.coveredTerms) {
            found += "term「${option.term}」不是目录里的事"
        }

        for ((field, text) in fieldsOf(option)) {
            checkWords(text, payToJoinWords).forEach { found += "$field 出现要花钱或留联系方式的说法「$it」" }
            checkWords(text, streakWords).forEach { found += "$field 出现累计或施压的说法「$it」" }
            checkWords(text, medicalWords).forEach { found += "$field 出现医疗口吻的词「$it」" }
            checkWords(text, vagueWords).forEach { found += "$field 出现没法照做的空话「$it」" }
        }

        return found
    }

    fun checkWords(text: String, words: List<String>): List<String> =
        words.filter { text.contains(it) }
}
