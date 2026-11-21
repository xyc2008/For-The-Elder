# 变更日志

格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循语义化版本。本文件记录**用户可感知的变化**，逐轮追加。

`Unverified`(未验证)一节是刻意保留的：**没验证的事不准当成已验证的写**。

---

## [Unreleased] — 0.1.0 开发中

### Added

**第 1 轮 — 需求基线**
- `docs/00-项目章程与需求计划书.md`：立项背景、目标 G-01~G-06、范围与明确不做清单、
  负面需求 NR-01~NR-05、用户画像与两条铁律、假定/约束、10+10 轮里程碑、验收准则、风险
- `docs/01-需求规格说明书.md`：术语表、用户故事 US-01~US-10、功能需求 FR-01~FR-14(带 AC 与证据)、
  非功能需求 NFR-01~NFR-17、数据需求 DR-01~DR-09、追溯矩阵、缺口清单 GAP-01~GAP-05

**第 2 轮 — 工程骨架与最小可运行链路**
- 构建：Gradle 8.13 + AGP 8.7.3 + Kotlin 2.0.21 + KSP 2.0.21-1.0.26，版本目录 `libs.versions.toml`
- 模块结构：单 `:app` + 包分层(core / data / domain / ui)，手工 DI(`core/AppContainer.kt`)
- 数据层：`SettingsStore` + `SettingsDataStore`(Preferences DataStore)，含三档字号枚举
- 领域层：`GreetingEngine`(按时段问候) + `AppClock`(集中时间取值，可注入以便测试)
- 界面：引导页(两页)、首页、设置页、`MainNav`(只登记已完成的目的地)
- 主题：松绿/暖白配色、`ElderDimens.minTouchTarget = 56.dp`、按钮 `heightIn(min = 64.dp)`
- 隐私：`allowBackup="false"`，**申请权限数 = 0**

**第 3 轮 — 字号可访问性修复与渲染级测试**
- 新增 `TypographyScaleTest`(JVM，5 例)
- 新增/重写 `ThemeFontScaleTest`(仪器化，5 例，量原始像素)
- 新增 `records/` 过程记录体系(本目录全部文件)

**第 4 轮 — 作息感知引擎(领域层，尚无界面)**
- 新增 `domain/engine/rhythm/RhythmSignal.kt`：五种作息信号(亮屏/灭屏/解锁/开始充电/结束充电)
- 新增 `domain/engine/rhythm/RhythmInference.kt`：`RhythmInference.infer()` +
  `RhythmConfig`(阈值集中在一处、全部有默认值)
- 新增 `domain/engine/rhythm/DailyRhythm.kt`：`TimeSpan`(允许跨子夜的半开时段)、
  `DailyRhythm`(起床/睡前/活跃段/空窗候选/入睡锚点/天数/置信度)、`RhythmResult`(三态)
- 新增 `RhythmInferenceTest`(JVM，23 例)

说明：本轮**没有用户可感知的变化**——引擎还没接界面，也没接系统广播(第 6 轮)。
列在这里是为了让「代码里多了什么」可追溯。

**第 5 轮 — 作息数据落库(设备上第一处真实存储)**
- **设置页新增「作息数据」卡片**(本轮唯一用户可见的变化)：已记录条数、三行说明
  (记什么 / 不读什么 / 留多久)、带二次确认的「清空作息数据」
- 新增 `data/db/`：`RhythmSignalEntity`/`RhythmSignalDao`(信号，`(kind, atEpochMilli)` 唯一索引去重)、
  `DailyRhythmEntity`/`DailyRhythmDao`(画像，每天一行)、`SpansCodec`(时段序列化)、
  `ElderDatabase`(Room 库)
- 新增 `data/rhythm/`：`RhythmMapping`(实体↔领域互转，全仓唯一做时区换算的地方)、
  `RhythmRepository`(读信号→调引擎→存画像→计数/清空/清理过期)、`CapturedSignal`(采集器将来产出的中间结构)
- 新增 `app/schemas/com.fortheelder.data.db.ElderDatabase/1.json`：Room 导出的 v1 schema，
  **必须进仓库**(迁移测试要用)，`.gitignore` 不含 `schemas` 是有意的
- 新增测试：`SpansCodecTest`(JVM 13)、`RhythmMappingTest`(JVM 11)、`RhythmTimeAxisTest`(JVM 9)、
  `RhythmDaoTest`(设备 13)、`RhythmRepositoryTest`(设备 13)

**第 6 轮 — 作息采集器真的收得到系统广播了**
- **设置页「作息数据」卡片可开关采集**(用户可见)：整行可点 + `Switch`，条数文案四态
  (「还没有记录。常伴现在还没开始自动记这些。」/「已经打开了，还没记到东西。」/
  「已记录 N 条。」/ 清空后回到未开始态)，两条说明随开关切换措辞
- **引导流程扩到 3 页**(用户可见)：第 3 页问一句要不要记，两个按钮都往下走
- 新增 `domain/engine/rhythm/RhythmBroadcasts.kt`：广播名 ↔ 领域动作映射，纯 Kotlin `object`，
  **不引 `android.content.Intent`**，所以能在 JVM 上测；认不出的 action 返回 `null`（不猜）
- 新增 `data/rhythm/RhythmCollector.kt`：匿名 `BroadcastReceiver` + `ContextCompat.registerReceiver`
  (`RECEIVER_NOT_EXPORTED`)，`start()`/`stop()` 幂等，按 `COLLECT_DEBOUNCE_MILLIS` 去抖，
  回调返回 `Job?`
- 新增 `data/rhythm/RhythmMaintenance.kt`：`pruneIfDue()`，以 `lastPruneEpochDay` 为闸，同一天只清一次
- `core/AppContainer.kt` 新增 `processScope` / `rhythmCollector` / `rhythmMaintenance` /
  `startProcessWork()`，由 `ElderApp.onCreate` 调用
- 新增测试：`RhythmBroadcastsTest`(JVM 8)、`RhythmBroadcastsPlatformTest`(设备 2)、
  `RhythmCollectorTest`(设备 6，其中一例用真实 `input keyevent 26` 收系统广播)、
  `RhythmMaintenanceTest`(设备 3)；`SettingsStoreTest` 9→12

**第 7 轮 — 意愿识别能听懂、也敢说听不懂**
- **首页新增「想说话的时候 / 跟常伴说两句」入口，点进去是新的「说两句」页**(本轮用户可见的变化)：
  5 句示例话，每句整行可点；点完立刻显示**听懂了什么**和**是听哪几个字听出来的**；
  听不出来时显示「这句我没听明白」，**不给任何推荐**
