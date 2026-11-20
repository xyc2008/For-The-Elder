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
 * **只有两页，且不用左右滑动翻页。** 常见的三到五页横滑引导对 55 岁以上用户并不友好：
 * 一是手指划过屏幕时容易误触，二是「还有几页」这件事本身看不见，会带来不确定感。
 * 这里改成两页直上直下，翻页只靠页面底部的按钮，每一步都能看清自己点了什么。
 *
 * 第一页只讲一件事：**这个应用不会做什么**。对一个面向刚退休人群的产品来说，
 * 建立信任靠的不是罗列功能，而是先把边界划清楚——不推销、不催促、不评判。
 *
 * 第二页收集两样东西：称呼和字号。两者都可以留到以后再改（见设置页），
 * 所以这里不强制填写，空着也能开始用。
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
                text = "这个版本还很素。陪您聊聊天、记住您平时的作息、帮您把喜欢的事做起来，" +
                    "这些都还在做。做好了会直接出现在这里，不会弹窗通知您，也不用您去开什么开关。"
            )

            PrimaryWideButton(
                text = "我看看怎么用",
                onClick = { page = 1 },
                modifier = Modifier.padding(top = ElderDimens.gapSm),
            )
        }

        else -> ElderScreen(title = "怎么称呼您", onBack = { page = 0 }) {
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

            PrimaryWideButton(
                text = "好了，开始吧",
                onClick = {
                    scope.launch {
                        container.settings.setAddress(address)
                        container.settings.setFontSize(fontSize)
                        container.settings.completeOnboarding()
                    }
                },
                modifier = Modifier.padding(top = ElderDimens.gapSm),
            )
            SecondaryWideButton(text = "回去看上一页", onClick = { page = 0 })
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
