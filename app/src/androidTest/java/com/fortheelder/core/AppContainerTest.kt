package com.fortheelder.core

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fortheelder.core.time.AppClock
import com.fortheelder.core.time.toEpochDay
import com.fortheelder.domain.engine.greeting.DayPart
import com.fortheelder.ui.关库排在组合之后
import com.fortheelder.ui.自己的存储
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith

/**
 * 「容器里那只钟」的接线验证。
 *
 * 这个类补的是第 32 轮量出来的一处真缺陷：那一轮为了给 `AppContainer` 的时钟做反向验证，
 * 把类体里那个公开属性 `val clock: AppClock = clock` 的初始化器改回 `SystemAppClock`，
 * 结果**只有一条屏幕级用例变红**——因为容器内部各处取的是构造参数、公开属性只是把它抄了
 * 一遍，两者恰好指向同一只钟，于是「没有差别」纯属巧合。也就是说在那之前，没有任何断言能
 * 区分「公开属性被打坏」与「内部接线被打坏」。
 *
 * 第 33 轮把那两处同名声明收成了一个构造属性（`val clock: AppClock`），两者成了同一个东西。
 * 本类的三条用例各自钉住一件：
 *
 * 1. `公开的那只钟就是注入进去的那一只`——**同一性**断言（`assertSame`），不是等值断言。
 *    变回第 32 轮那种写法会当场变红，而不是碰巧通过；
 * 2. `内部的问候引擎跟着注入的钟走`——`greet()` 的 `dayCount` 由**时钟那天**减去
 *    首次见面日算出来。这里传的首次见面日是「注入的钟那天减 4 天」，于是期望值恰好是 5。
 *    这句话跟着注入的钟走还是跟着系统钟走，结果差着大半年，**任何一天跑都不会碰巧对上**
 *    （这是有意的：光断言「14 点半说下午好」的话，系统钟在 14 点到 17 点之间也会给出同一句）；
 * 3. `内部的设置层跟着注入的钟走`——`ensureFirstLaunchRecorded()` 写进去的是**它那只钟**
 *    的那一天。
 *
 * 与 `HomeScreenTest` 的分工：那一类验的是「首页上真的印出来了」，代价是要挂 compose 规则、
 * 时钟只能在 `show()` 之前定死。本类不渲染任何界面，于是同一件事可以问得更直接。两条都留着。
 *
 * 一处已知边界：本类沿用了屏幕级用例那套收尾规则（`关库排在组合之后`）。那条规则里
 * 「宿主 Activity 先被销毁」那半句对本类不适用（本类不启动任何 Activity，前台上本来就是空的），
 * 但它做对了这里需要的另外两件事：先关库、再删库文件。它兜不住 `@Before` 自己抛错这件事
 * 本类一样兜不住（`lateinit` 会盖住真错），这是沿用而不是新欠的。
 */
@RunWith(AndroidJUnit4::class)
class AppContainerTest {

    /**
     * 关库这一步排在用例之后。本类不挂 compose 规则，所以不套 `RuleChain`——
     * 那条链解决的问题（谁先收摊）在这里不存在。
     */
    @get:Rule
    val rules: TestRule = 关库排在组合之后(TEST_DB) { container }

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** 这个用例自己的库文件与两份偏好，三样都不跟应用共用。 */
    private lateinit var container: AppContainer

    /** 两份偏好。 */
    private lateinit var 存储: 自己的存储

    /** 注入进容器的那只钟报的钟点：2026-03-10 下午两点半，落在「下午」这一段里。 */
    private val 定住的钟点: LocalDateTime = LocalDateTime.of(2026, 3, 10, 14, 30)

    /**
     * 真正递给容器的那只钟。
     *
     * 单独留一个字段，是为了能拿它做**同一性**断言：容器公开出来的那只钟必须就是这一只，
     * 而不是某个内容相同的新对象。
     */
    private val 注入的钟: AppClock = AppClock { 定住的钟点 }

    @Before
    fun 建一个只差时钟的容器() {
        context.deleteDatabase(TEST_DB)
        存储 = 自己的存储(context, TEST_DB)
        container = AppContainer(
            context = context,
            databaseName = TEST_DB,
            settingsData = 存储.settingsData,
            interventionData = 存储.interventionData,
            clock = 注入的钟,
        )
    }

    /**
     * 公开出来的那只钟，必须**就是**递进去的那一只。
     *
     * 用 `assertSame` 而不是 `assertEquals`：等值断言在「属性被换成另一只内容相同的钟」时
     * 仍然通过，而那正是第 32 轮 M3 干的事。
     */
    @Test
    fun 公开的那只钟就是注入进去的那一只() {
        assertSame(注入的钟, container.clock)
        assertEquals(定住的钟点, container.clock.now())
    }

    /**
     * 容器内部那个问候引擎，读的是注入的钟。
     *
     * `dayCount` 是「时钟那天」减首次见面日加 1。首次见面日取的是注入的钟那天减 4 天，
     * 所以期望值是 5；引擎要是读了系统钟，这里算出来就是几百天，红。
     */
    @Test
    fun 内部的问候引擎跟着注入的钟走() {
        val 问候 = container.greetingEngine.greet(称呼, 定住的钟点.toEpochDay() - 4)

        assertEquals("$称呼，下午好", 问候.headline)
        assertEquals(DayPart.AFTERNOON, 问候.dayPart)
        assertEquals(5L, 问候.dayCount)
        assertEquals("已经认识 5 天了。", 问候.note)
    }

    /**
     * 容器内部那个设置层，读的也是注入的钟。
     *
     * `ensureFirstLaunchRecorded()` 只在没有值的时候写，所以这个断言比较的是**它当场写下的**
     * 那一格。边界照实说：这条用的是固定的期望值，要是哪天代码错用了系统钟，这一例恰好只在
     * 2026-03-10 那天会通过——其余任何一天都是红的。
     */
    @Test
    fun 内部的设置层跟着注入的钟走() {
        val 设置 = runBlocking(Dispatchers.IO) {
            container.settings.ensureFirstLaunchRecorded()
            container.settings.settings.first()
        }
        assertEquals(定住的钟点.toEpochDay() as Long?, 设置.firstLaunchEpochDay)
    }

    private companion object {
        const val TEST_DB = "elder-test-app-container.db"
        const val 称呼 = "老熊"
    }
}
