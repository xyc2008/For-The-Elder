package com.fortheelder.ui.talk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fortheelder.data.anchor.AnchorRepository
import com.fortheelder.data.intervention.InterventionStore
import com.fortheelder.data.wish.WishRepository
import com.fortheelder.domain.companion.Anchor
import com.fortheelder.domain.companion.AnchorRules
import com.fortheelder.domain.companion.AnchorSlot
import com.fortheelder.domain.companion.Encouragement
import com.fortheelder.domain.companion.StarterCatalog
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
 *
 * 第 10 轮起这里多出一个维度：**用户愿意做的事能不能变成一条日常**。[anchors] 是
 * 已经记下的、跨重启还在；[openOption] 与 [anchorAskFor] 是「此刻摊开到哪一步」，
 * 只活在内存里；[encouragements] 是刚做完某一步之后的回话。
 */
data class TalkPageState(
    val heard: TalkUiState = TalkUiState.Idle,
    val draft: String = "",
    val remembered: List<StoredWish> = emptyList(),
    val anchors: List<Anchor> = emptyList(),
    val openOption: String? = null,
    val anchorAskFor: String? = null,
    val encouragements: Map<String, String> = emptyMap(),
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

    /**
     * 能配上起点的意愿：用户确认过的、而且是「想做的事」。
     *
     * 判据本身在 [StarterCatalog.offerable] 里，界面不自己拼条件——同一条规矩要被
     * 单元测试和界面同时用上时，只能有一个出处的定义。
     *
     * 状态类与请求类的话（睡不着、想找人帮忙）一律不在这里：给它们递一个活动，
     * 是打扰不是陪伴（RC-009）。
     */
    val offerableWishes: List<StoredWish>
        get() = remembered.filter { StarterCatalog.offerable(it.kind, it.term, it.status) }

    /** 这件事此刻记在日常里的样子；没记过就是 `null`。 */
    fun anchorOf(optionId: String): Anchor? = anchors.firstOrNull { it.optionId == optionId }
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
 *
 * 这里还兼着第三个维度：**用户开口这件事本身**要被记下来（[InterventionStore.recordAnswered]）。
 * 「愿意说」与「说了什么」互不依赖——说一句没被听懂的话也是接话，也该把「不理人」的计数清零。
 *
 * 第 10 轮补上第四个维度：**确认过之后能接着做什么**（FR-08/09/10）。传到这一层的只有
 * 三个动作——摊开一个起点、回一句「做完了」、把某一步记成日常——而它们全都**由用户点出来**。
 * 这里没有任何一条路径会自己建锚点，也没有任何一条会把「说了一句话」当成「做了一件事」。
 */
class TalkViewModel(
    private val wishes: WishRepository,
    private val interventions: InterventionStore,
    private val anchors: AnchorRepository,
) : ViewModel() {

    private val mutablePage = MutableStateFlow(TalkPageState())

    val page: StateFlow<TalkPageState> = mutablePage.asStateFlow()

    init {
        viewModelScope.launch {
            wishes.remembered.collect { list -> mutablePage.update { it.copy(remembered = list) } }
        }
        viewModelScope.launch {
            anchors.anchors.collect { list -> mutablePage.update { it.copy(anchors = list) } }
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
        viewModelScope.launch {
            // 用户是真的开口了：把「招呼过却没被接话」的计数清零（FR-05）。
            // 先记这个再记意愿：它只是一次本地小写入，最不该因为别的事情失败而被跳过——
            // 清零晚一步，下一次露面就可能被误判成「不理人」，把话往后推。
            interventions.recordAnswered()
            wishes.remember(text, hits)
        }
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

    // ---- 第 10 轮：低门槛的起点与生活锚点 ----

    /**
     * 用户点开（或收起）一个起点。
     *
     * 一次只摊开一个：三个起点各配两个按钮，一屏就是八个按钮，对老人来说等于没有按钮。
     * 摊开是纯界面动作，不落库、也不代表用户答应了什么。
     */
    fun toggleOption(optionId: String) {
        mutablePage.update { it.copy(openOption = if (it.openOption == optionId) null else optionId) }
    }

    /** 用户点「这一步做完了」。只回一句话，不留记录（[TalkPageState.encouragements] 的说明）。 */
    fun markDone(optionId: String) {
        val option = StarterCatalog.byId(optionId) ?: return
        mutablePage.update {
            it.copy(encouragements = it.encouragements + (optionId to Encouragement.forAction(option.firstStep)))
        }
    }

    /**
     * 用户点「把这一步记成日常」：先问时段，**不直接建**。
     *
     * 这一问是有意义的：同一件小事，记在早上和记在想起来的时候，是两种不同的安排，
     * 只有用户知道哪一种。不问就建，等于替他排了他的一天（AC-09.1）。
     *
     * 目录里没有的起点一律不理会：界面不该能凭一个手写的 id 造出锚点。
     */
    fun proposeAnchor(optionId: String) {
        if (StarterCatalog.byId(optionId) == null) return
        mutablePage.update { it.copy(anchorAskFor = optionId, openOption = optionId) }
    }

    /** 用户点了「先不记」。 */
    fun cancelAnchorProposal() {
        mutablePage.update { it.copy(anchorAskFor = null) }
    }

    /**
     * 用户点了某个时段：**这时候才真的建**。
     *
     * 再判一次闸门，用的是同一条领域规则 [AnchorRules.mayPropose]：界面已经只给确认过的
     * 意愿递起点了，但「不许凭自动识别建锚点」这条要落在写入路径上，而不能只靠界面自觉。
     * 意愿还没落库（`statusOf` 为 `null`）时同样不建——那种情况下用户面前也还没有这个按钮。
     */
    fun confirmAnchor(optionId: String, slot: AnchorSlot) {
        val option = StarterCatalog.byId(optionId) ?: return
        val status = mutablePage.value.statusOf(IntentKind.INTEREST, option.term) ?: return
        if (!AnchorRules.mayPropose(status, optionId)) return
        val draft = AnchorRules.draftOf(optionId, slot) ?: return
        mutablePage.update { it.copy(anchorAskFor = null) }
        viewModelScope.launch { anchors.add(draft) }
    }

    /**
     * 用户点「不用记了」。
     *
     * 这里**不弹任何对话框、不问任何话**，也不留一条「已删除」的痕迹（AC-09.3）。
     * 删掉一件事之后再问一句「要不要再记回来」，是把一次删除变成一场交涉。
     */
    fun forgetAnchor(optionId: String) {
        mutablePage.update { it.copy(anchorAskFor = null) }
        viewModelScope.launch { anchors.remove(optionId) }
    }
}
