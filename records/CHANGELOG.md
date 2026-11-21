# 变更日志

格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循语义化版本。本文件记录**用户可感知的变化**，逐轮追加。

`Unverified`(未验证)一节是刻意保留的：**没验证的事不准当成已验证的写**。

---

## [Unreleased] — 0.1.0 开发中

### Added

- **「现在」成了 `AppContainer` 的第四个口子**（第 31 轮）：容器那句「所有依赖都在这里显式声明，
  否则引擎就无法在测试里被替换」一直有个例外——`clock` 写死成 `SystemAppClock`，谁都换不掉。
  JVM 侧的用例不受影响（它们自己 `new` 引擎、自己传时钟），但**屏幕级用例是从容器往下取的**，
  于是「这会儿该说什么」只能按跑测试的钟点碰运气；上一轮 `SettingsScreenTest` 里那条安静时段
  用例就是绕开它写的（往库里摆一个把「现在」裹住的画像，见 `records/03` ADR-026）。本轮收下这个
  参数：`clock: AppClock = SystemAppClock`（`app/src/main/java/com/fortheelder/core/AppContainer.kt:63`）、
  `val clock: AppClock = clock`（`:74`），KDoc 补三段（`:49-56`）。**默认值就是应用自用的那一份**，
  `ElderApp.kt` 那句 `AppContainer(this)` 一个字没动 ⇒ 正式落盘路径与运行时行为**净变化为零**。
  取舍写进了 `records/03` **ADR-029**（声明为 **ADR-026 的部分取代**：不是当时判错，而是口子的
  成本结构变了——容器已收过三个同形的口子，第四个只剩「多一个参数」的代价，而关着它要一直付
  「首页那句问候语在设备上一次都没被验过」的账）。
- **首页那句问候语第一次在设备上被读到**（第 31 轮，本轮的核心证据）：新建
  `app/src/androidTest/java/com/fortheelder/ui/home/HomeScreenTest.kt`（225 行，4 例）——
  在此之前 `GreetingEngine` / `InterventionVoice` 的屏幕级输出**一次都没有**在真实设备上被断言过。
  骨架照抄 `SettingsScreenTest`（`createComposeRule()` 不挂 `@get:Rule`，
  `RuleChain.outerRule(关库排在组合之后(TEST_DB) { container }).around(compose)`；
  `TEST_DB = "elder-test-home-screen.db"`；`@Before` 里 `deleteDatabase` → `自己的存储`
  → 五参数 `AppContainer(…, clock = AppClock { now })` → `completeOnboarding()`）。
  四例的 `now` 一律定死在 2026-03-10：

  | 用例 | `now` | 前置 | 断言 |
  | --- | --- | --- | --- |
  | `早上九点首页那句话带刚存下的称呼` | 09:00 | `setAddress("老熊")` + `ensureFirstLaunchRecorded()` | 「老熊，上午好」1；「第一次见面，慢慢来。」+「说话的时机，我先按普通的作息来。」1；`HomeVoice.askAddress` 0 |
  | `换个钟点那句话就跟着换` | 14:30 | 与上一条**逐项相同** | 「老熊，下午好」1；「老熊，上午好」0 |
  | `深夜首页不迎上来说话也不冒出称呼` | 23:30 | 只 `setAddress("老熊")` | 「这会儿是您休息的时间。」1；任何含「老熊」的节点 0；`askAddress` 0 |
  | `还没填称呼时首页问一次而且那句话不带名字` | 09:00 | 无 | `askAddress` 1；「上午好」1；「老熊，上午好」0 |

  **第 1、2 条只差时钟**（同一份库、同一份存储、同一串前置动作、同一天同一台设备）⇒ 这个口子
  是真的在起作用，不是「换个写法而已」。背景常量：`InterventionConfig` 默认 `fallbackWakeAt=07:00` /
  `fallbackSleepAt=22:30` ⇒ 没有画像时安静时段是 22:30–07:00；`DayPart.of` 把 9..11 归上午、
  14..17 归下午、其余归深夜；`AppClock` 是 `fun interface { fun now(): LocalDateTime }`，
  `object SystemAppClock` 是它的生产实现。
- **`SettingsScreenTest` 一行断言未动**（第 31 轮）：本轮只改它**两处做法注释**（「四处必须交代的
  做法」第 1、2 条），把「屏幕级固定不了时钟」那句改成「现在能固定了」。370 行里断言的条数与内容
  **一个都没变** ⇒ L3 从 132 例涨到 137 例**全部来自新类**，不是靠改老用例凑数。
- **四层实测数字**（第 31 轮，四层全跑）：L1 `:app:testDebugUnitTest` **31 类 / 390 例 / 0 失败**
  （`BUILD SUCCESSFUL in 7s`、`26 actionable tasks: 7 executed, 19 up-to-date`）；L2
  `:app:assembleDebug` **真的重新打包**（`BUILD SUCCESSFUL in 2s`、`38 actionable tasks: 3 executed,
  35 up-to-date`），产物 `app/build/outputs/apk/debug/app-debug.apk` **11,090,224 字节 / md5
  `5b60eedf44b73a2fc5b5698a0e10d540`**；L3 `:app:connectedDebugAndroidTest` **20 类 / 137 例 /
  0 失败**（`Starting 137 tests on Elder_API35(AVD) - 15` → `Finished 137 tests`、
  `BUILD SUCCESSFUL in 2m 9s`、`EXIT=0`，比第 30 轮多 4 例，正是新类那 4 例），且**第一次跑就全绿**
  ——第 30 轮那个 `Process crashed`（首跑崩在 119/133 处）**本轮没有重演**；L4 装机走查沿用
  `build/probe30.py` **`RESULT=PASS`**（655 行 / 128 条 `OK` / 0 条 `NOTE` / 0 条 `!!`、11 张截图）。
- **`docs/03` 新增 §7.12「第 31 轮：给容器开第四个口子（「现在」），首页那句问候语第一次在设备上
  被读到」**（第 31 轮）：插在 §7.11 与 `## 8. 未验证项与缺口` 之间，含 `:63 / :74 / :49-56` 的位点表、
  四例对照表、代价三条与三条证据链。
- **`docs/01` 更新 AC-13.1 的「当前证据」列**（第 31 轮）：AC-13.1 / AC-13.2 是**同一行内只替换
  证据列**，编号与「标准」列**一字未动**；`records/02` 因而不写（本轮无需求变更）。
- **`docs/06` 新增 §3.10「第 31 轮追加实测（L1 / L2 / L3 / L4 四层数字）」**（第 31 轮）：
  插在 §3.9 末尾与 `## 4. L2 构建与打包` 之间，并在**修订记录**表尾追加一行。
- **两笔工作区证据被本轮覆盖，照实记**（第 31 轮）：`build/screens30.log` 与 `build/r30/` 的
  11 张截图。原因在于 `build/probe30.py` 里 `LOG_PATH = sys.argv[1] if len(sys.argv) > 1 else
  "build/screens30.log"`——本轮只重定向了 stdout，脚本自己那份**写回了第 30 轮的文件名**。
  细节见下面 Unverified 段。

- **「称呼已保存。」不再自己走掉**（第 30 轮）：第 27 轮给保存称呼加的回执是一句只挂 **2 秒**的话
  （`SAVED_HINT_MILLIS` + `LaunchedEffect` + `delay`），当时就在第 27 / 29 轮的记录里写着「这是**降级**
  不是**解决**」——一句给 55-65 岁用户看的话，不该由计时器决定它在不在屏上。本轮把计时器整段删掉，
  改成**两个回执槽位**：`var addressReceipt` / `var fontReceipt`
  （`app/src/main/java/com/fortheelder/ui/settings/SettingsScreen.kt:74-75`）。
  保存成功 → `addressReceipt = SettingsVoice.addressSaved`（`:97`）；**在输入框里再打一个字**
  → `addressReceipt = null`（`:86`）——这一行就是「下一次动手就撤」的全部实现，两头都由**用户动作**
  驱动，没有一个由时间驱动。
- **字号回执挪回它自己那张卡**（第 30 轮，**真实用户可见的错位修复**）：第 28 轮把字号回执
  写进了**称呼卡**的收尾段，于是点「更大」之后「字号已改成「更大」。」出现在称呼卡尾注上方，
  和「点一下立刻生效，不用按确定。」「留空就只问候，不带称呼。」挤在一起。本轮点字号改成
  `fontReceipt = SettingsVoice.fontSizeChanged(option.label)`（`:121`），渲染位置移到**字号卡内部**
  （三档按钮下面、字号卡尾注上面，`:140-141`）。两处回执共用一个新抽出的小组合体
  `private fun ChangeReceipt(text: String)`（`:196-203`）。
- **一条能证明「字号回执真的搬了家」的实测**（第 30 轮）：走查里点第 3 档「更大」之后，同一次
  `uiautomator dump` 里 `字的大小` 那一行的文字高度 **80 px → 103 px**（1.00 → 1.30，与
  `FontSize.EXTRA = 1.30f` 相符）；回执 `字号已改成「更大」。` 出现 **1** 次，其**顶边 1852**
  **同时大于** 字号卡提示语顶边 1287、第 3 档按钮底边 1809、称呼卡尾注顶边 933，**小于**字号卡尾注顶边
  1966 ⇒ 它在字号卡内部、在三档按钮**下面**、在字号卡尾注**上面**，**不在**称呼卡里；
  同屏「称呼已保存。」**0 个**。`font-size-back-to-standard` 那一步：`字号已改成「标准」。` 1 个、
  `字号已改成「更大」。` 0 个（换一档就换掉，不是叠加）。
- **一条真负例：「下一次动手就撤」被挖掉就必红**（第 30 轮）：把 `SettingsScreen.kt:86` 的
  `addressReceipt = null` 换成一行注释 `// MUTATION-30:` 后重打包——变异体 APK **11,052,805 字节 /
  md5 `bb5c58cd04979e1f6a3f4cbb575fd72f`**（与真包 11,052,832 字节 / `a281a1f7…` **字节数不同**，
  说明变异确实进了包）——只跑 `SettingsScreenTest`：**`Tests run: 8, Failures: 1`**，红的**恰好**是
  本轮新加的 `再打一个字回执就撤而且没有偷偷落库`，`AssertionError: 再动一次手之后回执就该撤了
  expected:<0> but was:<1>` at `SettingsScreenTest.kt:184`；另 7 例全绿。用完还原，源文件 md5 回到
  `d8af513aeb6f99ddf3c08776266ef063`、`grep -c MUTATION-30` = 0、重打包后**与变异前逐字节一致**。
- **走查脚本发现的一件事实：`uiautomator dump` 的可见性会骗人**（第 30 轮）：首跑走查停在
  `!! 屏上没有与 '更大' 整串相等的节点`——点开设置页之后**软键盘遮住下半屏**，而 `uiautomator dump`
  **只报可见节点**，三档字号按钮那时根本不在 dump 里。修法：dump 之前先
  `adb shell input keyevent KEYCODE_BACK` + `sleep 1.0`，再重新 dump。加完三档才出现、走查通过。
  走查里另有一条 `OK 收键盘那一下 BACK 只关了输入法，没有把设置页也一起关掉`。
- **`docs/03` 新增 §7.11「第 30 轮：回执不设倒计时，各住自己那张卡」**（第 30 轮）：
  插在 §7.10 与 `## 8. 未验证项与缺口` 之间，含 `:86 / :97 / :103-104 / :121 / :140-141 / :196-203`
  的位点表、代价三条、证据甲（坐标 / 高度表）、证据乙（变异实验全文）、证据丙（软键盘遮挡）。
- **`docs/06` 新增 §3.9「第 30 轮追加实测（L1 / L2 / L3 / L4 四层数字）」**（第 30 轮）：
  插在 §3.8 末尾的 `---` 与 `## 4. L2 构建与打包` 之间，并在**修订记录**表尾追加一行；
  同时把 §3.8 那条 `NOTE`（「抓到了那句只挂 2 秒的…」）行尾补上「**第 30 轮更正**：那句文案连同它的
  2 秒计时器已整体下线」，**第 29 轮那句话保留不删**。
- **四层实测数字**（第 30 轮，四层全跑）：L1 `:app:testDebugUnitTest --rerun` **31 类 / 390 例 / 0 失败**；
  L2 `:app:assembleDebug` **真的重新打包**（`3 executed, 35 up-to-date`）、产物 11,052,832 字节 /
  md5 `a281a1f7404711f7f5cdc2a48cfd9d06`；L3 `:app:connectedDebugAndroidTest` **19 类 / 133 例 / 0 失败**
  （`Starting 133 tests` → `Finished 133 tests`、`BUILD SUCCESSFUL in 1m 52s`，比第 29 轮多 1 例，
  正是本轮新加的那一例）——**⚠ 这是重跑的数字**，同一颗包**首跑**在 `119/133` 处因
  `Process crashed` 而 `BUILD FAILED`，见下面 Unverified 段；L4 装机走查 `build/probe30.py`
  **`RESULT=PASS`**（656 行 / 128 条 `OK` / 0 条 `NOTE` / 0 条 `!!`、11 张截图）。
- **`docs/09` 用户手册与 `docs/07` 风险项同步**（第 30 轮）：手册里「停 2 秒…然后就没了」改成
  「不自己走掉、在框里再动一下就撤」，§5.2 补一段讲字号回执在三行按钮**下面**、再点另一档就**换掉**；
  风险项**方向反转**成「回执不回撤算不算啰嗦」（`docs/07:257`，两头都未用目标用户验证）。

- **`AppContainer` 给两份偏好多开一个测试口子**（第 29 轮）：原来的签名只给数据库开了口
  （`databaseName`），`settings` 与 `intervention` 都是 `val`、直接取 `this` 上的那两份 `preferencesDataStore`
  （`SettingsDataStore.kt` 14 行、`InterventionStore.kt:26`，`name = "settings"` / `name = "intervention"`）。
  本轮把两个 `val` 改成构造参数，带默认值：
  `class AppContainer(context, databaseName = …, settings = context.settingsDataStore,
  intervention = context.interventionDataStore)` —— **默认值就是原来那两行取到的对象，落盘路径一行未改**，
  所以 `ElderApp.kt:24` 的 `AppContainer(this)` 与生产行为完全不变。全仓 `AppContainer(` 一共 6 处：
  5 个测试类 + 那 1 处生产调用。
- **`TestTeardown.自己的存储(context, 标签)`**（第 29 轮）：
  `app/src/androidTest/java/com/fortheelder/ui/TestTeardown.kt` 新增这个小工厂，用
  `PreferenceDataStoreFactory.create(scope = …, produceFile = { File(context.cacheDir, "<标签>.preferences_pb") })`
  造一份**只在 `cacheDir` 里**的 DataStore，两个标签各一份（设置 / 节流）。
  ⇒ 5 个屏幕级用例（`ui/a11y/A11ySemanticsTest.kt`、`ui/play/PlayScreenTest.kt`、
  `ui/settings/SettingsScreenTest.kt`、`ui/talk/TalkInputTest.kt`、`ui/talk/TalkStarterTest.kt`）此后
  **不再读写这台设备上应用真正的那两份偏好文件**。
- **一条能证明「隔离真的生效」的对照实验**（第 29 轮）：见本 CHANGELOG 的 Unverified 段与
  `records/04` 第 28.3 节——改动生效**前**，设备真实 `settings.preferences_pb` 跑前 143 字节 /
  md5 `5e06c361e629cacdfb4564baaa655d1a`，跑完 `SettingsScreenTest` 之后变成 **0 字节** /
  md5 `d41d8cd98f00b204e9800998ecf8427e`（空文件标准 md5）⇒ 用例**确实踩到了**真实文件；
  改动生效**后**同一条流水线再跑，两份文件跑前跑后**逐字节没动**。
- **`docs/03` 新增 §7.10「第 29 轮：偏好也开一个测试口子，收的是 DataStore 实例而不是文件名」**（第 29 轮）：
  插在 §7.9 末尾的 `---` 与 `## 8. 未验证项与缺口` 之间。
- **`docs/06` 新增 §3.8「第 29 轮追加实测（L1 / L2 / L3 / L4 四层数字）」**（第 29 轮）：
  插在 §3.7 末尾与 `## 4. L2 构建与打包` 之间（七行表 + 五处如实交代），并在**修订记录**表尾追加一行；
  同时把 §3.7 第 4 点那条「隔离缺口」行尾补上「第 29 轮更正：已在第 29 轮补上」的指向，
  **第 28 轮那句话保留不删**。
- **四层实测数字**（第 29 轮，四层全跑）：L1 `:app:testDebugUnitTest --rerun` **31 类 / 390 例 / 0 失败**；
  L2 `:app:assembleDebug` **38 actions 全 up-to-date**、产物 11,052,574 字节 /
  md5 `8361827a1a4b85e4d818080a83c60938`；L3 `:app:connectedDebugAndroidTest` **19 类 / 132 例 / 0 失败**
  （`Starting 132 tests` → `Finished 132 tests`、`BUILD SUCCESSFUL in 1m 51s`）；
  L4 装机走查 `build/probe29.py` **`RESULT=PASS`**（567 行 / 113 条 `OK` / 1 条 `NOTE` / 0 条 `!!`、
  crash log 0 行、10 张截图）。

- **仓库里第一条把 `AppRoot` 整棵渲出来的屏幕级用例**（第 28 轮）：新建
  `app/src/androidTest/java/com/fortheelder/ui/settings/SettingsScreenTest.kt`（299 行、**7 例**）。
  此前 **18 个屏幕级用例类都从 `MainNav` 或单独某一页起渲**，于是「应用一打开先看引导页、引导走过之后才进主界面」
  这条**最外层的分支**、以及 `settings.onboardingDone` 这个开关，**从来没有被渲染级验证过**。本类的
  `show()` 就是 `compose.setContent { AppRoot(container = container) }`；`@Before` 用
  `resetToDefaults()` + `completeOnboarding()` + `setFontSize(STANDARD)` 摆一个「引导已走过」的起点。
  七例覆盖 `docs/01` §4.13 的四条 AC：改称呼回首页不再问称呼 / 安静时段首页换安静语而称呼不冒出来 /
  点「更大」字号立刻落库 / 设置页显示 `BuildConfig.VERSION_NAME` / 清空设置先问一次点「不清」什么都没发生 /
  确认清空之后**回到引导页** / 清掉作息数据那一行立刻变成零。**`app/src/main` 一行未改。**
- **两条只能在这个高度验的承诺**（第 28 轮）：① 改完称呼回到首页，首页**立刻不再问**「怎么称呼您」（AC-13.1）；
  ② 确认「清空设置」之后真的**回到引导页**，而不是停在原地（AC-13.4）。这两条在别的 18 个类里都验不了——
  它们都从 `MainNav` 起渲，跳过了「引导走过没有」这一步。
- **`AppContainer.clock` 写死系统钟，屏幕级用例固定不了时钟**（第 28 轮）：所以本轮的绕过办法是**把这条限制
  本身做成一条被测行为**——`摆一个把现在裹在中间的安静时段()`往库里写 `wakeAt = now+10min`、
  `sleepAt = now-10min`（落库截秒，实际跨度 `[整分-10min, 整分+10min)`，等号两端都落在里面；跨子夜时
  `TimeSpan.contains` 走另一支照样为真），于是**不管几点跑，首页都该判 `SLEEP_HOURS`**。**没有**为了测试去给
  `AppContainer` 开一个可注入时钟的口子。
- **走查脚本 `build/probe28.py` 两处改动**（第 28 轮）：① 新增 `ensure_eq_any(nodes, descs, s, tag)`——
  先看当前 dump → 往下滑 3 次 → 往上滑 3 次（原来的 `ensure_eq` **只往下滑**，管不了在视口上方的目标）；
  ② `APK = os.environ.get("PROBE_APK", …)` **正式实现**——第 27 轮只把它写在 docstring 里、代码里没有，
  这一轮才真接上，坏前置（`PROBE_APK=/nonexistent.apk` → 退出码 1、日志写
  「`!! APK 没装上，后面不用走了（先跑 :app:assembleDebug）`」、后面一步未走）也是靠它才能构造。
- **`docs/06` 新增 §3.7「第 28 轮追加实测（L1 / L2 / L3 / L4 四层数字）」**（第 28 轮）：插在 §3.6 末尾的
  `---` 与 `## 4. L2 构建与打包` 之间（五行表 + 五处如实交代），并在**修订记录**表尾追加一行。
  四层实测：JVM **31 类 / 390 例**；`assembleDebug` **同一份源码拿到三个不同 md5**（见 Unverified 第一条）；
  设备 **19 类 / 132 例**、`Starting 132 tests` → `Finished 132 tests`、`BUILD SUCCESSFUL in 2m 20s`；
  装机走查 **`RESULT=PASS`**（578 行 / 113 条 `OK` / 0 条 `!!`、crash log 0 行、10 张截图）。
  **§3 的标题与上面的表是第 18 轮快照，保留不动。**
- **`docs/03` 新增 §7.9「第 28 轮：给「设置」补上屏幕级用例，`AppRoot` 第一次被整棵渲出来」**（第 28 轮）：
  插在 §7.8 末尾的 `---` 与 `## 8. 未验证项与缺口` 之间，记新文件体量、「其余 18 个类都从 `MainNav` 起渲」的
  对照、**只能在这个高度验的三条承诺**、取舍表（AC-13.1 只验到一半 / 隔离性弱于库那条、代价是这台机器的
  真实设置会被清）、**让时钟限制本身成为一条被测行为**的做法、L3 规模、反例只打一处与还原 md5、
  **同源码三个不同 md5**。
