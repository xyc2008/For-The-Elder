package com.fortheelder.domain.engine.rhythm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RhythmBroadcasts] 的单元测试。
 *
 * 这一层只守一件事：**动作名到信号种类的对应关系**。字面量是否真的等于平台常量，
 * 归设备侧的 `RhythmBroadcastsPlatformTest` 管——JVM 上拿不到 `android.content.Intent`，
 * 在这里断言「等于 Intent.ACTION_SCREEN_ON」是编译不过去的。
 */
class RhythmBroadcastsTest {

    @Test
    fun `五个动作各自映射到对应的信号种类`() {
        assertEquals(RhythmKind.SCREEN_ON, RhythmBroadcasts.kindOf(RhythmBroadcasts.ACTION_SCREEN_ON))
        assertEquals(RhythmKind.SCREEN_OFF, RhythmBroadcasts.kindOf(RhythmBroadcasts.ACTION_SCREEN_OFF))
        assertEquals(RhythmKind.USER_PRESENT, RhythmBroadcasts.kindOf(RhythmBroadcasts.ACTION_USER_PRESENT))
        assertEquals(RhythmKind.CHARGING_START, RhythmBroadcasts.kindOf(RhythmBroadcasts.ACTION_POWER_CONNECTED))
        assertEquals(RhythmKind.CHARGING_END, RhythmBroadcasts.kindOf(RhythmBroadcasts.ACTION_POWER_DISCONNECTED))
    }

    @Test
    fun `认不出来的动作返回 null 而不是抛异常`() {
        // onReceive 里逃出来的异常会把进程带走，所以这里必须是 null。
        assertNull(RhythmBroadcasts.kindOf("android.intent.action.BOOT_COMPLETED"))
        assertNull(RhythmBroadcasts.kindOf("com.example.something.ELSE"))
        assertNull(RhythmBroadcasts.kindOf(""))
    }

    @Test
    fun `动作名为 null 时返回 null`() {
        // intent 可能不带 action，采集器会把这个 null 直接传进来。
        assertNull(RhythmBroadcasts.kindOf(null))
    }

    @Test
    fun `动作名大小写敏感 空格也算认不出来`() {
        assertNull(RhythmBroadcasts.kindOf("android.intent.action.screen_on"))
        assertNull(RhythmBroadcasts.kindOf(" android.intent.action.SCREEN_ON"))
    }

    @Test
    fun `订阅列表就是这五个 顺序固定`() {
        // 顺序是界面文案的依据（亮屏、灭屏、解锁、插电、拔电），改了要连文案一起改。
        assertEquals(
            listOf(
                "android.intent.action.SCREEN_ON",
                "android.intent.action.SCREEN_OFF",
                "android.intent.action.USER_PRESENT",
                "android.intent.action.ACTION_POWER_CONNECTED",
                "android.intent.action.ACTION_POWER_DISCONNECTED",
            ),
            RhythmBroadcasts.actionableActions,
        )
    }

    @Test
    fun `订阅列表里每个动作都认得出来`() {
        // 订阅了却映射不出来的话，采集器会静默丢掉它，用户永远等不到那条数据。
        RhythmBroadcasts.actionableActions.forEach { action ->
            assertTrue("$action 应当映射到某个信号种类", RhythmBroadcasts.kindOf(action) != null)
        }
    }

    @Test
    fun `订阅列表没有重复项`() {
        // IntentFilter 里重复 addAction 不会报错，只会让「订阅了几种」这个说法变得不准。
        assertEquals(RhythmBroadcasts.actionableActions.size, RhythmBroadcasts.actionableActions.toSet().size)
    }

    @Test
    fun `两个电源动作保留平台自带的 ACTION 前缀`() {
        // 这五个字面量看着不一致，但后面两个的多余 ACTION_ 是平台自己起的，
        // 少写一个字符照样编译、照样测试通过，只是在真机上一条都收不到。
        assertTrue(RhythmBroadcasts.ACTION_POWER_CONNECTED.startsWith("android.intent.action.ACTION_"))
        assertTrue(RhythmBroadcasts.ACTION_POWER_DISCONNECTED.startsWith("android.intent.action.ACTION_"))
        assertTrue(RhythmBroadcasts.ACTION_SCREEN_ON.startsWith("android.intent.action."))
    }
}