- 新增 `domain/engine/intent/IntentLexicon.kt`：纯数据词表——19 个兴趣提示词、
  13 个主题词、7 个状态说法、2 个请求说法；`negationMarkers` 故意**不收裸「别」**
  (「特别想学书法」会被误杀)
- 新增 `domain/engine/intent/IntentScan.kt`：`IntentScanner.scan()`，
  **该文件 import 数为 0**(不依赖 Android，可在 JVM 逐条断言)；按句切分(逗号**不算**句断)、
  长词优先防重叠、否定按句丢弃、证据取**最短包住片段**、结果按位置排序去重；
  返回 `sealed interface IntentScan { Recognized / NotRecognized }`，
  拒答是**类型上必须处理**的分支
- 新增 `ui/talk/TalkViewModel.kt` + `ui/talk/TalkScreen.kt`：三态 `Idle / Heard / NotUnderstood`，
  结果卡上写明证据原话
- 新增测试：`IntentScanTest`(JVM **29**)、`TalkUiStateTest`(JVM **5**，
  两例守着「示例句必须一直是真话」)

说明：意愿识别目前**只读**——听得出来，但不会带来任何后果(确认闸门是第 8 轮的 FR-07)。
「常伴现在只是听，还不会替您安排任何事。」这句界面文案是当前功能的**如实描述**，不是占位。

**第 8 轮 — 意愿确认闸门 + 真输入框 + 落库(FR-07 收口)**
- **「说两句」页从「只能点示例句」变成有真输入框**(用户可见)：可以自己敲一句再点「说完了」，
  走的是与示例句**同一条** `IntentScanner` 链路；听不出来照样显示「这句我没听明白」，不推任何东西
- **新增意愿确认闸门**(用户可见，本轮核心)：听出意愿后**不直接记下**，先当面问一句
  「这件事要不要常伴替您记着？」；按钮按意愿类型换措辞(书法的「愿意了解一下」=
  学用手机的「放在心上」= 出行的「记下来」)；答「先不用」的意愿**不会被常伴再主动提起**，
  但用户自己在列表里点「我现在想了解一下」仍可改主意
- **新增「常伴记住的事」列表**(用户可见)：只列**已确认**的意愿，每条能给「不用记了」，
  整页有带二次确认的「全部清空」；**刻意不显示任何时间**(不写「上次提到」「3 天前」)
- 新增 `data/db/WishEntity.kt` + `WishDao.kt` + `Migrations.kt`：`wish` 表(唯一索引
  `index_wish_kind_term` 让同一件事重复提到只留一条)、`MIGRATION_1_2`(只建 `wish` 表，
  不动两张作息表)、`app/schemas/.../2.json`
- 新增 `domain/wish/Wish.kt`：`WishGate`(纯函数状态机，**零 Android 依赖**)——
  `mayOffer` 只对已确认为真、`mayBringUp` 只对「还没问过」为真、**两者永不同时为真**、
  `decide()` 在状态不变时返回 `null`(表态时刻不被覆盖)
- 新增 `data/wish/WishRepository.kt`：`INSERT OR IGNORE` + `Mutex` 串行化，
  读库时把认不出的状态当「先不用」处理(坏数据的方向是少打扰)
- 新增 `ui/talk/TalkVoice.kt`：措辞集中一处，按意愿类型给不同问句/按钮/结果语
- 新增 `ui/nav/MainNav.kt`：目的地登记表(此前导航只在 `MainActivity` 里散着写)
- 新增测试：`WishGateTest`(JVM 18)、`TalkVoiceTest`(JVM 16)、`WishDaoTest`(设备 11)、
  `WishRepositoryTest`(设备 12)、`MigrationTest`(设备 2)、`TalkInputTest`(设备 4)；
  JVM 由 125 → **159**，设备由 42 → **71**

**第 9 轮 — 低打扰节流与干预窗口调度(FR-05 收口)**
- **不再每次打开应用都重复招呼**(用户可感知，本轮核心)：刚打过招呼就再开应用，
  首页只会说「先不重复招呼了。我在这儿。想说话随时说。」——**不会**把问候语再念一遍。
  间隔从最短 30 分钟起，用户越不接话就往后放(每次 +3 小时)，**上限一天一次**，
  所以不存在「越不理越不理」的死锁；用户接一次话，间隔立刻收回 30 分钟，**不记仇**。
- **安静时段首页不迎上来说话**(用户可感知)：睡下之后打开应用，首页只说
  「这会儿是您休息的时间。我就不迎上来说话了，安静陪着您。您要是想说什么，随时说。」
  ——留了用户可以主动说的口子，不是关门。有画像时按**用户自己的**入睡/起床时刻判断，
  没有画像时退回 07:00 / 22:30 并**如实说明**「我先按普通的作息来」。
- **设置页新增「说话的时机」卡片**(用户可感知)：显示「现在打招呼的间隔：N 分钟」与
  「它打招呼的间隔会随您接话的多少自己调整：接话少就往后放，最多一天一次。不会越叫越勤。」
  以及作息了解进度(「还在了解中：已经看了 N 天，满 3 天就能用上」)。
- **用户接话这件事会被真的记下来**(用户可感知的间接影响)：在「说两句」页说了一句
  且被识别出意愿时，节流状态里写下回应时刻并清空忽略计数 —— 落盘实测 49 → 78 字节。
- 新增 `domain/engine/intervention/InterventionWindow.kt`：`InterventionConfig`
  (阈值集中一处、全部有默认值)、`ThrottleState`(带 `pendingSpeak`/`afterSpoken`/`afterAnswered`)、
  `RhythmBasis`(LEARNED / ASSUMED_NOT_ENOUGH_DATA / ASSUMED_INCONSISTENT)、
  `QuietReason`(SLEEP_HOURS / TOO_SOON)、`sealed interface InterventionVerdict`、
  **纯函数** `InterventionWindow.decide()` —— **零 Android 依赖、零 `java.time` 以外的时间库**
- 新增 `data/intervention/InterventionStore.kt`：节流状态落 DataStore，**独立文件 `intervention`**，
  键 `last_spoken_at` / `last_answered_at` / `ignored_streak`
- 新增 `data/rhythm/RhythmProfileService.kt`：把「信号 → 画像」这条链路接上——
  **只有 `Ready` 才落库**，`Gathering` / `Inconsistent` 一律不写
- 新增 `ui/home/InterventionVoice.kt`：把 `InterventionVerdict` 翻成人话，
  措辞集中一处，并有两张**禁用词表**的扫描测试(不许出现通知/提醒/推送/闹钟/到点；
  不许评判或催促用户)
