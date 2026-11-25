package com.fortheelder.domain.engine.intervention

import com.fortheelder.domain.engine.rhythm.DailyRhythm
import com.fortheelder.domain.engine.rhythm.RhythmResult
import com.fortheelder.domain.engine.rhythm.TimeSpan
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * 「这会儿要不要迎上去说话」的节流参数。
 *
 * 命名沿用 `RhythmConfig` 的风格：所有可调的量集中在一个 data class 里，有默认值，
 * 测试可以只改它关心的那一个。
 *
 * @param minGapMinutes 两次开口之间至少隔多久（用户一直没接话时的起点）
 * @param backoffStepMinutes 每被忽略一次，间隔往后放多少
 * @param maxGapMinutes 间隔的上限。**不是无限退避**：退到一天一次就停住，
 *   否则连续几次没接话就会永久沉默，那就不是「陪伴」而成了「放弃」
 * @param fallbackWakeAt 没有画像时的假定起床时刻
 * @param fallbackSleepAt 没有画像时的假定入睡时刻
 */
data class InterventionConfig(
    val minGapMinutes: Int = 30,
    val backoffStepMinutes: Int = 180,
    val maxGapMinutes: Int = 24 * 60,
    val fallbackWakeAt: LocalTime = LocalTime.of(7, 0),
    val fallbackSleepAt: LocalTime = LocalTime.of(22, 30),
) {
    init {
        require(minGapMinutes > 0) { "最小间隔必须为正，否则节流不成立" }
        require(backoffStepMinutes >= 0) { "退避步长不能为负，否则越忽略越频繁" }
        require(maxGapMinutes >= minGapMinutes) { "上限不能低于最小间隔，否则上限形同虚设" }
    }

    /** 没有画像时假定的安静时段：入睡到起床。 */
    val fallbackQuietSpan: TimeSpan get() = TimeSpan(fallbackSleepAt, fallbackWakeAt)

    /**
     * 被连续忽略 [ignoredStreak] 次之后，本次开口要等的分钟数。
     *
     * 用 `Long` 中转再收敛回 `Int`：`streak * step` 在 `Int` 上溢出会翻成负数，
     * 那样「越忽略越频繁」，正好与本节要保证的性质相反。
     */
    fun gapMinutes(ignoredStreak: Int): Int =
        (minGapMinutes.toLong() + ignoredStreak.coerceAtLeast(0).toLong() * backoffStepMinutes.toLong())
            .coerceAtMost(maxGapMinutes.toLong())
            .toInt()
}

/**
 * 节流状态：上次开口、上次被接话、以及「开口了却没被接话」连续了多少次。
 *
 * 只存事实，不存结论——要不要说话由 [InterventionWindow.decide] 现算，
 * 免得同一件事有两处判断而出现分歧。
 */
data class ThrottleState(
    val lastSpokenAt: LocalDateTime? = null,
    val lastAnsweredAt: LocalDateTime? = null,
    val ignoredStreak: Int = 0,
) {

    /** 上一次开口还没有得到回应。从没开过口时为 false。 */
    val pendingSpeak: Boolean
        get() {
            val spoken = lastSpokenAt ?: return false
            val answered = lastAnsweredAt ?: return true
            return !answered.isAfter(spoken)
        }

    /**
     * 记一次开口。若上一次开口仍无人接话，忽略计数加一。
     *
     * 首次开口不加：那时候没有任何「被忽略」的证据。
     */
    fun afterSpoken(at: LocalDateTime): ThrottleState =
        copy(lastSpokenAt = at, ignoredStreak = if (pendingSpeak) ignoredStreak + 1 else ignoredStreak)

    /** 记一次接话：计数归零，并把「最近一次互动」推到此刻。 */
    fun afterAnswered(at: LocalDateTime): ThrottleState =
        copy(lastAnsweredAt = at, ignoredStreak = 0)
}

/**
 * 这次判断依据的是哪来的作息。
 *
 * 界面需要把它说出来：按真实作息说话，和按「普通的作息」猜，对用户是两件事。
 * 尤其是采集开关没打开的用户——不能让他以为应用在悄悄学他。
 */
enum class RhythmBasis {
    /** 有画像，按用户自己的作息。 */
    LEARNED,

    /** 采集到的天数还不够，按普通作息先来。 */
    ASSUMED_NOT_ENOUGH_DATA,

    /** 有数据但凑不出说得通的作息，按普通作息先来。 */
    ASSUMED_INCONSISTENT,