- **`docs/01` §4.13 的「当前证据」列重写**（第 28 轮）：把 AC-13.1 / 13.2 / 13.3 / 13.4 四行原来含糊的
  「已由 `HomeViewModelTest` 覆盖」换写成屏幕级用例名与断言内容，并照实交代 AC-13.1 只验到「首页重新读了设置」、
  AC-13.3 **不能排除**源码写死同一字面量、`performTextInput` 不是真 IME。**只改「当前证据」列——
  FR / NFR / AC 编号一个没加、一个没删，判据（预期结果）一字未动。** 顺手删了一处双空行。

- **「关库必须排在界面组合退场之后」从一句注释变成一条会红的规则**（第 27 轮）：新建
  `app/src/androidTest/java/com/fortheelder/ui/TestTeardown.kt`（顶层函数，**不是测试类**），
  提供 `关库排在组合之后(databaseName, container)`，其 `finally` 顺序固定为
  **① 断言前台上本应用 Activity 数为 0**（`ActivityLifecycleMonitorRegistry.getActivitiesInStage(Stage.RESUMED)`
  ，用 `runOnMainSync` 兜主线程要求）→ ② `container().database.close()` → ③ `deleteDatabase(databaseName)`。
  四个屏幕级用例类（`PlayScreenTest` / `TalkInputTest` / `TalkStarterTest` / `A11ySemanticsTest`）改为
  `RuleChain.outerRule(关库排在组合之后(TEST_DB){container}).around(compose)`，并删掉原来的
  `@After 关掉并删掉测试库()`。**动机**：第 26 轮审收尾顺序时留下的最后一条未验项就是这一处——**写反了也
  照样全绿**，而这种反序产生的只是偶发数据级失败，平时看不出来。**`app/src/main` 一行未改。**
- **四个屏幕级用例类**（第 27 轮）：`PlayScreenTest.kt` / `TalkInputTest.kt` / `TalkStarterTest.kt` /
  `A11ySemanticsTest.kt` 的收尾方式改动如上（`createComposeRule()` 去掉 `@get:Rule`，改存 `private val compose`
  由 `RuleChain` 持有）。设备侧用例数与类数**未变**：仍是 **18 类 / 125 例**。
- **走查脚本补上自己动手做前置：`build/probe27.py`**（第 27 轮，修的是第 26 轮那句「前置状态靠手工、
  docstring 与实现不符」）：脚本自己 `pm clear` → `adb install -r app/build/outputs/apk/debug/app-debug.apk`
  （校验输出含 `Success`）→ `adb shell monkey …` 启动 → 轮询 `dumpsys window` 的 `mCurrentFocus`（最多 40×0.75s）
  等 `com.fortheelder.MainActivity`。**并用最坏前置实测过**：L3 全量跑完时 AGP 已把两个 APK 都卸掉
  （`pm list packages | grep fortheelder` 无输出），就在「应用不在设备上」这个状态下直接跑脚本 →
  **`RESULT=PASS`**（`build/screens27b.log` 568 行 / 113 条 `OK` / 0 条 `!!`；脚本日志头三行是
  `pm clear` → `install: … Install / Success` → 「第 3 次轮询到前台」）。另：批量改名不再用了，
  **10 张截图逐个显式写名**（`build/{onboard1_27,onboard2_27,onboard3_27,settings27,talk27,talk_heard27,starters27,anchor_ask27,anchor_created27,anchor_deleted27}.png`）。
- **走查里那句「只挂 2 秒」的提示语不再当硬判据**（第 27 轮）：第 27 轮第一跑 **`RESULT=FAIL`**，而且
  **只差这一条**——`点了「保存称呼」之后屏幕上说「称呼已保存。」`。根因在源码里：`SettingsScreen.kt:52`
  `SAVED_HINT_MILLIS = 2_000L` + `:74-79` 的 `LaunchedEffect` 到点清掉这句，而一次 `uiautomator dump` 往返 > 1s，
  **dump 比提示慢**。⇒ 这句提示**能抓到是运气，抓不到不能算「没出现」**，所以降级成「尽力抓一次 + `NOTE`
  照实说抓到没抓到」（本轮**抓到了**，`build/screens27.log:169` 写着「第 1 次 dump」），硬断言换成五条**持久**
  不变量：① 保存后 `count_disabled() == 1`（`SettingsScreen.kt:90-99` 的
  `PrimaryWideButton(enabled = editingAddress.trim() != current?.address.orEmpty())` ⇒ 存下来的称呼等于输入框
  内容时按钮**重新变回禁用**）② `count_eq(nodes, NAME) == 1`（不清空输入框）③ 回首页 `ADDRESS_ENTRY`
  （「告诉我您希望被怎么称呼」）消失 ④ `with_name` 里每条以 `NAME + "，"` 开头 ⑤ **再进一次设置页**，
  输入框预填 `LaoXiong` 且 `count_disabled() == 1`（⇒ 值真落了 DataStore）。
- **`docs/06` 新增 §3.6「第 27 轮追加实测（L1 / L2 / L3 / L4 四层数字）」**（第 27 轮）：插在 §3.5 末尾的
  `---` 与 `## 4. L2 构建与打包` 之间。四层实测：JVM **31 类 / 390 例**（与第 26 轮同为 31/390，
  本轮增量落在设备侧）；`assembleDebug` **10,971,528 字节 / md5 `462e4726e886a25e93d02f1bdd26ac14`**
  （**与第 26 轮基线逐字节相同**，当次 `38 actionable tasks: 38 up-to-date` ⇒ 它证明的是「交付物没被本轮改动
  影响」，**不是**「本轮重新验证过交付物」）；设备 **18 类 / 125 例**、`Finished 125 tests`、
  `BUILD SUCCESSFUL in 1m 57s`；装机走查 `RESULT=PASS`（**567 行 / 113 条 `OK` / 0 条 `!!`**、crash log 0 行、
  10 张截图）。**§3 的标题与上面的表是第 18 轮快照，保留不动。**
- **`docs/03` 新增 §7.8「第 27 轮：测试收尾顺序的守卫」**（第 27 轮）：插在 §7.7 末尾的 `---` 与
  `## 8. 未验证项与缺口` 之前，记 `RuleChain` 三种做法的取舍、**为什么屏幕级用例够不着 `ViewModelStore`**
  （组合的退场由 `ComposeTestRule` 自己的收尾触发，用例代码控制不了 `@After` 与规则退场谁先谁后）、
  负例原文与代价（**`TestTeardown.kt` 是 `androidTest` 里第一个不是测试类的 Kotlin 文件**，
  已由本轮 L3 全量的类数 **18** 实证未计入）。

- **`HomeViewModel` 有了第一份设备层用例：`HomeViewModelTest`（9 例）**（第 26 轮）：新建
  `app/src/androidTest/java/com/fortheelder/ui/home/HomeViewModelTest.kt`（415 行 / 9 个测试方法），
  **真 Room（`elder-test-home-vm.db`）、真 `AnchorRepository` / `RhythmRepository` / `RhythmProfileService` /
  `SettingsStore`、真 DataStore 文件、真 `ViewModelStore` 注销路径**，只把 `AppClock` 换成可控时钟，
  固定时区 `Asia/Shanghai` 与时刻 `2026-03-10 09:00`。它一次守住 **AC-01.3 / AC-03.1 / AC-03.4 /
  AC-05.1 / AC-05.2 / AC-09.1 / FR-09 / DR-10**：休息时段**不写**开口记录也不给锚点；真开口过一次之后
  `RhythmDao` 里能查到「上次开口」；锚点只在它自己的时段命中时才带出那一行；**同一次露面里 `refresh()`
  不重新判定**；`onStart` 时没真离开过就不算新露面；真的 `onStop` → `onStart` 之后才重判（太近走
  `StayQuiet(TOO_SOON)`）；称呼落进 `SettingsStore` 后首页文案立刻换；**跨时段边界只靠 `refresh()` 重读时钟、
  账本不变**；有 `RhythmProfile` 时按本人作息说话而不是按假设作息。**动机**：第 25 轮补完 `TalkViewModel`
  之后，判据里只剩「首页 ViewModel」这一块 0 例，而它恰是「**在什么时刻判、判完写不写、写完读什么**」
  三件事的交汇点——靠人肉走查看不出来。设备 **17 类 / 116 例 → 18 类 / 125 例**。
- **`docs/03` 新增 §7.7「第 26 轮的测试规模」**（第 26 轮）：插在 `## 8. 未验证项与缺口` 之前，
  记 9 例各自的不变量、**两处防假绿的关键写法**（① 用常驻收集器绕开 `stateIn(…,
  SharingStarted.WhileSubscribed(5_000L), …)`——没人订阅上游就停跑，只读 `state.value` 会读到
  `Loading` 或停跑前的旧值，所以挂一条 `mirror: MutableStateFlow<HomeUiState>` 并一律断言 `mirror`；
  ② `tearDown` 的固定顺序「先收摊再关库」：`observerJob.cancel()` → `observerScope.cancel()` →
  `viewModelStore.clear()` → `storeScope.cancel()` → `database.close()` → `deleteDatabase` →
  `dataFile.delete()`），以及反向验证的结果。`docs/03` **1048 → 1072 行**。
- **`docs/06` 新增 §3.5「第 26 轮追加实测（L1 / L2 / L3 / L4 四层数字）」**（第 26 轮）：插在 §3.4 末尾的
  `---` 与 `## 4. L2 构建与打包` 之间，四行表 + **「五处如实交代」**——① `app-debug.apk` 的整文件字节数
  与 md5 **都不是内容度量**；② L4 第一跑失败的根因；③ 第一跑覆盖掉第 25 轮 6 张截图；④ `settleQuietly()`
  300ms 只能证「窗口内无变化」；⑤ 反向验证只打两处、且 **M1 第一版只红 1 例**。**§3 的标题与上面的表是
  第 18 轮快照，保留不动**。`docs/06` **528 → 549 行**。四层实测：JVM **31 类 / 390 例**（与第 25 轮同为
  31/390，本轮增量落在设备侧）；`assembleDebug` **删包重建**得 **10,971,528 字节 / md5
  `462e4726e886a25e93d02f1bdd26ac14` / 175 条目**；设备 **18 类 / 125 例**、`BUILD SUCCESSFUL in 2m 1s`；
  装机走查 `RESULT=PASS`（**433 行 / 94 条 `OK` / 0 条 `!!`**、crash log 0 行、9 张截图）。
- **「同一份内容、两种 APK 字节数」被追到 zip 层并给出逐条目实测**（第 26 轮，修的是第 24 / 25 两轮
  留下的那句「整文件差 45,324 字节，根因未查清」的**度量口径**，不是根因）：三份产物
  `build/app-debug-25.apk`（11,016,852 / `b8209a99…`）、`build/app-debug-26-before.apk`
  （11,016,852 / `b3f804c0…`，`connectedDebugAndroidTest` 跑完留下的那一份）、
  `build/app-debug-26-repro.apk`（**10,971,528 / `462e4726…`**，删包重建的交付物）用 python `zipfile`
  只读中央目录逐条目比：**条目名集合相同、未压缩大小 / 压缩后大小 / CRC32 / `date_time` 全部相同、
  差异条目 0、双方 `Σcompress_size` 都是 10,876,437**。唯一差别是 **`classes10.dex` 在 zip 里的次序**
  （index 2 ↔ index 174）以及由此带动的中央目录 / 对齐填充。**⇒ `app-debug.apk` 的整文件字节数与 md5
  都不该被当作内容指纹用。** 本轮还多测一步：**删包重建复现两次，两次都是同一份**（`build/l2_26_rebuild.log`，
  `38 actionable tasks: 1 executed, 37 up-to-date`）。

- **`TalkViewModel` 有了第一份设备层用例：`TalkViewModelTest`（13 例）**（第 25 轮）：新建
  `app/src/androidTest/java/com/fortheelder/ui/talk/TalkViewModelTest.kt`（410 行 / 13 个测试方法），
  **真 Room、真仓库、真 `TalkViewModel`**，只把 `AppClock` 换成可控时钟，用 `Dispatchers.IO` 上的 `runBlocking`
  串起来断言「屏幕上和库里各留下什么」。它一次守住 **AC-06.2 / AC-07.1~07.4 / AC-08.1~08.3 / AC-09.1 /
  AC-09.3 / AC-10.1~10.3**：听出来的意愿先是没表态的；再说一遍不算表态；空话与没听懂的话不改变屏上、
  也不落意愿；**锚点四条守卫逐条打**（库里无意愿 / 有听出来的意愿但没确认 / 意愿是「先不用」/ 目录里没有的起点，
  四条都不许建）；确认过之后点时段才真建、且动作与耗时**继承自起点**；做完只回一句话不留记录；删掉就是删掉。
  **动机**：此前判据只落在两头（L1 纯函数 390 例、L3 仓库 / DAO 16 类），**`ViewModel` 把仓库、领域、UI 状态
  串起来的那一层 0 例**，而这一层正是产品语义最容易走样的地方。设备 **16 类 / 103 例 → 17 类 / 116 例**。
- **`HomeViewModel.kt:72-76` 的 KDoc 照事实改对**（第 25 轮）：原写「同一分钟内不重读」，实现却是
  「**这一次露面读一次**（`open`）」。`SessionFacts` 由 `open` 建一次、`open` 变化才重读，所以正确的话是
  「同一个露面里不重读……真的重新露面时才重读一次」。**这是本轮唯一一处 main 改动，只有两行注释、没有代码变更**
  （第 24 轮记录过、当时未处置的「候选 3」）。
- **`docs/06` §3.4 补记收尾重跑红过的一次**（第 25 轮）：L3 那行尾补「收尾重跑红过一次」，「三处如实交代」
  扩成「**四处如实交代**」，新增两条——第 3 条（`RhythmCollectorTest` 的测试侧 teardown 竞争、`RhythmMaintenanceTest`
  被牵连、67 例被吃、修后 116/116 绿、7 次复现全绿、口径「高置信重构，未按需复现」）与第 4 条（`probe25.py`
  第一版的三处假绿断言）。JVM **31 类 / 390 例**、设备 **17 类 / 116 例**，全绿；装机走查 `RESULT=PASS`
  （**94 条 `OK` / 0 条 `!!`**、433 行）。

- **「文案里写死的个数」这一类漂移，从第 24 轮起有一条机器守卫**：新增 JVM 单测
  `app/src/test/java/com/fortheelder/ui/CopyCountGuardTest.kt`（11,834 字节 / 1 例），把 **8 条登记在册的个数宣称**
  （小游戏款数、字号档数两种写法、同一件事下的起点条数、引导页数两种写法、「会做的」条数、「不会做的」条数）
  的「文案里的数」钉到**代码事实**（`PlayCatalog.games.size` / `FontSize.entries.size` /
  `StarterCatalog.all` 的分组条数 / `OnboardingScreen.PAGE_COUNT` / `doesBullets` / `wontBullets` 的 `.size`）。
  它扫 `fortheelder.mainSrc` 下全部 `**/*.kt`（**KDoc 与注释一起扫**，因为这次抓到的错就长在 KDoc 里），
  中文数字支持 一~十 / 两 / 十X / X十 / X十Y / 阿拉伯数字，**认不出的写法判失败而不是静默跳过**；
  另加三条防假绿断言（扫描文件数 ≥ 50、确实扫到 `*Voice.kt`、扫过行数 > 6000，本轮实测 70 文件 / 7,786 行）。
  动机见 `records/03` ADR-022、口径见 `records/02` RC-021：这类漂移此前三次案例（12 / 17 / 19 轮）
  **全是人肉比对撞上的**，`docs/07` R-10 当时记的原话是「没有机器守卫」。
- **`OnboardVoice.kt:36` 的 KDoc「四档排版表」改成「三档排版表」**（第 24 轮）：这是上面那条守卫**自己抓到的
  第一处**——`data/prefs/SettingsStore.kt:23-27` 的 `FontSize` 只有 `STANDARD` / `LARGE` / `EXTRA` 三档
  （1.00 / 1.15 / 1.30）。改前先做反向验证让它红过一次（失败原文见 `records/04` 22.3），再改对。这也是本轮
  **唯一一处 main 改动**（一行 KDoc），判据、UI 结构、数据**一行未动**。
- **`docs/07` R-10 的措辞由「没有自动校验」改成「有，但只在册」**（第 24 轮），并补上第 4 个案例
  （第 17 轮「两个小游戏」→「三个」）与「已落实措施」第 7 条（含那张 8 条宣称表）；`docs/03` 新增 §7.5 / §7.6、
  `docs/06` 新增 §3.3、`docs/01` NFR-17 行末尾补一句守卫范围。JVM **31 类 / 390 例**、设备 **103 例**，全绿；
  装机走查 `RESULT=PASS`（68 条 `OK` / 0 条 `!!`）、`logcat -b crash` 0 行。

- **规则页上多一条退路：`换一个游戏`**（第 23 轮）：玩「玩一会儿」时若已进到某个游戏的规则页、发现不是
  想玩的那个，除 `开始`，现在下面还多一条 `换一个游戏`（`PlayVoice.switchLabel`，`ui/play/PlayScreen.kt:155-157`），
  点它直接回**挑选页**——不用先退到首页再重进。这条**只开在规则页**：玩到一半（`Playing`）不摆（那一屏每格都在
  等人点，走掉就是半途而废），结束卡（`Finished`）不摆（那一屏只允许一个按钮，第 11 轮就钉下的断言 + AC-11.3）。
  判据落在纯函数 `playStartState(games)`（`ui/play/PlayViewModel.kt:154-159`）：应用里只有一个游戏时
  `canChooseAgain = false`，不摆一条点了没用的路。守卫：**新增单测** `ui.play.PlayViewModelTest`（311 行 /
  16 例，这个 ViewModel 的**第一份单测**）、`ui.play.PlayVoiceTest` 15 → **18 例**（两条词表扫描加上
  `switchLabel`，新用例 `theSwitchLabelBacksOutInsteadOfPushingForward` 要求这句读起来是往后退）、设备端
  `ui.play.PlayScreenTest` 5 → **6 例**。JVM **30 类 / 389 例**、设备 **103 例**，全绿。
- **`docs/09-用户手册.md` 里一句写反的话已改正**（第 23 轮）：原说结束卡那个 `回去歇会儿` 是回「挑选页」，
  实际回**首页**（`PlayScreen.kt:74` 的 `FinishedPanel(onLeave = onBack)` → `popBackStack()`，连挑选页一起
  收掉）。**代码是对的，错的是描述它的话**；已在 §4.5 改正并加 §8.9 记这一笔。同轮手册补上规则页那条新退路。

- **一条新的口径守卫：`PlayVoiceTest.thePromiseMakesNoHardLimitItCannotKeep`**（第 22 轮）：
  硬话词表（`最多` / `上限` / `不超过` / `不会超过` / `保证` / `必须`）在规则页那句承诺里
  **一个都不许出现**，并且必须同时含「不用计时」与「没有分数跟谁比」。同轮把原来的
  `thePromiseTakesTheCeilingFromTheGameItself` 拆成 `thePromiseSaysItHonestly` 与
  `theNumberInThePromiseIsComputedFromTheGame` 两条，盯「整句一致性」与「数字出处」。
  JVM 例数因此 **370 → 372**（29 类 / 372 例 / 0 失败 / 0 错误）。

- **`docs/06-测试计划与用例.md`**（第 19 轮，**新建**，**35,822 字节 / 468 行 / 纯 LF**、11 节）：
  补齐 `docs/` 的 **00~09 全部编号**。内容全部是**转抄已有证据并重排**：① 测试分四层——L1 单元测试（JVM）、
  L2 构建与打包、L3 设备仪器化测试、L4 装机走查，并写明 L3 与 L4 为什么**同时存在**（语义定位器点得到控件，
  但**看不见点击落在哪个像素**；两者不互相替代）；② 四层最新数字（第 18 轮实测：L1 29 类 / 370 例、
  L2 APK **11,052,250** 字节 / md5 `84327b14…`、L3 16 类 / 102 例、L4 `build/screens18.log` **88 行**）；
  ③ **需求 → 测试追溯矩阵**（FR-01~16、NFR-01~17，每行写出守它的类名与例数）；④ 未覆盖项与缺口清单
  （平台性限制 / 未测量 / 无障碍只做到「读得到」/ 守卫自身的盲区 / 文档滞后）；⑤ 缺陷清单分产品侧、
  测试侧、记录侧三栏；⑥ 回归测试清单（改什么跑哪几层）。**本文件不含任何本轮新测的数字**（见 `Unverified`）。

- **`app/src/main/java/com/fortheelder/ui/common/A11y.kt`**（第 18 轮，**58 行** / 5 个 `Modifier` 扩展）：
  全 App 无障碍语义的**唯一出口**——`asScreenTitle(state)` / `asSectionTitle()` / `announcesWhenItChanges()` /
  `gridOf(rows, columns)` / `gridCell(row, column)`。它**一个字节的可见文字都不发**，只往 Compose 语义树里
  放标签（`heading` / `stateDescription` / `liveRegion` / `collectionInfo` / `collectionItemInfo`）。
  集中放一处的理由、以及**哪些地方刻意不加**，见 `records/03` ADR-019。