- 新增测试：`InterventionWindowTest`(JVM 21)、`ThrottleStateTest`(JVM 11)、
  `InterventionStoreTest`(JVM 10)、`InterventionVoiceTest`(JVM 11)、
  `RhythmProfileServiceTest`(设备 4)；JVM 由 159 → **212**，设备由 71 → **75**
- 静态检查：权限数仍是 **0**，清单里 `<receiver>` 仍是 **0**，全 main 源网络栈仍为 **0 matches**

**第 10 轮 — 低门槛行动卡与生活锚点(FR-08/09/10 收口)**
- 数据层新增第三张业务表 `anchor`：6 列(`id` / `term` / `optionId` / `action` / `slot` /
  `createdAtEpochMilli`)，`optionId` 上有唯一索引 `index_anchor_optionId`；库版本 2 → 3，
  新增 `MIGRATION_2_3`(`data/db/Migrations.kt`)，并导出 schema
  `app/schemas/com.fortheelder.data.db.ElderDatabase/3.json`
- 新增 `data/anchor/AnchorRepository.kt`(85 行)：对外只有 `anchors` / `add` / `remove` /
  `dueNow` / `count` 五个成员，入参是 `AnchorDraft`；**表里没有任何计数列**，
  「记了多少次」「坚持了几天」这类字段不存在，也就无从显示
- 新增 `domain/companion/` 四个文件(纯数据 + 纯函数，不 import `android.*`)：
  `StarterOptions.kt`(448 行，**39 条起点** / 13 个兴趣主题，`maxMinutes = 20`)、
  `Anchor.kt`(81 行，`AnchorSlot` 四时段 + `AnchorRules.draftOf` / `mayPropose` / `dueAt`)、
  `CompanionVoice.kt`(119 行)、`Encouragement.kt`(39 行，`forAction(action)` 唯一入口 + 三张词表)
- 新增界面：`ui/talk/StarterCard.kt`(148 行，起点卡与目标卡)、`ui/talk/AnchorCard.kt`(76 行)、
  首页的锚点行(`ui/home/InterventionVoice.kt`，只在 `Speak` 判定下出现)
- 新增 `docs/02-概要设计说明书.md`：设计约束 C-01~C-06、架构总览、56 个源文件的构成表、
  数据设计、领域层、UI 层、构建与依赖、测试策略(含迁移测试守什么)、设计取舍 D-01~D-10
- 新增测试类：`StarterCatalogTest`(JVM 21)、`AnchorRulesTest`(JVM 16)、
  `CompanionVoiceTest`(JVM 12)、`EncouragementTest`(JVM 10)、
  `AnchorDaoTest`(设备 7)、`AnchorRepositoryTest`(设备 6)、`TalkStarterTest`(设备 1)；
  JVM 由 212 → **279**，设备由 75 → **92**
- 静态检查：权限数仍是 **0**，清单里 `<receiver>` 仍是 **0**；
  主源由 47 → **56 个 .kt / 6,133 行**，JVM 测试由 16 → **20 文件**，
  设备测试由 11 → **14 文件**(`git ls-tree -r HEAD` 与工作区实测对比)

### Changed

- **意愿识别的召回范围被明确收窄**(第 7 轮，`records/02` RC-006)：句内**只有主题词、
  没有提示词**时判定为「没听懂」，不推断也不推荐（如单独一句「书法」）。
  这是主动用召回换准确：「猜错方向的陪伴者比说『我没听明白』的陪伴者更让人不放心」。
  代价是「下棋挺好的」这类话识别不出来，属于**已知的、写下来的**限制。
- **最小的对话界面提前到第 7 轮**(第 7 轮，`records/02` RC-006)：原计划引擎(第 7 轮)与
  界面(第 8 轮)分开，但「只做引擎不做界面」会让这轮产出无人可见、无法在设备上验证，
  与「不留死代码、不留假功能」冲突。所以先接一条最小但真实的链路，界面确实薄
  (只能点示例句，还不能自由说话)。
- **词表规则代替模型**(第 7 轮，`records/03` ADR-010)：不联网、不引模型、不加载 native 库，
  因此**权限数仍是 0**、用户说的话不离开设备，错误也**可读可复现**。
- **否定按「整句丢弃」处理**(第 7 轮，`records/03` ADR-010)：同一句里出现否定词时，
  这一句的**兴趣**命中全部作废，状态与请求不受影响；不做逐词消解(那需要句法分析，
  靠词表做一半反而会产生看起来更聪明的错误)。代价是「不错，下棋挺好玩」会整句不识别。
- **界面不显示任何置信度百分比**(第 7 轮，`records/03` ADR-010)：词表匹配没有概率，
  输出一个 `0.87` 只能是编的。界面只给规范词与证据原话。

- **引导流程由 2 页扩到 3 页**(第 6 轮，`records/02` RC-005)：第 3 页当面问一句
  「要不要让常伴记一记您什么时候用手机」，两个按钮「好，记吧」/「先不用」**都往下走**，
  选择写进 `collectRhythm`。此前引导走完把采集开关**留在关位**、让用户自己去设置里找，
  既不符合产品立场，也让作息画像永远是空的。详见 `records/02` RC-005。
- **引导第 3 页的布局为可点性做了压缩**(第 6 轮)：修复前次按钮只露 14px(等于看不见)，
  修复后两个按钮都完整在屏内(次按钮 168px = 64dp)。数字见 `records/04` 第 6.5 节。
- **广播采集改为只在运行时注册**(第 6 轮，`records/03` ADR-009)：manifest 里
  **没有任何 `<receiver>` 声明**，也不申请 `RECEIVE_BOOT_COMPLETED`；注册用
  `RECEIVER_NOT_EXPORTED`。权限数**仍是 0**。代价是进程不在就收不到广播。
- **`pruneExpired` 有了宿主**(第 6 轮)：`RhythmMaintenance.pruneIfDue()` 挂在启动路径上，
  以 `lastPruneEpochDay` 为闸，同一天只清一次。此前它是「有实现、有测试、没有调用者」。
- **字号缩放机制**(第 3 轮)：由「替换 `LocalDensity.fontScale`」改为
  「按档位换一套排版表」(`elderTypography(scale)`)。原因是平台的非线性字体换算法
  使 ≥30sp 的标题在「更大」档完全不放大——即标题档是假功能。详见 `records/03` ADR-004、
  `records/02` RC-001。
