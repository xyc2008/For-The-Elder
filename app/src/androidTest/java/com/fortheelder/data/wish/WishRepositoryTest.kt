package com.fortheelder.data.wish

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fortheelder.core.time.AppClock
import com.fortheelder.data.db.ElderDatabase
import com.fortheelder.domain.engine.intent.IntentHit
import com.fortheelder.domain.engine.intent.IntentKind
import com.fortheelder.domain.engine.intent.IntentScan
import com.fortheelder.domain.engine.intent.IntentScanner
import com.fortheelder.domain.wish.WishDecision
import com.fortheelder.domain.wish.WishStatus
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 意愿仓库的**真机**验证，用的是和正式应用同一个 [ElderDatabase.open]。
 *
 * 这里要证明的不是「能存能取」，而是仓库替上层扛住的三件事：
 * 1. 时区换算——表态时刻存的是绝对毫秒，换算用的是注入进来的时区和时钟；
 * 2. 写入串行化——用户点完一句示例话立刻点同意，那次表态必须等插入写完，而不是落空；
 * 3. 长期保留——意愿没有保留期，时间过去多久都还在、都还能改主意（DR-07）。
 *
 * 识别结果一律走真的 [IntentScanner]，不手搓 `IntentHit`：这一层要的是「用户说了什么
 * 就被记下什么」，手搓的命中会和真实输入脱节。
 */
@RunWith(AndroidJUnit4::class)
class WishRepositoryTest {

    private lateinit var context: Context
    /** 自己的库文件，不碰应用那个 `elder.db`；原因见 `ElderDatabase.open` 的注释。 */
    private val testDb = "elder-test-wish-repository.db"

    private lateinit var database: ElderDatabase
    private lateinit var repository: WishRepository

    /** 固定时区：不用系统默认值，免得跑测试的机器时区一变结果就变。 */
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 固定「现在」。测试里可以往前拨，用来证明意愿不会因为时间过去而消失。 */
    private var now: LocalDateTime = LocalDateTime.of(2026, 3, 10, 9, 0)

    private val sentence = "我一直想学书法"