- **`app/src/test/java/com/fortheelder/ui/A11yPlacementTest.kt`**（第 18 轮，**125 行 / 3 例**）：源码级守卫。
  它验的是「该挂的地方有没有**漏挂**」——语义树断言对「缺席」是无感的（漏挂与本来不需要长得一模一样）。
  三条规则：① 每个 `*Screen.kt` 必含 `ElderScreen(`（扫到 ≥5 个才算数）；② 每个 `Icon(` 必须显式写
  `contentDescription =`，写 `null` 要在前 4 行 / 后 8 行内留 `a11y-decorative: 理由`（扫到 ≥3 个才算数）；
  ③ `semantics {` 只许出现在 `common/A11y.kt`，就地写要留 `a11y-ok: 理由`。源码目录由系统属性
  `fortheelder.mainSrc` 提供，**缺失即 `error(...)` 失败**（不静默跳过——静默跳过会让守卫悄悄失效）。
- **`app/src/androidTest/java/com/fortheelder/ui/a11y/A11ySemanticsTest.kt`**（第 18 轮，**379 行 / 5 例**）：
  语义树断言，其中一例走的是**平台层** `AccessibilityNodeInfo`（不是 Compose 语义树），是「从系统那一侧
  也看得到」的证据。设备用例 97 → **102 例 / 16 类**（`build/connected18c.log`）。

- **第三个脑力互动「算一算」`domain/play/SubtractSeven.kt`**(第 17 轮，161 行)：从 100 起每次减 7、共 **5 道**
  (`QUESTION_COUNT = 5`)，每题**恰好三个**备选数且只有一个对(`COLUMNS = 3`，干扰项由 `offsets =
  listOf(-10, -2, -1, 1, 2)` 加在**答案**上得到)。点错只给一句「不是它，再看看」且**题面不动**(复用第 12 轮
  就有的 `PlayHint.NOT_THIS_ONE`)，**不报用时**(全 App 唯一报用时的仍只有舒尔特方格)。玩点、规则与三次代价
  见 `records/03` ADR-018，需求条目 `docs/01` FR-16 / AC-16.1~16.4。
- **`app/src/test/java/com/fortheelder/domain/play/PlayStaysClocklessTest.kt`**(第 17 轮，99 行 / 1 例)：
  DR-09「领域层不认识时钟」的**第一条机械守卫**——把 `domain/play` 与 `domain/voice` 的全部源码读进来，
  断言其中不出现任何时钟 / 时间获取符号。
- **`app/src/test/java/com/fortheelder/domain/play/SubtractSevenTest.kt`**(第 17 轮，328 行 / **21 例**)：
  五道答案依次 `93/86/79/72/65`、每题恰好三数且对的那个必然在、同一个 `Random` 出同一局、点错后 `step` 与
  题面都不动、走完五道才是完成态。
- **`ui.play.PlayScreenTest` 新增一例**「算一算点错只说一句五道算完就收尾」(第 17 轮，设备用例 4 → **5**)：
  设备侧合为 **97 例 / 15 类**(`build/connected17b.log`)。

- **`ui/` 层用户可见文案全部收进同包的 `*Voice.kt`**(第 16 轮)：新增 `ui/common/CommonVoice.kt`(44 行) /
  `ui/home/HomeVoice.kt`(36) / `ui/onboard/OnboardVoice.kt`(97) / `ui/settings/SettingsVoice.kt`(115)；
  `ui/talk/TalkVoice.kt` 109 → 171(顺手把 `TalkScreen` 里私有的 `IntentKind.label()` 并过去，改名
  `TalkVoice.kindLabel`)。`ui/` 下 `*Voice.kt` 共 **7 个 / 663 行**(`wc -l` 实测)。
- **新增机械守卫 `app/src/test/java/com/fortheelder/ui/UiCopyPlacementTest.kt`**(第 16 轮，1 例)：
  `ui/**/*Voice.kt` 自由写中文，**其余 `.kt` 里出现含中文的字符串字面量即失败**，`copy-ok: 理由` 可豁免，
  注释行不算。另加三条防假绿断言(扫到的 `ui` 文件 ≥ 10 / 至少扫到一个 `*Voice.kt` / 扫过行数 > 500)，
  且缺 Gradle 注入的源码根时 `error(...)` **直接失败而不是跳过**。
- **`Encouragement.forAction` 新增 `terminator(action)` 与 `sentenceEnds`**(第 16 轮)：只让**标点**变成条件，
  不动 `action` 本身——因为 `violations` 要求回话包含那件事的原文。
- **`app/build.gradle.kts` 把 `src/main/java` 路径经 `systemProperty("fortheelder.mainSrc", …)` 传给 JVM 测试**
  (第 16 轮，`:96-98`)：守卫要知道去哪里扫源码，缺属性即失败。
- **新增 `docs/09-用户手册.md`**(第 15 轮，784 行 / 53,111 字节，全 LF)：`docs/` 第 10 份，
  原先缺的两份之一。11 节 + 修订记录：`0.` 给谁看(含导航图) / `1.` 三页引导 /
  `2.` 首页(顶上那句话会变) / `3.` 说两句(含起点卡与日常锚点) / `4.` 玩一会儿(两个游戏) /
  `5.` 设置(六块) / `6.` 它刻意不会做的事(每行带 ADR/RC 依据) / `7.` 常见问题 6 条 /
  `8.` 做不到的事(诚实清单) / `9.` 与其他文件的关系 / `10.` 一页速查对照卡。**代码一行未动。**
- **`records/04` 第十三节**(第 15 轮)：为写手册做的界面证据采集，16 屏 / `TAP MISS` 0 /
  `DUMP FAILED` 0 / 498 秒 / 日志 207 行 12,993 字节；含采集复现命令、GMT 时区的解释、
  `docs/01:269` 悬案的结案、起点卡与锚点全链路观测、双句号瑕疵。
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

**第 11 轮 — 脑力互动框架与舒尔特方格(FR-11/FR-12 收口)**
- 新增 `domain/play/`(4 个文件，纯 Kotlin，全目录只有两行 import、都是 `kotlin.random.Random`)：
  `PlayGame.kt`(125 行，`PlayHint` 三态 / `PlayRound` 5 字段 / `PlayGame` 6 字段 + 出局函数)、
  `SchulteGrid.kt`(48 行，5×5 数字 1~25，`longestSeconds = 180`)、
  `PlayCatalog.kt`(20 行，`games` 只有一条)、
  `PlayWords.kt`(53 行，15 个宣称禁用词 + 朴素子串扫描)
- 新增 `ui/play/`：`PlayVoice.kt`(85 行，**唯一**放用户可见文字的地方)、
  `PlayViewModel.kt`(92 行，`Ready` / `Playing` / `Finished` 三态；**没有 `viewModelScope`、
  没有 repository、全程只读时钟两次**)、`PlayScreen.kt`(187 行，文件内没有任何用户可见字面量)
- 首页新增「手上想有点事的时候 → 玩一会儿」入口(`ui/home/HomeScreen.kt`)；
  导航目的地由 3 个增到 4 个(`ui/nav/MainNav.kt` 的 `Route.PLAY`)
- 新增 `docs/03-详细设计说明书.md`(8 章)：本轮范围、文件清单与行数、领域层逐类细设、
  界面层(文案表 / 三状态 / 状态机 / 结构树 / 接线)、边界与异常表、DR-09 结构检查表、
  一局时序、回溯矩阵、未验证项与新增缺口
- 新增测试类：`SchulteGridTest`(JVM 15)、`PlayCatalogTest`(JVM 7)、`PlayWordsTest`(JVM 6)、
  `PlayVoiceTest`(JVM 11)、`PlayScreenTest`(设备 3)；JVM 由 279 → **318**，设备由 92 → **95**
- 需求收窄(`records/02` RC-010)：结束卡**不放**「再来一局」、**不留**历史最好成绩、
  框架**不预埋**没做完的游戏；守法方式从「记得别写」换成「结构上写不出来」
  (`PlayRound` 里没有 `score` 字段，`PlayViewModel` 里没有协程，见 `records/03` ADR-015)
- 静态检查：权限数仍 **0**，清单里 `<receiver>` 仍 **0**，网络栈 **0**，
  `TODO/FIXME/敬请期待/not implemented` **0**；宣称词扫描 main 源 **16 行全是词表定义行**、
  res **0 行**；主源由 56 → **63 个 .kt / 6,762 行**，JVM 测试 20 → **24 文件**，
  设备测试 14 → **15 文件**

**第 12 轮 — 第二个游戏「找出一样的」与三处缺口收口(FR-15 / GAP-07/08/09)**
- 新增 `domain/play/FindPair.kt`(66 行，纯 Kotlin，全目录仍只有 `kotlin.random.Random`
  这一行 import)：`FindPair.SIDE = 4`、20 个二字日常物件词、`longestSeconds = 120`；
  `deal` 先取 15 个互不相同的词、再把其中一个补一次、最后整体打乱——「恰好一对同字」
  是**构造出来的**，不靠概率撞
- 新增 `domain/voice/ClaimWords.kt`(52 行)：15 词宣称禁用词表(`:21-38`) +
  `allowedWords`(`:40`) + `checkWords`(`:42`) + `violations`(`:46`)，
  把第 11 轮分在两处的词表**收成单一来源**(GAP-07)；同目录旧文件
  `domain/play/PlayWords.kt` **删除且不留转发壳**
- `domain/play/PlayGame.kt` 由 125 改到 **242** 行：`PlayRound` 由 `data class` 改
  **`sealed interface`**(公共 7 项：`gameId` / `cells` / `hint` / `finished` /
  `stepsToFinish` / `tap` / `markOf`)，新增 `CellMark`(`PLAIN` / `PICKED` / `DONE`)、
  `OrderRound`(舒尔特)、`FindPairRound`(找一样)；`PlayHint` 由三值增到四值
  (+`NOT_THIS_ONE`，`NOT_YET` 与 `ALREADY_DONE` 归 `OrderRound` 专用)
- `SchulteGrid.kt` 50 行(`deal` 返回类型收窄为 `OrderRound`)、`PlayCatalog.kt` 24 行
  (`games` 由 1 条增到 **2** 条，`first` 仍是 `games.first()` 而**不是**「舒尔特」硬编码)
- `ui/play/PlayViewModel.kt` 119 行：状态由三态增到 **四态**(+`Choose`，
  初值按 `games.size` 分支)；`ui/play/PlayScreen.kt` 249 行(+`ChoosePanel` / `GameCard`)；
  `ui/play/PlayVoice.kt` 111 行(选择页文案 + 两种完成文案，找一样的**不报用时**)
- 收口 GAP-08：`ui/onboard/OnboardingScreen.kt` 255 行，把「这个版本还很素……这些都还在做」
  改成「上面这些都在这一版里，不用再打开什么开关，它也不会弹窗通知您。」，
  并把「有一个小游戏」改成「有两个小游戏」
- 收口 GAP-09：`domain/play/PlayGame.kt` 类 KDoc 里错标的「docs/02 约束 C-04」改为
  「`docs/02` 第 2.2 节、`docs/01` NFR-14」
- 新增测试类：`FindPairTest`(JVM 19)、`ClaimWordsTest`(JVM 7)；**删除** `PlayWordsTest`(6)；
  改动 `PlayVoiceTest`(11 → 15)、`SchulteGridTest`、`PlayCatalogTest`、
  `PlayScreenTest`(设备 3 → 4)。JVM 由 318 → **342**(25 个类)，设备由 95 → **96**(15 个类)
- 文档：整轮重写 `docs/03-详细设计说明书.md`(740 行)；`docs/01` 新增 4.15 FR-15 与
  AC-15.1~15.4、更新 AC-11.1 与 DR-09、里程碑表加第 12 轮行、GAP-07/08/09 三行关闭；
  `docs/02` 结构与文件构成表同步
- 静态检查：权限数仍 **0**，清单里 `<receiver>` 仍 **0**，网络栈 **0**，
  `TODO/FIXME/敬请期待/not implemented/ComingSoon` **0**；宣称词扫描 main 源由 16 行 → **15 行**
  (全部是 `ClaimWords.kt:21-38` 的词表定义行)、res **0 行**；主源 63 → **64 个 .kt / 7,065 行**，
  JVM 测试 24 → **25 文件**，设备测试仍 **15 文件**(`git ls-tree -r HEAD` 与工作区实测对比)

### Changed

- **拆掉一句无法兑现的硬承诺：规则页「一局最多 N 分钟就完了」改成「一局大概 N 分钟就完了」**
  （第 22 轮）：`longestSeconds` 全仓只被 `PlayVoice.promise` / `PlayVoice.minutes` 与测试读，
  `ui/play/` 与 `domain/play/` 里 **`delay(`、`LaunchedEffect`、倒计时、超时结束一处都没有**（逐字
  grep 实测为空）——所以「最多 3 分钟」在屏幕上**没有任何东西执行它**。对照「最多一天一次」
  （`InterventionWindow.kt:26` 的 `maxGapMinutes = 1440`、`:47` 的 `coerceAtMost`，有测试守着）
  站得住。**改文案、不动机制**：不加倒计时（会撞 AC-11.3「不做倒计时」，对大龄用户也是催促）、
  不加超时中断、不改取值（180 / 120 / 180 照旧）、不改名（`longestSeconds` 名字仍像上限，
  账记在 `records/03` ADR-020）。**四个文件**：`ui/play/PlayVoice.kt`（**130 行**，`promise` `:67-68`、
  KDoc `:49-66` 与 `:116` 重写）、`domain/play/PlayGame.kt:232-240`（KDoc 首行「最多」→「大概」）、
  `ui/play/PlayVoiceTest.kt`（**243 行**）、`docs/01` + `docs/03` + `docs/09`（口径与行号同步）。
- **四层验证全部重跑，因为 KDoc 也进 dex 的行号表**（第 22 轮）：改完 `PlayVoice.kt` 打的那份 APK
  （内部叫 22a）虽然 102 例仪器化测试全绿，但改过 `PlayGame.kt` 的 KDoc 之后它的行号表已不同，
  **证据作废、重跑**。新数字：JVM **29 类 / 372 例 / 0 失败 / 0 错误**（`BUILD SUCCESSFUL in 5s`）；
  新 APK **11,089,357 字节** / md5 `9290b5eba96017cd6d2ff68d57fbfd04`（22a = 11,052,250 /
  `4aae7bf6aed0d9c3e5e578377e7cbed2`）；设备端 `Starting 102 tests` → `Finished 102 tests`，
  **0 failed**，`EXIT_CONNECTED=0`；装机走查 `RESULT=PASS`、截图 `build/rule22.png`（119,707 字节），
  规则页新文案实测在 **y750-876**（与旧文案同一区间）。**尺寸差 37,107 字节已逐条定位为容器对齐
  填充**（175 个 zip 条目名字集合完全相同、内容不同的**只有 `classes15.dex`**、两侧 dex 未压缩总量
  都是 27,179,248）；16 个 dex 串扫描：`一局大概` 命中 **1**（`classes12.dex`）、`一局最多` 命中 **0**。
- **文档与记录同步（第 22 轮）**：`docs/01` 三行需求（AC-11.3 / 15.3 / 16.3）各加「第 22 轮澄清：
  这是估时，不做倒计时也不强制中断」并补迭代表五行；`docs/03` §3.5、字段表、成员表整块按实测行号
  重校（111 行 → **130 行**）、`:483` 新增行号重校说明、状态图与走查断言表改新文案；`docs/09` 四处。
  `records/04` **新增第二十节**（20.1 ~ 20.10）、`records/01` 补第 22 轮条目、`records/02` RC-019、
  `records/03` ADR-020。**`records/` 里历史轮次的行号不改**——它们在各自那一轮都是实话。

- **性能基线首次实测：GAP-03 从「从未测量」变成「只在模拟器上测过一轮」**（第 21 轮）：在 AVD
  `Elder_API35`（Android 15 / x86_64 / `-gpu swiftshader_indirect` **软件渲染** / 1080×2400）上量了——
  冷启动 9 次（min **942** / median **1178** / max **1495** ms，**9 条全是 `LaunchState: COLD`**）、
  `framestats` 帧时长（三连点三窗口合并 **n=388**、50% **35.3** ms、>16.7ms **383/388 = 98.7%**）、
  `gfxinfo` 汇总（走查 889 帧 / Janky **138 = 15.52%**）、内存 PSS（**76,261 / 100,324 / 87,005** KB）、
  崩溃与 ANR 缓冲区（读到为空）。**NFR-07 拿到单侧证据**（可交互夹逼在 `-W` 返回后 300~530 ms），
  **NFR-08 判不了**（见 `Unverified`）。**本轮 `app/` 零改动**（`assembleDebug` 报 `UP-TO-DATE`，被测物仍是
  第 18 轮那份 APK：**11,052,250** 字节 / md5 `84327b14218630002a6e4a26721333b6`），**所以本文的 Added 与
  Fixed 两节本轮为空**——没有新增文件、没有修任何东西。
- **文档与记录同步（第 21 轮，纯口径更新）**：`docs/01` / `docs/02` / `docs/03` / `docs/06` / `docs/07` /
  `docs/09` **六份共 12 处**改成实测口径，GAP-03 一律标为**部分关闭（第 21 轮）**（`docs/03` 新增 §8.5、
  `docs/06` 新增 §6.4、`docs/07` 的 R-04 标题改成「只在模拟器上测过」）；`records/04` 新增**第十九节**、
  `records/01` 补第 21 轮条目。**这些都不是需求变更也不是代码变更**（`records/02` / `records/03` 因此不加条目）。

- **推送执行 + 推送口径收口**（第 20 轮）：`git push origin main` 一次推上**第 11 ~ 19 轮的 9 个提交**
  （`d769c69..d9d755f  main -> main`，退出码 0；前置 `GIT_TERMINAL_PROMPT=0` 防挂），验证见 `records/04`
  第十八节；随后把本文件里那条「第 11 ~ 19 轮都没有推送到 GitHub」改成**已推送**口径，`records/01` 补
  第 20 轮条目、`records/04` 新增第十八节。**本轮 `app/` 零改动、`docs/` 零改动**——所以本文的
  **Added 与 Fixed 两节本轮为空**（没有新文件、没有修任何东西），Changed 只有这一条推送口径的收口。

- **`docs/09-用户手册.md:771` 改写一句**（第 19 轮）：原话「这一版的编号里 `docs/06`（测试计划与用例）
  故意还没写——测试的数字每轮都在变，早写会立刻过期，排在后面几轮。」→ 改为「……已在**第 19 轮补齐**——
  它把每轮变化的测试数字汇总成一处（四层 L1~L4、需求→测试追溯矩阵、未覆盖项、缺陷清单），数字本身仍以
  `records/04` 的逐轮实测为准。」**只改这一处，界面可见文字零改动**（行数未变，仍在 `:771`）。
- **`records/01` / `records/02` / `records/04` 三份过程记录同步**（第 19 轮，用 `build/append_crlf.py` 按
  目标原有 EOL 追加）：`records/01` 补第 19 轮条目（产出表、两处问题与解决、三条验证、文档同步清单、
  五条遗留，**+4,647 字符**）；`records/04` 新增**第十七节**（四层数字出处表、两处自写措辞的更正、
  本轮未跑 L3 / L4 的声明，**+3,596 字符**）；`records/02` 新增 **RC-018**（第 19 轮**未修改任何已基线
  需求**，记的是「补一份汇总文档**不算**需求变更」这个判断本身，**+2,374 字符**）。
  **FR / AC / NFR / DR 的编号与正文一个字都没有改**。
- **`records/03-架构决策记录.md` 本轮刻意不加 ADR**（第 19 轮）：`docs/06` 是 `records/04` 的**汇总视图、
  不是替代品**，这层边界已写进 `records/02` RC-018 与 `records/01` 第 19 轮条目，**不必单开一条决策记录**。
  末条仍是 ADR-019（第 18 轮）。
- **`docs/00` / `docs/01` / `docs/04` 本轮不动**（第 19 轮）：`docs/00:168` 本来就把测试指向
  「`docs/06`、`records/04`」两处，无需改；`docs/01:456` 的注写明「第 13 ~ 15 轮是纯文档轮、不在覆盖矩阵
  占行」，第 19 轮同属纯文档轮，按既有惯例**不占行**；本轮不涉及数据库，`docs/04` 不动。

- **9 个无障碍语义挂载点**（第 18 轮）：`ui/common/ElderComponents.kt:110`（表头标题，四屏各一）、`:142`
  （`SectionCard` 标题）、`:157`（`SectionTitle`）；`ui/play/PlayScreen.kt:142`（题面）、`:153`（提示行）、
  `:167`（结束语）三处 `liveRegion`、`:191`（棋盘 `collectionInfo`）、`:250`（格子 `collectionItemInfo`）。
  **全仓 `onClickLabel` 0 处**——本项目的按钮文字本身已经说清动作，不加第二层标签。
- **装饰性图标显式 `contentDescription = null`**（第 18 轮，`ui/home/HomeScreen.kt:158` 注释 / `:162` 赋值）：
  全仓**恰好 1 处**，就地留 `a11y-decorative:` 豁免标记，由 `A11yPlacementTest` 第二条规则盯着。另两处
  `contentDescription` 是**带信息**的（`ElderComponents.kt:98` 返回按钮、`StarterCard.kt:102` 展开/收起）；
  全仓 `Icon(` 与 `contentDescription` 各 3 处、一一对上。