- `ui/onboard/OnboardingScreen.kt` 中字号预览卡的说明文字随之改写。
- **输入信号里删掉「应用前台时长」**(第 5 轮，`records/02` RC-004)：这一行原本写着「第 5 轮实现」，
  但它需要读**别的应用**的使用记录，与本项目「不申请 `PACKAGE_USAGE_STATS`、不读其他 App 使用记录」的
  承诺直接冲突。需求改判为「不做」，并且**不换别的信号顶上**(理由见 `records/02`)。
- **删掉 `material-icons-extended` 依赖**(第 5 轮)：它带来 11,404 个图标类，而界面只用了两个图标，
  两个都在 `material3` 传递进来的 `material-icons-core` 里。debug APK
  **17,656,298 → 10,774,743 字节(−39%)**。`libs.versions.toml` 里的别名**保留**，需要时一行加回。
- **删掉三个声明了却从未使用的依赖**(第 5 轮)：`okhttp`、`kotlinx-serialization-json`、
  `androidx-work-runtime-ktx`，以及 `kotlin("plugin.serialization")`——全仓零引用。

- **意愿的「拒绝」是可以撤回的**(第 8 轮，`records/02` RC-007、`records/03` ADR-011)：
  `DECLINED + CONFIRM → CONFIRMED` 是**刻意的**。AC-07.3 挡的是**常伴主动提起**，
  不是用户自己改主意——「先不用」是当时的回答，不是永久禁令。用户在列表里点
  「我现在想了解一下」就能翻回来，实测在设备上走通(第 8 轮 8.5 表第 10 屏)。
- **意愿不设保留期，界面也不显示任何时间**(第 8 轮，`records/02` RC-007)：
  作息数据有 30 天窗口，意愿**没有**——从 55-65 岁的使用节奏看，「几个月没提起」
  不代表不重要，「上次提到是 3 天前」这种字眼更是催促。代价是意愿表会一直长，
  换来的是「用户自己删」这条唯一的出口，与 NR-02/NR-03 一致。
- **确认闸门独立于意愿识别**(第 8 轮，`records/03` ADR-011)：`WishGate` 是纯函数状态机，
  `domain/wish/` 目录里 `grep "rhythm|unlock"` **零命中**——「听懂了什么」与
  「要不要记下来」是两个正交的问题，混在一起会让任何一边的改动都影响另一边。
- **一组闸门按钮只服务于当前那一件事**(第 8 轮，`records/03` ADR-011)：
  页面上永远只有一组「愿意了解一下 / 先不用」，不给「全部同意/全部拒绝」。批量同意
  等于替用户回复，批量拒绝等于替用户关门，两者都不是陪伴者该做的动作。
- **数据测试各自带库名，不再共用产品库文件**(第 8 轮，`records/03` ADR-012)：
  `ElderDatabase.open(context, name)` 与 `AppContainer(context, databaseName)` 各加一个
  默认参数(默认值就是产品库 `elder.db`，**唯一的生产调用点没变**)。改前四个数据测试
  轮流 `deleteDatabase("elder.db")`，而测试进程里应用的 Room 实例长期存活，
  于是 `SELECT * FROM wish` 撞上被删空的壳库直接崩(`no such table: wish`)。
  这不是产品缺陷，是测试隔离问题；改后 `connectedDebugAndroidTest` 71/71 绿。
- **清空意愿后页面会重新渲染一次闸门按钮**(第 8 轮，**刻意保留**)：
  第 8 轮实测到——清空之后页面还显示着刚说完的那件事，`wishOf` 返回 `null` 便按
  「还没问过」重画按钮。理由是状态由「库里有没有这条」推出来，界面不另存副本；
  用户看到的是「常伴又问了一次」，不是错的数据。判定可接受，未改代码。

- **「介入」的含义被明确收窄为「打开应用时首页是否迎上来说话」**(第 9 轮，`records/02` RC-008)：
  本版本**不建立通知通道**，因此 FR-05 的产出只作用于用户打开应用的那一刻。
  「用户没打开应用时，常伴什么都不做」是**刻意的**，不是漏做(记为 GAP-06)。理由：
  通知需要申请权限、需要在清单里注册 receiver，与「权限数 0」「`<receiver>` 0 个」
  这两条硬判据直接冲突；而且对 55-65 岁人群，一次不合时宜的通知比少说一句话伤害大得多。
- **接话之后立刻重开应用仍然安静是设计，不是待修的缺陷**(第 9 轮，`records/03` ADR-013)：
  `afterAnswered()` 只清 `ignoredStreak` 并记下回应时刻，**不动 `lastSpokenAt`**。
  如果接话就把「上次开口时刻」往后推，会出现「越理它、它越晚来搭话」的反直觉结果。
  设备实测确认：第 9 轮设备复核第 8 步记下 `last_answered_at` 后
  `force-stop` + 重开，首页仍说「先不重复招呼了。」——这是**预期行为**。
- **「这次要不要开口」只在构造时判一次，`onStart()` 必须先见过 `onStop()` 才重判**
  (第 9 轮，`records/03` ADR-013)：`LifecycleRegistry.addObserver` 会把新观察者同步推进到
  当前状态，界面刚订阅时系统会补发 ON_CREATE/ON_START/ON_RESUME；那一刻 ViewModel 刚在
  `init` 里判过一次，若见 ON_START 就再判，第二次会读到**刚刚写下的**「已经招呼过」，
  首页于是从问候翻成「先不重复招呼了」——自己把自己说没了。改成
  `init { open() }` + `onStart() { if (!leftSinceLastOpening) return; … }` +
  `onStop() { leftSinceLastOpening = true }`，并用 `openingMutex` 串行化两条可能的开面路径。
  设备实测：首次安装走完引导落到首页，看到的是正常判定结果(当时设备 tz=GMT、本地 02:14，
  显示的是安静语)，**不是**「先不重复招呼了」。

- **意愿列表侧的按钮文案收窄**(第 10 轮，`records/02` RC-009)：`ui/talk/TalkScreen.kt:322`
  与 `:356` 的「不用记了」改为「这件事不用记了」。原因是本轮起「记成日常」成了正面动作，
  短句「不用记了」在语境里可以被读成「不用把这件事记下来」，与「记成日常」撞车；
  锚点卡上的删除按钮仍是短句「不用记了」(那里上下文唯一)。
- **FR-09 的措辞收窄为「用户自己把某一步记成日常」**(第 10 轮，`records/02` RC-009)：
  「生活习惯自动识别」**不做**。陪伴者不该替用户宣布「我把你的生活记下来了」，
  锚点只能由用户在起点卡上亲手点出来。
