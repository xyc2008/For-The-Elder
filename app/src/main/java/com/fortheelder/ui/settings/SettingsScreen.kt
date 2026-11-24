package com.fortheelder.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.fortheelder.BuildConfig
import com.fortheelder.core.AppContainer
import com.fortheelder.data.prefs.FontSize
import com.fortheelder.data.rhythm.SIGNAL_RETENTION_DAYS
import com.fortheelder.domain.engine.intervention.asHumanGap
import com.fortheelder.domain.engine.intervention.isQuietOn
import com.fortheelder.domain.engine.intervention.wakeSleepSummary
import com.fortheelder.domain.engine.rhythm.RhythmResult
import com.fortheelder.ui.common.BulletLine
import com.fortheelder.ui.common.CommonVoice
import com.fortheelder.ui.common.ElderScreen
import com.fortheelder.ui.common.PrimaryWideButton
import com.fortheelder.ui.common.QuietNote
import com.fortheelder.ui.common.SecondaryWideButton
import com.fortheelder.ui.common.SectionCard
import com.fortheelder.ui.theme.ElderDimens
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * 设置页。
 *
 * 这里放的都是**立刻生效、随时可改**的东西，不设「确定/取消」。老人改设置时最容易卡住的
 * 一步是「改完了要不要按确定」，所以每一项改完就生效，界面直接给出可见的结果。
 *
 * 改动只有两处例外需要确认：清空全部数据、清空作息数据。这两处都是不可撤销的，必须问一次。
 * （第 47 轮加的「再看一遍开头那几页」不是改动，点进去只看、不写盘，所以它不在这句话的口径里。）
 *
 * 「清空全部数据」自第 71 轮起走的是 [com.fortheelder.data.erase.AllDataEraser]：称呼、字号、
 * 作息记录、说过的意愿、记下的日常、说话的时机一次清干净。之前它只清 `settings` 这一个
 * DataStore 文件，另一处 DataStore（说话的时机）与数据库里那四张表还留在盘上，用户却再也
 * 看不到、也清不掉（见 records/03 的 ADR-066）。
 * 卡里那句 `SettingsVoice.resetCounts` 就是在清之前先把「现在存着什么」摆在眼前。
 *
 * 「重新来一遍」那张卡里现在有**两行**（第 47 轮起）：上面一行是只读重看引导（
 * [SettingsVoice.reviewGuideButton]，配的说明在 [SettingsVoice.reviewGuideNote]，
 * 紧跟按钮之前），下面一行是清空（[SettingsVoice.resetButton]）。两行同处一卡，
 * 是因为它们回答的是同一个念头——「我想回到最开始的样子」——而重看是不丢设置的那条路。
 *
 * 这一页的文案在 [SettingsVoice]，确认弹窗那几句在 [CommonVoice]（NFR-17，取舍见 ADR-017）。
 *
 * 「改完了」这句回执**不设倒计时**（第 30 轮拿掉了原来的 2 秒计时器，见 ADR-028）：
 * 它挂到用户下一次动手为止，而且长在它所属的那张卡里。理由是两个具体的坑——
 * ① 计时器让「屏幕上有没有那句话」变成一个和读屏速度赛跑的问题，横竖都是不稳的断言；
 * ② 一个字号的回执原本渲染在称呼卡里，点完「更大」之后它出现在上方可能已经滚出视口的
 *    位置，用户会以为没改成功。
 */
