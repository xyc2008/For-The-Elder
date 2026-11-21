package com.fortheelder.domain.play

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 游戏目录的单元测试（JVM）。
 *
 * AC-11.4 说「注册表里每个游戏都必须可用，未完成的不得注册」。这条标准听着像自律，
 * 其实可以机械地判：每条目录项都拿十个种子各出一局，出一局就该是一个**能玩完**的局——
 * 格子不为空、第一个目标真的摆在格子上、而且不是一进去就已经结束。
 * 塞一个空壳进来「先把框架撑起来」的话，这几条一定有过不去的。
 *
 * 目录里现在有两条（舒尔特方格、找出一样的），所以这些用例的价值不在今天，而在于第三个、
 * 第四个游戏加进来时会自动被扫一遍，不需要谁记得来补测试。为了这一条，几处断言只能问
 * 两种游戏都有的东西（步数、格子、`markOf`），跟「按顺序点」有关的那条单独收在 `if` 里。
 */
class PlayCatalogTest {

    @Test
    fun theCatalogIsNotEmpty() {
        assertTrue("目录里一个游戏都没有，首页那个入口点进去就是空的", PlayCatalog.games.isNotEmpty())
    }

    @Test
    fun idsAreUnique() {
        val ids = PlayCatalog.games.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun firstPointsAtTheFirstGame() {
        assertEquals(PlayCatalog.games.first(), PlayCatalog.first)
    }

    @Test
    fun everyGameSaysWhatItIsAndHowToPlayIt() {
        for (game in PlayCatalog.games) {
            assertTrue("${game.id} 没有名字", game.name.isNotBlank())
            assertTrue("${game.id} 没有玩法说明", game.howTo.isNotBlank())
            assertTrue(
                "${game.id} 的玩法说明只有 ${game.howTo.length} 个字，照它玩不起来",
                game.howTo.length >= 10,
            )
        }
    }

    @Test
    fun everyGameStaysInsideTheRoundCeiling() {
        for (game in PlayCatalog.games) {
            assertTrue(
                "${game.id} 声明的上限是 ${game.longestSeconds} 秒，超了 3 分钟（AC-11.3）",
                game.longestSeconds in 1..180,
            )
        }
    }

    @Test
    fun everyGameDeclaresAUsableGrid() {
        for (game in PlayCatalog.games) {
            assertTrue("${game.id} 声明一行摆 ${game.columns} 个，摆不出格子", game.columns >= 1)
        }
    }

    @Test
    fun everyGameCanActuallyDealAPlayableRound() {
        for (game in PlayCatalog.games) {
            for (seed in 0..9) {
                val round = game.deal(Random(seed))
                assertEquals("${game.id} 出的局挂的不是自己的 id", game.id, round.gameId)
                assertTrue(
                    "${game.id} 在种子 $seed 下出了个只有 ${round.stepsToFinish} 步的局",
                    round.stepsToFinish >= 2,
                )
                assertTrue("${game.id} 在种子 $seed 下一进去就已经结束了", !round.finished)
                assertTrue("${game.id} 在种子 $seed 下没有格子", round.cells.isNotEmpty())
                assertTrue(
                    "${game.id} 在种子 $seed 下一进去就有格子已经被点过或标完了",
                    round.cells.indices.all { round.markOf(it) == CellMark.PLAIN },
                )
                // 只有按顺序点的游戏才有「第一个该点谁」这回事；找一样的开局谁都能点。
                if (round is OrderRound) {
                    assertTrue(
                        "${game.id} 在种子 $seed 下第一个该点的 ${round.target} 根本不在格子上，点不下去",
                        round.cells.contains(round.target),
                    )
                }
            }
        }
    }
}