- **起点目录的覆盖范围被限定在已识别的兴趣主题内**(第 10 轮，`records/02` RC-009)：
  `StarterOptions` 只有 13 个主题各若干条起点，识别不到的兴趣**不出卡片**，
  也不退回「通用建议」——宁可不说，也不给一条与用户无关的话。

### Fixed

- **设备侧用例被应用进程自己的采集器污染，导致一条断言失败**(第 7 轮)。
  首次重跑 `connectedDebugAndroidTest` 时 `RhythmCollectorTest > 屏幕开关广播真的能从系统收到`
  失败：`expected:<[2026-03-10T09:00:12.345]> but was:<[…12.345, 2026-11-21T00:12:34.765,
  2026-11-21T00:12:34.833]>`。原因是仪器化用例与应用**同进程**，`ElderApp.onCreate` 已按
  用户设置挂上了进程级采集器，而用例里的 `input keyevent 26` 是发给**系统**的，
  两个采集器同时收到同一广播，一个用注入时钟写、一个用真实时钟写。
  处理：`setUp()` 里先 `(context.applicationContext as ElderApp).container.rhythmCollector.stop()`，
  把写库的人收敛成一个。**这不是产品缺陷**(真机上只有一个采集器)，是测试隔离问题；
  修完 42/42 绿。详见 `records/04` 第 7.2 节。
- **`records/CHANGELOG.md` 的 `Unverified` 一节有重复段**(第 7 轮顺手清理)：
  「30,117,942 字节那个 APK」与「作息画像的实际价值」两条各出现了两遍，
  是上一轮追加时的粘贴重复。本轮删掉重复的一份，内容一字未改。
- **标题在「更大」档不放大**(第 3 轮)：≥30sp 的 style 此前不跟档位变化，已修复。
  修复后标题、小标题、正文三档都逐级真实放大，实测容差 3% 内通过。
- **对平台做了不成立的线性假设**(第 3 轮)：仪器化测试原先断言
  「系统字号 1.5 × 应用内 1.3 线性相乘」，实测系统 1.5 只兑现 1.309×。
  这是**断言写错了**而非代码错。已改写为断言「不抹平系统设置」与「应用档位仍显著放大」，
  并把实测倍率打印归档。
- **引导第 3 页的次按钮被裁掉，只剩下 14px**(第 6 轮)。修复前实测次按钮
  `(53,2386)–(1027,2400)`，屏幕高 2400，等于用户看不见它——一个「点不到」的按钮
  比没有按钮更糟。处理：删掉第一张卡片的 `QuietNote`（含义并入末尾那句，省约 189px）
  + 去掉主按钮的 `Modifier.padding(top = ElderDimens.gapSm)`（省 21px）。
  修复后主按钮 `(53,2008)–(1027,2176)`、次按钮 `(53,2218)–(1027,2386)`，
  两个都完整在屏内。数字见 `records/04` 第 6.5 节。

- **设备侧用例把产品库删空，导致 `SELECT * FROM wish` 崩**(第 8 轮)。
  重跑 `connectedDebugAndroidTest` 时 `TalkInputTest > 点了先不用之后不再问但用户自己还能改主意`
  崩溃：`android.database.sqlite.SQLiteException: no such table: wish (code 1 SQLITE_ERROR)`，
  `Tests 63/71 completed` / `Test run failed to complete`，崩溃栈为
  `WishDao_Impl$10.call` → `CoroutinesRoom$Companion$createFlow$1$1$1.invokeSuspend`。
  原因是仪器化用例与应用**同进程**，测试进程里 `AppContainer` 的 Room 实例长期存活，
  而四个数据用例轮流 `deleteDatabase("elder.db")`，把同一个文件删空重建。
  处理：`ElderDatabase.open(context, name = FILE_NAME)` 与
  `AppContainer(context, databaseName = ElderDatabase.FILE_NAME)` 各加一个**默认参数**
  （唯一的生产调用点 `ElderApp.kt:24` 未改），各测试自带库名
  （`elder-test-wish-repository.db` 等 5 个）。改后 71/71 绿。
  **不是产品缺陷**，是测试隔离问题；SQLite 的逐帧时序**未取证**，
  只定位到「共用库文件」这条路径。详见 `records/04` 第 8.2 节、`records/03` ADR-012。
- **对话框里的按钮点不动，因为它在另一个窗口**(第 8 轮)。
  `TalkInputTest > 全部清空要先问一次再真的清掉` 失败于
  `java.lang.AssertionError: Semantic Node has no parent layout with a Scroll SemanticsAction`
  （`ActionsKt.scrollToNode` ← `tap(TalkInputTest.kt:79)`）。`AlertDialog` 不挂在页面的
  滚动祖先下，`performScrollTo` 必然失败。处理：新增 `tapInDialog()`，对话框内的点击
  不走 `performScrollTo`。改后绿。
- **一条用例断言写反了**(第 8 轮)。`WishDaoTest > 同一件事记两次只留一条` 起初失败，
  是**测试笔误**：`INSERT OR IGNORE` 撞唯一索引本来就返回 `-1`，用例却期望第二次也 `> 0`。
  改为断言「第一次 `rowId > 0`、第二次恰为 `-1`、`count() == 1`」后绿。产品代码未改。
- **第 9 轮没有用户可感知的缺陷修复，这一节刻意留空**(第 9 轮)。
  本轮 `testDebugUnitTest` 首次运行即全绿(212/212)，设备侧 75/75，`assembleDebug` 通过，
  手工复核 10 步零崩溃(`logcat -b crash -d` 无输出)。只有在 `compileDebugKotlin` 阶段
  修过两处**编译期**错误，它们是写的时候就没写对，**不是已发布行为的缺陷**：
  (1) `InterventionStore` 里 `prefs[Long键] = nullable Long` 不被允许 —— 改成
  `val spoken = next.lastSpokenAt; if (spoken != null) prefs[KEY_LAST_SPOKEN_AT] = spoken.toEpochMilli() else prefs.remove(KEY_LAST_SPOKEN_AT)`
  （`last_answered_at` 同理）；
  (2) `SettingsScreen.kt:303 Unresolved reference 'wakeSleepSummary'` —— 补 import。
  把「本轮没有真实 Fixed」写在这里，是为了让「Fixed 是空的」看上去像结论而不像遗漏。

