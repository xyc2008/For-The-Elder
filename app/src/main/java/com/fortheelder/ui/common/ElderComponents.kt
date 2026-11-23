package com.fortheelder.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
 * 内容区放在滚动容器里，大字号下用户可以上下滑。滚动只保证**内容**到得了，
 * 不保证**动作**看得见：内容一长，主按钮就被推到屏幕外，而「先滑一下」对 55 岁以上用户
 * 约等于不存在。第 50 轮的整屏首屏实测就撞在这上面——引导第 2 页的「下一步」落到 y2405，
 * 而屏幕只有 2400 高（数字记在 records/01-迭代记录.md 的第 50 轮）。
 *
 * 滚动区自带一层兜底提示 [ScrollMoreHint]：**内容还有没露出来的部分、且滚动区停在顶端**时，
 * 它浮在滚动区底部，**只提示、不含任何手势**；一下往下滑它就退场，把那一带还给内容，
 * 滑回顶端它再回来（第 58 轮改的规则，判定抽在 [shouldShowScrollHint] 里，见 ADR-054）。
 * 它不占内容区的高度，所以不会因为自己出现而把本来放得下的内容挤出去；
 * 也正因为它是盖在内容之上的一层，它必须对触摸**完全透明**——第 51 轮实测里，它盖住的那一条带
 * 上的点击会被收走，同一次 L3 有 11 条既有用例因此点不动（记在 records/01-迭代记录.md）。
 * 去 `clickable` 不够（非可点的 `Surface` 也带 `pointerInput`，见 [ScrollMoreHint] 的注释），
 * 它的底色最终改用普通 `Box` 的 `background`。
 *
 * 需要钉住的动作放进 [footer]：它在滚动区之下，不随内容滚动，与内容区之间留
 * [ElderDimens.gapSm] 的上间距，底部留 [ElderDimens.gapLg]。
 *
 * **本层不处理键盘遮挡**：整个应用的 insets 只在根层 `AppRoot` 的 `safeDrawingPadding()`
 * 里消费一次，而 `safeDrawing` 已经包含 IME，键盘弹起时这里的内容区与 [footer] 会一起被抬
 * 到键盘之上。第 55 轮在设备上实测确认：给这两个容器各挂一次 `imePadding()` 读到的 inset
 * 恒为 0（根层已经消费过），是死代码；删除前后滚动区底边、EditText 与底部动作区的坐标
 * 逐项一致。反过来说，任何屏幕都不得自行再挂 `imePadding` / `windowInsetsPadding`，
 * 否则等于让位两次（有源码级守卫测试钉住这一点）。
 *
 * @param titleState 需要朗读、但屏幕上不显示的补充状态（例如引导的「第 2 页，共 3 页」）。
 *   见 [asScreenTitle]。
 * @param footer 钉底的动作区。为 null 时这一层不参与组合，其余屏的版式一像素不变。
 * @param scrollState 调用方要自己控制滚动位置时传进来，典型用途是把刚出现的那张卡带进视口
 *   （第 62 轮 `TalkScreen` 的回应卡就是这么做的）。为 null 时自持一个 [ScrollState]，
 *   行为与过去逐项一致。注意这里无论传不传都会无条件走一次 `rememberScrollState`，
 *   条件式 remember 会让 slot table 错位。
 */
@Composable
fun ElderScreen(
    title: String,
    onBack: (() -> Unit)? = null,
    titleState: String? = null,
    footer: (@Composable () -> Unit)? = null,
    scrollState: ScrollState? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ElderHeader(title = title, onBack = onBack, titleState = titleState)
            val fallbackScroll = rememberScrollState()
            val scroll = scrollState ?: fallbackScroll
            // 只看 canScrollForward 不够：`ScrollState` 的 maxValue 初值是 Int.MAX_VALUE
            // （反编译 1.7.5 的构造器确认过），要等第一次布局量完才落回真值；在那之前
            // value(0) < maxValue 成立，于是**每一屏的首帧都会先闪一下提示**。
            // viewportSize 的初值是 0、同样在第一次布局里才被写上，拿它当「量过了」的门槛：
            // 短内容屏首帧不再有提示，长内容屏只是晚一帧出现。
            // 第 58 轮起还要求**停在顶端**（scroll.value == 0），判定抽到纯函数里，规则与理由
            // 见 [shouldShowScrollHint]：一滑动浮标就退场、把盖住的 64dp 还给内容，滑回顶端它再回来。
            // 外面这层 derivedStateOf 把「每滚一像素都变的位置」折成一个布尔，浮标只在真假翻转时
            // 才参与重组；省下的重组次数本机没量过，不当成已测量的收益。
            val showScrollHint by remember(scroll) {
                derivedStateOf {
                    shouldShowScrollHint(
                        viewportSize = scroll.viewportSize,
                        scrollValue = scroll.value,
                        canScrollForward = scroll.canScrollForward,
                    )
                }
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll)
                        .padding(horizontal = ElderDimens.screenPadding)
                        .padding(top = ElderDimens.gapSm, bottom = ElderDimens.gapXl),
                    verticalArrangement = Arrangement.spacedBy(ElderDimens.gapMd),
                    content = content,
                )
                if (showScrollHint) {
                    ScrollMoreHint(modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
            if (footer != null) {
                TargetStack(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ElderDimens.screenPadding)
                        .padding(top = ElderDimens.gapSm, bottom = ElderDimens.gapLg),
                ) {
                    footer()
                }
            }
        }
    }
}

