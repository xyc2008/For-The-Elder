package com.fortheelder.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fortheelder.ui.theme.ElderDimens

/**
 * 页面外壳。
 *
 * 刻意**不使用 Material 的 `TopAppBar`**：它把高度写成了固定值，在系统字号 1.5 倍再叠加
 * 应用内「更大」档时，标题文字会被裁掉下半截。这里用普通 `Row` 自绘表头，高度随文字增长，
 * 只有返回按钮保持一个不小于 [ElderDimens.minTouchTarget] 的点击区。
 *
 * 内容区放在滚动容器里，大字号下用户可以上下滑，不会出现「按钮在屏幕外点不到」。
 *
 * @param titleState 需要朗读、但屏幕上不显示的补充状态（例如引导的「第 2 页，共 3 页」）。
 *   见 [asScreenTitle]。
 */
@Composable
fun ElderScreen(
    title: String,
    onBack: (() -> Unit)? = null,
    titleState: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ElderHeader(title = title, onBack = onBack, titleState = titleState)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = ElderDimens.screenPadding)
                    .padding(top = ElderDimens.gapSm, bottom = ElderDimens.gapXl),
                verticalArrangement = Arrangement.spacedBy(ElderDimens.gapMd),
                content = content,
            )
        }
    }
}

/** 自绘表头。返回按钮的点击区不小于 [ElderDimens.minTouchTarget]，符合触摸目标下限。 */
@Composable
fun ElderHeader(title: String, onBack: (() -> Unit)? = null, titleState: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (onBack == null) ElderDimens.screenPadding else ElderDimens.gapSm,
                end = ElderDimens.screenPadding,
                top = ElderDimens.gapSm,
                bottom = ElderDimens.gapSm,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(ElderDimens.minTouchTarget),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = CommonVoice.back,
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (onBack == null) 0.dp else ElderDimens.gapXs)
                .asScreenTitle(titleState),
        )
    }
}

/**
 * 内容卡片。
 *
 * 用 1dp 描边而不是阴影：阴影在暖白背景上很脏，而且对低视力用户没有信息量；
 * 清晰的边界线反而更容易让视线定位到「这一块是一件事」。
 *
 * 描边用**不透明**的 `colorScheme.outline`。它同时是 WCAG 1.4.11 要求的那条线：
 * 非文本的界面边界与相邻底色要 ≥ 3:1。所以这里不允许再乘透明度——半透明描边在浅底色上
 * 合成出来只有 1.x:1，肉眼却看不出任何问题。
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(ElderDimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(ElderDimens.gapSm),
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.asSectionTitle(),
                )
            }
            content()
        }
    }
}

/**
 * 主入口卡：整块可点，左边一枚装饰徽章，右边标题加一句说明。
 *
 * 与 [ActionRow] 的分工是**权重**：首页上「想说话」与「手上想有点事」是该 App 的两条主干，
 * 用这张卡；「设置」这类次要入口继续用 [ActionRow]。此前三条入口是三个同构的行，
 * 视觉上分不出主次——而 Nielsen Norman Group 的《Usability for Older Adults》里
 * 反复出现的失败模式正是「目标又小又挤」，同一组织的《Touch Targets on Touchscreens》
 * 给的做法是「减少屏上元素，把关键目标做大」。
 *
 * 几点刻意的取舍：
 * - **底色用 `surface`，范围由描边给出。** 描边（[com.fortheelder.ui.theme.OutlineStrong]）
 *   对相邻底色过 3:1；卡片的填充色本身对页面背景只有 1.0x:1，这是允许的——1.4.11 管的是
 *   「用来界定范围的边界」，界定这张卡的是那条线，不是填充。
 * - **图标是装饰性的**，所以不带 `contentDescription`：[title] 已经把话说全了。
 * - **颜色不承担区分职责**（WCAG 1.4.1）：两张卡的区别同时落在图标形状与文字上，
 *   色觉异常或纯灰度下仍然分得清。
 *
 * @param iconContainer 徽章圆底的填充色，由调用方从 `colorScheme` 里取。
 * @param onIconContainer 徽章里图标的颜色，必须与 [iconContainer] 成对取用。
 */
@Composable
fun EntryCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconContainer: Color,
    onIconContainer: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ElderDimens.entryCardMinHeight)
                .padding(ElderDimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(ElderDimens.minTouchTarget)
                    .background(iconContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                // a11y-decorative: 徽章只是让这张卡更好认，含义由同一行的 title 说全。
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = onIconContainer,
                )
            }
            Spacer(Modifier.width(ElderDimens.gapMd))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ElderDimens.gapXs),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 小节标题。用次级文字色，避免整页出现太多同权重的粗黑标题。 */
@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.asSectionTitle(),
    )
}

/**
 * 一条带圆点的事实说明。
 *
 * 圆点用色块绘制而不是「•」字符：字符的垂直位置随字体变化，大字号下会飘。
 */
@Composable
fun BulletLine(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = ElderDimens.gapSm)
                .size(7.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )
        Spacer(Modifier.width(ElderDimens.gapMd))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 补充说明。字号比正文小一档、颜色更淡，读不到也不影响理解主干。 */
@Composable
fun QuietNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * 主操作按钮。
 *
 * 用 `heightIn(min = ...)` 而不是 `height(...)`：大字号下文字会换行成两行，
 * 固定高度会把第二行切掉，而这里会跟着长高。
 */
@Composable
fun PrimaryWideButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ElderDimens.buttonHeight),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}

/** 次要操作按钮。用于「返回上一步」这类不该抢视线的动作。 */
@Composable
fun SecondaryWideButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ElderDimens.buttonHeight),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 一行可点击的条目，整行都是点击区。
 *
 * 老人点按时常落在文字附近而不是正中，所以点击区要覆盖整行，而不是只有文字宽度。
 * 描边同样用不透明色，理由见 [SectionCard]。
 */
@Composable
fun ActionRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ElderDimens.minTouchTarget)
                .padding(horizontal = ElderDimens.cardPadding, vertical = ElderDimens.gapMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) trailing()
        }
    }
}
