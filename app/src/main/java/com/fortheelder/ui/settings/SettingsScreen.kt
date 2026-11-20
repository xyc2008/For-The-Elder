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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import com.fortheelder.ui.common.BulletLine
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

    ElderScreen(title = "设置", onBack = onBack) {
        SectionCard(title = "怎么称呼您") {
            OutlinedTextField(
                value = editingAddress,
                onValueChange = { editingAddress = it },
                label = { Text("比如：老张、王老师") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryWideButton(
                text = "保存称呼",
                onClick = {
                    scope.launch {
                        container.settings.setAddress(editingAddress)
                        savedHint = "称呼已保存。"
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
            QuietNote(text = "留空就只问候，不带称呼。")
        }

        SectionCard(title = "字的大小") {
            QuietNote(text = "点一下立刻生效，不用按确定。")
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
                                        savedHint = "字号已改成「${option.label}」。"
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
            QuietNote(text = "这里的字号会和手机系统的字号叠在一起变大。")
        }

        SectionCard(title = "关于常伴") {
            BulletLine("版本 ${BuildConfig.VERSION_NAME}")
            BulletLine("不联网上传您的任何内容。")
            BulletLine("没有广告，没有积分，没有要每天完成的任务。")
            QuietNote(
                text = "您的称呼、字号这些设置只存在这台手机里。卸载应用时它们会一起消失，" +
                    "我们这边不会留有副本。"
            )
        }

        SectionCard(title = "重新来一遍") {
            QuietNote(text = "会把称呼和字号清回初始状态，然后重新显示第一次打开时的说明。")
            SecondaryWideButton(
                text = "清空设置",
                onClick = { confirmingReset = true },
            )
        }
    }

    if (confirmingReset) {
        AlertDialog(
            onDismissRequest = { confirmingReset = false },
            title = { Text("确定要清空吗？") },
            text = { Text("称呼和字号会回到初始状态。这一步不能撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingReset = false
                        scope.launch {
                            container.settings.resetToDefaults()
                        }
                    }
                ) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingReset = false }) { Text("不清了") }
            },
        )
    }
}
