package com.fortheelder.domain.play

import kotlin.random.Random

/**
 * 找出一样的。4×4 一共 16 张牌，全都摆在明面上，其中**恰好两张字相同**。
 *
 * 它是这个应用的第二个游戏，也是第一个不走「按顺序点」这条路的游戏。选它进来有两个理由：
 *
 * - 动作最少。只有「看」和「找」，没有计时、没有翻牌记忆、没有数字输入。当时点的三个候选
 *   后来各有归属：记一记（翻牌）要按住「第一张翻开来停多久再盖回去」这条规矩，那就得让
 *   领域层持有时间，顺延；反应速度要计时，与 AC-11.2、AC-11.3 冲突，判退；连续减七原本
 *   看着要输入数字，但它的**缩减形式**（每题三个数、三选一）既不读时钟也不用打字，已在
 *   第 17 轮以「算一算」落地（判定表见 `records/03-架构决策记录.md` 的 ADR-018）。
 *   这一段此前写的「连续减七会破坏 DR-09」是错的，已按实测更正。
 * - 它测的是结构：它的规则形状跟舒尔特不一样，加它进来必须动 [PlayRound]。
 *   一个只加目录项的例子证明不了「加游戏要改多少」。
 *
 * 边界跟舒尔特一致，而且同样是结构性的：没有用时（这一局根本不看表）、没有错误次数
 * （[FindPairRound] 里没有能放它的字段）、中途退出不留痕（全部状态就是那个值）。
 */
object FindPair {

    const val ID = "find-pair-4x4"

    /** 边长。4×4 = 16 张。 */
    const val SIDE = 4

    /**
     * 可以拿来当牌面的字。
     *
     * 全部是**两个字**的日常物件：四列摆下来不会挤成两行，而且都是这个年纪的人
     * 一眼认得的东西。彼此之间没有一个是另一个的一部分（测试会查），否则「看起来一样」
     * 会变成真的看不出来。这二十个字也过了词表（[ClaimWords]）。
     */
    val words: List<String> = listOf(
        "桔子", "苹果", "白菜", "豆腐", "茶壶",
        "蒲扇", "毛衣", "布鞋", "象棋", "毛笔",
        "藤椅", "凉席", "算盘", "棉被", "雨伞",
        "桂花", "月饼", "核桃", "花生", "石榴",
    )

    val game: PlayGame = PlayGame(
        id = ID,
        name = "找出一样的",
        howTo = "格子里有 16 个字，其中两个是一样的。把这两个找出来。点错了也不要紧，再看看别的。",
        columns = SIDE,
        longestSeconds = 120,
        deal = ::deal,
    )

    /**
     * 摆一局：先随机挑 15 个字铺满 15 格，再从这 15 个里挑一个字补成第 16 格。
     *
     * 这样摆出来的盘面**恰好**有一对：那 15 个字两两不同（[words] 里没有重复），
     * 补进去的那个跟其中一个相同。用 `shuffled(random)` 而不是 `shuffled()`：
     * 同一个种子出同一局，测试才能造出确定的局面。
     */
    fun deal(random: Random): FindPairRound {
        val pool = words.shuffled(random).take(SIDE * SIDE - 1)
        val pairLabel = pool[random.nextInt(pool.size)]
        return FindPairRound(
            gameId = ID,
            cells = (pool + pairLabel).shuffled(random),
            pairLabel = pairLabel,
        )
    }
}