/**
 * 「下面还有内容」浮标：**下面还有没露出来的内容、且滚动区停在顶端**时，浮在滚动区底部
 * （判据是纯函数 [shouldShowScrollHint]，改法见 ADR-054）。
 *
 * 做成浮层而不是内容末尾的一行，是因为后者的高度会参与滚动判定——它一出现就把视口挤小，
 * 于是内容更需要滚动，判据被自己改写。浮层不占高度，`canScrollForward` 只由内容与视口决定。
 *
 * **这一层不含任何手势，也不许在触摸链上留下任何节点。** 上一版把它整块做成可点区域
 * （点一下滚到底）：手势是该人群最不熟的一类操作，而「可点」在 Compose 里就是一条
 * `pointerInput`。第 51 轮先照着「可点才吞触摸」改掉了 `clickable`，重跑 L3 那 11 条既有用例
 * **一条没好**；同一次的守卫用例「提示不吞掉它盖住那一带的点击」报 `expected:<1> but was:<0>`。
 * 顺着这条线索查字节码：Material3 1.3.1 的**非可点** `Surface` 重载自己就把
 * `Modifier.pointerInput(Unit) {}` 挂上了修饰符链（`javap` 出来是 `SurfaceKt$Surface$1` 里那句
 * `SuspendingPointerInputFilterKt.pointerInput`，证据记在 records/04）。它一个手势都不响应，
 * 却照样在它命中的位置上收走触摸——官方 KDoc 把这件事写成了「blocking touch propagation
 * behind the surface」。所以底色改用 [Box] 的 `background` 画：底色留住，触摸链上不再有节点，
 * 落在这一带里的点击照常穿到下面的内容上。
 *
 * 遮挡量仍是 64dp（本机 168 px、屏高 2400 px），但**不是永久的**：第 58 轮起判据多要一条
 * 「滚动区停在顶端」，一往下滑浮标就退出组合、把这一带还给内容，滑回顶端它再回来。
 * 要盖住底部就必然压住一条带，但「压住一条带」既不该连点击一起压住，也不该一路盖到底——
 * 第 50 轮登记的缺陷是**用户不知道下面还有内容**，这一层要解决的是那件事，不是替代用户去滑。
 * 高度仍取 [ElderDimens.minTouchTarget]：遮挡量与第 51 轮那一版相同、跨轮比较时位置可比；
 * 它现在与「触摸目标下限」无关。
 */
@Composable
private fun ScrollMoreHint(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ElderDimens.minTouchTarget)
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = CommonVoice.scrollMore,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
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
 * 一叠**相邻的可点元素**。
 *
 * 布局上与 `Column(verticalArrangement = Arrangement.spacedBy(...))` 完全一样，作用只有一个：
 * 把间距钉在 [ElderDimens.minTouchGap] 上。抽成组件不是为了省一行代码，而是为了给这条间距
 * 一个**能被搜到的名字**——裸写 `spacedBy(ElderDimens.gapSm)` 的地方，读代码时看不出那里是
 * 「相邻的两个可点目标」，于是也不会有人想到它应当比 `gapSm` 宽。
 *
 * 什么时候用：列出来的每一项都**整行可点**（现成的话、入口行、底部的动作按钮），
 * 也就是手指可能点到隔壁去的地方。什么时候不用：一列里混着不可点的正文行——
 * 那种卡片的内层间距仍是 [ElderDimens.gapSm]，一律 16dp 会把正文撑散、页面拉长。
 *
 * 第 63 轮只改了三处最要紧的（外壳 footer、首页「您自己的节奏」那一组、说话页五句现成的话），
 * 其余相邻可点栈连同上面这条判据一起留到后面几轮。判据与依据见 [ElderDimens.minTouchGap]。
 */
@Composable
fun TargetStack(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ElderDimens.minTouchGap),
        content = content,
    )
}

/**
 * 主操作按钮。
 *
 * 高度比次要按钮的 `buttonHeight` 高一档（[ElderDimens.primaryActionHeight]）：
 * 同一篇 NN/g 综述说 `primary calls to action often deserve greater visual prominence and
 * thus deserve a larger tap target`——主次不只靠填色与描边分，尺寸也要分层。
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
            .heightIn(min = ElderDimens.primaryActionHeight),
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
