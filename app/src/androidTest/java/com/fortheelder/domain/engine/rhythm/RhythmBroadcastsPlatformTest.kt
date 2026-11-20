package com.fortheelder.domain.engine.rhythm

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 字面量与平台常量的对照。
 *
 * [RhythmBroadcasts] 刻意把动作名写成普通字符串（好让引擎包不依赖 Android），
 * 代价是「写错一个字符也能编译通过」。JVM 那边只能断言自己跟自己一致，
 * 真正能发现拼错的只有这里：拿 `Intent.ACTION_*` 逐条对照。
 *
 * 跑不到这一条用例的构建（比如只跑单元测试）就等于没有这层保护，
 * 所以它是 `connectedDebugAndroidTest` 的一部分，不是可选项。
 */
@RunWith(AndroidJUnit4::class)
class RhythmBroadcastsPlatformTest {

    @Test
    fun `五个字面量逐个等于Intent里的常量`() {
        assertEquals(Intent.ACTION_SCREEN_ON, RhythmBroadcasts.ACTION_SCREEN_ON)
        assertEquals(Intent.ACTION_SCREEN_OFF, RhythmBroadcasts.ACTION_SCREEN_OFF)
        assertEquals(Intent.ACTION_USER_PRESENT, RhythmBroadcasts.ACTION_USER_PRESENT)
        assertEquals(Intent.ACTION_POWER_CONNECTED, RhythmBroadcasts.ACTION_POWER_CONNECTED)
        assertEquals(Intent.ACTION_POWER_DISCONNECTED, RhythmBroadcasts.ACTION_POWER_DISCONNECTED)
    }

    @Test
    fun `订阅列表整体等于平台常量列表`() {
        // 顺序也要一致：界面上的说明文案按这个顺序写。
        assertEquals(
            listOf(
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_SCREEN_OFF,
                Intent.ACTION_USER_PRESENT,
                Intent.ACTION_POWER_CONNECTED,
                Intent.ACTION_POWER_DISCONNECTED,
            ),
            RhythmBroadcasts.actionableActions,
        )
    }
}
