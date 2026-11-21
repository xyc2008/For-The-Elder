package com.fortheelder.domain.play

import kotlin.random.Random

/**
 * 舒尔特方格。
 *
 * 5×5 个格子里摆着 1 到 25，每次摆的位置都不一样；按 1、2、3……的顺序挨个点下去。
 * 它的定位只是一件「手上有点事做、又不费神」的小事，所以三条边界都落在结构上：
 *
 * - **只算这一局用了多久**，算完只活在这一屏里。库里没有一列可以放用时，
 *   所以「上次 32 秒、这次 41 秒」这种比较不会出现（AC-12.2、DR-09）。
 * - **点错没有代价**，只回一句「这个已经点过了」。[OrderRound] 里没有一个字段能存错误次数，
 *   界面也就没有地方可以把次数显示出来（AC-12.3）。
 * - **中途退出不留痕迹**。这一局的全部状态就是一个 [OrderRound] 值；这一页关掉，它就没了，
 *   库里也不会多出一条「没玩完」的记录（AC-12.4）。
 */
object SchulteGrid {

    const val ID = "schulte-5x5"

    /** 边长。5×5 = 25 格。 */
    const val SIDE = 5

    /** 1 到 25。同时也是**该点的顺序**——它本身不参与洗牌，洗的只是摆法。 */
    private val numbers: List<String> = (1..(SIDE * SIDE)).map { it.toString() }

    val game: PlayGame = PlayGame(
        id = ID,
        name = "舒尔特方格",
        howTo = "格子里是打乱的 1 到 25，按从小到大的顺序挨个点一遍。点错了也不要紧，接着点就是。",
        columns = SIDE,
        longestSeconds = 180,
        deal = ::deal,
    )

    /**
     * 摆一局。25 个数字一个不少，只是位置每次不同。
     *
     * `shuffled` 返回新列表，[numbers] 本身不变——否则第二次开局点的顺序就跟着变了，
     * 而「按从小到大点」是这个游戏唯一的一条规矩。
     *
     * 返回的是具体的 [OrderRound]：这一局的形状本来就是它，调用的人不必强转。
     */
    fun deal(random: Random): OrderRound = OrderRound(
        gameId = ID,
        cells = numbers.shuffled(random),
        order = numbers,
    )
}