    /**
     * 画像就在库里，但用户把设置里那颗 `按我的作息说话` 关掉了，所以按普通作息来。
     *
     * 与上面两种**不是**同一件事：那两种是「学不出来」，这一种是「学过、也还在库里，
     * 但用户不让用」。分成单独一档，是因为设置页此时会照旧显示「以前记下的」那份画像
     * （[com.fortheelder.data.rhythm.RhythmProfileService.current] 不看清这个开关），
     * 只有这一档能解释清楚「画像还在，说话的时机为什么不按它来」。
     */
    ASSUMED_COLLECT_OFF,
}

/** 不说话的原因。界面据此换一句不同的话，而不是笼统地「静默」。 */
enum class QuietReason {
    /**
     * **用户自己说的**：今天先安静一会儿。
     *
     * 与另两种根本不同——不是应用推出来的结论，而是用户的一句请求。放最前面判定，
     * 因为「用户说了」优先于任何由数据算出的理由。
     */
    USER_ASKED,

    /** 落在安静时段（用户平常已经睡了）。 */
    SLEEP_HOURS,

    /** 离上次开口太近。 */
    TOO_SOON,
}

/**
 * 判定结论。
 *
 * [StayQuiet.earliestNextAt] 是为界面与测试准备的「下一次最早可能在什么时候」，
 * 可空因为它只是提示，不是判定依据。
 */
sealed interface InterventionVerdict {

    data class Speak(val basis: RhythmBasis) : InterventionVerdict

    data class StayQuiet(
        val reason: QuietReason,
        val basis: RhythmBasis,
        val earliestNextAt: LocalDateTime?,
    ) : InterventionVerdict
}

/**
 * 干预窗口。
 *
 * ## 这个版本里「干预」是什么
 *
 * 本版本**没有通知、没有前台服务、权限为零**（ADR-009），应用无法在用户没打开它的时候
 * 说话。所以 FR-05 的「介入」在这里被收窄成一件看得见的事：**用户打开应用时，
 * 首页顶部迎上来问候，还是换一句安静的话**。这是本版本里唯一真实存在、也能被
 * 自动化观测到的「开口」，不装样子。
 *
 * ## 判定顺序
 *
 * 1. **用户明确要求安静**。用户自己说「今天先安静一会儿」，就不迎上去说话——
 *    这条压过所有由数据算出的理由。
 * 2. **安静时段**。用户平常已经睡了，任何节流理由都不该让它开口。
 * 3. **间隔**。距上次开口不足 [InterventionConfig.gapMinutes] 就往后放。
 * 4. 都不是，则说话。
 *
 * ## 单调性（AC-05.3）
 *
 * 忽略次数**只能把间隔推远**，没有任何分支会因为它而放宽。[InterventionWindowTest]
 * 用穷举守着这一条：它既是对用户的承诺，也是这段代码唯一容易写反的地方。
 * 新增的用户要求安静与忽略次数无关（AC-05.4），正交，不参与这条单调性。
 */
object InterventionWindow {