@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onReviewOnboarding: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsState(initial = null)
    val current = settings

    // 清空前要说清「现在存着什么」，所以这三条流在这一页读一次。前两条初值给 null：
    // 「还没读到」与「确实是空的」要分得开，第一帧不能就说「0 条」。作息条数沿用作息卡
    // 的同一个 [com.fortheelder.data.rhythm.RhythmRepository.signalCount]，初值也就沿用那个 0。
    val wishes by container.wishRepository.remembered.collectAsState(initial = null)
    val anchors by container.anchorRepository.anchors.collectAsState(initial = null)
    val signals by container.rhythmRepository.signalCount.collectAsState(initial = 0)

    var editingAddress by remember(current?.address) { mutableStateOf(current?.address.orEmpty()) }

    // 两张卡各有一句回执。它们不自动消失：离开这一页时随组合一起丢掉，回到这一页就是干净的。
    var addressReceipt by remember { mutableStateOf<String?>(null) }
    var fontReceipt by remember { mutableStateOf<String?>(null) }

    var confirmingReset by remember { mutableStateOf(false) }

    ElderScreen(title = SettingsVoice.title, onBack = onBack) {
        SectionCard(title = CommonVoice.addressQuestion) {
            OutlinedTextField(
                value = editingAddress,
                onValueChange = {
                    editingAddress = it
                    // 「下一次动手就撤」：一打字就撤，不必再等它自己过期。
                    addressReceipt = null
                },
                label = { Text(CommonVoice.addressPlaceholder) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryWideButton(
                text = SettingsVoice.saveAddress,
                onClick = {
                    scope.launch {
                        container.settings.setAddress(editingAddress)
                        addressReceipt = SettingsVoice.addressReceipt(editingAddress)
                    }
                },
                enabled = editingAddress.trim() != current?.address.orEmpty(),
            )
            // 回执紧跟在它回执的那一行下面（称呼卡里说称呼的事）。
            addressReceipt?.let { ChangeReceipt(it) }
            QuietNote(text = SettingsVoice.addressHint)
        }

        SectionCard(title = CommonVoice.fontSection) {
            QuietNote(text = SettingsVoice.fontHint)
            Column(modifier = Modifier.selectableGroup()) {
                FontSize.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = ElderDimens.minTouchTarget)
                            .selectable(
                                selected = option == current?.fontSize,
                                role = Role.RadioButton,
                                onClick = {
                                    scope.launch {
                                        container.settings.setFontSize(option)
                                        fontReceipt = SettingsVoice.fontSizeChanged(option.label)
                                    }
                                },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == current?.fontSize, onClick = null)
                        Spacer(Modifier.width(ElderDimens.gapMd))
                        Column(verticalArrangement = Arrangement.spacedBy(ElderDimens.gapXs)) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
            // 字号回执住在字号卡里（第 30 轮之前它渲染在**称呼卡**那一行，是用户可见的错位）。
            fontReceipt?.let { ChangeReceipt(it) }
            QuietNote(text = SettingsVoice.fontStackNote)
        }

        RhythmDataCard(container = container)

        SpeakingTimingCard(container = container)

        SectionCard(title = SettingsVoice.aboutSection) {
            BulletLine(SettingsVoice.versionLine(BuildConfig.VERSION_NAME))
            BulletLine(SettingsVoice.noUpload)
            BulletLine(SettingsVoice.noAds)
            QuietNote(text = SettingsVoice.localOnlyNote)
        }

        SectionCard(title = SettingsVoice.resetSection) {
            QuietNote(text = SettingsVoice.reviewGuideNote)
            SecondaryWideButton(
                text = SettingsVoice.reviewGuideButton,
                onClick = onReviewOnboarding,
            )
            QuietNote(
                text = SettingsVoice.resetCounts(
                    wishes = wishes?.size ?: 0,
                    anchors = anchors?.size ?: 0,
                    signals = signals,
                ),
            )
            QuietNote(text = SettingsVoice.resetNote)
            SecondaryWideButton(
                text = SettingsVoice.resetButton,
                onClick = { confirmingReset = true },
            )
        }
    }

    if (confirmingReset) {
        AlertDialog(
            onDismissRequest = { confirmingReset = false },
            title = { Text(CommonVoice.confirmClearTitle) },
            text = { Text(SettingsVoice.resetConfirmText) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingReset = false
                        scope.launch {
                            // 一次清掉全部本地数据，不只是设置文件（ADR-066）。
                            container.allDataEraser.erase()
                        }
                    }
                ) { Text(CommonVoice.confirmClear) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingReset = false }) { Text(CommonVoice.dismissClear) }
            },
        )
    }
}

/**
 * 一次改动的回执，例如「称呼已保存。」「字号已改成「更大」。」。
 *
 * 单独抽出来有两个用处：两张卡各放一句、措辞与颜色只有一处可改；以及让「它属于哪张卡」
 * 变成**调用位置**的事——第 30 轮之前这两句共用一个状态、都渲染在称呼卡里，
 * 于是改字号的回执会跑到上方那张卡去（见 ADR-028）。
 *
 * 只有一句回执、没有图标、没有动画：它要传达的全部信息就是「刚才那一下生效了」。
 */