- **`MigrationTest` 的旧用例绕过了真实开库路径**(第 10 轮)。旧用例用手拼的
  `Room.databaseBuilder` 开库，没有注册 `ElderDatabase.open` 里的迁移，于是它**守不住**
  应用真实走的那条路；本轮把库版本升到 3 时才暴露成
  `A migration from 1 to 3 was required but not found`。处理：两条跨版本的用例改用
  产品自己的 `ElderDatabase.open(context, name = FILE_NAME)`。**这是第 9 轮用例的缺陷，
  不是已发布行为的缺陷**——产品代码本来就是对的。
- **一条设备用例的兴趣选项写成了不存在的 `chess-square`**(第 10 轮)。
  `StarterOptions` 对未知 `optionId` 返回 null，用例随即 NPE。改成真实存在的 `chess-solo`。
  同样是**测试写错**，产品行为无误。
- **第 10 轮没有已发布行为的缺陷**。本轮第一次跑设备测试是 2 条红(见上两条)，
  修完全绿；`logcat -b crash -d` 无输出。写在这里同样是让「Fixed 里只有测试缺陷」
  看上去像结论而不像遗漏。

### Known Issues

1. **系统字号调大后，应用内档位区分度明显下降**(第 3 轮实测，API 35)。
   系统字号 1.5 只兑现 **1.309×**；在其之上再选应用内「更大」只额外兑现 **1.100×**(标称 1.30×)。
   「大一点」档在系统 1.5 下尤其接近无效。
   这是平台的非线性字体换算造成的，**刻意不干预**(掰直等于覆盖用户手机的系统级设置，
   属可访问性反模式)。缓解：默认档本身就是大字号(正文 20sp，Material 默认 16sp)。
   详见 `records/03` ADR-004、`records/02` RC-002。
2. 引导页第二页在 1080×2400、系统字号 1.0 下主按钮需滚动才完整可见(页面偏长)。
   **这是滚动，不是裁切**——`ElderScreen` 是 `verticalScroll`，滑一下就能点到
   (实测下滑后主按钮 `(53,1875)–(1027,2043)`，高 168px = 64dp)。
   与第 2 条并列存在的是：**第 3 页曾真的被裁切，已在第 6 轮修掉**(见 Fixed 末条)。
3. 设置页「已保存」提示停留 2 秒，对 55 岁以上用户可能偏短，尚未用目标用户验证。
4. **作息推断的全部阈值都是推导值**(第 4 轮)。「每天至少 20 条在场信号」「至少 3 天」
   「安静密度 < 均值 × 0.35」「离起床/睡前 45 分钟内不报空窗」这些数字是从需求推出来的，
   **没有任何真实用户数据支撑**，也没有在真机上跑过真实信号。
   它们保证的是「宁可不说，也不要猜错」，不代表贴合目标用户的真实使用频率。
   详见 `records/04` 第 4.5 节。
5. **采集只覆盖进程存活期间，事件的丢失量没有量化**(第 6 轮，取代第 5 轮那条「条数恒为 0」)。
   第 5 轮记的「还没有东西往里写」本轮已经不成立：设置页实测能显示「已记录 4 条。」
   （设备上开关关-开-关逐次核对，见 `records/04` 第 6.6 节）。但采集器**只在运行时注册广播**
   （`records/03` ADR-009），进程被杀或手机关机期间的事件收不到，
   而**这部分丢了多少没有任何数据**。这是设计选择带来的已知代价，不是缺陷。
6. **30 天保留窗口的清理有了宿主，但只挂在启动路径上**(第 6 轮，取代第 5 轮那条「没有宿主」)。
   `RhythmMaintenance.pruneIfDue()` 以 `lastPruneEpochDay` 为闸，同一天只清一次，
   由 `AppContainer.startProcessWork()` 在冷启动时调。**真机跨天路径未验证**
   （只在仪器化测试里用合成时钟拨表跑过）——设备上跑不到「第二天」。
7. **`CapturedSignal` 现在有生产者了**(第 6 轮，取代第 5 轮那条「没有生产者」)。
   `data/rhythm/RhythmCollector` 从系统广播构造它并落库。第 5 轮把它记成待办是正确的，
   本轮已关闭。
8. **引导页在 composition scope 里写盘**(第 5 轮记下的隐患，本轮扩大到第 3 页)。
   `OnboardingScreen.kt` 用的是 `rememberCoroutineScope()`，composition 被销毁时写入可能被取消。
   第 3 页新增的 `collectRhythm` 写入走同一条路径。两轮实测都落盘成功（含本轮关-开-关的实测），
   所以**仍然没有改**，列入待观察。
9. **引导第 3 页在大字号下的可点性未验证**(第 6 轮)。AC-04.6 只在**默认字号**下量过像素；
   系统字号 1.5 / 应用内「更大」档下两个按钮是否仍同屏，**没有测**。
   考虑到第 2 条的既有结论（大字号下页面变长），这里有真实风险。
10. **意愿识别的词表召回天生长不全，这是设计选择而不是缺陷**(第 7 轮)。
    `IntentLexicon` 是手写词表（19 个兴趣提示词 / 13 个兴趣主题 / 7 个状态短语 / 2 个请求短语），
    刻意收窄的代价是**说漏**：换一个说法（比如「练练」「活动活动」）就识别不到。
    「宁可漏，不可猜错」是 FR-06 的口径（AC-06.2），所以这个代价是第 7 轮主动接受的；
    但它意味着**真人的自然说话方式能覆盖多少，我没有数据**。
11. **裸「想」与裸「别」都在词表外，带来两类已知的边界行为**(第 7 轮)。
    「想」单独不成 cue，所以「我想换个大点的手机」只有主题能命中；
    `negationMarkers` 故意不收裸「别」（口语里常是虚词），
    所以「别的不说，我想学书法」这种句子**会被识别成兴趣**。
    这两条边界都被 `IntentScanTest` 的用例显式钉住了，但它们是不是目标用户真会说的话，未验证。
12. **意愿识别目前是只读的回放，用户还不能自己说话**(第 7 轮)。
    第 7 轮交付的是引擎 + 4 句**固定示例**的回放界面，**没有任何输入框**，
    也没有把识别结果落盘。示例点下去是真的在跑扫描器（不是假功能），
    但「用户自己打一句话」要等第 8 轮；`adb shell input text` 送不了中文(GAP-02)
    也限制了这轮在设备上能验证到什么程度。
13. **中文手工输入仍然做不到**(第 8 轮，**部分取代第 12 条**)。第 8 轮把真输入框、
    识别与落库这条链路补齐了，所以「用户自己说一句话」在**代码上**已经成立；
    但 `adb shell input text` 送不了中文(GAP-02)，设备侧的 `performTextInput` 是
    Compose **直接把字符注入文本框**、不是走真实输入法。也就是说
    「目标用户用中文输入法打字」这条路径**这一轮没有被任何人验证过**。
