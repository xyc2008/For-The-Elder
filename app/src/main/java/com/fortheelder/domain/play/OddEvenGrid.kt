package com.fortheelder.domain.play

import kotlin.random.Random

/**
 * 单双数。
 *
 * 盘面跟 [SchulteGrid]、[ReverseGrid] **一模一样**：5×5 个格子里摆着打乱的 1 到 25，摆法
 * 每局都不同。这一局不同的也只有一处——该点的先后不是顺数也不是倒数，而是**分两拨**：
 * 先把十三个单数从小到大点完（1、3、5……25），再回头把十二个双数从小到大点完（2、4、6……24）。
 *
 * 同一批字、同一张盘，换的只是「先点哪一拨」。这不是换了个说法重抄一遍舒尔特：顺数靠的是
 * 扫一眼就抓住下一个数在哪（注意广度），倒着点考的是「换个方向还做不做得到」，而分两拨
 * 多点了一道**切换**——点完 25 之后得先想起「单数完了」，再从 2 重新起一轮，而 1 就在屏幕上、
 * 手最容易往那儿去。考的是认知灵活性里那条「换一条规则还做不做得到」。三件事用的是同一个
 * 引擎，但不是同一个任务——[OrderRound] 只认「相等」和「先后」两条，它本来就不知道那一串先后是按什么规矩排出来的。
 *
 * ## 这一条是怎么加进来的（第 158 轮）
 *
 * [PlayCatalog] 的类注释把「加一个游戏要动多少」按代价分了四种，第一种是
 * **「规则是已有的，只多写一条 `PlayGame(...)`，界面与测试都不动」**。第 148 轮「倒着点」
 * 第一次兑现了它；这一局是**第二次兑现**：产品码同样只新增这一个文件、另动几处注释，
 * `ui/play/PlayScreen.kt` 一个字没动，`ui/play/PlayVoice.kt` 也只改了一处注释、没多出
 * 行为分支（两者那几处 `when` 都是按 [PlayRound] 的**子类型**分支的，不是按 `gameId` 分的），
 * 既有测试也没为它加过一条。
 *
 * 三条边界跟舒尔特那三条是同一套，理由也同一套（都在结构上，不在界面的自觉上）：
 *
 * - **只算这一局用了多久**，算完只活在这一屏里。库里没有一列可以放用时，「上次 41 秒、
 *   这次 32 秒」这种比较不会出现（AC-12.2、DR-09）。
 * - **点错没有代价**。分两拨下两句提示仍然是实话：点到还没轮到的小数字，[PlayHint.NOT_YET]
 *   说「这个还在后面，不急」；点到已经点过的数字，[PlayHint.ALREADY_DONE] 说「这个已经点过了，换一个」。
 * - **中途退出不留痕迹**。这一局的全部状态就是一个 [OrderRound] 值；这一页关掉，它就没了。
 */
object OddEvenGrid {

    const val ID = "odd-even-5x5"

    /** 边长。5×5 = 25 格。与 [SchulteGrid.SIDE]、[ReverseGrid.SIDE] 同值，但各写各的：哪天分开了，改这里不影响那两局。 */
    const val SIDE = 5

    /** 1 到 25。**先单后双**才是该点的顺序；它本身不参与洗牌，洗的只是摆法。 */
    private val numbers: List<String> = (1..(SIDE * SIDE)).map { it.toString() }

    /**
     * 该点的先后：单数从小到大（1、3……25）在前，双数从小到大（2、4……24）在后，共 25 项。
     *
     * 现算而不抄一份字面量：抄了就有两处能对不上，而「先单后双」是这个游戏唯一的一条规矩。
     */
    private val order: List<String> =
        numbers.filter { it.toInt() % 2 == 1 } + numbers.filter { it.toInt() % 2 == 0 }

    val game: PlayGame = PlayGame(
        id = ID,
        name = "单双数",
        howTo = "格子里是打乱的 1 到 25。先点单数：1、3、5……一直到 25；再点双数：2、4、6……一直到 24。点错了也不要紧，接着点就是。",
        columns = SIDE,
        longestSeconds = 180,
        deal = ::deal,
    )

    /**
     * 摆一局。25 个数字一个不少，只是位置每次不同，而**该点是先单后双**。
     *
     * `shuffled` 返回新列表，[numbers] 本身不变；[order] 是常量、每局共用同一份，洗牌动不到它。
     *
     * 返回的是具体的 [OrderRound]：这一局的形状本来就是它，调用的人不必强转。
     */
    fun deal(random: Random): OrderRound = OrderRound(
        gameId = ID,
        cells = numbers.shuffled(random),
        order = order,
    )
}