    /**
     * @param quietOn 用户明确要求安静的那一天（作息日）。null 表示没要求过。
     *
     * 传日期而不是布尔值，是因为「今天先安静一会儿」这句话的效力**只到当天为止**：
     * 到点自动失效靠的是「存的是哪一天」这个结构性事实，而不是另起一个计时器去关它
     * （ADR-028 删掉了所有计时器）。用户改系统日期会让它提前或延后失效，这是接受的代价。
     *
     * @param useLearnedRhythm 这一次判定允不允许拿**学出来的**作息说话——就是设置页那颗
     * `按我的作息说话` 的当前值。**刻意不给默认值**：漏传的后果是「用户明明关掉了，
     * 说话的时机还按他的画像来」，而这是屏幕上看不出来的一处撒谎，没人会为它报红
     * （与 [com.fortheelder.ui.home.InterventionVoice.opening] 里 `now` 必填同一条理由）。
     * 传 false 时 [RhythmResult.Ready] 与「没有画像」同等待遇：判定依据与安静时段
     * 双双退回普通作息，用户可见的差别就是问候语后面会多一句说明（AC-04.4）。
     */
    fun decide(
        rhythmResult: RhythmResult,
        now: LocalDateTime,
        throttle: ThrottleState,
        config: InterventionConfig = InterventionConfig(),
        quietOn: LocalDate? = null,
        useLearnedRhythm: Boolean,
    ): InterventionVerdict {
        // 采集开关关掉之后，画像没有消失（设置页还要显示它），但它不再算数：
        // 下面两处都按「没有画像」那一支走。开关只管采集、不管说话的时机，
        // 是第 89 轮之前的旧口径——那时的界面照旧承诺「以后改按普通作息」，
        // 于是设置页会在同一屏上同时写出「按您自己的作息」和「以后按普通的作息来」。
        val usable = rhythmResult.takeIf { useLearnedRhythm }
        val basis = when (usable) {
            is RhythmResult.Ready -> RhythmBasis.LEARNED
            is RhythmResult.Gathering -> RhythmBasis.ASSUMED_NOT_ENOUGH_DATA
            is RhythmResult.Inconsistent -> RhythmBasis.ASSUMED_INCONSISTENT
            null -> RhythmBasis.ASSUMED_COLLECT_OFF
        }

        if (isQuietOn(quietOn, now.toLocalDate())) {
            return InterventionVerdict.StayQuiet(
                reason = QuietReason.USER_ASKED,
                basis = basis,
                earliestNextAt = now.toLocalDate().plusDays(1).atStartOfDay(),
            )
        }

        val quietSpan = config.quietSpanOf(usable)

        if (quietSpan.contains(now.toLocalTime())) {
            return InterventionVerdict.StayQuiet(
                reason = QuietReason.SLEEP_HOURS,
                basis = basis,
                earliestNextAt = nextOccurrence(quietSpan.end, now),
            )
        }

        val spoken = throttle.lastSpokenAt
        if (spoken != null) {
            val gap = config.gapMinutes(throttle.ignoredStreak)
            // 负数（系统时钟被往回调）同样落到这里：不确定的时候不说话，而不是猛说。
            if (ChronoUnit.MINUTES.between(spoken, now) < gap) {
                return InterventionVerdict.StayQuiet(
                    reason = QuietReason.TOO_SOON,
                    basis = basis,
                    earliestNextAt = spoken.plusMinutes(gap.toLong()),
                )
            }
        }

        return InterventionVerdict.Speak(basis)
    }

    /** [of] 在 [now] 之后（含等于时取今天）最近的一次出现。 */
    private fun nextOccurrence(of: LocalTime, now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate().atTime(of)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }
}

/**
 * 画像存在时用它自己的安静时段，否则用假定值。判定窗口与测试共用这一处口径。
 *
 * 第 101 轮起 `decide` 也调它——原本那里是同一段 `when` 的第二份抄写。参数因此
 * 开成可空，好直接接上 `takeIf { useLearnedRhythm }` 之后那个可能为空的画像。
 */
fun InterventionConfig.quietSpanOf(rhythmResult: RhythmResult?): TimeSpan =
    when (rhythmResult) {
        is RhythmResult.Ready -> rhythmResult.rhythm.quietSpan
        else -> fallbackQuietSpan
    }

/** 把分钟数说成人话：给设置页用，避免出现「1440 分钟」这种读不出来的数。 */
fun Int.asHumanGap(): String = when {
    this < 60 -> "$this 分钟"
    this % 60 == 0 -> "${this / 60} 小时"
    else -> "${this / 60} 小时 ${this % 60} 分钟"
}

/** 画像的起床/入睡时刻，给界面摘要用。`LocalTime.toString()` 本身就是 `HH:mm`。 */
val DailyRhythm.wakeSleepSummary: String get() = "$wakeAt 起、$sleepAt 睡"

/**
 * 用户要求安静的那一天，是不是就是 [day]。
 *
 * 「今天先安静一会儿」这句话的效力**只到当天为止**，而这条判断在本仓库里有三个调用点：
 * 判定窗口自己（决定说不说话、理由是不是「用户自己说的」）、首页那只 ViewModel（决定首页
 * 换哪句话、以及「那就说话吧」摆不摆出来）、设置页「说话的时机」卡（把这条指令的真实状态
 * 摆给用户看）。
 *
 * 三处各写一遍 `quietOn != null && quietOn == ...`，迟早会有一处漏掉那一天、或者被写成
 * 「只要设过就一直安静」——那种坏法不会让任何别的东西立刻变红，只会安静地多静一天。
 * 所以规则只留这一份，三处共用它。
 */
fun isQuietOn(quietOn: LocalDate?, day: LocalDate): Boolean = quietOn != null && quietOn == day