@Composable
private fun ChangeReceipt(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * 「作息数据」区块。
 *
 * 条数直接来自数据库（[com.fortheelder.data.rhythm.RhythmRepository.signalCount]），
 * 不是估算值——清空之后它会立刻变成 0，采集到新信号会立刻涨。
 *
 * 这里做三件事：告诉用户记了什么、让用户开关采集、允许用户一次全删。
 * 没有「导出」「分享」——屏幕开关记录导出给谁都没意义，多一个按钮只会让人担心数据去了哪儿。
 *
 * 开关的状态自己读（而不是让上层传进来），这样「说明文字」和「开关位置」在同一次重组里
 * 用的是同一份设置，不会出现写着「没有打开」、开关却已经是开着的错位画面。
 */
@Composable
private fun RhythmDataCard(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val count by container.rhythmRepository.signalCount.collectAsState(initial = 0)
    val settings by container.settings.settings.collectAsState(initial = null)
    val collectRhythm = settings?.collectRhythm == true
    var confirmingClear by remember { mutableStateOf(false) }

    SectionCard(title = SettingsVoice.rhythmSection) {
        Text(
            text = when {
                count > 0 -> SettingsVoice.rhythmCount(count)
                collectRhythm -> SettingsVoice.rhythmOnNoData
                else -> SettingsVoice.rhythmNone
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        // 整行都能点：Switch 本身只有约 32dp 宽，远小于触摸目标下限，
        // 所以点击区由整行承担；`onCheckedChange = null` 是让开关自己不吃点击，
        // 否则会出现「点开关行不通、必须点开关本身」这种一半好使的状态。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ElderDimens.minTouchTarget)
                .toggleable(
                    value = collectRhythm,
                    role = Role.Switch,
                    onValueChange = { wanted ->
                        scope.launch { container.settings.setCollectRhythm(wanted) }
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = SettingsVoice.rhythmSwitch,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = collectRhythm, onCheckedChange = null)
        }

        QuietNote(
            text = if (collectRhythm) SettingsVoice.rhythmOnNote else SettingsVoice.rhythmOffNote
        )
        QuietNote(text = SettingsVoice.noPermissionNote)
        QuietNote(text = SettingsVoice.retentionNote(SIGNAL_RETENTION_DAYS))
        SecondaryWideButton(text = SettingsVoice.clearRhythm, onClick = { confirmingClear = true })
    }

    if (confirmingClear) {
        AlertDialog(
            onDismissRequest = { confirmingClear = false },
            title = { Text(CommonVoice.confirmClearTitle) },
            text = { Text(SettingsVoice.rhythmConfirmText) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingClear = false
                        scope.launch { container.rhythmRepository.clearAll() }
                    }
                ) { Text(CommonVoice.confirmClear) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingClear = false }) { Text(CommonVoice.dismissClear) }
            },
        )
    }
}

/**
 * 「说话的时机」区块。
 *
 * 这一块存在的理由，是把 FR-05 的**判定依据**摆到台面上：常伴什么时候会迎上来说话、
 * 什么时候换成一句安静的话，用户有权知道，也应该能自己核对。
 *
 * 卡上几行各说一件事：
 * 1. 现在用的是哪份作息（自己学到的 / 还看不够 / 太不固定），以及它的起床入睡时刻；
 * 2. 现在的开口间隔是多少——这个数会随「招呼了却没被接话」的次数往后放；
 * 3. 间隔有上限，免得用户以为一直不理它就会被它一直喊；
 * 4. 这个数真被推长之后，用户可以按一句「我现在就在这儿」把它当场拉回最短（第 77 轮补）。
 *
 * 画像读取走 [com.fortheelder.data.rhythm.RhythmProfileService.current]，与首页同一个入口。
 * 因此进这一页也可能顺手把今天的画像算出来落库——这正是「同一作息日内只算一次」的用处。
 *
 * 第 76 轮补的最后一小节是**用户自己那条安静指令**（AC-05.4）。这一块自己写下的存在理由就是
 * 「把 FR-05 的判定依据摆到台面上」，而「今天先安静一会儿」正是判定窗口的五个入参之一；
 * 在补这一小节之前，用户按了首页那颗按钮、过一会儿回这一页想核「它今天为什么不来搭话」，
 * 是一个字都看不到的。现在这条指令的状态、以及撤销它的出口，都摆在这里。
 *
 * 第 77 轮补的是第 2 行与第 4 行之间那扇**只进不出**的门：间隔被「没被接话」推到最长
 * 24 小时之后，用户原本没有任何办法把它拉回来——唯一能让它回落的途径是接到一次招呼，
 * 而那恰恰是 24 小时才发生一次的事。现在计数还大于 0 时，卡上多出一句说明与一颗出口按钮，
 * 按下去就把被忽略计数清零、上面那个数当场变回最短；计数一回到 0，这一整段一个字都不占
 * （那时间隔已经是最短，再摆一颗按钮就是个按下去没有任何变化的死按钮）。
 */
