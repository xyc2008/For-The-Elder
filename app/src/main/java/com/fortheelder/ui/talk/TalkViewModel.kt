package com.fortheelder.ui.talk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fortheelder.data.wish.WishRepository
import com.fortheelder.domain.engine.intent.IntentKind
import com.fortheelder.domain.engine.intent.IntentScan
import com.fortheelder.domain.engine.intent.IntentScanner
import com.fortheelder.domain.wish.StoredWish
import com.fortheelder.domain.wish.WishDecision
import com.fortheelder.domain.wish.WishGate
import com.fortheelder.domain.wish.WishStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 「说两句」这一页当前显示什么。 */
sealed interface TalkUiState {
    /** 用户还没说过任何一句话。 */
    data object Idle : TalkUiState

    /** 听出来了：[hits] 至少有一条，按在原文里出现的先后排。 */
    data class Heard(val sentence: String, val hits: List<com.fortheelder.domain.engine.intent.IntentHit>) : TalkUiState

    /** 没听出来。刻意做成一个独立状态而不是「空列表」，见 [IntentScan] 的说明。 */
    data class NotUnderstood(val sentence: String) : TalkUiState

    companion object {
        fun of(sentence: String, scan: IntentScan): TalkUiState = when (scan) {
            is IntentScan.Recognized -> Heard(sentence, scan.hits)
            IntentScan.NotRecognized -> NotUnderstood(sentence)
        }
    }
}

/**
 * 「点一句」里可选的话。
 *
 * 保留这个入口（而第 8 轮又加了真的输入框）不是妥协：刚退休的人第一次面对一个
 * 会说话的空白输入框，多半会不知道写什么，点一句的门槛是零。输入框负责「说自己想说的」，
 * 示例句负责「先看看它怎么听」。
 *
 * [recognized] 与 [notRecognized] 分开，是为了让单测能对两边都提要求：前四句**必须**
 * 一直被识别出来（否则页面上演示的是坏掉的功能），最后那句**必须**一直识别不出来
 * （否则「它会老实说不知道」这个承诺就没有被展示过）。
 */
object TalkExamples {
    /** 能被识别出来的示例。每加一句都要有对应的单测守着。 */
    val recognized: List<String> = listOf(
        "我一直想学书法",
        "想学用手机",
        "最近老是睡不着",
        "帮我找找附近能下棋的地方",
    )

    /** 故意留一句听不明白的：用户得亲眼看见「不猜」长什么样，才敢信它不猜。 */
    val notRecognized: List<String> = listOf("今天天气还行")

    /** 页面上的点选顺序。 */
    val sentences: List<String> = recognized + notRecognized
}

/**
 * 整页的状态。
 *
 * [heard] 是「这一次说了什么」，[remembered] 是「历来说过、被记住的」——
 * 两者分开：前者只活在内存里，退出页面就没了；后者是落盘的，跨重启还在。
 *
 * [draft] 是输入框里正在写、还没提交的字。
 */
data class TalkPageState(
    val heard: TalkUiState = TalkUiState.Idle,
    val draft: String = "",
    val remembered: List<StoredWish> = emptyList(),
) {
    /** 还没表过态、可以问一次的（AC-07.3 的反面：已拒绝的不在此列）。 */
    val toConfirm: List<StoredWish> get() = WishGate.bringUpable(remembered)

    /** 已经定下来的：同意过的和说过不用的。 */
    val settled: List<StoredWish> get() = remembered.filterNot { WishGate.mayBringUp(it.status) }

    /**
     * 这条意愿现在的状态；还没落库时是 `null`。
     *
     * 界面把 `null` 当作「还没表态」处理，而不是当作「没有这件事」：用户刚点完一句示例话
     * 就立刻点同意，那时候插入可能还在路上，这个窗口里按钮必须照常在、按下去也必须有用。
     * 落库这一侧的保证在 [WishRepository.decide] 的串行化里。
     */
    fun statusOf(kind: IntentKind, term: String): WishStatus? =
        remembered.firstOrNull { it.kind == kind && it.term == term }?.status
}

/**
 * 「说两句」的状态。
 *
 * 识别仍然**同步一次算完**（[hear] 里直接出结果），只有落库是后台的：
 * 词表匹配是纯字符串操作、微秒级，把它做成异步只会多出一帧「转圈」，
 * 反而让用户看不清到底发生了什么。
 *
 * 状态三态（Idle / Heard / NotUnderstood）里的 [TalkUiState.Heard] 只负责
 * 「听出了什么」，**「要不要记」是另一个维度**——它来自 [TalkPageState.remembered]
 * 与数据库，不由识别结果决定。这个切分就是 AC-07.4 要的「闸门独立于 NLU」。
 */
class TalkViewModel(private val wishes: WishRepository) : ViewModel() {

    private val mutablePage = MutableStateFlow(TalkPageState())

    val page: StateFlow<TalkPageState> = mutablePage.asStateFlow()

    init {
        viewModelScope.launch {
            wishes.remembered.collect { list -> mutablePage.update { it.copy(remembered = list) } }
        }
    }

    /** 输入框里打了个字。 */
    fun editDraft(text: String) {
        mutablePage.update { it.copy(draft = text) }
    }

    /**
     * 用户点了「说完了」。
     *
     * 提交之后输入框清空：那句话已经交给常伴了，还留在框里会让人以为没发出去。
     */
    fun submitDraft() {
        val draft = mutablePage.value.draft
        hear(draft)
        mutablePage.update { it.copy(draft = "") }
    }

    /**
     * 用户说了一句（自己写的，或点了示例）。
     *
     * 识别是同步的，都是纯 Kotlin；**落库是后台的**，不挡着界面出结果。
     * 落库失败也不会让这一页显示错误，最多是这次说的没被记住——
     * 页面本来就不承诺「一定记住」，承诺的是「听懂了什么会如实告诉你」。
     *
     * 注意：这里**不碰任何状态字段**。又说了别的、或者把同一件事再说一遍，
     * 都不构成同意（AC-07.2：「继续对话」不算表态）。
     */
    fun hear(sentence: String) {
        val text = sentence.trim()
        if (text.isEmpty()) return
        val scan = IntentScanner.scan(text)
        mutablePage.update { it.copy(heard = TalkUiState.of(text, scan)) }
        val hits = (scan as? IntentScan.Recognized)?.hits.orEmpty()
        viewModelScope.launch { wishes.remember(text, hits) }
    }

    /** 用户在闸门上表了态。 */
    fun decide(kind: IntentKind, term: String, decision: WishDecision) {
        viewModelScope.launch { wishes.decide(kind, term, decision) }
    }

    /** 用户点「不用记了」。 */
    fun forget(kind: IntentKind, term: String) {
        viewModelScope.launch { wishes.forget(kind, term) }
    }

    /** 用户点「全部清空」。界面必须已经问过一次。 */
    fun forgetAll() {
        viewModelScope.launch { wishes.forgetAll() }
    }
}