    /** 「想学书法」这句话唯一的那条 INTEREST 命中。 */
    private val hit: IntentHit =
        hitsOf(sentence).first { it.kind == IntentKind.INTEREST && it.term == "书法" }

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(testDb)
        database = ElderDatabase.open(context, testDb)
        repository = WishRepository(database, AppClock { now }, zone)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(testDb)
    }

    @Test
    fun 记住一句听出来的话之后列表里就有它了() = runBlocking(Dispatchers.IO) {
        repository.remember(sentence, listOf(hit))

        val stored = repository.remembered.first().single()
        assertEquals(IntentKind.INTEREST, stored.kind)
        assertEquals("书法", stored.term)
        assertEquals(sentence, stored.sentence)
        assertEquals(hit.evidence, stored.evidence)
        assertTrue("证据片段必须是用户原话里的一段", sentence.contains(stored.evidence))
        assertEquals(WishStatus.PENDING, stored.status)
        assertNull("还没表态就不该有表态时刻", stored.decidedAtEpochMilli)
        assertEquals(1, repository.count())
    }

    @Test
    fun 同一件事说两遍不会多出一条也不会把状态冲掉() = runBlocking(Dispatchers.IO) {
        repository.remember(sentence, listOf(hit))
        assertTrue(repository.decide(hit.kind, hit.term, WishDecision.DECLINE))

        repository.remember(sentence, listOf(hit))

        assertEquals(1, repository.count())
        assertEquals(
            "已经说了先不用的事，再被提到一遍还是先不用（AC-07.2）",
            WishStatus.DECLINED,
            repository.remembered.first().single().status,
        )
    }

    @Test
    fun 重复点同一个按钮第二次不算数() = runBlocking(Dispatchers.IO) {
        repository.remember(sentence, listOf(hit))

        assertTrue(repository.decide(hit.kind, hit.term, WishDecision.CONFIRM))
        assertFalse("状态没变就不该假装成功", repository.decide(hit.kind, hit.term, WishDecision.CONFIRM))
        assertEquals(WishStatus.CONFIRMED, repository.remembered.first().single().status)
    }

    @Test
    fun 表态的时刻只记第一次那一次() = runBlocking(Dispatchers.IO) {
        val decidedAt = LocalDateTime.of(2026, 3, 10, 10, 0)
        now = decidedAt
        repository.remember(sentence, listOf(hit))
        assertTrue(repository.decide(hit.kind, hit.term, WishDecision.CONFIRM))
        val first = repository.remembered.first().single().decidedAtEpochMilli

        assertEquals("表态时刻按注入的时区换算成绝对毫秒", decidedAt.atZone(zone).toInstant().toEpochMilli(), first)

        now = decidedAt.plusDays(30)
        assertFalse(repository.decide(hit.kind, hit.term, WishDecision.CONFIRM))

        assertEquals(first, repository.remembered.first().single().decidedAtEpochMilli)
    }

    @Test
    fun 落库还没写完时的表态也要算数() = runBlocking(Dispatchers.IO) {
        // 真实场景：用户点了一句示例话，紧接着点「愿意了解一下」。这两次点击各自起一条
        // 协程，第一条的插入可能还没进到数据库。仓库的写锁必须让这次表态等前一次写完。
        //
        // UNDISPATCHED 是为了让「插入已经发起、锁已经拿住」这件事确定发生，而不是碰运气：
        // 不用它的话，用例能不能过取决于两条协程谁先被调度。
        val writer = launch(start = CoroutineStart.UNDISPATCHED) {
            repository.remember(sentence, listOf(hit))
        }

        assertTrue("落库在飞的时候表态也必须生效", repository.decide(hit.kind, hit.term, WishDecision.CONFIRM))
        writer.join()

        assertEquals(1, repository.count())
        assertEquals(WishStatus.CONFIRMED, repository.remembered.first().single().status)
    }

    @Test
    fun 表一条库里没有的意愿会如实说不算数() = runBlocking(Dispatchers.IO) {
        assertFalse(repository.decide(IntentKind.INTEREST, "书法", WishDecision.CONFIRM))
        assertEquals(0, repository.count())
    }

    @Test
    fun 没听出任何意愿时什么都不记() = runBlocking(Dispatchers.IO) {
        repository.remember("今天天气还行", emptyList())

        assertEquals(0, repository.count())
        assertTrue(repository.remembered.first().isEmpty())
    }

    @Test
    fun 不用记了只删掉这一条() = runBlocking(Dispatchers.IO) {
        repository.remember(sentence, listOf(hit))
        repository.remember("最近老是睡不着", hitsOf("最近老是睡不着"))

        assertTrue(repository.forget(hit.kind, hit.term))
        assertFalse("已经删掉了，再删一次不算数", repository.forget(hit.kind, hit.term))
        assertEquals(1, repository.count())
    }

    @Test
    fun 全部清空之后一件都不剩() = runBlocking(Dispatchers.IO) {
        repository.remember(sentence, listOf(hit))
        repository.remember("最近老是睡不着", hitsOf("最近老是睡不着"))

        repository.forgetAll()

        assertEquals(0, repository.count())
        assertTrue(repository.remembered.first().isEmpty())
    }

    @Test
    fun 同一个词条在不同类别下算两件事() = runBlocking(Dispatchers.IO) {
        repository.remember(sentence, listOf(hit))
        repository.remember(sentence, listOf(hit.copy(kind = IntentKind.STATE)))

        assertEquals(2, repository.count())
    }

    @Test
    fun 关掉数据库再打开被记住的事还在() = runBlocking(Dispatchers.IO) {
        repository.remember(sentence, listOf(hit))
        assertTrue(repository.decide(hit.kind, hit.term, WishDecision.CONFIRM))
        database.close()

        database = ElderDatabase.open(context, testDb)
        val reopened = WishRepository(database, AppClock { now }, zone)

        val stored = reopened.remembered.first().single()
        assertEquals("书法", stored.term)
        assertEquals(WishStatus.CONFIRMED, stored.status)
    }

    @Test
    fun 过五个月再来读它还在而且还能改主意() = runBlocking(Dispatchers.IO) {
        repository.remember(sentence, listOf(hit))
        assertTrue(repository.decide(hit.kind, hit.term, WishDecision.DECLINE))

        now = now.plusMonths(5)

        assertEquals("意愿没有保留期，五个月后仍应在（DR-07）", 1, repository.count())
        assertEquals(WishStatus.DECLINED, repository.remembered.first().single().status)
        assertTrue(
            "误点「先不用」之后必须留一条补救的路",
            repository.decide(hit.kind, hit.term, WishDecision.CONFIRM),
        )
        assertEquals(WishStatus.CONFIRMED, repository.remembered.first().single().status)
    }

    private fun hitsOf(sentence: String): List<IntentHit> =
        (IntentScanner.scan(sentence) as IntentScan.Recognized).hits
}
