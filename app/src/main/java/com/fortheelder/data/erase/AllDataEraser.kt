package com.fortheelder.data.erase

/**
 * 「清空全部数据」的唯一执行者（FR-14 / AC-14.3）。
 *
 * ## 它为什么存在
 *
 * AC-14.3 的口径是「清空**全部**本地数据」。在第七十一轮之前，设置页那颗按钮的名字叫
 * 「清空设置」，做的事只有 [com.fortheelder.data.prefs.SettingsStore.resetToDefaults]
 * 一件——清掉的是 `settings` 这个 DataStore 文件。可是这台手机上常伴还存着四样东西：
 * 作息信号与作息画像（`rhythm_signal` / `daily_rhythm` 两张表）、被记住的意愿（`wish` 表）、
 * 用户记下的日常（`anchor` 表）、说话时机的节流状态（`intervention` 这个 DataStore 文件）。
 * 也就是说，用户点下那颗写着「清空设置」的按钮之后，界面上写着「回到初始状态」，
 * 而库里那些东西一件都没少。这正是本轮要关掉的缺口。
 *
 * ## 为什么是「一串挂起函数」而不是「五个仓库」
 *
 * 五样东西分住在三个存储里（Room 一个库文件 + 两个 DataStore 文件），它们的类型没有共同祖先，
 * 而这一层要做的只是**按顺序逐个调一遍**。把五个仓库类型写进构造函数，会让它连 Room
 * 和 DataStore 一起拖进来，于是这件「顺序 + 覆盖面」的事只能在设备上验；收成五个挂起函数之后，
 * 它是一段纯顺序代码，JVM 单测拿五个记录调用的小 lambda 就能把「每一类都被调到、顺序对」
 * 钉死（`AllDataEraserTest`）。接线的正确性由 `EraseAllWiringTest` 对着 `AppContainer.kt`
 * 的源码守。
 *
 * ## 顺序，以及顺序为什么是这个顺序
 *
 * [erase] 按「用户数据 → 设置」走，最后一步才是 [resetSettings]：
 *
 * 1. 四个数据入口都**不读设置**（删表不需要知道字号），但 [com.fortheelder.data.rhythm.RhythmMaintenance]
 *    和作息采集器**会读设置**——`collectRhythm` 的开关决定采集器要不要注册接收者。
 *    先把库里的东西清干净、再翻设置，可以让「清空」这件事在做数据库那几步时，
 *    采集器还处在用户原本设定的状态上，不会在半途被提前注销/重新注册。
 * 2. 设置一清，`onboardingDone` 变 `false`，整棵导航树立刻换回引导页——这是**用户能看见的**
 *    那一步。把它放最后，界面不会在数据还没删完时就把人送走。
 *
 * ## 它不保证什么（诚实交代）
 *
 * - **不是原子操作**。三个存储之间没有共同事务，Room 的事务盖不到 DataStore 的两个文件。
 *   中途失败会留下「删了一部分」的状态。这里的选择是**不吞异常**：某一步抛了就让它抛出去，
 *   后面的步骤不再执行，界面因此停在设置页、设置也没被清掉——不假装成功。
 *   宁可停在半路让用户再点一次，也不要清掉设置、把人送回引导页，却留着一库没删的旧数据：
 *   那种「看起来成功了」的失败是最坏的一种。这条残余已登记在
 *   `docs/07-风险管理计划.md`（R-15）。
 * - **删不掉磁盘上的文件本身**。DataStore 清空后文件还在（只是键空了），Room 表还在（只是没行）。
 *   真正的「从没装过」只有卸载应用。用户手册（`docs/09`）里那句「最彻底的做法是卸载应用」
 *   在这一轮之后仍然成立，措辞不改。
 * - **不碰任何别的东西**：没有网络、没有权限、没有导出、没有日志留存。清理这件事本身就是它的全部。
 */
class AllDataEraser(
    /** 忘掉被记住的意愿（`wish` 表）。 */
    private val clearWishes: suspend () -> Unit,
    /** 忘掉用户记下的日常（`anchor` 表）。 */
    private val clearAnchors: suspend () -> Unit,
    /** 清掉作息信号与作息画像（`rhythm_signal` / `daily_rhythm`）。 */
    private val clearRhythm: suspend () -> Unit,
    /** 清掉说话时机的节流状态（`intervention` DataStore）。 */
    private val resetThrottle: suspend () -> Unit,
    /** 把设置恢复到出厂状态（`settings` DataStore）。**最后一步**，理由见类注释。 */
    private val resetSettings: suspend () -> Unit,
) {

    /**
     * 按上表顺序清一遍。
     *
     * 没有返回值：这一步的结果不是「删了多少行」，而是「这台手机上再没有常伴记下的东西」，
     * 而后者由调用方在清空前把各类条数摆给用户看（见 `SettingsScreen` 的「重新来一遍」卡）。
     * 清空之后条数各归零，用户自己看得见。
     */
    suspend fun erase() {
        clearWishes()
        clearAnchors()
        clearRhythm()
        resetThrottle()
        resetSettings()
    }
}
