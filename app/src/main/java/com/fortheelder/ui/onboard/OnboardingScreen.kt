package com.fortheelder.ui.onboard

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.fortheelder.core.AppContainer
import com.fortheelder.data.prefs.FontSize
import com.fortheelder.data.rhythm.SIGNAL_RETENTION_DAYS
import com.fortheelder.ui.common.BulletLine
import com.fortheelder.ui.common.CommonVoice
import com.fortheelder.ui.common.ElderScreen
import com.fortheelder.ui.common.PrimaryWideButton
import com.fortheelder.ui.common.QuietNote
import com.fortheelder.ui.common.SecondaryWideButton
import com.fortheelder.ui.common.SectionCard
import com.fortheelder.ui.theme.ElderDimens
import com.fortheelder.ui.theme.ForTheElderTheme
import kotlinx.coroutines.launch

/** 引导一共几页。表头的无障碍状态要用（见 [OnboardVoice.pageIndicator]），所以只写一次。 */
private const val PAGE_COUNT = 3

/**
 * 引导流程。
 *
 * **三页，且不用左右滑动翻页。** 常见的三到五页横滑引导对 55 岁以上用户并不友好：
 * 一是手指划过屏幕时容易误触，二是「还有几页」这件事本身看不见，会带来不确定感。
 * 这里改成三页直上直下，翻页只靠页面底部的按钮，每一步都能看清自己点了什么。
 *
 * 第一页只讲一件事：**这个应用不会做什么**。对一个面向刚退休人群的产品来说，
 * 建立信任靠的不是罗列功能，而是先把边界划清楚——不推销、不催促、不评判。
 *
 * 第二页收集两样东西：称呼和字号。两者都可以留到以后再改（见设置页），
 * 所以这里不强制填写，空着也能开始用。
 *
 * 第三页是**先问再采**（AC-04.4）：把「为什么要知道您什么时候用手机」说清楚，
 * 再由用户点同意或不同意。这一页不能省，也不能把默认值设成同意——见 `AppSettings.collectRhythm`
 * 的注释。两个按钮都会往下走，选「先不用」不会让人卡在引导里出不去。
 *
 * 三页上的字都在 [OnboardVoice]（NFR-17，取舍见 ADR-017）；跨页共用的几句在
 * [CommonVoice]。版式与文案分开之后，改一句话不会再碰到布局代码。
 *
 * ## 重看模式（第 47 轮）
 *
 * [onReviewExit] 不为空，就是**只读重看**：从设置页「重新来一遍」卡里进来，把这三页再走一遍。
 * 这样一条入口的必要性来自一个具体的落差——在这之前，唯一能再见到引导的办法是清空设置，
 * 而用户想做的事往往只是「当时那句话是怎么说的」，并不想丢掉称呼和字号。
 *
 * 重看与首次引导的差别只有两处，都在本文件里看得见：
 * 1. **收集输入的地方把输入收掉**：第二页的称呼框与字号单选行都 `enabled = false`，
 *    第一页多一个返回箭头（[onReviewExit]，首次引导时为 null 所以没有箭头）；
 * 2. **第三页只剩「看完了」一个出口**：不再出现「同意 / 先不用」。
 *
 * 关键在**写盘**：三页一路看下来，一个字节都不该改。这件事不是靠调用方的自觉，
 * 而是靠结构性排除——四处写盘（称呼、字号、采集、完成标记）全在那一个 `finish` lambda 里，
 * 而那个 lambda 只挂在首次引导的两个按钮上；重看路径上它连调用点都没有
 * （`onReviewExit` 在 lambda 体里出现 0 次）。`ReviewOnboardingPlacementTest` 机械守着这条，
 * 谁把写盘挪出 `finish` 都会红。
 */
