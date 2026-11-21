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
import androidx.compose.runtime.produceState
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 「已保存」提示停留多久。够看清，又不至于一直挂在屏幕上。 */
private const val SAVED_HINT_MILLIS = 2_000L

/**
 * 设置页。
 *
 * 这里放的都是**立刻生效、随时可改**的东西，不设「确定/取消」。老人改设置时最容易卡住的
 * 一步是「改完了要不要按确定」，所以每一项改完就生效，界面直接给出可见的结果。
 *
 * 改动只有两处例外需要确认：清空全部数据。那是不可撤销的，必须问一次。
 *
 * 这一页的文案在 [SettingsVoice]，确认弹窗那几句在 [CommonVoice]（NFR-17，取舍见 ADR-017）。
 */
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsState(initial = null)
    val current = settings

    var editingAddress by remember(current?.address) { mutableStateOf(current?.address.orEmpty()) }
    var savedHint by remember { mutableStateOf<String?>(null) }
    var confirmingReset by remember { mutableStateOf(false) }

    LaunchedEffect(savedHint) {
        if (savedHint != null) {
            delay(SAVED_HINT_MILLIS)
            savedHint = null
        }
    }

    ElderScreen(title = SettingsVoice.title, onBack = onBack) {
        SectionCard(title = CommonVoice.addressQuestion) {
            OutlinedTextField(
                value = editingAddress,
                onValueChange = { editingAddress = it },
                label = { Text(CommonVoice.addressPlaceholder) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryWideButton(
                text = SettingsVoice.saveAddress,
                onClick = {
                    scope.launch {
                        container.settings.setAddress(editingAddress)
                        savedHint = SettingsVoice.addressSaved
                    }
                },
                enabled = editingAddress.trim() != current?.address.orEmpty(),
            )
            if (savedHint != null) {
                Text(
                    text = savedHint.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
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
                                        savedHint = SettingsVoice.fontSizeChanged(option.label)
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
                            container.settings.resetToDefaults()
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
 * 三行各说一件事：
 * 1. 现在用的是哪份作息（自己学到的 / 还看不够 / 太不固定），以及它的起床入睡时刻；
 * 2. 现在的开口间隔是多少——这个数会随「招呼了却没被接话」的次数往后放；
 * 3. 间隔有上限，免得用户以为一直不理它就会被它一直喊。
 *
 * 画像读取走 [com.fortheelder.data.rhythm.RhythmProfileService.current]，与首页同一个入口。
 * 因此进这一页也可能顺手把今天的画像算出来落库——这正是「同一作息日内只算一次」的用处。
 */
@Composable
private fun SpeakingTimingCard(container: AppContainer) {
    val throttle by container.interventionStore.state.collectAsState(initial = null)
    val settings by container.settings.settings.collectAsState(initial = null)
    val profile by produceState<RhythmResult?>(initialValue = null, container.rhythmProfileService) {
        value = container.rhythmProfileService.current()
    }

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
    }
}