- **措辞收窄：「叠两个 `semantics` 块会让语义节点分裂」改成「本项目未实测、选择不碰」**（第 18 轮末）。
  原话在 `ui/common/A11y.kt` 的 KDoc 与 `docs/03` §4.7（`:652`）两处都被当成**已知事实**写，但**从未做过
  A/B 对照**——两种写法在语义树与平台层各是什么样，没有测过。两处都**等行数**改写，新口径落在
  `records/03` ADR-019 第 2 条；`A11y.kt:22-23` 与 `docs/03` §4.7 现在都引用它。改注释后
  `assembleDebug UP-TO-DATE`、APK **未重打包**，16 个 `classes*.dex` 改前改后 md5 **逐条相同**
  （`build/dex18-before.txt` / `build/dex18-after.txt`）——交付的就是设备上测过的那份字节码。
- **`docs/01` / `docs/02` / `docs/03` / `docs/07` / `docs/09` 五份文档随代码同步**（第 18 轮）：`docs/01` 的
  GAP-05 从「尚未添加」改成**已关闭**、GAP-03 那一格补上第 18 轮的预算去向；`docs/03` 改 §2.6 / §4.7 /
  §7.2 / §7.3 / 追溯矩阵 GAP-05 行 / §8 / §8.3 / §8.4；`docs/02`、`docs/07`、`docs/09` 各按其位补记。
  **FR / AC / NFR / DR 的编号与正文一个字都没有改**（见 `records/02` RC-017）。
- **口径更正：本轮 JVM 用例的「净增」**（第 18 轮）。相对**第 17 轮**的 367 例 / 28 类，本轮是
  **+3 例 / +1 类**（= `A11yPlacementTest` 的 3 例），其余既有类一例不增不减。重头在设备侧：
  `A11ySemanticsTest` 挂在 `androidTest/`，**不进 JVM 计数**。

- **`PlayCatalog.games` 2 → 3**(第 17 轮，`PlayCatalog.kt:20`)：第三个游戏注册进目录。加游戏的代价本轮落到
  **第三种形状**——`PlayGame` 的六个字段一字未动、只多一条目录项，新规则虽然是一个新的 `PlayRound` 实现，
  但 `PlayScreen.kt`(249 行)与 `PlayViewModel.kt`(119 行)**本轮零改动**，代价换到 `ui/play/PlayVoice.kt` 的
  三处 `when`：**漏分支当场编译不过**，编译器把该补的分支直接报出来。三种代价的对比见 `records/03` ADR-018。
- **引导页文案「有两个小游戏」→「有三个小游戏」**(第 17 轮，`ui/onboard/OnboardVoice.kt:44`)：原文与选择页
  的三张卡直接矛盾，属真实缺陷，见 `Fixed` 一节。
- **只改 KDoc 的两处**(第 17 轮)：`FindPair.kt` 更正其中「连续减七要输入数字」那句错话；`PlayCatalogTest.kt`
  的断言写成「目录里每一条都……」，第三个游戏进来时**一条用例都不用改**。
- **口径更正：本轮 JVM 用例的「净增」**(第 17 轮)。相对**第 16 轮**的 345 例 / 26 类，本轮是 **+22 例 / +2 类**
  (= `SubtractSevenTest` +21 + `PlayStaysClocklessTest` +1)，其余既有类一例不增不减。`docs/01` 追溯矩阵第 17 行、
  `docs/03` 第 7.1 节与 `records/01` 第 17 轮条目三处，把初稿里误用的「净增 25」(那是相对**第 12 轮**基线 342 的
  累计值)统一改成逐轮口径，并同时注明第 12 轮基线。
- **「APK 字节数总与第 12 轮相同」这条挂了第 15 / 16 两轮的已知问题已解释并关闭**(第 17 轮)：逐条目比对
  (`build/apkcmp17.log`，24,972 字节)显示改文案只让 `classes6.dex` 的**压缩后长度 −3**，紧随其后的
  `lib/arm64-v8a/libandroidx.graphics.path.so` 因对齐填充**等量 +3**，本地记录合计两边都是 **10,951,648**
  字节，加尾部 19,880 = **10,971,528**——**在这种改动下总字节数必然不变**，它不是「产物没更新」的证据。
  推导写进 `docs/03` 第 8.2 节。
- **`docs/01` / `docs/02` / `docs/03` / `docs/07` / `docs/09` 五份文档随代码同步**(第 17 轮)：`docs/01` 新增
  FR-16 与 AC-16.1~16.4、AC-11.4 条目数 2→3、追溯矩阵加第 17 行；`docs/02` 模块表加 `SubtractSeven.kt`、第 8 节
  规模改成 367 / 97；`docs/03` 按 ADR-018 改第 3 节形状并新增 8.2 / 8.3 两节；`docs/07` 补「游戏款数会漂移、
  暂无机器守卫」；`docs/09` 新增 §4.4 算一算(原 4.4 顺延为 4.5)并把全文游戏款数 2→3。

- **GAP-04 收口：文案落点定为同包的 `*Voice.kt`，不是 `res/values/strings.xml`**(第 16 轮)。理由写进
  `records/03` ADR-017：禁用词机器扫描(AC-11.1)**只扫 Kotlin 源码**(15 个宣称词在 `main` 源里只命中
  词表定义行、在 `res/` 里 0 行)，文案一进 XML 这条守护就失效。**先红后绿**：搬迁前同一命令报 **138 处**
  (`SettingsScreen` 49 / `OnboardingScreen` 39 / `TalkScreen` 34 / `HomeScreen` 9 / `TalkViewModel` 5 /
  `StarterCard` 1 / `ElderComponents` 1，逐条核对无一处误报，全文落盘 `build/offenders16.txt` 10,774 字节)，
  搬迁后同一条命令通过。**范围如实**：守卫只扫 `ui/`；`domain/` 层符合「集中常量」但无机械守卫，列
  **评审项**(见 `records/02` RC-013)，不写成已彻底关闭。
- **文案搬家后做了「零漂移」核对**(第 16 轮)：把第 15 / 16 两轮的走查日志按屏解析成文字集合做双向差，
  **5 组全部零差异**(intro 14/14、称呼 14/14、说明 11/11、首页 11/11 两组)；唯一差异是 5 句示例样本句
  本轮落在首屏之外。**没搬丢字、没改字**，见 `records/04` 14.6 节。
- **第 16 轮排期变更：第三个游戏顺延到第 17 轮，GAP-05 顺延到第 18 轮**(见 `records/02` RC-012)。
  `docs/01` 里 FR-13 与 AC 的内容一个字没改，改的只是「哪一轮做」。
- **`docs/09-用户手册.md` 首版落地**(第 15 轮)：兑现 `docs/07:538` 对红色风险 **R-09** 的承诺
  ——「把『怎么用』讲得比界面更清楚一点」。写法是**先采证据再动笔**：`uiautomator dump` 逐屏 +
  一次性脚本，共 **16 屏 / `TAP MISS` 0 / `DUMP FAILED` 0**，498 秒，日志 207 行 12,993 字节
  (脚本与日志在 `build/`，`gitignore` 覆盖，不提交)。凡实测到的字面标「实测」，
  只从源码读出的标「源码字面」，两者不混用。
- **`docs/09` 新增两节，是本项目手册与参照项目手册的主要差别**(第 15 轮)：`## 6. 它刻意不会做的事`
  列「能做到但决定不做」的 10 件事，每行给 ADR/RC 依据(不建通知通道=RC-008；零权限零接收器=ADR-009；
  不用模型=ADR-010；不显示打卡积分=RC-010/ADR-015；不留最好成绩、不预留半成品游戏=RC-010；
  间隔只往上放=ADR-013；锚点只能亲手建立=ADR-014)，让读者能分辨「没做完」与「决定不做」；
  `## 8. 做不到的事` 把欠账摊开(从未有真人用过 / GAP-02 / GAP-03 / 只验过一种环境 / 两个已知瑕疵)
  并附 GAP-01~09 逐条现状表。
- **两处数字被实测钉死**(第 15 轮)：`requiredDays = 3`(设置页原话「满 3 天就能用上」)、
  退避首档 30 分钟(「现在打招呼的间隔：30 分钟」)。
- **一处悬案结案**(第 15 轮)：`docs/01:269` 的「示例句按钮好像点不动、原因未查明」**未复现**，
  成因是上一轮探针的可点节点挑选规则(长度 ≥ 6 且含「，」或「。」)挑中了**说明段文字节点**而非按钮。
  改用坐标直点，界面确实变化(出现「我听见的」卡，再点 `愿意了解一下` 真的落库)。**该疑点作废。**
- **新增 `docs/07-风险管理计划.md`**(第 14 轮，49,294 字节 / 617 行)：13 条风险
  R-01~R-13（**红 1 / 橙 7 / 黄 4 / 绿 1**），每条「来源 / 类别 / 描述 / 触发条件 / P / I /
  等级 / 应对 / 监控信号」七项，另加「已落实措施」（逐条指向 ADR-001~016、RC-001~011、
  GAP-01~09 与实测命令）与「残余风险」两张小表。**代码一行未动。**
  与参照项目 TripWear 的 07（16 条，其中 6 条外部依赖类）形状不同：本项目 §0 先用
  可复现判据说明结构差异——网络面宽口径 1 行 / 窄口径 0 行、权限 **0**、`<receiver>` **0**、
  第三方 SDK **0**、无服务端、无模型——并明确写出结论：**不是更安全，是能做的事更少**。
- **新增 `docs/07` §5「本版本刻意不登记的风险」**(第 14 轮)：6 类在参照项目里是主要风险、
  在本项目里不成立的类别，每类写「为什么不成立 / 哪一天要补」。写它是因为若只留一张
  13 行的表，读的人只会得出「漏了」或「特别安全」两个结论，**两个都是错的**。
- **「接受」策略在本项目的地位被显式写明**(第 14 轮，`docs/07` §1.4)：本项目缺的东西
  几乎都是「必须有一个真人才拿得到」，不是「再加点代码就能补」。所以处理方式是
  **写清代价 + 定触发信号 + 记下何时重判**，而不是编一个缓解措施给自己看。
- **红风险 R-09「把『不打扰』做成『没用』，用户会卸载」有了明确落点**(第 14 轮)：
  第 15 轮的 `docs/09-用户手册` 要把「怎么用」讲得比界面更清楚，作为一次减轻；
  **真人试用是唯一能关闭它的路径**，本环境里不存在。R-01 / R-02 / R-05 同理。
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

- **结束卡上不再有第二个按钮**(第 11 轮，`records/02` RC-010)：**不提供「再来一局」**。
  一局玩完，用户面前只有一个「回去歇会儿」。想再玩一次要自己回首页再点进来——
  回家的路只有两步，但那两步足够长到让人有机会想「算了，去喝口水」。
  对 55-65 岁人群，在「刚做完一件事」的那一秒出现的引导，伤害比少玩一局大得多。
- **不留历史最好成绩、不做趋势图**(第 11 轮，`records/02` RC-010)：用时只在结束卡上显示一次
  （「这一局用了 1 分 37 秒。」+「用时只在这里显示，关掉就没了。」），
  退出即失去。一旦留下最好成绩，下一次打开就会自动出现一个比较对象，
  「玩一会儿」就变成了考核——而这正是这个模块唯一的着意处。
- **注册表里不许有不能玩的游戏**(第 11 轮，`records/02` RC-010)：
  `PlayCatalog.games` 当前只有 1 条，「敬请期待」「即将上线」这类占位在适老化产品里格外有害
  （用户会以为软件坏了），且与项目验收准则直接冲突。
- **点错的反馈被收窄为「只说一句话」**(第 11 轮)：`PlayHint` 只有三态，
  点错只改 `hint`、`step` 单调不减，**不扣分、不计数、不批评**。
  「错了几次」这个数在 `PlayRound` 里**没有字段可以放**，不是靠界面 `if` 屏蔽的。
- **里程碑偏移如实记入章程**(第 11 轮)：`docs/00` 第 7 节的 10+10 轮表改为
  「计划轮次 / 主题 / 状态 / 实际落地」四列，并注明第 3 行与第 4 行互换、
  第 10 行功能后移一轮、**推送节奏未偏**。

- **`PlayRound` 由 `data class` 改成 `sealed interface`**(第 12 轮，`records/03` ADR-016)。
  这是本轮最值钱的一条教训：**「加一个游戏」的代价不只是一条注册表记录**——必须先把
  「一局」从一个形状变成一族形状。舒尔特的「已点到第几个」与找一样的「先点中的哪一格」
  根本不是同一个概念，硬塞进一个 `data class` 只能变成两个可空字段加一堆 `if`，
  而那种结构里「另一个游戏忘了判空」是迟早的事。改成 `sealed interface` 之后，
  `PlayScreen` 的 `when` 变成**穷尽**的，第九个使用点也由编译器盯着。
  这也更正了 ADR-015 里「加游戏只多写一条注册」的判断——**部分不成立**。
- **宣称词表收到中立位置 `domain/voice/ClaimWords.kt`**(第 12 轮，`records/02` RC-011，GAP-07)。
  `domain/play/PlayWords.kt` **删除、不留转发壳**。放在 `domain/voice/` 而不是
  `domain/play/` 的理由：这条规则是**全 App** 的(`docs/01` AC-11.1)，
  挂在某一个功能目录下就会让人以为它只管那个功能——第 11 轮两张表分裂正是这么来的。
  收口后扫描命中由 16 行降到 **15 行**，且全在词表定义行。
- **「找出一样的」的完成卡不显示用时**(第 12 轮，`records/03` ADR-016 决策五)。
  舒尔特报「这一局用了 1 分 38 秒」，找一样的**一个字都不报**：它的完成卡上
  `doneLine` 的 `FindPairRound` 分支里**没有 `seconds` 这个入参可以引用**，
  不是靠界面 `if` 屏蔽。理由：这个游戏点错只是重选，用时主要反映「看不看得清」，
  报出来更接近一次视力测评而不是消遣(`docs/03` 第 4.1 节、`docs/01` AC-15.3)。
- **游戏选择页只在真有两个游戏时出现**(第 12 轮)。`PlayViewModel` 的初值是
  `if (games.size == 1) Ready(games.first()) else Choose(games)`——
  只有一个游戏时多问一句「你想玩哪个」是纯粹的多余一屏。这条也让第 11 轮的三态测试
  不必全改：只有一个游戏的世界里，状态机退化成原来的样子。
- **引导屏文案改为与事实相符**(第 12 轮，GAP-08)。`ui/onboard/OnboardingScreen.kt`
  把「这个版本还很素……陪您聊聊天……这些都还在做」换成
  「上面这些都在这一版里，不用再打开什么开关，它也不会弹窗通知您。」，
  并把「有一个小游戏」改成「有两个小游戏」。**改文案不是小事**：
  原来那句把三个已经能用的入口说成「还在做」，用户读完会去找那个不存在的开关。
- **`domain` 层新增两条合法依赖方向**(第 12 轮)：`domain/companion → domain/voice`
  与 `ui/play → domain/voice`。两个新目录对 `android.*` 的 import 仍是 **0 行**，
  `domain/` 全目录的 Android 依赖仍是 **0**；`domain/play` + `domain/voice` 合计
  3 行 import，全是 `kotlin.random.Random`。

- **新增 `docs/05-接口设计说明书.md`**(第 13 轮，**文档，无用户可见变化**)。
  60,509 字节 / 1,024 行，按**第 13 轮代码现状**笔写：外部接口 3 项 + 平台入口 3 项、
  内部数据层 11 组(IF-03~IF-13)、领域契约 10 组(IF-14a~IF-14j)、界面契约 5 组(IF-15~IF-19)，
  每一组都写全四件事：**签名 / 谁调它 / 保证什么 / 不保证什么**。
  **本轮代码一行未动**，不产生任何新的测试或构建数字——第 12 轮那四个数字继续有效。
- **接口文档把「刻意没有的东西」也写成条目**(第 13 轮)：§2.2 单列四个**故意不存在**的服务面
  ——网络、通知、Alarm/WorkManager、权限——每条附理由。四个面每一个都是「顺手就能加、
  加了就违背产品立场」的东西（没有后端；打扰正是要避免的；定时唤醒就是按我们的钟点
  去打断用户；清单里权限 **0** 条、`<receiver>` **0** 个）。写「没有」是为将来想加的人
  先设一道要回答的问题。
- **这一份是与 TripWear 差别最大的一份文档**(第 13 轮)：TripWear 的 05 主体是网络 API
  清单；常伴**一个网络接口都没有**，所以 §2 改成「Android 系统服务与平台入口」。

### Fixed

- **`docs/06` 初稿里两处自己写错的措辞**（第 19 轮，改的都是**这份新文档自己的字**，产品代码零改动）：
  ① §3.1 原把禁用词写成「三张禁用词表」，读起来像有三份各自独立的来源。核实到的代码事实是：
  `domain/voice/ClaimWords.kt:21-38` 的 `banned` 是 **15 个词**的**单一来源**（训练/改善/提升/延缓/评估/
  认知能力/疗效/疗程/治疗/预防/诊断/康复/大脑退化/记忆力/变聪明），`:40` 的 `allowedWords` 是「玩/试试/
  大脑体操」，由 `ClaimWordsTest`(7 例) 守；另两处是**内联在测试里**的短表——`ui/home/InterventionVoiceTest.kt:212`
  （不许承诺通知或排期）与 `:221`（不许评判或催促用户）。改后只陈述这三处的**位置与内容**，不再暗示统一来源。
  ② §9.1 原把「按『分』查完成卡会命中『用了 1 分 37 秒』」列成产品缺陷 **P-06**——重查确认那是**走查脚本的
  子串假阳性**（不是用户看得见的问题），已从产品侧移出、改列到 §9.2 的 **T-14**；§9.1 结论句同时改为
  「**19 轮里**只有 4 条（P-01/02/03/04）真的进了交付物；P-05 是设计期自查抓到的（未进入任何构建物）；
  第 11 轮那条是脚本假阳性，不是产品缺陷」。**产品缺陷账本轮零新增。**

- **测试侧缺陷三处，产品侧零缺陷**（第 18 轮）：本轮改的全是测试与走查脚本自己。
  ① `A11ySemanticsTest.assertGrid` 原用 `expectValue(CollectionInfo(r, c))` 断言，**永远是红的**——
  `javap` 查实 Compose UI 1.7.5 的 `CollectionInfo` / `CollectionItemInfo` **都没有重写 `equals`**，比较
  退化成引用相等，构造出来的新实例永不等于节点上那个。这是「**永远红**」不是假绿，但一样会让守卫失效。
  改成**自造 `SemanticsMatcher` 按字段读值**（`A11ySemanticsTest:232-241`）。
  ② 算一算的棋盘被误写成 **3 行**；真实行数由 `(round.cells.size + game.columns - 1) / game.columns` 现算
  （`ui/play/PlayScreen.kt:189`），是 **1 行 3 列**，已改（`A11ySemanticsTest:208-211`）。
  ③ `build/walk18.py` 两处环境问题：仪器化测试跑完会**卸载 APK**、且前台被 `com.android.camera2` 占着 →
  补 `pm install -r -t` + `force-stop` + `KEYCODE_HOME`；设置页一屏放不下 → `reach` 拆成两次。都不是产品问题。

- **引导第 1 页写「有两个小游戏」，而选择页有三张卡**(第 17 轮实测并修复)。`ui/onboard/OnboardVoice.kt:44`
  改成「手上闲不住的时候，有三个小游戏，不算分也不排名次。」。**是走查脚本逼出来的**——先把引导第一页
  **逐字**转储，再对着 `PlayCatalog.games.size`(= 3) 看，2 ≠ 3。已加两条断言钉住(必须含「有三个小游戏」、
  必须不含「两个小游戏」，`build/screens17.log`)。它同时说明：**游戏款数这种数字会随功能漂移，而引导页
  文案里的数字目前没有任何机器守卫**(已记 `docs/07` 风险条目)。属 GAP-08 的补记。

- **起点卡「这一步做完了」的祝贺语双句号已修**(第 16 轮)。第 15 轮设备走查抓到的屏幕原话是
  `…留下最顺手的那一支。。这件事是做完了的…`——`firstStep` 正文自带「。」，鼓励语模板又加了一个。
  修法是在拼接处判断句末标点(`sentenceEnds` = 「。」「！」「？」「…」)，**不改 39 条 `firstStep` 正文**。
  回归覆盖全量：`noEncouragementHasDoubledPunctuation` 遍历 `StarterCatalog.all` 的**全部 39 条**，断言不含
  `。。` / `！！` / `？？` / `，。` / `。，` / `。！` / `。？` 且以「。」结尾；另一例
  `forActionAddsAPeriodWhenTheActionHasNone` 管住反向情形。`EncouragementTest` 10 → **12 例**。
  设备复核：`build/screens16.log:172` 读到的是**单句号**那句，日志全文检索六类双标点 **0 处**。
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

- **格子的第一版用 `aspectRatio(1f)` 做正方形，大字号下会裁字**(第 11 轮，设计期发现，
  **未进入任何构建产物**)。改成 `heightIn(min = ElderDimens.minTouchTarget)`，
  格子可以变高、内容区本来就能滚。改完实测：三档字号下 25 格 / 5 行全部在屏内，
  最小行距 158px ÷ 2.625 = 60.2dp ≥ 56dp。