@Composable
fun OnboardingScreen(
    container: AppContainer,
    initialAddress: String?,
    initialFontSize: FontSize,
    onReviewExit: (() -> Unit)? = null,
) {
    val reviewing = onReviewExit != null
    var page by rememberSaveable { mutableIntStateOf(0) }
    var address by rememberSaveable { mutableStateOf(initialAddress.orEmpty()) }
    var fontSize by rememberSaveable { mutableStateOf(initialFontSize) }
    val scope = rememberCoroutineScope()

    when (page) {
        0 -> ElderScreen(
            title = CommonVoice.appTitle,
            onBack = onReviewExit,
            titleState = OnboardVoice.pageIndicator(1, PAGE_COUNT),
            footer = {
                PrimaryWideButton(text = OnboardVoice.nextButton, onClick = { page = 1 })
            },
        ) {
            Text(
                text = OnboardVoice.subtitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionCard(title = OnboardVoice.doesSection) {
                OnboardVoice.doesBullets.forEach { BulletLine(it) }
            }

            SectionCard(title = OnboardVoice.wontSection) {
                OnboardVoice.wontBullets.forEach { BulletLine(it) }
            }

            QuietNote(text = OnboardVoice.closingNote)
        }

        1 -> ElderScreen(
            title = CommonVoice.addressQuestion,
            onBack = if (reviewing) onReviewExit else ({ page = 0 }),
            titleState = OnboardVoice.pageIndicator(2, PAGE_COUNT),
            // 动作区钉在底部（见 ElderScreen 的 footer）：这一页是全流程里最长的一页，
            // 内容一长就把「下一步」推出首屏。第 50 轮的首屏实测数字记在 records/01-迭代记录.md。
            footer = {
                // 这一页**不落盘**：称呼和字号要等第三页一起写下去，这样用户中途退出时，
                // 下次打开还是从第一页开始，不会出现「设置存了一半、引导没走完」的状态。
                PrimaryWideButton(text = OnboardVoice.nextStep, onClick = { page = 2 })
                SecondaryWideButton(text = OnboardVoice.backStep, onClick = { page = 0 })
            },
        ) {
            // 重看时先说一句「这里改不了」：不然看到一个灰掉的输入框，第一反应是应用坏了。
            if (reviewing) {
                QuietNote(text = OnboardVoice.reviewReadOnlyNote)
            }

            SectionCard(title = OnboardVoice.page2Title) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(CommonVoice.addressPlaceholder) },
                    singleLine = true,
                    enabled = !reviewing,
                    modifier = Modifier.fillMaxWidth(),
                )
                QuietNote(text = OnboardVoice.addressHint)
            }

            SectionCard(title = CommonVoice.fontSection) {
                Text(
                    text = OnboardVoice.fontPickNote,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Column(modifier = Modifier.selectableGroup()) {
                    FontSize.entries.forEach { option ->
                        FontSizeOption(
                            option = option,
                            selected = option == fontSize,
                            enabled = !reviewing,
                            onSelect = { fontSize = option },
                        )
                    }
                }
            }

            FontPreviewCard(container = container, address = address, fontSize = fontSize)
        }

        else -> ElderScreen(
            title = OnboardVoice.page3Title,
            onBack = { page = 1 },
            titleState = OnboardVoice.pageIndicator(PAGE_COUNT, PAGE_COUNT),
            // 动作区钉在底部（见 ElderScreen 的 footer）：这一页的两个选择必须同时看得见，
            // 而说明卡片一长就会把「先不用」推出首屏。第 50 轮起由 footer 用结构保证这一点，
            // 不再靠「卡片和按钮之间不放东西」那条只在默认字号下成立的实测约束。
            footer = {
                // 两个按钮都走这里，只差一个 true/false。写成一个 lambda 是为了让「同意」和
                // 「不同意」在代码里看起来一样短——长度不一样会诱使人把「不同意」那条路少写一步。
                val finish: (Boolean) -> Unit = { collect ->
                    scope.launch {
                        container.settings.setAddress(address)
                        container.settings.setFontSize(fontSize)
                        container.settings.setCollectRhythm(collect)
                        container.settings.completeOnboarding()
                    }
                }

                // 用 `onReviewExit != null` 而不是 `!reviewing`：只有写出那个可空参数本身，
                // 编译器才认得出这个分支里它不为空，`onClick` 才收得下这个引用。
                if (onReviewExit != null) {
                    PrimaryWideButton(text = OnboardVoice.reviewDone, onClick = onReviewExit)
                } else {
                    PrimaryWideButton(
                        text = OnboardVoice.accept,
                        onClick = { finish(true) },
                    )
                    SecondaryWideButton(text = OnboardVoice.decline, onClick = { finish(false) })
                }
            },
        ) {
            Text(
                text = OnboardVoice.askFirst,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionCard(title = OnboardVoice.whySection) {
                Text(
                    text = OnboardVoice.whyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            SectionCard(title = OnboardVoice.whereSection) {
                BulletLine(OnboardVoice.whereLocalOnly)
                BulletLine(OnboardVoice.whereNoPermission)
                BulletLine(OnboardVoice.retentionNote(SIGNAL_RETENTION_DAYS))
                BulletLine(OnboardVoice.whereForgotten)
            }

            QuietNote(
                text = if (reviewing) OnboardVoice.reviewClosingNote else OnboardVoice.bothChoiceNote
            )
        }
    }
}

/**
 * 字号单选行。
 *
 * `RadioButton` 本身只有 20dp，远小于触摸目标下限，所以点击区由整行承担：
 * 整行 `selectable` + `heightIn(min = ElderDimens.minTouchTarget)`，圆圈只作为状态显示，不自己接收点击。
 *
 * [enabled] 为 false 时（第 47 轮的只读重看），整行既不响应点击，也在无障碍语义里
 * 标成「不可用」——读屏会念出来，所以「点不动」是有解释的，不是静默失灵。
 */
@Composable
private fun FontSizeOption(
    option: FontSize,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ElderDimens.minTouchTarget)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(ElderDimens.gapMd))
        Text(
            text = option.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * 实时字号预览。
 *
 * 这里**就地嵌套一层 [ForTheElderTheme]**，而不是把选中的档位传回根主题。
 * 原因是预览要在保存之前就生效；而嵌套主题能成立，靠的是主题**按档位换一套排版表**
 * （见 Theme.kt）——内层只乘自己的档位，所以不会出现「点两次更大就变成 1.3 × 1.3 × 1.3」的累乘。
 *
 * 预览用的是真正的问候引擎，不是写死的示例文字，所以这里看到的就是启动后看到的。
 */
@Composable
private fun FontPreviewCard(container: AppContainer, address: String, fontSize: FontSize) {
    val greeting = remember(address) {
        container.greetingEngine.greet(
            address = address.takeIf { it.isNotBlank() },
            firstLaunchEpochDay = null,
        )
    }

    ForTheElderTheme(fontSize = fontSize) {
        SectionCard(title = OnboardVoice.previewSection) {
            Text(
                text = greeting.headline,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = greeting.note,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(ElderDimens.gapXs)) {
                QuietNote(text = OnboardVoice.previewNote)
            }
        }
    }
}