14. **意愿不设保留期，`wish` 表会一直长**(第 8 轮，`records/02` RC-007)。
    作息数据有 30 天窗口，意愿**没有**——因为「几个月没提起」不代表不重要，
    「上次提到是 3 天前」这种字眼更是催促。代价是这张表只增不减，
    **唯一的出口是用户自己按「不用记了」或「全部清空」**。这是刻意换取的设计代价。
15. **意愿措辞是本项目里约束最弱的一处**(第 8 轮，`records/03` ADR-011)。
    `TalkVoiceTest` 用两张**禁用词表**（提醒/通知/推送/安排/课程/报名/链接/优惠/已为您/我帮您…
    与 加油/坚持/优秀/真棒/应该/必须/要努力/别偷懒/上次/昨天/多久没/好几天）扫描措辞，
    能挡住已列举的词，**挡不住一句新造出来的、同样空洞但没进词表的承诺**，
    也没有人工评审。加词表能挡已知的错，不能证明措辞是对的。
16. **用户不打开应用时，常伴什么都不做**(第 9 轮，`records/02` RC-008、GAP-06)。
    本版本**刻意不建通知通道**，所以 FR-05 的产出只作用于「用户打开应用的那一刻」。
    「早上想被陪一下，但一整天没开应用」这种情形，本轮**没有任何机制**覆盖。
    这是**明确不做**，不是待做项。
17. **最坏情况下一天仍会说一次**(第 9 轮)。退避上限是有意的 24 小时
    （`InterventionConfig.maxGapMinutes = 1440`），目的是防「越不理越不理」的死锁。
    这个值意味着：一个连续不接话的用户，仍会每天被招呼一次。对一个
    「低打扰」产品，这个上限是否偏松，**没有目标用户数据支撑**。
18. **`ignored_streak` 的定义没有真实用户数据验证**(第 9 轮)。当前语义是
    「连续 N 次开口都没被接话」，其中「被接话」只由「说两句」页里**被识别出意愿**
    这一件事触发——用户说了一句没被听懂的话，我们**不**算他回应了
    （`TalkViewModel.hear()` 里 `recordAnswered()` 与 `wishes.remember()` 在同一个分支）。
    这个取舍是否正确，需要真人使用才知道。
19. **首页的锚点行默认看不见**(第 10 轮)。`InterventionVoice` 在安静时段返回
    `StayQuiet(SLEEP_HOURS)`，而锚点行只在 `Speak` 判定下才出现，所以刚记完的日常
    回到首页可能**看不到**——用户只能在「说两句」页里看到它。这是**有意的**
    （陪伴者不主动把「你昨天记的事」摆到眼前，见 `records/03` ADR-014），
    但代价是用户可能以为没记上。真人是否这样理解，**没有验证**。
20. **39 条起点是设计出来的人写字句**(第 10 轮)。它们没有经过目标用户评审，
    也不知道「现在就能做、不用花钱、不用跑一趟」这个筛选标准是否贴近真实生活；
    13 个兴趣主题是否覆盖 55-65 岁人群的常见爱好，同样只是推断。

### Unverified

以下事项**没有**验证过，不得在别处当作已验证引用：

- **「用户用中文输入法打字」这条路径完全未验证**(第 8 轮)。第 8 轮把真输入框建起来了，
  但设备上能走通靠的是 Compose 的 `performTextInput` **把字符直接注入文本框**、
  **不是**走真实输入法；`adb shell input text` 也送不了中文(GAP-02)。
  所以「自由输入下的识别效果」仍然是对着**注入的句子**看的，不是真人打的字。
- **意愿措辞只有单测扫描，没有人工评审**(第 8 轮)。`TalkVoiceTest` 那两张禁用词表
  是**机器扫的**，它能证明「这些话里没有列举过的词」，**不能**代替
  「让一个人读一遍，判断它像不像一个不催促的陪伴者」。见 Known Issues 第 15 条。
- **第 8 轮新增控件的 TalkBack 朗读与焦点顺序未验证**(第 8 轮)。新增了意愿列表项里的
  两个按钮、带二次确认的 `AlertDialog`、真输入框，三者的无障碍焦点顺序与朗读内容都没测
  （第 6 轮那个整行开关、第 7 轮首页入口也仍然没测）。
- **用户还不能自己打字说话，所以「自由输入下的识别效果」完全未验证**(第 7 轮)。
  第 7 轮只做了**固定示例**的回放；`adb shell input text` 送不了中文(GAP-02)，
  连「手工敲一句中文进去看识别结果」这件事在设备上都做不到。
  `IntentScanTest` 的 29 例是**对着合成句子**跑的，真人措辞未验。
- **「说两句」页在系统字号 1.5 / 应用内「更大」档下的观感未验证**(第 7 轮)。
  证据行本身较长（实测「是从「一直想学书法」这几个字听出来的。」占 `(166,1937)–(974,2095)`），
  大字号下是否换行压住别的卡片**没有测**；这是 Known Issues 第 9 条同类问题的第 7 轮版本。
- **第 7 轮新增的首页入口、示例按钮与结果卡的 TalkBack 朗读未验证**(第 7 轮)。
  新增了首页整行可点的「跟常伴说两句」入口、4 个示例按钮、结果卡里的证据文案，
  三者的无障碍焦点顺序与朗读内容都没测（第 6 轮那个整行开关也仍然没测）。
- 真机(非模拟器)上的字号渲染与整体观感。
- 系统字号**真实**设为 1.5(通过系统设置界面)时的观感——目前只在仪器化测试里
  用合成的 `density` 验证过行为，没有在真实系统设置下看过界面。
- 性能基线：启动耗时、滚动帧率、内存占用。**第 6 轮给它加了新负担**
  （Room 开库 + 广播注册 + 一次异步清理），所以这条比第 5 轮更值得测，归入 GAP-03。
- TalkBack 与无障碍焦点顺序。第 6 轮新增的整行 `toggleable` 开关**没有做过无障碍验证**。
- 目标用户(55-65 岁刚退休人群)可用性走查——尚无真人参与。
- 本机 `adb` 无法输入中文，所以 UI 层链路用 ASCII 称呼走通；
  中文称呼的持久化由 `SettingsStoreTest` 覆盖，**UI 层未验证中文输入**。