- **第 11 轮没有已发布行为的缺陷**。本轮两次测试运行都是首跑即绿
  （JVM 318 例 / 24 类、设备 95 例 / 15 类），没有出现红，
  `logcat -b crash -d` 全程无输出。唯一需要说明的是**走查脚本自身的假阳性**：
  按 `分` 查完成卡会命中「这一局用了 **1 分** 37 秒」里的分钟，
  那是脚本判据写得糙，**不是**产品里出现了分数。
  这条也写进 `records/04` 第 11.2 与 11.6 节，防止后来者把它当成「没有分数」的证据。
- **第 12 轮没有已发布行为的缺陷**。设备侧首跑（96 例 / 15 类）出现 **3 条红**，
  逐条定性后**全部落在测试侧，没有一条是产品缺陷**：① 我自己把断言写错——
  点中一对之后提示行会被清空，我却断言它非空；② `HomeScreen.kt:89` 首帧是 `Loading`，
  Room / DataStore 的加载不归 Compose idling 管，`tap()` 在此之前就点了，
  属**先已存在**的测试竞态，改成先 `waitUntil` 再点；③
  `TalkInputTest > 全部清空要先问一次再真的清掉` 超时，第 2 次运行全绿，
  定性为 **flake**（没有改任何代码）。改完第 2 次运行 96 例全绿。
- **第 11 轮记下的「APK 字节数存疑」在本轮收口**。第 11 轮记的是 11,012,408 字节，
  与第 10 轮完全相同；本轮跑 `./gradlew :app:assembleDebug --rerun-tasks` 拿到
  **10,971,528 字节**（mtime `2026-11-21 12:35:31`；同一轮收尾前为在最新产物上重跑走查
  又构建了一次，字节数仍是 10,971,528，mtime `2026-11-21 13:13:06`），并已确认 `build/outputs/apk/debug/`
  下没有第二份 APK、第 11 轮那个数字确实出自一次 UP-TO-DATE 运行。
  这条不列为缺陷——它**从未进入任何交付物**，两个数字描述的是同一份代码是否被重新打包。
  详见 `docs/03` 第 8.1 节与 `records/04` 第 12.6 节。

### Known Issues

- **`docs/06` 引进一处长期维护责任：四层数字现在写在两处**（第 19 轮）：`records/04` 的各轮小节与
  `docs/06` 的最新值**要一起改**，漏改一处两份文档就会对不上。本轮只在 `docs/06` 里写明「数字以
  `records/04` 的逐轮实测为准」，**没有任何机械守卫**防止两处漂移——这条账记在这里。
- **`docs/06` 里的 L3 / L4 数字本轮没有重跑**（第 19 轮）：16 类 / 102 例与 88 行都是**第 18 轮**那一刻的
  事实的**转抄**；若模拟器或环境变了，这两个数字不会自己更新（见 `Unverified`）。

- **「读屏」这条路径在需求里没有编号**（第 18 轮）：`docs/01` 里跟读屏有关的只有 GAP-05 一行缺口（本轮关闭），
  **没有任何 FR / AC / NFR 承诺「语义标签覆盖到什么程度」**。因此将来若有人删掉 `A11y.kt`，**机械上不会
  有任何需求层的东西拦着**（只有那 8 个测试会红）。不给它补编号的理由见 `records/02` RC-017。这条账记在这里。
- **首页问候语与「说两句」的结果卡刻意不加 `liveRegion`**（第 18 轮）：它们是**新出现的节点**，而「新节点
  会不会被朗读」本项目**没有实测过**——这是「不知道、选择不碰」，**不是**「已知不会读」（`records/03`
  ADR-019 第 1、3 条）。
- **装饰图标的豁免只覆盖 1 处，且守卫有盲区**（第 18 轮）：`a11y-decorative` 全仓恰好 1 处；
  `A11yPlacementTest` 只在 `ui/` 一层检查，且要求先扫到 ≥3 个 `Icon(` 才动手——覆盖率靠**阈值**而不是全量巡检。
- **空字符串的提示行仍生成带 `LiveRegion` 的语义节点**（第 18 轮实测）：这是有意保留的（节点始终占位，
  才有「原地换字」这个触发时刻），但它会不会引起多读**没有测**。

- **引导页文案里的数字没有机器守卫**(第 17 轮)。只有走查脚本里针对「有三个小游戏」那两句的断言
  (`build/screens17.log`)，不是通用机制：下次游戏数再变，`ui/onboard/OnboardVoice.kt:44` 与那两条断言
  必须一起改，漏改一处只会在下一次走查时变红。
- **`domain/` 层没有机械守卫**(仍是评审项)：第 16 轮的 138 处收口只覆盖 `ui/` 一层，见 `Unverified`。
- **`docs/06-测试计划与用例.md` 已于第 19 轮补齐**（原为第 18 轮仍未写）：文件 35,822 字节 / 468 行 /
  纯 LF、11 节，把四层数字（L1 29 类 / 370 例、L2 APK 11,052,250 字节、L3 16 类 / 102 例、L4 88 行
  走查日志）与需求→测试追溯矩阵、缺口清单、缺陷清单、回归清单汇总到一处。数字仍以 `records/04`
  的逐轮实测为准，`docs/06` 只是**汇总视图、不是替代品**（`records/02` RC-018）。

- **起点卡「这一步做完了」的祝贺语有双句号**(第 15 轮实测)。屏幕原话是
  `…留下最顺手的那一支。。这件事是做完了的…`——`firstStep` 正文自带「。」，鼓励语模板又加了一个。
  **已于第 16 轮修复**，见 `Fixed` 一节；回归覆盖全部 39 条 `firstStep`，设备复核见 `records/04` 14.5 节。
- **引导第 1 页没有任何无障碍语义标签**（第 15 轮实测 **0 条**；**已于第 18 轮关闭**）。即 `docs/01:479`
  的 GAP-05：第 18 轮新建 `ui/common/A11y.kt`（58 行 / 5 个修饰符）并挂到 9 个点——引导三页的表头都成了
  标题，页码走 `stateDescription`（**不显示在屏幕上**，所以走查里搜不到「第 N 页 / 共 3 页」，
  `build/screens18.log`）。`docs/01` GAP-05 那一行同步改成「已关闭」；见 `records/03` ADR-019 与
  `records/02` RC-017。
- **设备时区是 GMT**(第 15 轮实测，`adb shell date` = `Sat Nov 21 07:31:12 GMT 2026`，
  `persist.sys.timezone` = `GMT`)。首页在 07:31 显示「早上好」是对的、**不是 bug**；
  已写进 `docs/09` 的 2.1 与 8.4(「只在一种环境里验过」)。
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

21. **首页「玩一会儿」里只有 1 个游戏**(第 11 轮)。`PlayCatalog.games` 目前只有一条
    （`SchulteGrid.game`），用户看不到这是个能往里加东西的框架，只会看到一个孤零零的
    舒尔特方格。三个新游戏顺延到第 12~14 轮（`docs/00` 第 7 节的里程碑偏移如实记）。
    **这不是占位**：唯一注册的那个游戏是完整可玩的，注册表里没有任何点不开的条目
    （`docs/01` AC-11.4）。
22. **25 个格子没有逐个 `contentDescription`**(第 11 轮)。读屏会念出格子里的数字，
    但**不会**告知「这个已经点过了」——点过的格子只是颜色压暗（`PlayCell`）。
    靠颜色区分、不靠朗读区分，对低视力但用 TalkBack 的用户是不够的。
    与 GAP-05（第 17 轮做这一页的读屏走查）是同一处，见 `docs/03` 第 8 节。
23. **一局的用时关掉就没了**(第 11 轮)。结束卡上写着「用时只在这里显示，关掉就没了」，
    这是**刻意的**（`records/02` RC-010：不留最好成绩、不做趋势、不诱导再来一局），
    结构上也没有地方可以存它（不落库，见 `records/03` ADR-015）。
    代价是：用户如果想「再看一眼刚才用了多久」，本版本做不到。

24. **「找出一样的」的 16 格也没有逐个 `contentDescription`**(第 12 轮)。
    与第 22 条同一个坑，而这个游戏**更依赖视觉**：读屏会念出 16 个字，
    但既**不会**告知「这个已经点过了」，也**不会**念出「哪两张是一样的」——
    后者恰恰是这一局的全部内容。GAP-05（第 17 轮）的一部分，
    本轮既没有解决，也没有实测影响面。
25. **`PlayCatalog` 仍是手写列表，加游戏要改三处**(第 12 轮)。
    第 12 轮加「找出一样的」的实测代价：新的 `data class FindPairRound`、
    一个出局函数、注册表一条，**外加一次 `PlayRound` 改型**（`records/03` ADR-016）。
    **没有**做插件化或自动发现——两个元素不值得。顺延的三个游戏在第 13~15 轮，
    框架将来共 **5** 个。
26. **进了某一局就不能就地换成另一个游戏**(第 12 轮)。想换得先「回去歇会儿」
    回首页再进来。这是**刻意的**（`docs/01` AC-11.4：结束卡上只有一个退路按钮，
    `docs/03` 第 3.2 节：「刻意不含的东西」里有「换一个游戏」），
    代价是多点两下，好处是不在多处开口子。
27. **「找出一样的」的词池只有 20 个词**(第 12 轮，`FindPair.kt:34-39`)。
    每局抽 15 个不重复的再补一次重复，所以多玩几局会明显看到重复的物件。
    没有做词池轮换或「刚玩过的不再出」——那需要跨局状态，与 DR-09「一局不落库」
    直接冲突。

### Unverified

- **屏幕级仍然拿不到 `refresh()`**（第 31 轮）：新口子让屏幕级用例能固定「现在」，但测试**回不到**
  `HomeViewModel` 那个私有的 `refresh()` ⇒ 「到点自动改口」（`CLOCK_POLL_MILLIS = 60_000L` 到点
  重算问候语）仍然只在 `ui.home.HomeViewModelTest` 层验。30.3 那 4 例之所以写成「先定钟、再写库、
  最后 `show()`」，就是这个原因。
- **四条用例只覆盖了一支**（第 31 轮）：全部落在「空库 / `dayCount <= 1`」→
  `Speak(ASSUMED_NOT_ENOUGH_DATA)` 这条路上；`LEARNED`（已经认识 N 天了）与 `TOO_SOON`
  两个分支**都没有在屏幕层验过**。
- **钟点只取了三个**（第 31 轮，09:00 / 14:30 / 23:30）：`DayPart` 其余时段（含 12、13 点这两个
  「不在上午也不在下午」的边界钟点）未测；`fallbackWakeAt` / `fallbackSleepAt` 那两个**边界分钟**
  （07:00、22:30）未测——**边界落在哪一分钟属推断，不是实测**。
- **`AppContainer` 没有 JVM 单测**（第 31 轮，继承未变）：它要 `Context` ⇒ 这条口子只能在设备上量。
- **本轮没有做变异实验**（第 31 轮）：「打坏必红」的证据累计仍只有第 28 轮
  （`SettingsScreen.kt:175` 换 `// MUTATION-28:`，7 例里恰好 1 例红）与第 30 轮
  （`SettingsScreen.kt:86` 换 `// MUTATION-30:`，`Tests run: 8, Failures: 1`）那两处。
  ⇒ 本轮新加那 4 例的断言**能不能红，本轮未被检验**。
- **两笔工作区证据被本轮覆盖**（第 31 轮）：①`build/screens30.log` —— `build/probe30.py` 里
  `LOG_PATH = sys.argv[1] if len(sys.argv) > 1 else "build/screens30.log"`，本轮只重定向了 stdout，
  脚本自己那份**写回了第 30 轮的文件名**；两份 `md5sum` 相同 = `88c1c3446aa5b703ea8c4848ad356eda`；
  第 30 轮记 656 行、本轮 655 行、`OK` 同为 128 ⇒ 差的那 **1 行已无法核对**，`records/01` 第 30 轮
  条目与 `docs/06` §3.9 里引用的 `build/screens30.log:169` 行号**从此不可复核**。
  ②`build/r30/` 的 11 张截图**全部被同名覆盖**（其中 6 张还留着第 27 轮的名字：`anchor_ask27.png` /
  `anchor_created27.png` / `anchor_deleted27.png` / `starters27.png` / `talk27.png` /
  `talk_heard27.png`）。`build/` 不入库 ⇒ 影响限于工作区，但**行号引用失效是真的**。
- **L2 的 APK 比第 30 轮大 37,392 字节，未解释**（第 31 轮）：11,090,224（`5b60eedf44b73a2fc5b5698a0e10d540`）
  vs 11,052,832（`a281a1f7404711f7f5cdc2a48cfd9d06`）。本轮 `app/src/main` 的净变化只有「多一个带默认值的
  构造参数 + KDoc」与 `androidTest` 里一个新类，**这个增量与实际字节增量对不上**；本轮**没有去逐 dex 核**。
- **`AppContainer` 的构造签名已经收到第四个参数**（第 31 轮，继承未变）：它正在变成一块「测试接线
  面板」；**是否该拆开（比如单独一个 `TestWiring` 数据类）未决**。
- **本轮走查没有做 crash 日志检查**（第 31 轮）：沿用的 `build/probe30.py` 里**没有**
  `adb logcat -b crash` 那一段 ⇒ **本轮不宣称「crash 缓冲区 0 行」**。不做没有做的事。
- **11 张截图仍然没有目视看过**（第 31 轮，累计 50 张）：`build/r30/` 下那 11 个 png——上面给的
  都是**坐标 / 高度 / 次数**这些数字，不是「看过」。
- **走查里对不上中文**（第 31 轮，继承）：中文 `adb shell input text` 会 NPE
  （`InputShellCommand.sendText` → `Attempt to get length of null array`），走查只能打 ASCII，
  称呼固定 `LaoXiong` ⇒ **GAP-02（真实中文输入法）仍空**。
- **`performTextInput` 不是真 IME**（第 31 轮，继承）：它往语义节点里塞文本、绕过输入法 ⇒
  证明不了「真实中文输入法下也能改地址」。
- **`am instrument` 的进程退出码恒为 0**（第 31 轮，继承的工具坑）：`adb shell am instrument`
  无论红绿都返回 0，红不红**只能看日志里的 `Tests run: N, Failures: M`**（本轮 137 例全绿，
  但这是**读日志**得出的，不是读退出码得出的）。⇒ 用 `$?` 判定仪器化结果是错的读法。
- **AC-13.3 不能排除源码写死同一字面量**（第 31 轮，继承）：`InterventionVoice.kt:78` 处没有可引用的常量。
- **本轮不推送**（第 31 轮）：按「每满十轮推一次」，**下一次推送是第 40 轮末**。**推送前的远端**是
  `0c40292af6ae8c1f991885c61aec4d2043aae8ff`（第 30 轮那次推送），本轮提交是**下一批的第一笔未推**。
  **本文件写在推送之前**，所以**不写一个还不存在的 sha**——推送后的远端 sha 见本轮最终答复，
  可用 `git rev-parse origin/main` 复核。
- 继续挂着（未变）：`longestSeconds` 未改名；「大概 3 分钟」仍是估时不是测量值；首页坐标位移 **79 px**
  未复测；**GAP-03（NFR-08 需真机）未排**；第 21 / 23 轮的性能数字对应**另一份 APK**（md5 `84327b14…`）；
  `classes10.dex` 进 zip 的次序**仍未查清**；`settleQuietly()` 的 300 ms 口径；`RhythmCollectorTest`
  的 `join` 只覆盖快照时在飞的 job；第 25 轮 6 张截图被覆盖（永久损失）；旧 `@After` 顺序
  「会产生可观察的数据级失败」**仍不可构造**；`Stage.RESUMED == 0` 只在定向的 10 例上实测；
  `getActivitiesInStage` 的主线程要求是照文档推断；规则只保证「关库在组合之后」不保证「在飞写库已结束」；
  **APK 的 md5 不是内容度量**；`自己的存储` 的两份文件不事后删；`intervention` 的 md5 跨种子不能互比；
  `TestTeardown.关库排在组合之后` 在 `container` 未初始化时抛 lateinit 掩真错；`ensure_eq_any`
  只是「上下各滑 3 次」的有限补偿；**无真机、没戴耳机听过 TalkBack**。

- **「回执不回撤是不是更好」没有被目标用户验证过**（第 30 轮）：把「2 秒自己走掉」改成「下一次动手
  才撤」是本轮的设计判断。原来的做法担心的是**没看到**，现在的做法担心的是**嫌啰嗦**——**两头都
  没有**用 55-65 岁的目标用户做过对比。`docs/07-风险管理计划.md:257` 已把这个风险方向反转记下来，
  但那是**写下风险**，不是**排掉风险**。
- **L3 首跑崩在 119/133 处，触发条件没有定位到**（第 30 轮）：同一颗包，首跑
  `Tests 119/133 completed. (0 skipped) (1 failed)` → `Test run failed to complete. Instrumentation run
  failed due to Process crashed.` → `BUILD FAILED in 1m 53s`；红例
  `TalkViewModelTest > 说了没被听懂的话也算接话，但没有意愿落库`，异常
  `java.lang.IllegalStateException: attempt to re-open an already-closed object: SQLiteDatabase:
  /data/user/0/com.fortheelder.debug/databases/elder-test-talk-vm.db`（栈落在
  `SQLiteClosable.acquireReference` ← `SQLiteDatabase.inTransaction` ← `RoomDatabase.query` ←
  `WishDao_Impl$10.call` ← `CoroutinesRoom$createFlow`）。XML 重算 `tests=119 failures=1`，
  ⇒ 相对全量 **133 例丢了 14 例**（`TalkViewModelTest` 13→4 丢 9、`ThemeFontScaleTest` 5→0 丢 5）。
  **重跑一次 133/133 全绿**。根因判断（`ElderDatabase.open(...)` 建库**没设 executor**，收尾关库时
  有一笔写库还在飞）是**对栈的读法，不是构造出来的复现**；本轮**没有**去改 executor。
  ⇒ **「重跑绿了」不等于「不崩了」。**
- **`ThemeFontScaleTest` 那 5 例本轮只在重跑里跑过一次**（第 30 轮）：首跑里它整类没跑到（5→0）。
- **字号卡那条上界（1966）只在这一次跑里测到过**（第 30 轮）：它是本轮的实测值，
  **不是**每次都成立的不变量。
- **首跑走查被软键盘遮挡断过一次**（第 30 轮）：修法是先收键盘再 dump；但「**收键盘会不会影响别的
  断言**」（例如某一步本来就依赖键盘在屏上的位置）本轮**没有逐条排查**。
- **11 张截图仍然没有目视看过**（第 30 轮，累计 50 张）：`build/r30/` 下那 11 个 png——上面给的都是
  **坐标 / 高度 / 次数**这些数字，不是「看过」。其中 6 张的文件名还是第 27 轮留下的残留名
  （`anchor_ask27.png` / `anchor_created27.png` / `anchor_deleted27.png` / `starters27.png` /
  `talk27.png` / `talk_heard27.png`），**本轮不改名、照实记**。
- **本轮走查没有做 crash 日志检查**（第 30 轮）：`probe30.py` 里**没有** `adb logcat -b crash` 那一段
  ⇒ **本轮不宣称「crash 缓冲区 0 行」**。不做没有做的事。
- **变异实验只打了 1 处**（第 30 轮）：只打坏 `SettingsScreen.kt:86` 那一行。
  `SettingsScreenTest` 另 **7 例没有各自的负例** ⇒ 「这 7 例里的断言真的能红」**本轮未被检验**。
  仓库里「打坏必红」的证据累计只有两处：第 28 轮那次（`SettingsScreen.kt:175` 换
  `// MUTATION-28:`，7 例里恰好 1 例红）与本轮这次（8 例里恰好 1 例红）。
- **`am instrument` 的进程退出码是 0**（第 30 轮的工具坑）：`adb shell am instrument` 无论红绿都返回 0，
  红不红**只能看日志里的 `Tests run: N, Failures: M`**（本轮 `build/mut30_instrument.log` 就是
  `Tests run: 8, Failures: 1` 而退出码 0）。⇒ 用 `$?` 判定仪器化结果是错的读法。
- **屏幕级用例固定不了时钟**（第 30 轮，继承）：`AppContainer.clock` 写死是系统钟，AC-13.1 的
  「那句话里带着新称呼」那一半仍只在 `ui.home.HomeViewModelTest` 层验；AC-13.3 **不能排除**
  源码写死同一字面量。
- **`performTextInput` 不是真 IME**（第 30 轮，继承）：它往语义节点里塞文本、绕过输入法，
  ⇒ 证明不了「真实中文输入法下也能改地址」。**GAP-02（中文真实输入法）仍空**。
- **走查名称仍只能用 ASCII**（第 30 轮）：中文 `adb input text` 会 NPE（`InputShellCommand.sendText`
  → `Attempt to get length of null array`），称呼固定 `LaoXiong`。
- **`docs/01` 的改动没有使该文件行号移动**（第 30 轮）：AC-13.1 / AC-13.2 是**同一行内只替换
  「当前证据」列**，编号与「标准」列一字未动；全仓 `grep 回执 docs/01` 只命中 2 处，两处都在本轮
  新加的证据列里 ⇒ **没有任何一条已基线需求写过「回执活多久、住哪张卡」**（结论见 `records/02` RC-027）。
