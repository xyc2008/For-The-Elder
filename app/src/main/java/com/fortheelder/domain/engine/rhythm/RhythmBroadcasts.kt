package com.fortheelder.domain.engine.rhythm

/**
 * 系统广播与 [RhythmKind] 的对应关系。
 *
 * 这里是**纯 Kotlin**：动作名写成字面量，不引 `android.content.Intent`。
 * 这样做有两个好处——引擎包继续保持不依赖任何 Android API（AC-04.1），
 * 五条对应关系能在 JVM 单测里逐条断言；而「字面量和平台常量是不是同一个字符串」
 * 这件事另有一条设备侧用例（`RhythmBroadcastsPlatformTest`）拿 `Intent.ACTION_*`
 * 对照，写错了会在那里红，不会带着一个收不到广播的采集器进版本。
 *
 * 五个动作**全是系统保护广播**：Android 8.0 起清单里注册这些隐式广播不生效，
 * 只能运行时注册（AC-04.5）。写进清单不但收不到，还会被 lint 拦下来。
 *
 * 常量名刻意和 `Intent` 里的名字保持一致（`ACTION_POWER_CONNECTED` 对应
 * `Intent.ACTION_POWER_CONNECTED`，值里那个多余的 `ACTION_` 前缀是平台自带的），
 * 免得两边对照时看串行。
 */
object RhythmBroadcasts {

    const val ACTION_SCREEN_ON = "android.intent.action.SCREEN_ON"
    const val ACTION_SCREEN_OFF = "android.intent.action.SCREEN_OFF"
    const val ACTION_USER_PRESENT = "android.intent.action.USER_PRESENT"
    const val ACTION_POWER_CONNECTED = "android.intent.action.ACTION_POWER_CONNECTED"
    const val ACTION_POWER_DISCONNECTED = "android.intent.action.ACTION_POWER_DISCONNECTED"

    /**
     * 采集器要订阅的全部动作。
     *
     * 顺序就是清单顺序，设置页和引导页里列举的说明文案按它写：
     * 亮屏、灭屏、解锁、插电、拔电。往这个列表里加动作时必须同时改
     * [kindOf] 和两处文案，否则用户看到的说明会比实际记的多。
     */
    val actionableActions: List<String> = listOf(
        ACTION_SCREEN_ON,
        ACTION_SCREEN_OFF,
        ACTION_USER_PRESENT,
        ACTION_POWER_CONNECTED,
        ACTION_POWER_DISCONNECTED,
    )

    /**
     * 认不出来的动作返回 null。
     *
     * 返回 null 而不是抛异常：[android.content.BroadcastReceiver.onReceive] 里逃出来的
     * 异常会直接把进程带走。一个多出来的动作名不该有这种后果。
     */
    fun kindOf(action: String?): RhythmKind? = when (action) {
        ACTION_SCREEN_ON -> RhythmKind.SCREEN_ON
        ACTION_SCREEN_OFF -> RhythmKind.SCREEN_OFF
        ACTION_USER_PRESENT -> RhythmKind.USER_PRESENT
        ACTION_POWER_CONNECTED -> RhythmKind.CHARGING_START
        ACTION_POWER_DISCONNECTED -> RhythmKind.CHARGING_END
        else -> null
    }
}