- **作息引擎吃到的仍是模拟器上「按电源键」产生的信号，不是真实生活节奏**（第 6 轮更新）。
  第 5 轮记的「采集器还不存在、引擎从未处理过真实信号」本轮已经不成立——
  设备上实测「已记录 4 条。」，采集链路是通的。但**这些信号是测试动作造出来的**，
  不能代表一个真人一天里自然的亮屏/解锁分布，所以
  「作息推断在真实数据上准不准」**依然未验证**，`RhythmConfig` 的全部阈值仍是推导值。
- **进程被杀 / 关机期间的事件丢失量未量化**(第 6 轮)。ADR-009 选了只运行时注册广播，
  代价是采集不连续，但这个代价有多大**没有数据**；这也意味着作息画像的样本量
  低于「常驻采集」的方案。
- **`RhythmMaintenance.pruneIfDue()` 的真机跨天路径未验证**(第 6 轮)。
  只在仪器化测试里用合成时钟拨表跑过，设备上跑不到「第二天」；
  30 天保留窗口的**真实清理仍未在设备上长期观察过**。
- **引导第 3 页在大字号下的两个按钮是否仍同屏未验证**(第 6 轮，见 Known Issues 第 9 条)。
- **Room 首次开库对冷启动耗时的影响未测**(第 5 轮引入的读库行为，第 6 轮又接了采集器)。
- **「用户改了时区」这件事没有在设备上真的做过**(第 5 轮)。时区换算只在 `RhythmMapping`
  一处实现，但这不代表它被验证过。
- **手动注入系统广播这条路走不通，已实测证伪**(第 6 轮)：
  `adb shell am broadcast -a android.intent.action.SCREEN_ON` 被
  `SecurityException: Permission Denial: not allowed to send broadcast ... from uid=2000` 拒绝。
  这不是「未验证」，而是一条**已验证的约束**——记在这里是为了防止下一轮再试一次。
- **30,117,942 字节那个 APK 无法复现**(第 5 轮)。结论是「压缩层偶发异常」，
  证据是两者未压缩载荷逐字节一致；但**它为什么会出现，我没有查到根因**。
- 作息画像的实际价值(识别出的空窗是不是用户真的「没事做」)**未验证**，需要真人参与。
- `RhythmInference.infer()` 的耗时**未测**。
- **退避增长在真设备上没有被观察到**(第 9 轮)。设备上 `ignored_streak` 始终是 0
  （唯一的评论者是我自己，每次都「接话」了），两次打开之间的间隔也一直落在 30 分钟里。
  要让计数涨上去需要「开过口 → 用户没接话 → 又开了一次」这一串，真机上**没有构造**。
  JVM 侧由 `ThrottleStateTest`(11) 与 `InterventionWindowTest.退避序列与上限` 守着，
  但「JVM 守住了」不等于「设备上真的这样」。
- **有画像时的设置页样子没有取证**(第 9 轮)。设备上的画像一直是 `Gathering`
  （装完不到一小时，信号不够），所以「说话的时机」卡片里显示画像摘要（`wakeSleepSummary`）
  的那条路径**设备上没有走到**，没截图也没 dump。
- **`RhythmProfileService` 的跨天行为未验证**(第 9 轮)。设备时区在取证过程中
  在 GMT 与 Asia/Shanghai 之间来回改过，但没有专门构造跨天边界。
- **「杀进程 → 重新打开 → 状态恢复」只走了一条粗路径**(第 9 轮)。
  `am force-stop` + `am start` 后首页确实仍显示「先不重复招呼了。」，
  但系统级的「低内存回收」「重启设备」这两种情形**没有模拟过**。
- **第 8 轮那处「说两句页下半部分示例句点不动」仍未查明**(第 9 轮续)。
  本轮走的是输入框那条路，没有再点示例句，**没有新增线索，也没有排除真机同样存在**。
- **`InterventionVoice` 的措辞只有禁用词扫描，没有人工评审**(第 9 轮，同第 15 条)。
  `noStringPromisesNotificationsOrScheduling` 与 `noStringJudgesTheUserOrPressuresThem`
  能挡住已列举的词，挡不住一句新造的、没进词表的话。
- **`InterventionConfig` 的全部阈值仍是推导值**(第 9 轮，同第 4 条)。
  `minGapMinutes=30` / `backoffStepMinutes=180` / `maxGapMinutes=1440` /
  `fallbackWakeAt=07:00` / `fallbackSleepAt=22:30` 五个值都没有真实用户数据支撑。
- **`assembleDebug` 本轮没有被真正重跑**(第 10 轮)。`./gradlew :app:assembleDebug`
  输出 `BUILD SUCCESSFUL in 1s`，38 个任务**全部 UP-TO-DATE**。
  「能构建」这件事本轮只是**没被推翻**，不是被重新证明；APK 体积 11,012,408 字节是实测的。
- **起点卡的观感与大字号下的排版未验证**(第 10 轮)。走查时设备是默认字号；
  39 条起点的卡片、以及「把这一步记成日常」的四个时段按钮，在系统字号 1.5 下
  是否仍同屏、是否换行压字，**没有看**。
- **锚点卡的 `dueNow` 只有合成时钟覆盖**(第 10 轮)。JVM 侧由 `AnchorRulesTest` 守
  「哪个时段该出现」，设备上**没有真的等到那个时段**再打开应用，所以
  「到了上午，首页锚点行真的会说话吗」这条路径**设备上没走到**。
- **`AnchorRepositoryTest` 是设备侧的 6 例，`AnchorDaoTest` 7 例**——两者都在
  模拟器的 API 35 上跑过，但**没有在 API 26(最低支持版本)上跑过**；
  唯一索引 `index_anchor_optionId` 在老版本 SQLite 上的行为未验证。
- **`git push` 已实测成功，但它只证明「推上去了」**(第 10 轮)。推送前先查：
  远端**不是空的**（`8286376 Initial commit`，只有 `README.md`），
  `git merge-base` 失败说明两侧是**不相关历史**，所以先
  `git rebase origin/main` 把 8 个提交重放上去，再推：
  `8286376..1afc687  main -> main`，退出码 0；
  推送后 `git ls-remote origin refs/heads/main` 与本地 `git rev-parse main` 逐字符相同。
  **没有验证**的是：仓库是 public 还是 private、别人克隆后能不能构建、有没有配 CI。
  （副作用：rebase 改写了提交号，第 1~9 轮记录里的短号已失效，见 `records/01` 第 10 轮补记。）
- **禁用词扫描是朴素子串扫描**(第 10 轮续，同第 15 条)。本轮新增了
  `Encouragement.kt` 的三张词表与 `CompanionVoice.kt` 的全部文案，
  它们只被同一套子串扫描守过，**没有人工评审**。