- **本轮推送是第 21~30 轮共 10 笔一起推**（第 30 轮）：推送**前**远端是
  `a0a9d4095cc20560c47db02bed7019dc7a14c019`（第 20 轮那次）。**本文件写在推送之前**，所以
  **不写一个还不存在的 sha**——推送后的远端 sha 见本轮最终答复，可用 `git rev-parse origin/main` 复核。
- 继续挂着（未变）：`longestSeconds` 未改名；「大概 3 分钟」仍是估时；首页坐标位移 **79 px** 未复测；
  **GAP-03（NFR-08 需真机）未排**；第 21 / 23 轮的性能数字对应**另一份 APK**（md5 `84327b14…`）；
  `classes10.dex` 进 zip 的次序**仍未查清**；`settleQuietly()` 的 300 ms 口径；
  `RhythmCollectorTest` 的 `join` 只覆盖快照时在飞的 job；第 25 轮 6 张截图被覆盖（永久损失）；
  **旧 `@After` 顺序「会产生可观察的数据级失败」仍不可构造**；`Stage.RESUMED == 0` 只在定向的 10 例上
  实测；`getActivitiesInStage` 的主线程要求是照文档推断；规则只保证「关库在组合之后」不保证
  「在飞写库已结束」；**`APK` 的 md5 不是内容度量**；`自己的存储` 的两份文件不事后删；
  `intervention` 的 md5 跨种子不能互比；`TestTeardown.关库排在组合之后` 在 `container` 未初始化时
  抛 lateinit 掩真错；`HomeScreenTest` 是 `HomeViewModelTest` KDoc:54 的**悬挂引用**；
  `ensure_eq_any` 只是「上下各滑 3 次」的有限补偿；**无真机、没戴耳机听过 TalkBack**。

- **「真实偏好有没有被改」这条证据走的是另一条流水线**（第 29 轮）：它用
  `adb install -r` + `am instrument` 直接跑，**不是** `./gradlew connectedDebugAndroidTest` 那条
  （gradle 跑完 AGP 会把 app 与 test 两个 APK 连应用数据一起卸掉，`records/04` 已记过多次）。
  两条流水线跑的**是同一份被编译出来的 androidTest 代码**，但「谁装的包」不同，
  ⇒ **不能把这条对照的结论直接搬到 gradle 那条上**。
- **改动前那一跑里，app APK 并没有被重打包**（第 29 轮）：改动生效前那一次，`assembleDebug` 是
  `38 up-to-date`，装在机器上的 app 还是旧产物（mtime 未变）。**新旧默认值都指向真实 DataStore**
  （旧的直接取进程单例，新的默认参数取同一对象），所以这处差异对本次测量的中性是**推理，不是实测**。
- **`intervention.preferences_pb` 跨种子不能互比**（第 29 轮）：本轮只做**同一种子内**的前后对比
  （改前 md5 `c7bc0179d1ac688552584be177c02b39` → 改后 `0c94a5656737e961093eec9c8a05dd02`，
  这两个数**不能**直接读成「文件没被碰」或「被碰了」）。节流状态本来就随上一次运行残留。
- **`自己的存储` 造出来的两份文件不事后删**（第 29 轮）：它们落在 `context.cacheDir`，跑完留在设备上；
  而且 `PreferenceDataStoreFactory.create` 的 `scope` 用了默认作用域 ⇒ **每个用例都会留下一个不会取消的
  DataStore 实例**，靠 GC 收。132 例跑完没有观察到问题，**但这不是「没有泄漏」的证明**。
- **5 个类换成空存储后本轮没有新红**（第 29 轮）：19 类 / 132 例全绿，**但没有**证明「以后每次都不会红」——
  本轮只跑了一遍。用例里凡是依赖「上一轮留下的设置 / 节流状态」的写法，此后拿到的是干净存储，
  行为可能与第 28 轮及更早不同。
- **本轮没有做任何注入负例**（第 29 轮）：`records/04` 第 28.7 节照实写着「本节没有打坏任何一处源码」。
  仓库里**唯一**一处「打坏必红」仍只有第 28 轮那次
  （`SettingsScreen.kt:175` 换成 `// MUTATION-28:`，7 例里恰好 1 例红）。
  ⇒ 「这 5 个类里的断言真的能红」**本轮未被检验**。
- **`TestTeardown.kt` 的 `关库排在组合之后` 在 `container` 未初始化时会抛 lateinit 异常**（第 29 轮）：
  那是 `@After`，一旦 `@Before` 在 `AppContainer` 之前就失败，这个 `@After` 会用自己的异常盖掉真正的错因。
  **既有行为，本轮不修，照实记。**
- **`build/r29/*_27.png` 文件名残留**（第 29 轮）：`probe29.py` 是从 `probe28.py` 生成的
  （`build/mkprobe29.py` 做了 6 处替换），其中一处 `.replace("28", "")` 剥不掉已经变成 `27` 的旧后缀，
  于是截图名里留下了 `_27`。**本轮不改名、照实记**。
- **10 张截图仍然没有目视看过**（第 29 轮，累计 39 张）：`build/r29/` 下那 10 个 png——上面给的都是
  **文件字节数**，不是「看过」。
- **`build/l3_29_xml.xml` 与 `build/l3_28_xml.xml` 同为 18,416 字节**（第 29 轮）：两份规模一样
  （19 类 / 132 例），字节数相同是**合理**的，但**不当证据**用。
- **走查名称只能用 ASCII**（第 29 轮）：`adb input text` 打中文会 NPE；**GAP-02（中文真实输入法）仍空**；
  **GAP-03（性能基线）仍未排**；**无真机、没戴耳机听过 TalkBack**；**首页坐标位移 79 px 未复测**。
- **第 21 / 23 轮的性能数字对应另一份 APK**（md5 `84327b14…`，第 29 轮未重跑）；
  `longestSeconds` 仍未改名；「大概 3 分钟」仍是估时不是测量值。
- **本轮不推送**：按「每十次迭代推一次」，**下一次推送是第 30 轮末**；远端此刻仍是
  `a0a9d4095cc20560c47db02bed7019dc7a14c019`（第 20 轮推送后），本地已积 `69b49ec`（21）、
  `d83b994`（22）、`44691f5`（23）、`402a1a2`（24）、`4eacd11`（25）、`60478a9`（26）、`dfa2a3b`（27）、
  `0be3d87`（28）**八笔未推**，本轮提交是**第九笔**。

- **APK 的 md5 不是内容度量——同一份源码，同一台机器，三个不同 md5**（第 28 轮新实测，
  `app/src/main` 净变化为零）：① 全 up-to-date 直接用磁盘旧产物 → **`462e4726e886a25e93d02f1bdd26ac14`** /
  10,971,528 字节（`build/l2_28.log`，`38 actionable tasks: 38 up-to-date`、`BUILD SUCCESSFUL in 1s`）；
  ② 反例还原之后 → **`be1ac132e75d95cf890d7a3d96df387c`** / **11,052,250** 字节（`build/l2_28_restore.log`，
  `1 executed, 4 from cache, 33 up-to-date`）；③ `--rerun-tasks` 全量重打 →
  **`35513690e04a32aa9321d1ce55a19f5e`** / 10,971,528 字节（`build/l2_28_rerun.log`，`38 executed`、
  `BUILD SUCCESSFUL in 14s`）。⇒ **「md5 变了」不能推出「代码变了」，「md5 没变」也不能推出「重新验证过交付物」**；
  第 26 / 27 轮那份「与基线逐字节相同」的表述，本轮起应当读作「没被本轮改动影响」。**本轮 L4 走查装的是
  第三颗（`35513690…`），不是全 up-to-date 时留在磁盘上的 `462e4726…`。**
- **L4 首跑那 2 条红是脚本的滚动假设，不是应用回归**（第 28 轮）：第一跑 `build/screens28_first.log`
  **594 行 / 111 条 `OK` / 1 条 `NOTE` / 2 条 `!!`**、`RESULT=FAIL`。① `check(count_eq(nodes,"先不记")==1)`
  那次 dump 只保证「想起来就做」在屏上，「先不记」在其下方被顶出屏外；② `DONE_LINE` 在**视口上方**而
  `ensure_eq` **只往下滑**。⇒ 两条根因都在脚本上。已加 `ensure_eq_any` 修好，重跑 **`RESULT=PASS`**
  （`build/screens28.log` 578 行 / 113 条 `OK` / 0 条 `NOTE` / 0 条 `!!`）。**但「脚本从此不再因滚动失误
  假红」没有被证明**——`ensure_eq_any` 只是「上下各滑 3 次」的**有限补偿**。
- **反例只打了一处**（第 28 轮）：只把 `app/src/main/.../settings/SettingsScreen.kt:175` 的
  `container.settings.resetToDefaults()` 换成 `// MUTATION-28:` 注释跑过一次——`MUT_EXIT=1`、
  `Starting 7 tests` / `Finished 7 tests` / `BUILD FAILED in 54s`，XML `tests="7" failures="1"`，
  **恰好**「确认清空设置之后就回到引导页」这一例红、另外 6 例全绿（含走「点不清」分支那条）；
  还原后 md5 回到 `5384c0bdfe42338d50cba8c693887713`、无 `MUTATION-28` 残留、
  `git diff --stat -- app/src/main` 空。**7 例里另外 6 例没有做过「打坏必红」的检验。**
- **`SettingsScreenTest` 摸的是与应用共用的 `settings` DataStore，隔离性明显弱于库那条**（第 28 轮）：
  `AppContainer` 只给数据库开了测试口子（`databaseName`，本类用 `elder-test-settings-screen.db`），
  `settings` 取的是应用也在用的那个进程单例。⇒ **本类往设置里写的每一次改动都真写在这台设备的应用存储上**；
  `@After 把共用的设置清回出厂状态()` 用 `resetToDefaults()` 能清回出厂（代价：**这台机器上真实的设置会被清掉**，
  下次打开应用会重新看到引导页），而 `onboardingDone` / `firstLaunchEpochDay` 这类字段**公开 API 恢复不回去**，
  「先存一份、事后还原」这条路不通。
- **AC-13.1 在屏幕层只验到一半**（第 28 轮）：「首页重新读了设置」验到了；「那句话里带着新称呼」那一半仍只能在
  `ui.home.HomeViewModelTest`（能注入时钟）验。**屏幕级用例固定不了时钟**（`AppContainer.clock` 写死系统钟），
  本轮的绕过办法是往库摆一个把此刻裹在中间的安静时段，**不是**解决了「时钟不可注入」这件事本身。
- **`InterventionStore` 是进程单例、没有清空口**（第 28 轮）：节流状态会跨用例残留，所以屏幕层不硬断言
  「那句话里出现名字」——那会变成一条随运行时刻忽红忽绿的用例。
- **AC-13.3 不能排除源码写死同一字面量**（第 28 轮）：用例比的是「界面上的字」与「库里的字」相等；
  源码若把 `"这会儿是您休息的时间。"` 写死，这个用例**照样绿**。`InterventionVoice.kt:78` 处确实没有
  可引用的常量。
- **`performTextInput` 不是真 IME**（第 28 轮）：它绕过输入法直接塞文本，所以这条用例**证明不了**
  「真实中文输入法下也能改地址」——它和第 27 轮那句 `adb input text` 打不了中文是同一类缺口。
  **GAP-02（中文真实输入法）仍空。**
- **首次编译失败的两处标识符错误**（第 28 轮，发生在负例之前）：`点「更大」那一行字号立刻落库` →
  `Expecting member declaration`；`清掉作息数据之后那一行立刻变成 0` → `Function declaration must have a name`。
  原因都是 Kotlin 反引号函数名里含 `「」` 与结尾的 ASCII 数字。已改成 `点更大那一行字号立刻落库` /
  `清掉作息数据之后那一行立刻变成零`。**这是本轮真实发生的返工，记在案。**
- **`HomeScreenTest` 是悬挂引用**（第 28 轮）：`HomeViewModelTest` 的 KDoc:54 里提到过这个类名，
  **仓库里并没有这个类**。可顺手记一笔，本轮不修。
- **`docs/01` 的证据列改写是「同一行内替换」，行号没有移动**（第 28 轮）：AC 表是「一条 AC 一行」，
  `git diff --numstat` 对 `docs/01` 报 `4 / 4`（净零行），所以此前引用「`docs/01:350`」这类行号的记录
  **仍然对得上**。这一条上一稿曾写成「行号后移」，是错的，照实改回来。
- **10 张截图仍然没有目视看过**（第 28 轮）：`onboard1_.png` / `onboard2_.png` / `onboard3_.png` /
  `settings.png` / `talk.png` / `talk_heard.png` / `starters.png` / `anchor_ask.png` / `anchor_created.png` /
  `anchor_deleted.png`（前三个带着第 27 轮 `_27` 被 `replace` 后残留的多余下划线，**本轮不改名、照实记**）——
  上面给的都是**文件字节数**，不是「看过」。
- **本轮走查覆盖的链路**（第 28 轮）：引导三页 → 首页 → 设置页改称呼 → 首页 → 玩一会儿 → 选择页 → 返回 →
  对话页 → 起点卡 → 建锚点 → 删锚点。**走查只能打 ASCII**（中文 `input text` 走 `adb` 会 NPE：
  `InputShellCommand.sendText` → `Attempt to get length of null array`）；**首页坐标位移 79 px 本轮未复测**；
  **GAP-03（性能基线）本轮仍未排**（NFR-08 需真机）；第 21 / 23 轮的性能数字对应**另一份 APK**
  （md5 `84327b14…`），本轮没有重跑，**不能**套到本轮这份上；**无真机、没戴耳机听过 TalkBack**。
- **`longestSeconds` 仍未改名**（第 28 轮未处置，读起来像上限，实际是估时）；**「大概 3 分钟」仍是估时**
  不是测量值。**本轮不推送**：按「每十次迭代推一次」，**下一次推送是第 30 轮末**；远端此刻仍是
  `a0a9d4095cc20560c47db02bed7019dc7a14c019`（第 20 轮推送后），本地已积 `69b49ec`（21）、`d83b994`（22）、
  `44691f5`（23）、`402a1a2`（24）、`4eacd11`（25）、`60478a9`（26）、`dfa2a3b`（27）**七笔未推**，
  本轮提交是**第八笔**。

- **旧 `@After` 关库写法「会产生可观察的数据级失败」仍不可构造**（第 27 轮）：本轮能拿出来的只有
  **代码对照**（旧写法把关库排在组合之前）+ **新写法全绿**。**「反序必红」的负例是对新规则本身打的
  ——把 `TalkStarterTest` 的规则链反过来写成 `RuleChain.outerRule(compose).around(关库排在组合之后(...))`
  → `EXIT=1` / `AssertionError: 关库时前台上还有 1 个 Activity：收尾顺序反了（见 TestTeardown.kt）` /
  `BUILD FAILED in 25s`（`build/l3_27_neg.log`，用完即还原、md5 与备份一致）——不是对旧写法打的。**
  这两件事混起来就是一次假绿。
- **反例只打了一处**（第 27 轮）：只把 `TalkStarterTest` 反过来验过；`PlayScreenTest` / `TalkInputTest` /
  `A11ySemanticsTest` 三类**没有各自的注入负例**。
- **`Stage.RESUMED == 0` 那条断言只在 10 例上实测成立**（第 27 轮）：定向跑的是 `PlayScreenTest` +
  `TalkInputTest`（`Starting 10 tests` → 10/10，`build/l3_27_targeted.log`，`BUILD SUCCESSFUL in 55s`）；
  **`A11ySemanticsTest` 与 `TalkStarterTest` 两类只在 L3 全量里绿过，没有单独的定向证据**。
- **`getActivitiesInStage` 要求主线程是照文档推断的**（第 27 轮）：实现里用 `runOnMainSync` 兜住，
  **没有反例去证「不在主线程调会怎样」**。
- **这条规则只保证「关库在组合之后」**（第 27 轮）：**不保证**「用例里所有在飞的写库都已结束」——
  后者归第 25 轮那条 `join` 纪律（`records/03` ADR-023），两者互补，不互相替代。
- **那句 2 秒提示的断言是「降级」而不是「解决」**（第 27 轮）：它现在只能证明「尽力抓到过 / 没抓到」，
  **抓不到时不能推出「提示没出现」**；本轮是抓到了。
- **走查只能打 ASCII**（第 27 轮）：中文 `input text` 会 NPE（`InputShellCommand.sendText` →
  `Attempt to get length of null array`），所以称呼固定 `LaoXiong`。**GAP-02（中文真实输入法）仍空**，
  走「点示例句」**不等于**验过真实中文输入。
- **有一条链路故意不断言**（第 27 轮）：「改称呼后回首页立刻在问候语里看到名字」——节流窗内首页会说
  「先不重复招呼了。」，这是设计行为；该链路归 `HomeViewModelTest.kt:283` 覆盖，走查里不硬断言，
  免得把正确行为写成失败。
- **L2 这一轮是「没变」不是「又测一次」**（第 27 轮）：`38 actionable tasks: 38 up-to-date`、`app/src/main`
  未改，所以那个 md5 **不能**当作「本轮重新验证过交付物」的证据。本轮也**没有**再做「删掉产物再重建」；
  **`classes10.dex` 进 zip 的次序由哪一步决定，仍未查清**。
- **10 张截图仍然没有目视看过**（第 27 轮）：`onboard1_27.png` / `onboard2_27.png` / `onboard3_27.png` /
  `settings27.png` / `talk27.png` / `talk_heard27.png` / `starters27.png` / `anchor_ask27.png` /
  `anchor_created27.png` / `anchor_deleted27.png`——上面给的都是**文件字节数**，不是「看过」。
- **本轮走查覆盖的链路**（第 27 轮）：引导三页 → 首页 → 设置页改称呼 → 首页 → 玩一会儿 → 选择页 → 返回 →
  对话页 → 起点卡 → 建锚点 → 删锚点。**首页坐标位移 79 px 本轮未复测**（不知还在不在）；
  **GAP-03（性能基线）本轮仍未排**（NFR-08 需真机）；第 21 / 23 轮的性能数字对应**另一份 APK**
  （md5 `84327b14…`），本轮没有重跑，**不能**套到本轮这份上；**无真机、没戴耳机听过 TalkBack**
  （无障碍只做到「读得到」，听感一直没验）。
- **`docs/01:350` / `:353` 的 AC-13.1 / 13.4 证据列陈旧**（第 27 轮未处置）：与 `:475` GAP-01「已关闭」
  自相矛盾；`longestSeconds` 仍未改名（读起来像上限，实际是估时）；「大概 3 分钟」仍是估时不是测量值。
  **本轮不推送**：按「每十次迭代推一次」，**下一次推送是第 30 轮末**；远端此刻仍是 `a0a9d40`（第 20 轮
  推送后），本地已积 `69b49ec`（21）、`d83b994`（22）、`44691f5`（23）、`402a1a2`（24）、`4eacd11`（25）、
  `60478a9`（26）六笔未推，本轮是**第七笔**。

- **`classes10.dex` 进 zip 的次序由哪一步决定，仍未查清**（第 26 轮）：原先那句「怀疑是 Gradle build cache
  回放」**已被排除**——本轮把产物删掉重建，**复现两次、两次都是同一份**，build cache 解释不了「删包重建
  可复现」。但替代解释本轮也没有，只记现象、不编解释。可确证的只有度量口径那一条：**整文件字节数与 md5
  不是内容度量**。
- **反向验证只打了两处注入**（第 26 轮）：**M1** = 在 `HomeViewModel.refresh()` 里加一次 `open()`
  （「刷新也重新判定」）→ 红；**M2** = 去掉 `onStart()` 里那句 `if (!leftSinceLastOpening) return`
  （「只要 `onStart` 就算新露面」）→ 红。**其余 7 例没有各自的注入负例**，即 9 例中只有 2 例被证明过「会红」。
- **M1 第一版只红 1 例——「注入后红了」不等于「负例有效」**（第 26 轮）：原本预测 M1 会红 2 例，实际只红了
  `同一露面内再刷新也不改口`（`build/l3_26_m1.log`，`BUILD FAILED in 23s`）。**根因**：`跨时段边界靠刷新重读，
  但不动账本` 里那句 `awaitUntil { headline == "中午好" }` 在 `open()` **落笔记账之前**就已满足——问候语由
  `state` 的 `combine(settings, refreshTick, session)` 重算，时钟一过 12:00 画面立刻变「中午好」，而写库还没到。
  **修法**：断言前补一次 `settleQuietly()`；补完重跑**恰好红 2 例**（`build/l3_26_m1b.log`，
  `BUILD FAILED in 21s`，第二条是 `expected:<2026-03-10T09:00> but was:<2026-03-10T12:30>`）。
  **若就此收工，就会把一个假绿用例当作守卫留在仓库里**，而且它一直是绿的、看不出任何问题。
- **`settleQuietly()` 只用 300ms 窗口**（第 26 轮）：它只能证明「**这个窗口内没观察到变化**」，
  **不能**证明「永远不会有变化」。凡用到它的用例（`跨时段边界靠刷新重读，但不动账本` 等）结论强度都受此限制。
- **`PlayScreenTest.kt:72` 走 `AppContainer` 的注销路径没有逐字读过**（第 26 轮）：第 26 轮顺手审了其余
  16 个设备用例类的 `tearDown` 顺序，**只有 `TalkViewModelTest:126` 与 `RhythmCollectorTest:87` 两处，
  且顺序都是对的**，纯 DAO / 仓库类无可疑点；唯一未验点就是这一处——它走的是 `AppContainer`（而不是
  `ViewModelStore`），接收者的注销时机**只在源码里扫到、没有逐行读过**。
