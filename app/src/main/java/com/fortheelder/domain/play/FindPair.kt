package com.fortheelder.domain.play

import kotlin.random.Random

/**
 * 找出一样的。4×4 一共 16 张牌，全都摆在明面上，其中**恰好两张字相同**。
 *
 * 它是这个应用的第二个游戏，也是第一个不走「按顺序点」这条路的游戏。选它进来有两个理由：
 *
 * - 动作最少。只有「看」和「找」，没有计时、没有翻牌记忆、没有数字输入。当时点的三个候选
 *   后来各有归属：记一记（翻牌）当初被按住，理由是「第一张翻开来停多久再盖回去」这条规矩
 *   得让领域层持有时间；**第 41 轮这条理由被实测推翻**——翻回去改成由用户点一下驱动，
 *   领域层一秒都不用认识（ADR-038），记一记因此落地；反应速度要计时，与 AC-11.2、AC-11.3
 *   冲突，判退；连续减七原本看着要输入数字，但它的**缩减形式**（每题三个数、三选一）
 *   既不读时钟也不用打字，已在第 17 轮以「算一算」落地（判定表见
 *   `records/03-架构决策记录.md` 的 ADR-018）。这一段此前写的「连续减七会破坏 DR-09」是错的，
 *   已按实测更正。
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
     * 可以拿来当牌面的字。现在是 [CardWords] 的一个别名。
     *
     * 第 41 轮之前这二十个字就长在这里；记一记也要往牌面写字，于是提到了 [CardWords]，
     * 由两个游戏共用一份。这里留一个名字是因为调用方和测试都已经在用它——**写成一个
     * 计算属性而不是再存一份**，照 [OrderRound.stepsToFinish] 那条老规矩：不另外留一个
     * 能跟它矛盾的字段。
     */
    val words: List<String> get() = CardWords.all

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
