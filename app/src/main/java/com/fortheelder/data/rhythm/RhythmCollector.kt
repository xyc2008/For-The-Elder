package com.fortheelder.data.rhythm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.fortheelder.core.time.AppClock
import com.fortheelder.domain.engine.rhythm.RhythmBroadcasts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 作息采集器：把系统广播变成库里的一行信号。
 *
 * 三条设计约束，每一条都有代价，都写在明面上：
 *
 * 1. **只运行时注册，不写清单**（AC-04.5）。屏幕上亮灭、解锁、插拔电源这五个动作从
 *    Android 8.0 起不允许清单注册，只能运行时注册。
 * 2. **不要任何权限**（AC-04.3）。这五个广播都是系统直接投给已注册接收者的，
 *    既不需要 `PACKAGE_USAGE_STATS`，也不需要别的授权——所以 `AndroidManifest.xml`
 *    里仍然是 0 条权限。
 * 3. 代价是**进程活着才收得到**。用户把常伴从最近任务里划掉、或者系统在内存紧张时
 *    回收了进程，这期间发生多少次亮屏都不会有人记下来。这一点必须如实告诉用户，
 *    不能让人以为「打开过一次就一直在记」。界面上那句话见设置页与引导页。
 *
 * 除了进程寿命，还有一层用户意愿：用户没同意之前不会注册（AC-04.4），
 * 这是由 [com.fortheelder.core.AppContainer] 监听设置里的开关来驱动的，
 * 采集器自己不认识「用户」这个概念。
 *
 * @param scope 落库用的协程域。广播回调运行在主线程，Room 的写操作不能压在这里。
 * @param clock 时间一律从它取，采集器里没有一处 `LocalDateTime.now()`（AC-03.5）。
 */
class RhythmCollector(
    private val context: Context,
    private val repository: RhythmRepository,
    private val clock: AppClock,
    private val scope: CoroutineScope,
) {

    /**
     * 接收者本体。
     *
     * 用匿名对象而不是把 `onReceive` 写在类上：采集器不是接收者，它只是**持有**一个。
     * 这样 `start`/`stop` 可以重复调用，而不会出现「已经注册过的自己再注册一遍」。
     */
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            onBroadcast(intent?.action)
        }
    }

    private var registered = false

    /** 当前是否已注册。设置页和测试用它确认开关真的生效了。 */
    val isRunning: Boolean get() = registered

    /**
     * 收到一个动作名时做的事。动作认不出来时返回 null（不落库）。
     *
     * 返回值是落库协程的 [Job]，只在测试里 join 用：广播路径本身是「发出即忘」的，
     * 界面和系统都不等它。写进去的条数不回传——用户不需要知道这一条是新的还是重复的。
     */
    fun onBroadcast(action: String?): Job? {
        val kind = RhythmBroadcasts.kindOf(action) ?: return null
        val at = clock.now()
        return scope.launch { repository.record(listOf(CapturedSignal(at, kind))) }
    }

    /**
     * 开始接收。重复调用是安全的空操作。
     *
     * `RECEIVER_NOT_EXPORTED`：这五个都是系统广播，系统照样投得进来，
     * 而别的应用投不进来——这正是我们要的。用 `RECEIVER_EXPORTED` 会让任何一个
     * 装在这台手机上的应用都能往我们的作息数据里塞假信号。
     */
    fun start() {
        if (registered) return
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter().apply { RhythmBroadcasts.actionableActions.forEach(::addAction) },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        registered = true
    }

    /** 停止接收。用户撤回同意时立刻生效，重复调用是安全的空操作。 */
    fun stop() {
        if (!registered) return
        context.unregisterReceiver(receiver)
        registered = false
    }
}