- **L4 走查脚本的前置状态靠手工，`probe26.py` 的 docstring 与实现不符**（第 26 轮）：docstring 第 13 行宣称
  「从清空应用数据起步（`pm clear`）」，但**脚本里根本没有 `pm clear`，也没有启动 app 的步骤**——这两步从
  第 18 轮起一直是手工做的。第一跑因此撞上系统对话框 `Remember photo locations?`，打了 **14 条 `!!`**；另一个
  根因是 **`connectedDebugAndroidTest` 跑完会把被测 APK 卸载掉**。修法是手工 `adb install -r` → `monkey` 启动
  → 确认 `mCurrentFocus=…com.fortheelder.debug/com.fortheelder.MainActivity`（第二次重跑前还必须先 `pm clear`
  + 重新启动）。**⇒ 这一轮的 L4 不是「脚本一键复现」出来的，脚本自己不能独立复现一次走查。**
- **第 25 轮那 6 张不带下划线的截图被本轮第一跑覆盖，没有副本**（第 26 轮）：`probe26.py` 的 `sed` 批量改名
  只盖住带下划线的文件名，**漏了 `talk25.png` / `talk_heard25.png` / `starters25.png` / `anchor_ask25.png` /
  `anchor_created25.png` / `anchor_deleted25.png`** 这 6 个。已补齐改名，uiautomator dump 路径也从
  `/sdcard/d25.xml` 换成 `/sdcard/d26.xml`。`build/` 被 `.gitignore` 覆盖，**提交物没受影响**，
  但第 25 轮那 6 张图的原始副本已不存在。
- **9 张截图没有目视看过**（第 26 轮）：`onboard1_26.png`(285,060) / `onboard2_26.png`(198,396) /
  `onboard3_26.png`(294,931) / `talk26.png`(190,112) / `talk_heard26.png`(230,268) / `starters26.png`(219,512) /
  `anchor_ask26.png`(181,644) / `anchor_created26.png`(205,733) / `anchor_deleted26.png`(183,572)，
  上面给的字节数都只是**文件大小**，不是「看过」。
- **本轮走查覆盖的链路**（第 26 轮）：引导三页 → 首页 → 玩一会儿 → 选择页 → 返回 → 对话页 → 起点卡 → 建锚点 →
  删锚点（相对第 25 轮多一条支线：在首页改称呼后立刻改口）。**设置页 / 作息感知的采集分支没有在本轮串行走过**；
  **首页 79 px 位移本轮未复测**（不知还在不在）；**GAP-03（性能基线）本轮仍未排**（NFR-08 需真机）；
  第 21 / 23 轮的性能数字对应**另一份 APK**（md5 `84327b14…`），本轮没有重跑，**不能**套到本轮这份上；
  **GAP-02（中文真实输入法）本轮仍为空**（走的是「点示例句」，**不等于**验过真实中文输入）；
  **无真机、没戴耳机听过 TalkBack**（无障碍只做到「读得到」，听感一直没验）。
- **`docs/01:350` / `:353` 的 AC-13.1 / 13.4 证据列陈旧**（第 26 轮未处置）：与 `:475` GAP-01「已关闭」自相矛盾；
  `longestSeconds` 仍未改名（读起来像上限，实际是估时）；「大概 3 分钟」仍是估时不是测量值。
  **本轮不推送**：按「每十次迭代推一次」，**下一次推送是第 30 轮末**；远端此刻仍是 `a0a9d40`（第 20 轮推送后），
  本地已积 `69b49ec`（第 21 轮）、`d83b994`（第 22 轮）、`44691f5`（第 23 轮）、`402a1a2`（第 24 轮）、
  `4eacd11`（第 25 轮）五笔未推，本轮是**第六笔**。

- **`settleQuietly()` 只用 300ms 窗口**（第 25 轮）：`TalkViewModelTest` 的「空话不改变屏幕上显示的东西」
  与「一次只摊开一个起点」两例靠它——它只能证明「**这个窗口内没观察到变化**」，**不能**证明「永远不会有变化」。
- **反向验证只打了一条守卫**（第 25 轮）：只把 `Anchor.kt:72-73` `mayPropose` 的 `&&` 注入成 `||`，得
  `没确认过的时候点了时段也不建锚点` + `说了不用的意愿也推不出锚点` **2 例红**（`BUILD FAILED in 27s`），还原后
  `git diff` 该文件 0 行。**其余 12 例没有各自的「注入错误」负例**。
- **收尾重跑 L3 红过一次，7 次复现尝试全绿**（第 25 轮）：同一份源码树先跑 L1 得绿、再跑 L3 撞红——`Starting
  116 tests` → `Finished 49 tests` → `There was 1 failure(s)` → `BUILD FAILED in 33s`；失败用例
  `RhythmMaintenanceTest | 第一次检查会真删过期数据并记下今天`，崩溃是 `FATAL EXCEPTION:
  DefaultDispatcher-worker-2` 上的 `IllegalStateException: Cannot perform this operation because there is no
  current transaction.`（`RhythmSignalDao_Impl$4.call` → `CoroutinesRoom.execute` → `TransactionExecutor`，
  末端 `Suppressed: …{Cancelling}…, Dispatchers.IO`）。修法与取证见 `records/04` 第二十四节。**根因的定性是
  「高置信重构，未按需复现」**——试了 1 次单类 + 5 次循环 + 1 次全量共 **7 次全绿、没复现出那次红**，所以
  「去掉 join 就必红」**是推断、不是实测**。`logcat` 里 `DefaultDispatcher-worker-2` 与 `ThreadPoolExecutor`
  的对应关系**也未查清**。
- **`RhythmCollectorTest` 的 join 修法有边界**（第 25 轮）：它只 join「**快照时已在飞的子 job**」，
  对快照之后新起的 job 无覆盖；`withTimeoutOrNull(5_000L)` 一旦超时仍会继续 `close()`；而且这条纪律
  **只落在 `RhythmCollectorTest` 一个类上，其余 16 个设备用例类的 teardown 顺序没有逐类审过**。
- **`TalkViewModelTest` 的两条路只在源码上读过**（第 25 轮）：`confirmAnchor` 在 `statusOf` 为 null 时
  **静默丢弃点击**——这条路径**没在真机上构造过**；`HomeViewModel` 的同类设备层测试**留给第 26 轮**。
- **APK 整文件字节数仍不能当内容度量**（第 25 轮）：本轮 APK = **11,016,852 字节** / md5 `b8209a99284cc5586bdb708e4716663e`
  / 175 个 zip 条目。与第 24 轮（10,971,528 / `462e4726…`）逐条目比：175 = 175、名字集合相同，**6 个 dex 变**，
  未压缩 +8,716 / 压缩后 +4,050，整文件却 +45,324。量「file − Σcompress_size」：24 轮 99,141 → 本轮 **140,415**，
  即约 **41 KB 的差出在 zip 本地头 / 中央目录 / 对齐填充上**；按本地头顺序在 offset 10,951,648 断言失败，
  **根因未查清，标未验**。
- **9 张截图没有目视看过**（第 25 轮）：`onboard1_25.png` / `onboard2_25.png` / `onboard3_25.png` / `talk25.png` /
  `talk_heard25.png` / `starters25.png` / `anchor_ask25.png` / `anchor_created25.png` / `anchor_deleted25.png`，
  上面给的字节数都只是**文件大小**，不是「看过」。
- **`probe25.py` 第一版有三处假绿断言**（第 25 轮）：① `count_sub` 用**子串匹配**，「把这一步记成日常」被更长的
  句子骗过；② 起点按钮要先点「展开这一步」才可见，第一版没展开就断言「看不到」，把「没看到」当「不存在」；
  ③ `ensure()` 只会往下滑，屏外节点被判成「不在」。三条修掉后才有 94 / 0 的数字。**第 24 轮记的「示例句要先上滑」
  的同类教训：走查里「没看到」不等于「不存在」**。
- **本轮走查覆盖的链路**（第 25 轮）：引导三页 → 首页 → 玩一会儿 → 选择页 → 返回 → 对话页 → 起点卡 → 建锚点 →
  删锚点。**作息感知 / 设置页没有在本轮串行走过**；**首页 79 px 位移本轮未复测**（不知还在不在）；
  **GAP-03（性能基线）本轮仍未排**（NFR-08 需真机）；第 21 / 23 轮的性能数字对应**另一份 APK**（md5 `84327b14…`），
  本轮没有重跑，**不能**套到本轮这份上；**GAP-02（中文真实输入法）本轮仍为空**（走的是「点示例句」，
  **不等于**验过真实中文输入）；**无真机、没戴耳机听过 TalkBack**（无障碍只做到「读得到」，听感一直没验）。
- **`docs/01:350` / `:353` 的 AC-13.1 / 13.4 证据列陈旧**（第 25 轮未处置）：与 `:475` GAP-01「已关闭」自相矛盾；
  `longestSeconds` 仍未改名（读起来像上限，实际是估时）；「大概 3 分钟」仍是估时不是测量值。
  **本轮不推送**：按「每十次迭代推一次」，**下一次推送是第 30 轮末**；远端此刻仍是 `a0a9d40`（第 20 轮推送后），
  本地已积 `69b49ec`（第 21 轮）、`d83b994`（第 22 轮）、`44691f5`（第 23 轮）、`402a1a2`（第 24 轮）四笔未推。

- **守卫只看得见「在册的 8 条宣称」**（第 24 轮）：`docs/*.md` 正文里的个数（守卫一律不扫文档）、速查卡、
  以及「举个例子」式的列举文案，写错了**一律看不见**，仍靠人眼比对；「第二个游戏」「不止一个」「几个」这类
  **不定数**的历史叙述也不在射程（它们本来就不是写死的个数）。所以本轮的抬升是「**有，但只在册**」，
  **不是「有自动校验」**。另外 `minHits` 达标**不等于**「该说的地方都说了」——它只保证「至少这些地方说得对」。
- **APK 整文件字节数不能当内容度量**（第 24 轮）：本轮 APK = **10,971,528 字节** / md5 `462e4726…`，
  比第 22 轮副本 `build/app-debug-22a.apk`（11,052,250 字节）**小 80,722 字节**；但逐 zip 条目比，条目名集合
  完全相同（175 = 175）、**未压缩大小只有 `classes12.dex` 一个不同**（90,840 → 93,264，+2,424）、压缩后总和
  只差 +1,143。量「file − Σcompress_size」：22a = 176,956、本轮 = 95,091 ⇒ **约 81 KB 的差出在 zip 头 /
  中央目录 / 对齐填充上，不是内容**。**这 81 KB 填充差的来源本轮没有查清，标未验**。
- **改了 KDoc 的一行，在 dex 里查不到**（第 24 轮）：16 个 dex 逐个扫，「三档排版表」0 次、「四档排版表」0 次——
  **这是预期**（KDoc 不进 dex），所以那条串扫描**既不能证成也不能证伪**本轮改动进了 APK；能证成的是 JVM 守卫
  （读磁盘源码）与走查里「字号档位正好 3 个」那条断言。
- **引导页数只有行为验证**（第 24 轮）：页数只挂在 `stateDescription` 里，`uiautomator dump` 看不到，所以
  第 5 / 6 条宣称在走查侧用的是「连翻两次到底」——**弱一点的东西，不是等价物**。
- **示例句要先上滑才可见**（第 24 轮）：`不知道说什么，点一句试试` 只是 `TalkScreen.kt:115` `SectionCard` 的
  标题、**不可点**；5 句示例折在折叠线以下（y > 2310），dump 看不到屏外节点。走查改成一版：先 `swipe` 上滑
  再逐句断言。**GAP-02（中文真实输入法路径）本轮仍为空**——走的是「点示例句」，**不等于**验过真实中文输入。
- **本轮 6 张截图没有目视看过**（第 24 轮）：`onboard1_24.png` / `onboard2_24.png` / `onboard3_24.png` /
  `talk24.png` / `talk_heard24.png` / `starters24.png`，上面给的字节数都只是**文件大小**，不是「看过」。
- **本轮走查只覆盖一小段链路**（第 24 轮）：引导三页 → 首页 → 玩一会儿 → 选择页 → 返回首页 → 对话页 →
  起点卡；**作息感知 / 设置页没有在本轮串行走过**。**首页 79 px 位移本轮未复测**（不知还在不在）。
  **GAP-03（性能基线）本轮仍未排**；第 21 轮的冷启动 / 帧率 / 内存数字对应**另一份 APK**（md5 `84327b14…`），
  本轮**没有重跑**，**不能**套到本轮这份上；NFR-08 仍需真机。**无真机、没戴耳机听过 TalkBack**。
- **崩溃缓冲区只读了「本轮」**（第 24 轮）：`adb logcat -b crash -d` 为 **0 行**，只说明本轮走查期间没崩，
  不能推到历史或别的机器。**候选 2 / 候选 3 本轮仍不处置**（候选 2 = `docs/01:350` / `:353` 的 AC-13.1 / 13.4
  证据列陈旧、与 `:475` GAP-01「已关闭」自相矛盾；候选 3 = `HomeViewModel.kt:74` 的 KDoc 说「同一分钟内不重读」
  而实现是整次露面只读一次）。**本轮不推送**：按「每十次迭代推一次」，**下一次推送是第 30 轮末**；远端此刻仍是
  `a0a9d40`（第 20 轮推送后），本地已积 `69b49ec`（第 21 轮）、`d83b994`（第 22 轮）、`44691f5`（第 23 轮）三笔未推。

- **「换一个游戏」这条退路只在 3 个游戏的应用上装机验过**（第 23 轮）：`canChooseAgain = false` 那条分支
  （应用里只剩一个游戏时不摆这条路）**只有单测**，本轮没有装机跑过。「`Playing` 上没有这条路」是由「走查点
  `开始` 后此屏计数 0」+「单测在 `Playing` 上调 `chooseAgain` 不动状态」两条一起证的，**没有**在那一屏上
  强行点过那个坐标（那儿是格子，不是按钮）。
- **没有做逐条 zip 比对**（第 23 轮）：本轮与第 22 轮的 APK **总字节数相同**（都 11,089,357）但 **md5 不同**
  （`d5ad915b…` vs `9290b5eb…`）——同尺寸不同内容是第 22 轮查清过的 zip 对齐现象，所以「字节数相同」不能当
  「没改」的判据，两侧 md5 也不互相背书。本轮「内容确实变了」只由 `classes12.dex` 里三个串（`换一个游戏`×1 /
  `canChooseAgain`×2 / `playStartState`×1）支持，**没有**做「哪些 dex 变了」的完整清单。
- **首页 79 px 位移没有复测**（第 23 轮）：第 21 / 22 轮观察到的那三处坐标差，本轮**没有重测**，所以不知道
  还在不在。
- **本轮走了 `pm clear` 全量起步，但仍只覆盖一小段链路**（第 23 轮）：只走了「首页 → 玩一会儿 → 挑一个 →
  规则页 → 换一个游戏 → 回挑选页 → 挑另一个 → 开始」；作息感知 / 意愿发现 / 兴趣陪伴三块**没有装机走过**。
- **崩溃缓冲区只读了「本轮」**（第 23 轮）：`adb logcat -b crash -d` 为 **0 行**，**只说明本轮走查期间没崩**，
  不能推到历史或别的机器。**GAP-03（性能基线）第 23 轮仍未排**；第 21 轮的冷启动 / 帧率 / 内存数字对应
  **另一份 APK**（md5 `84327b14…`），本轮**没有重跑**，**不能**套到本轮这份上；NFR-08 仍需真机。
- **`build/rule23.png` 没有被目视检查**（第 23 轮）：走查结论全部来自 `dump` 的文本节点与坐标，截图只留档
  （142,402 字节是文件大小，不是「看过」）。
- **候选 2 / 候选 3 本轮仍不处置**（第 23 轮）：候选 2 = `docs/01:350` / `:353` 的 AC-13.1 / 13.4 证据列陈旧、
  与 `:475` GAP-01「已关闭」自相矛盾；候选 3 = `HomeViewModel.kt:74` 的 KDoc 说「同一分钟内不重读」而实现是
  整次露面只读一次。**本轮不推送**：按「每十次迭代推一次」，**下一次推送是第 30 轮末**；远端此刻仍是
  `a0a9d40`（第 20 轮推送后），本地已积 `69b49ec`（第 21 轮）、`d83b994`（第 22 轮）两笔未推。

- **「一局大概 3 分钟」是估时，不是测量值**（第 22 轮）：180 / 120 / 180 三个数是设计时定的余量，
  **全项目没有采过任何一局真实用时的样本**。本轮只验了「屏幕上的字与 `longestSeconds` 一致、且不含
  无法兑现的硬话」（3 条 JVM 守卫 + 装机走查 + dex 串扫描），**没有验这句估时对真实用户准不准**。
- **「超时以后什么也不会发生」是源码事实推出来的，不是玩出来的**（第 22 轮）：证据是 grep
  `ui/play/` + `domain/play/` 里 `delay(` / `LaunchedEffect` / 倒计时 / 超时结束**全部为空**；
  本轮**没有**做「玩满 3 分钟会发生什么」的实测。
- **`longestSeconds` 的名字没改**（第 22 轮）：它读起来仍像上限（实际是估时），KDoc 已写清。
  改名会牵动 3 个游戏定义 + 4 个测试文件，本轮判断收益不值——账记在 `records/03` ADR-020。
- **首页三处 y 坐标比上次实测高 79 px，原因未查明**（第 22 轮）：本轮实测 `玩一会儿` y1304-1383、
  `设置` y1610-1689、末句 y1773-1899（上次 y1383-1462 / y1689-1768）。**只记录「两次实测值不同」，
  **不编解释**；不影响本轮结论（规则页新文案与旧文案同一区间 y750-876）。
- **L4 走查只覆盖一小段链路，而且这次没有 `pm clear`**（第 22 轮）：只走了「首页 → 玩一会儿 →
  算一算 → 规则页」；`build/screens22.log` 里 `walk` 屏等于 `home` 屏是**没清数据**造成的，
  不能读成「走查少走了一屏」。作息感知 / 意愿发现 / 兴趣陪伴三块**本轮没有装机走过**。
- **本轮没有崩溃 / ANR 检查，也没有重跑第 21 轮那套性能测量**（第 22 轮）：没读 logcat 崩溃缓冲区，
  所以**不能**说「本轮没崩溃」。**GAP-03（性能基线）第 22 轮仍未排**——第 21 轮的冷启动 / 帧率 /
  内存数字**对应另一份 APK**（第 21 轮 `app/` 零改动），**不能**直接套到本轮这份上；NFR-08 仍需真机。
- **APK 逐条比对只到「差异定位在 2 个 dex」**（第 22 轮）：`classes12.dex` / `classes15.dex` 里**具体**
  哪几个字节变了**没有**逐字节 diff（`classes12.dex` 由 `一局大概` 串扫描间接证实）。
- **没有真机、也没有戴耳机听过 TalkBack**（第 22 轮）：全部证据来自 AVD `Elder_API35`。
- **上一轮怀疑的「点了示例句之后回应卡在屏外」已被实测否证**（第 22 轮）：本轮**没有**为它做任何改动。
- **候选 3 / 候选 5 / 候选 2 本轮都不做**（第 22 轮）：候选 3 = `HomeViewModel.kt:74` 的 KDoc 说
  「同一分钟内不重读」而实现是整次露面只读一次；候选 5 = 游戏内没有「回到游戏列表」入口；
  候选 2 = `docs/01:350` / `:353` 的 AC-13.1 / 13.4 证据列陈旧、与 `:475` GAP-01「已关闭」自相矛盾
  （可作第 23 轮切片）。**本轮不推送**：按「每十次迭代推一次」，**下一次推送是第 30 轮末**，
  远端此刻仍是 `a0a9d40`（第 20 轮推送后）。

- **性能基线只在模拟器上测过一轮，而且是 CPU 软件渲染**（GAP-03；第 21 轮）：这台 AVD 用
  `-gpu swiftshader_indirect`（**没有 GPU**），所以上面那些毫秒数**不能当作任何真机的性能**。
  **NFR-08（滚动不掉帧）在本环境不可判**——唯一真能滚的设置页中位数 **34** ms，超过 32 ms 的字面门槛，
  可同环境连点窗口的中位数也有 **35.3** ms（**地板就在 30 ms 量级**）；首页与「挑一个」一屏放得下、
  **不可滚动**，所以 NFR-08 原话里的「首页滚动」在这台机器上**没有适用对象**。本轮**不做折算、不估算
  真机、不拿模拟器数字当真机用**。要关掉 NFR-08，**必须真机复测**。
- **三条工具学结论只到「实测推断」，没查源码**（第 21 轮）：① Android 15 的 `dumpsys gfxinfo reset`
  **先打印清零前的统计再清零**（第一版脚本因此误记存量 7 帧）；② `framestats` 会吐出 dump 那刻在飞的
  最后一帧，`FrameCompleted` 是垃圾值（实测算出来 ≈ **2.3 天**，处理办法=按时长 >5 s 剔除并说明剔了几行）；
  ③ **`framestats` 只保留最近约 119 ~ 120 帧**，所以 `n=388` 是「三段窗口各自末尾样本的合并」，**不是**
  `gfxinfo` 报的 889 帧的全样本；`gfxinfo 50th` 与 `framestats 50%` **统计对象不同，两套并列记录、不取
  平均、不互相校准**。这些上限、以及连点窗口 flags（`{0:74,2:22}` / `{0:63,2:24}` / `{0:67,2:21}`）里
  **位 2 的语义，都没有查 AOSP 源码**——所以记录里只写位号，**不写名字**。