@Composable
private fun SpeakingTimingCard(container: AppContainer) {
    val throttle by container.interventionStore.state.collectAsState(initial = null)
    val settings by container.settings.settings.collectAsState(initial = null)
    // 第 38 轮：这里原本是 produceState(initialValue = null) { value = ...current() }。
    // Android Lint 的 ProduceStateDoesNotAssignValue 把它判成「lambda 从不赋值、State 永不变」，
    // 报成唯一的 error。这个结论是错的：设备上实测进设置页后卡片显示的不是初始那句「正在了解…」，
    // 而是恰有一句真实结论（见 records/04 第三十七节）。换成 lint 认得的等价写法，行为不变，
    // 只是不让一条已知误报长期压着 error 计数。
    var profile by remember { mutableStateOf<RhythmResult?>(null) }
    LaunchedEffect(container.rhythmProfileService) {
        profile = container.rhythmProfileService.current()
    }

    val scope = rememberCoroutineScope()
    var quietReceipt by remember { mutableStateOf<String?>(null) }
    var presentReceipt by remember { mutableStateOf<String?>(null) }
    /*
     * 「今天是不是处在用户自己要求的安静里」在组合期读一次。
     *
     * 已知的残余：这一页没有时钟轮询（首页有，一分钟一次），所以停在这一页上跨过午夜时，
     * 这行字不会自己消失，要离开再回来才重判。这与「跨过午夜首页不自己重判」是同类的口径，
     * 都写在 docs/09 的 §8.10 里，不假装已经解决。
     *
     * 判据用的是与判定窗口、首页同一个 [isQuietOn]，不在这里另写一遍「存的是哪一天」。
     */
    val quietToday = isQuietOn(
        settings?.quietEpochDay?.let { LocalDate.ofEpochDay(it) },
        container.clock.now().toLocalDate(),
    )

    SectionCard(title = SettingsVoice.timingSection) {
        Text(
            text = when (val result = profile) {
                null -> SettingsVoice.timingLoading
                is RhythmResult.Ready ->
                    SettingsVoice.timingReady(
                        summary = result.rhythm.wakeSleepSummary,
                        sampleDays = result.rhythm.sampleDays,
                    )
                is RhythmResult.Gathering ->
                    SettingsVoice.timingGathering(
                        validDays = result.validDays,
                        requiredDays = result.requiredDays,
                    )
                RhythmResult.Inconsistent -> SettingsVoice.timingInconsistent
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        if (settings?.collectRhythm != true) {
            QuietNote(text = SettingsVoice.collectOffNote)
        }

        val gap = container.interventionConfig.gapMinutes(throttle?.ignoredStreak ?: 0)
        Text(
            text = SettingsVoice.gapLine(gap.asHumanGap()),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        QuietNote(text = SettingsVoice.gapExplainNote)

        /*
         * 间隔被推长之后的出口（第 77 轮加出来，第 79 轮把门控抽成 `SettingsVoice.gapExitOf`）。
         *
         * 门控在**计数大于 0**：计数为 0 时间隔已经是最短那一档，这时摆一颗按钮，按下去什么都
         * 不会变，就是个死按钮；而计数一清零，上面那句说明与这颗按钮一起收起来，卡片回到原样。
         *
         * 门控依据就是卡上那个数自己的来源 `throttle?.ignoredStreak`：同一个来源，不另起一个
         * 「要不要显示出口」的判据——两者一旦对不上，就会出现「写着 24 小时却没有出口」这种
         * 用户没法自己解释的画面。这条判据连同它印的两句话现在整段坐在 `SettingsVoice` 上，
         * 于是「计数为 0 / 为负 / 为 1 / 很大」四种边界都能在没有设备的地方逐一钉住；
         * 界面这里只剩「非空就把两样一起摆上去」。
         */
        val gapExit = SettingsVoice.gapExitOf(throttle?.ignoredStreak ?: 0)
        if (gapExit != null) {
            QuietNote(text = gapExit.note)
            SecondaryWideButton(
                text = gapExit.button,
                onClick = {
                    scope.launch {
                        container.interventionStore.recordPresent()
                        presentReceipt = SettingsVoice.presentNowDone
                    }
                },
            )
        }

        // 只在**今天真的被要求安静**时才出现：平时这一段一个字都不占。
        if (quietToday) {
            Text(
                text = SettingsVoice.quietTodayLine,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            SecondaryWideButton(
                text = SettingsVoice.quietCancel,
                onClick = {
                    scope.launch {
                        container.settings.clearQuiet()
                        quietReceipt = SettingsVoice.quietCancelled
                    }
                },
            )
        }

        // 回执**长在 if 外面**：按下撤销那一刻 `quietToday` 就翻成假，上面那一整段连同按钮
        // 一起收起来；回执要是放在里面，它会跟着自己报的那件事一起消失，用户看到的是一片
        // 空白——正是「点了没用」的样子。它与另两张卡的回执同一个口径：不留倒计时，
        // 一直挂到用户离开这一页（ADR-028）。
        quietReceipt?.let { ChangeReceipt(it) }

        // 上面那颗出口按钮的回执同理：计数一清零，`ignoredStreak` 就翻成 0，说明句与按钮一起
        // 收起来，回执跟着进去就没了。同一个口径：不留倒计时，挂到用户离开这一页（ADR-028）。
        presentReceipt?.let { ChangeReceipt(it) }
    }
}
