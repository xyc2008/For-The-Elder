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
import com.fortheelder.ui.common.ElderScreen
import com.fortheelder.ui.common.PrimaryWideButton
import com.fortheelder.ui.common.QuietNote
import com.fortheelder.ui.common.SecondaryWideButton
import com.fortheelder.ui.common.SectionCard
import com.fortheelder.ui.theme.ElderDimens
import com.fortheelder.ui.theme.ForTheElderTheme
import kotlinx.coroutines.launch

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
 */
@Composable
fun OnboardingScreen(
    container: AppContainer,
    initialAddress: String?,
    initialFontSize: FontSize,
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var address by rememberSaveable { mutableStateOf(initialAddress.orEmpty()) }
    var fontSize by rememberSaveable { mutableStateOf(initialFontSize) }
    val scope = rememberCoroutineScope()

    when (page) {
        0 -> ElderScreen(title = "常伴") {
            Text(
                text = "一个懂节制的陪伴者",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionCard(title = "它现在会做的") {
                BulletLine("记得您希望被怎么称呼。")
                BulletLine("按一天里的时段，跟您问一声好。")
                BulletLine("按您的需要放大字，不用去改手机设置。")
            }

            SectionCard(title = "它不会做的") {
                BulletLine("不会向您推销任何东西。")
                BulletLine("不会催您、不会记您几天没来。")
                BulletLine("不会替您判断身体状况。")
            }

            QuietNote(
                text = "这个版本还很素。陪您聊聊天、帮您把喜欢的事做起来、按您的作息说句话，" +
                    "这些都还在做。做好了会直接出现在这里，不会弹窗通知您，也不用您去开什么开关。"
            )

            PrimaryWideButton(
                text = "我看看怎么用",
                onClick = { page = 1 },
                modifier = Modifier.padding(top = ElderDimens.gapSm),
            )
        }

        1 -> ElderScreen(title = "怎么称呼您", onBack = { page = 0 }) {
            SectionCard(title = "您希望被怎么称呼") {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("比如：老张、王老师") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                QuietNote(text = "空着也行，以后在设置里随时能补。")
            }

            SectionCard(title = "字的大小") {
                Text(
                    text = "选一个看着最舒服的。下面这句话会立刻变成您选的大小。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Column(modifier = Modifier.selectableGroup()) {
                    FontSize.entries.forEach { option ->
                        FontSizeOption(
                            option = option,
                            selected = option == fontSize,
                            onSelect = { fontSize = option },
                        )
                    }
                }
            }

            FontPreviewCard(container = container, address = address, fontSize = fontSize)

            // 这一页**不落盘**：称呼和字号要等第三页一起写下去，这样用户中途退出时，
            // 下次打开还是从第一页开始，不会出现「设置存了一半、引导没走完」的状态。
            PrimaryWideButton(
                text = "下一步",
                onClick = { page = 2 },
                modifier = Modifier.padding(top = ElderDimens.gapSm),
            )
            SecondaryWideButton(text = "回去看上一页", onClick = { page = 0 })
        }

        else -> ElderScreen(title = "想先跟您说件事", onBack = { page = 1 }) {
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

            Text(
                text = "这一步得先问过您。",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionCard(title = "为什么要知道您什么时候用手机") {
                Text(
                    text = "因为「该跟您说句话了」这件事得按您的作息来。常伴不看闹钟——" +
                        "有人六点就起了，有人九点才醒。知道您什么时候醒着、什么时候在歇着，" +
                        "它才不会挑您午睡的时候开口。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            SectionCard(title = "记在哪里、记多久") {
                BulletLine("只记在这台手机上，不上传，常伴也不联网。")
                BulletLine("不读您看了哪个应用、跟谁聊了，也不要任何额外权限。")
                BulletLine("只保留最近 $SIGNAL_RETENTION_DAYS 天，过期的自动删掉。")
                BulletLine("常伴从后台被划掉之后，那期间就记不到了。")
            }

            // 两张卡片和按钮之间**不放任何东西**：实测 1080×2400 默认字号下，
            // 中间多一行说明就会把「先不用」挤到首屏之外，而两个选择必须同时看得见。
            // 这条实测数字记在 records/01-迭代记录.md 第六轮里。
            PrimaryWideButton(
                text = "可以，按我的作息来",
                onClick = { finish(true) },
            )
            SecondaryWideButton(text = "先不用", onClick = { finish(false) })

            QuietNote(
                text = "两个按钮都能选。以后在设置里随时能改，也能一键把记下的东西全删掉。"
            )
        }
    }
}

/**
 * 字号单选行。
 *
 * `RadioButton` 本身只有 20dp，远小于触摸目标下限，所以点击区由整行承担：
 * 整行 `selectable` + `heightIn(min = 56dp)`，圆圈只作为状态显示，不自己接收点击。
 */
@Composable
private fun FontSizeOption(option: FontSize, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ElderDimens.minTouchTarget)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
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
        SectionCard(title = "您会先看到这样一句话") {
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
                QuietNote(text = "字号预览就到这里，下面的按钮不受影响。")
            }
        }
    }
}