- **「`-W` 返回后早 300 ms 的点击为什么会丢」只是假说**（第 21 轮）：12 次扫描证明**真变量是
  `adb shell input tap` 自身的发射延迟（230 ~ 390 ms 浮动）**——发射时刻 ≥532 ms 的 **14/14 可点**、
  ≈300 ms 的 **0/3 点不动**，可点窗口夹逼在 `-W` 返回后 **300 ~ 530 ms**（从启动算约 1.2 ~ 1.5 s）。
  中间那一段为什么是「丢」而不是「等待」，**没有查到根因**。
- **本轮没重跑 L3 / L4，崩溃检查也没先清缓冲区**（第 21 轮）：L3（设备仪器化 16 类 102 例）与 L4
  （装机走查 88 行）**沿用**第 18 轮；`adb logcat -d -b crash` 读到空，但**本轮没有先清缓冲区再跑一遍**，
  所以只能说「**读到的时候是空的**」，不能说「本轮全程没有崩溃」。内存那三个 PSS **只作参考信息**——
  `docs/01` 里**没有内存类 NFR**，它们不构成任何通过 / 失败判定。
- **本文件里第 16 / 17 / 18 轮那几条「性能基线仍未测量」保持原样**（第 21 轮）：它们在各自那一轮
  都是实话，**不改写**；要看当前状态读本条目。GAP-03 现在的状态是**部分关闭**——NFR-07 有模拟器上的
  单侧证据，NFR-08 等真机。**本轮不推送**：按「每十次迭代推一次」，**下一次推送是第 30 轮末**，
  远端此刻仍是 `a0a9d40`（第 20 轮推送后）。

- **推送只验了 ref 与内容哈希，别的都没验**（第 20 轮）：`git ls-remote origin refs/heads/main`、
  `git rev-parse HEAD`、`git rev-parse origin/main` 三处同为 `d9d755fcd3c2506d451d3308de8de19de95cb091`，
  这只证明远端的 `refs/heads/main` 指向该提交（commit id 是内容哈希，同 id ⇒ 内容逐字节一致）。
  **没有查** GitHub 网页端能否正常打开、仓库可见性设置、分支保护规则、默认分支，也**没有** CI——
  本项目没有流水线，远端那一侧不会自己跑构建或测试。
- **本轮没有跑任何测试**（第 20 轮）：`app/` 零改动，四层数字（L1 29 类 / 370 例、L2 APK 11,052,250 字节、
  L3 16 类 / 102 例、L4 88 行）**全部沿用**第 18 / 19 轮，本轮**一条新数字都没有产生**。

- **`docs/06` 不含任何本轮新测的数字**（第 19 轮）：本轮只**复跑**了 L1（`testDebugUnitTest --rerun-tasks`，
  29 类 / 370 例 / 0 失败，`BUILD SUCCESSFUL in 14s`，与第 18 轮**逐行相同**），**没有重跑** L3 设备
  仪器化测试与 L4 装机走查；L2 也**未重新打包**（`assembleDebug UP-TO-DATE`，APK 仍是 **11,052,250** 字节 /
  md5 `84327b14218630002a6e4a26721333b6`，mtime 未变）。
- **`docs/06` 的四层数字会随时间过期**（第 19 轮）：它记的是**第 18 轮**的实测值，而 `records/04` 会继续
  往下写。引用 `docs/06` 的任何数字之前，先看 `records/04` 里更晚的章节。
- **`docs/06` §8 / §9 里若干「未测量」条目仍是承接的旧账**（第 19 轮）：NFR-07 / NFR-08 性能、NFR-05 对比度、
  真实中文输入法、「新出现的节点会不会被朗读」、真人试用——状态本轮**一个都没有改变**，只是搬了个位置。

- **没有人真的用耳朵听过 TalkBack**（第 18 轮）：本轮验的是 Compose 语义树（4 例）与平台层
  `AccessibilityNodeInfo`（1 例）——**读得到**不等于**读得好听**。语速、停顿、播报时机、连续播报会不会
  互相打断，一条都没听过。所以本轮**没有**给读屏补需求编号（`records/02` RC-017）。
- **走查没有先清 crash 缓冲区再跑**（第 18 轮）：事后补跑 `adb logcat -b crash -d` 读到 0 行，因此措辞只能
  写「**读到的时候是空的**」，不能说「本轮没有崩溃」；`build/walk18.py` 里没有任何 crash 检查。
- **`A11ySemanticsTest` 第 4、5 例（`:259-379`）的断言细节未读**（第 18 轮）：已知的只有用例名与它们
  各自验的那一类东西（第 4 例验「哪些地方挂了播报区、哪些没挂」，第 5 例走平台层）。
- **「新出现的节点是否会请求朗读」未实测**（第 18 轮）：首页问候语与 `TalkScreen` 结果卡因此**刻意不加**
  `liveRegion`——是「不知道、选择不碰」，**不是**「已知不会读」。
- **「同一节点叠两个 `semantics` 块会分裂」未实测**（第 18 轮末收窄）：没有 A/B 对照，本轮只是**选择不碰**
  这个不确定的行为（`records/03` ADR-019 第 2 条）。
- **装机走查看不见本轮的任何语义**（第 18 轮实测）：`uiautomator dump` 只吐 17 个节点属性
  （`bounds` / `checkable` / … / `text`），标题、页码、行列、播报区**一个都不在里面**。结论：**语义只能靠
  仪器化测试来验**，`walk18.py` 里因此没有为语义加任何断言。走查能证的只有「加了标签之后，屏幕上的
  可见文字没有变」（负向断言：「第 1 页」「共 3 页」在引导三页与首页都搜不到，`build/screens18.log`）。
- **性能基线仍未测量**（GAP-03）：NFR-07 / NFR-08 在第 18 轮**仍没有数字**——本轮预算给了无障碍语义标签
  （GAP-05）。冷启动、帧率、内存**一个数都没有**，也**不做估算或推断**。
- **`domain/` 层没有机械守卫**（第 18 轮）：只有 `ui/` 一层有守卫；`A11yPlacementTest` 亦然，它的扫描目录
  就在 `ui/` 下。
- **真实中文输入法仍未验证 / 没有真人试用过**（第 18 轮）：全部观察仍来自 `Elder_API35` 模拟器上的脚本操作
  （点击与逐字转储），**没有一位真实用户**参与；本轮尤其要紧——语义标签是给**用读屏的人**做的。
- **`docs/09` 的 16 屏界面采集没有重跑**（第 18 轮）：界面可见文字本轮未改，因此没有重新采集截图；
  `docs/09` §8.8 与第 15 轮那次采集的措辞未逐字比对，如有冲突一律按「第 18 轮未重跑」口径。

- **性能基线仍未测量**(GAP-03)：NFR-07 / NFR-08 在第 17 轮**仍没有数字**——本轮预算给了第三个游戏
  「算一算」。冷启动、帧率、内存**一个数都没有**，也**不做估算或推断**。
- **`domain/` 层没有机械守卫**(第 17 轮)：`domain/` 的历史常量(`StarterOptions` / `CompanionVoice` /
  `InterventionVoice` / `ClaimWords`)是集中存放的，但只有 `ui/` 一层有守卫，`domain/` 列代码评审项
  (见 `records/02` RC-013)，不写成「已关闭」。
- **真实中文输入法仍未验证 / 没有真人试用过**(第 17 轮)：本轮全部观察仍来自 `Elder_API35` 上的脚本
  操作(点击与逐字转储)，`adb shell input text` 那类路径未覆盖真实输入法候选，**没有一位真实用户**参与。
- **`docs/09` 的 16 屏界面采集没有重跑**(第 17 轮)：本轮只改文字与计数，界面除引导第一页那句外未动，
  因此没有重新采集截图。

- **第 16 轮未重跑 `connectedDebugAndroidTest`**(第 16 轮；**已于第 17 轮关闭**)：当时那份 96 例是第 12 轮的
  数字。第 17 轮把这条命令**真实重跑了两遍**，两次都是 `Starting 97 tests` / `Finished 97 tests`、0 失败
  0 跳过(`build/connected17.log` 1m 5s、`build/connected17b.log` 1m 8s)，XML 聚合 **97 例 / 15 类**。
- **`domain/` 层没有机械守卫**：第 16 轮的 138 处收口只覆盖 `ui/` 一层，`domain/` 列评审项(见 `records/02` RC-013)。
- **性能基线仍未测量**(GAP-03)：NFR-07 / NFR-08 至今没有数字，第 16 轮同样没排性能测量。**不做估算也不做推断。**
- **真实中文输入法仍未验证**(GAP-02)：走查里的中文是测试框架直接往文本框塞字符，不是真人用输入法打字。
- **没有真人试用过**(R-09 仍是红色)：所有观察都是脚本在 API 35 模拟器(AVD `Elder_API35`)里操作得到的。
- **APK 字节数与第 12 轮相同这件事未能解释**(第 16 轮；**已于第 17 轮解释并关闭**)。第 17 轮逐条目比对
  (`build/apkcmp17.log`)：改文案只让 `classes6.dex` 的**压缩后长度 −3**，紧随其后的
  `lib/arm64-v8a/libandroidx.graphics.path.so` 因对齐填充**等量 +3**，本地记录合计两边都是 **10,951,648**
  字节，加尾部 19,880 = **10,971,528**——**在这种改动下总字节数必然不变**。推导见 `docs/03` 第 8.2 节。
- **第 11 ~ 19 轮已推送到 GitHub**（原为第 19 轮的「都没有推送」，**第 20 轮执行**）：
  `git push origin main` 输出 `d769c69..d9d755f  main -> main`，**9 个提交**一次推完，退出码 0。
  推送后 `git rev-parse HEAD` / `git rev-parse origin/main` / `git ls-remote origin refs/heads/main`
  三个来源都是 `d9d755fcd3c2506d451d3308de8de19de95cb091`——commit id 是内容哈希，同 id 即内容逐字节一致。
  **没有查** GitHub 网页端与仓库设置（可见性 / 分支保护 / CI），见 `Unverified`。
  **下一次推送窗口是第 30 轮末**。
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
- **NFR-07 / NFR-08 的性能基线仍未测量**(第 4~11 轮同一笔账，GAP-03)。
  冷启动到首页可交互的耗时、首页滚动掉帧，**一条数据都没有**。
  第 11 轮预算放在实现与行为验收上，不做任何估算或推断。
- **APK 字节数与第 10 轮完全相同(11,012,408)，这个数字的可靠程度存疑**(第 11 轮)。
  已实测排除「新代码没打进包」：`unzip -p app-debug.apk classes15.dex | grep -ac schulte`
  = **1**；新源文件 mtime 全在 11:35~11:44，晚于第 10 轮提交时间 11:30；
  `git ls-tree d769c69` 确认第 10 轮提交里**没有** play 相关源文件。
  推测第 10 轮那次 `assembleDebug` 是 UP-TO-DATE。**第 11 轮没有跑 `--rerun-tasks` 复核**，
  第 12 轮补一次再定论（`docs/03` 8.1 记的是同一件事）。
- **三档字号只测了「装得下」，没测「好不好看」**(第 11 轮)。25 格全部在屏内、
  数字逐档变大，这两条是量出来的（标准 90px / 大一点 103px / 更大 117px 文字高）；
  但格子之间的留白是否均匀、最大档下 25 格是否显得拥挤，**没有看**。
- **`domain/play` 在 API 26 上的行为未验证**(第 11 轮)。仪器化用例全在 API 35 模拟器上跑。
  本轮新增的代码没有用到新 API（纯 Kotlin + Compose），但
  「没有用到新 API」是读代码的判断，不是设备上的实测。
- **TalkBack 读屏下「玩一会儿」页没有走查过**(第 11 轮)。25 个格子是 25 个 `Text`，
  读屏会念出数字，但**不会**告知「这个已经点过了」——点过的格子只是颜色变暗。
  这是 GAP-05（第 17 轮）的一部分，本轮既没有解决也没有实测影响面。
- **宣称词扫描是朴素子串，且没有人工评审**(第 11 轮续，同第 10 轮那一条)。
  本轮新写的全部文案（`PlayVoice` 8 处、`PlayGame` 的 `howTo`、`PlayWords.allowedWords`）
  只被同一套子串扫描与单测守过。扫描命中的 16 行**全是词表定义行**
  （`StarterOptions.kt:409` 一行 9 个词 + `PlayWords.kt:23-37` 15 行），
  但「命中的都是定义行」这句话本身也是机器判的。
- **走查脚本里 `has 分: []` 不是「没有分数」的证据**(第 11 轮)。它命中的是
  「1 分 37 秒」里的分钟。结论靠的是另外两条：领域模型没有 `score` 字段、
  完成卡上没有「再来一局」与「排名」。写在这里是防止后来者把那条脚本判据当更硬的证据。
- **卡片在系统字号 1.5 下的观感未验证**(第 11 轮续，承接第 10 轮同一笔账)。
  第 11 轮走查的「三档字号」是**应用内**档位，不是把**系统**字号调到 1.5。
- **三条新缺口只是记录，没有修**(第 11 轮)：GAP-07（两处词表分立：`PlayWords` 与
  `StarterOptions`，暂定第 12 轮合一到中立位置）、GAP-08（引导屏结尾文案与事实不符）、
  GAP-09（`PlayGame.kt:93` 注释里的交叉引用标错出处）。三条都在 `docs/03` 第 8 节。
- **第 11 轮没有推送**(第 11 轮)。按「每十次迭代推一次」的约定，
  第 20 轮才推；本轮只做本地提交，所以远端此刻还看不到第 11 轮的东西。
- **「找出一样的」只测了「装得下」，没测「好不好玩」**(第 12 轮)。16 格在
  标准 / 大一点 / 更大三档下都在屏内（走查逐字数字见 `records/04` 12.3），
  但一局只点两下会不会太短、20 个词反复出现会不会厌人，**没有看、没有找真人试过**。
- **NFR-07 / NFR-08 的性能基线仍未测量**(第 4~12 轮同一笔账，GAP-03)。
  冷启动到首页可交互的耗时、首页与盘面的滚动掉帧，**一条数据都没有**；
  第 12 轮预算放在第二个游戏与三处缺口收口上，不做任何估算或推断。
- **第 11 轮「APK 字节数存疑」已在本轮收口**(第 12 轮)。本轮用
  `./gradlew :app:assembleDebug --rerun-tasks` 真重跑，产物 10,971,528 字节、
  mtime `2026-11-21 12:35:31`，与第 11 轮那个 11,012,408 不同 →
  证实第 11 轮那次是 UP-TO-DATE、数字来自第 10 轮产物。**收口的是「存疑」，
  不是「字节数意味着什么」**：大小与功能多少、有没有假功能之间没有推出关系。
- **`ClaimWords.kt` 的 15 个词只被朴素子串扫描守过**(第 12 轮续，同第 15 条)。
  「这 15 个词就是该禁的全部」**没有人工评审、没有用户评审**；
  扫描命中的 15 行全是 `ClaimWords.kt:21-38` 的词表定义行，
  但「命中的都是定义行」这句话本身也是机器判的。
- **「不是它，再看看」「这个已经点过了，换一个」两句没人听过**(第 12 轮)。
  `FindPair` 的两句即时提示只被单测与走查脚本按字符串比对过，
  对 55-65 岁的人是安慰还是责备、会不会让人觉得被纠正，**没有真人反应**。
- **walk12d 的「盘面变了」是概率判据**(第 12 轮)。判据是「A 局首字 ≠ B 局首字」，
  而每局是从 20 词里抽 16 个，理论上两次开局可能撞上同一个首字而**假报没变**。
  本轮实测两次盘面确实不同（A 局首字 `核桃`、B 局首字 `毛衣`），
  但这条判据本身不硬，不能当「中途退出必定不留半局」的证明；
  同一轮里更硬的两条是：B 局第一次点「棉被」提示为空（**不是**「已经点过了」）、
  完成卡上**没有**「用时」也没有「再来一局」。
- **`domain/play` 与 `domain/voice` 在 API 26 上的行为未验证**(第 12 轮续，
  同第 11 轮那一笔账)。仪器化 96 例全在 API 35 模拟器上跑；
  本轮新增的 `FindPair` / `ClaimWords` 是纯 Kotlin（`domain/` 下
  `^import android` 实测 0 行、两个包 `^import` 实测共 3 行全是 `kotlin.random.Random`），
  但「没有用到新 API」是读代码的判断，不是设备上的实测。
- **第 12 轮没有推送**(第 12 轮)。按「每十次迭代推一次」的约定，
  第 20 轮才推；本轮只做本地提交，所以远端此刻仍是 `d769c69`——
  第 11、12 两轮的东西在 GitHub 上都还看不到。
- **`docs/05` 与代码的一致性靠人工核对，没有自动校验**(第 13 轮)。这份文档里每一个签名、
  字段名、常量值都来自写文档时**实际打开的源文件**，但**没有一个脚本**把 05 的签名与代码
  AST 比对过。所以「人工看漏」这件事无法排除：代码改了而 05 没跟着改，不会有任何检查报红。
  为了让复核有手可下，05 末尾附了三条可复现命令（`grep -rn` 顶层声明、
  `sed -n '/^dependencies/,/^}/p'` 依赖清单、`cat` 清单文件）。
  **本条同时是 `docs/02 / 03 / 04 / 08` 的状态**——它们也都是人工核对，性质相同。
- **第 13 轮未重跑任何构建或测试**(第 13 轮)。本轮**代码一行未动**，
  所以第 12 轮那四个数字（JVM 25 类/342 例、设备 96 例/15 类/52 秒、
  APK 10,971,528 字节、走查 `walk12e` 第 13 次通过）**没有在本轮被重新验证过**，
  它们只在「代码确实没改」这个前提成立时才有效。`git status` 本轮实测工作区
  在提交前只有 05 一个新增文件，其余为空——这是这个前提的唯一证据。
- **`docs/07` 的「已落实措施」靠人工指向编号，没有自动校验**(第 14 轮)。13 条风险里
  每一条都写了一个「已落实措施」栏，指向 ADR / RC / GAP 编号或一条可复现命令。
  这些指向**是写文档时逐条打开核对过的**，但**没有脚本**去验证「编号对应的事实是否还成立」
  （例如某个 ADR 后来被 RC 收窄了，07 里引它的那句话可能就已过期）。
  **本条与 `docs/02 / 03 / 04 / 05 / 08` 的状态相同**——都是人工核对，性质一样。
- **`docs/07` §3.1 对 R-09 的承诺在第 15 轮之前不算数**(第 14 轮)。§3.1 写的是
  「第 15 轮的 `docs/09-用户手册` 要把『怎么用』讲得比界面更清楚，第一版手动说明
  本身就是对这条风险的一次减轻」。**在 09 真的写出来之前，这句话是一个未兑现的计划**，
  不能当成已经落地的减轻措施；也因此 R-09 的等级仍是**红**，没有因为写了这份文档而降级。
- **第 14 轮未重跑任何构建或测试**(第 14 轮)。本轮**代码一行未动**，
  所以第 12 轮那四个数字（JVM 25 类/342 例、设备 96 例/15 类/52 秒、
  APK debug 10,971,528 字节 / release 未签名 7,550,437 字节、走查 `walk12e` 第 13 次通过、
  41 条 `ASSERT ok`）**没有在本轮被重新验证过**。它们只在「代码确实没改」这个前提成立时有效；
  本轮支撑这个前提的证据是 `git status` 实测——写文档前工作区为空，
  写完 07 与记账后只有 `docs/` 与 `records/` 下的文件变动，**`app/src` 无任何改动**。
- **第 14 轮没有推送，远端仍是 `d769c69`**(第 14 轮)。按「每十次迭代推一次」的约定，
  **第 20 轮**才推。所以此刻 GitHub 上能看到的最后一次迭代仍是**第 10 轮**，
  **第 11、12、13、14 四轮的内容（脑力互动框架、第二个游戏、接口设计说明书、
  风险管理计划）都还看不到**，包括第 11/12 轮那批真实代码与测试。
  这是约定内的行为，不是漏推；但「远端与本地差 4 个提交」这件事必须留在这里，
  免得将来有人拿 GitHub 上的代码当成最新状态。
- **《用户手册》与界面的一致性靠人工核对，没有任何自动校验**(第 15 轮)。16 屏的字面是脚本采的，
  但「手册里这句描述对应屏幕上哪句」是人工对着日志比的，没有测试盯着它；
  界面文案一改，手册就可能悄悄过期。这是这本手册**结构性的**未验证点。
- **第 15 轮未重跑任何测试与构建**(本轮未改代码)。`docs/09` 与 `records/04` 第十三节里引用的
  JVM 342 例 / 设备 96 例 / APK 10,971,528 字节**全是第 12 轮的数字**，第 13~15 轮三轮未复核。
- **性能基线仍空**(GAP-03 / NFR-07/08，第 4~15 轮同一笔账)；**中文手工输入仍空**(GAP-02)。
- **第 15 轮未推送**：按「每十次迭代推一次」的约定，**第 20 轮**才推；此刻远端仍是 `d769c69`，
  **第 11 ~ 15 轮(五轮)在 GitHub 上都还看不到**。
