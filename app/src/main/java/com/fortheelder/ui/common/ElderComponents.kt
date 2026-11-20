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
 */
@Composable
fun ElderScreen(
    title: String,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ElderHeader(title = title, onBack = onBack)
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

/** 自绘表头。返回按钮的点击区固定 56dp，符合触摸目标下限。 */
@Composable
fun ElderHeader(title: String, onBack: (() -> Unit)? = null) {
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
                    contentDescription = "返回上一页",
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
                .padding(start = if (onBack == null) 0.dp else ElderDimens.gapXs),
        )
    }
}

/**
 * 内容卡片。
 *
 * 用 1dp 描边而不是阴影：阴影在暖白背景上很脏，而且对低视力用户没有信息量；
 * 清晰的边界线反而更容易让视线定位到「这一块是一件事」。
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
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
                )
            }
            content()
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
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
