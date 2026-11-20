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

### Changed

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

### Fixed

- **标题在「更大」档不放大**(第 3 轮)：≥30sp 的 style 此前不跟档位变化，已修复。
  修复后标题、小标题、正文三档都逐级真实放大，实测容差 3% 内通过。
- **对平台做了不成立的线性假设**(第 3 轮)：仪器化测试原先断言
  「系统字号 1.5 × 应用内 1.3 线性相乘」，实测系统 1.5 只兑现 1.309×。
  这是**断言写错了**而非代码错。已改写为断言「不抹平系统设置」与「应用档位仍显著放大」，
  并把实测倍率打印归档。

### Known Issues

1. **系统字号调大后，应用内档位区分度明显下降**(第 3 轮实测，API 35)。
   系统字号 1.5 只兑现 **1.309×**；在其之上再选应用内「更大」只额外兑现 **1.100×**(标称 1.30×)。
   「大一点」档在系统 1.5 下尤其接近无效。
   这是平台的非线性字体换算造成的，**刻意不干预**(掰直等于覆盖用户手机的系统级设置，
   属可访问性反模式)。缓解：默认档本身就是大字号(正文 20sp，Material 默认 16sp)。
   详见 `records/03` ADR-004、`records/02` RC-002。
2. 引导页第二页在 1080×2400、系统字号 1.0 下主按钮需滚动才完整可见(页面偏长)。
3. 设置页「已保存」提示停留 2 秒，对 55 岁以上用户可能偏短，尚未用目标用户验证。
4. **作息推断的全部阈值都是推导值**(第 4 轮)。「每天至少 20 条在场信号」「至少 3 天」
   「安静密度 < 均值 × 0.35」「离起床/睡前 45 分钟内不报空窗」这些数字是从需求推出来的，
   **没有任何真实用户数据支撑**，也没有在真机上跑过真实信号。
   它们保证的是「宁可不说，也不要猜错」，不代表贴合目标用户的真实使用频率。
   详见 `records/04` 第 4.5 节。
5. **设置页的「作息数据」目前只能显示 0 条**(第 5 轮)。存储、去重、清理都实现了，仪器化测试
   31 例全绿，但**还没有东西往里写**——采集器排在第 6 轮。所以「已记录 N 条」那种状态
   **一次都没在界面上出现过**，设备上看到的全部是空态文案。
6. **30 天保留窗口的自动清理目前没有宿主**(第 5 轮)。`RhythmRepository.prune()` 已实现，
   也有用例守着，但**没有任何定时器在跨进程调它**，计划与第 6 轮采集器一起落。
   在那之前库不会自己变短；用户仍可手动「清空作息数据」。
7. **`CapturedSignal` 还没有生产者**(第 5 轮)。它只是给采集器预先定下的中间结构形状，
   现在没有任何代码构造它。它不是界面上的假功能，但**它现在确实什么都不做**。
8. **引导页在 composition scope 里写盘**(第 5 轮记下的隐患)。`OnboardingScreen.kt:62` 用的是
   `rememberCoroutineScope()`，composition 被销毁时写入可能被取消。本轮实测落盘成功，
   所以**没有改**，列入待观察。

### Unverified

以下事项**没有**验证过，不得在别处当作已验证引用：

- 真机(非模拟器)上的字号渲染与整体观感。
- 系统字号**真实**设为 1.5(通过系统设置界面)时的观感——目前只在仪器化测试里
  用合成的 `density` 验证过行为，没有在真实系统设置下看过界面。
- 性能基线：启动耗时、滚动帧率、内存占用。
- TalkBack 与无障碍焦点顺序。
- 目标用户(55-65 岁刚退休人群)可用性走查——尚无真人参与。
- 本机 `adb` 无法输入中文，所以 UI 层链路用 ASCII 称呼走通；
  中文称呼的持久化由 `SettingsStoreTest` 覆盖，**UI 层未验证中文输入**。
- 作息引擎**从未处理过真实信号**：`RhythmSignal` 目前只能由测试代码构造，
  系统广播采集器要到 **第 6 轮**才有(此处原写「第 5 轮」，是排期估算偏差，第 5 轮做的是落库)。
  所有「作息推断能用」的结论都只建立在合成数据上。
- 设备上**没有任何真实信号进过库**：设置页「作息数据」卡片的条数**恒为 0**，
  「已记录 N 条」那种状态一次都没在界面上出现过(第 5 轮)。
- **Room 首次开库对冷启动耗时的影响未测**(第 5 轮新引入的读库行为)。
- 30 天保留窗口的**真实清理未在设备上长期观察过**，只在仪器化测试里用合成时钟验证；
  而且**没有定时器在调 `prune()`**(见 Known Issues 第 6 条)。
- **「用户改了时区」这件事没有在设备上真的做过**(第 5 轮)。时区换算只在 `RhythmMapping`
  一处实现，但这不代表它被验证过。
- **30,117,942 字节那个 APK 无法复现**(第 5 轮)。结论是「压缩层偶发异常」，
  证据是两者未压缩载荷逐字节一致；但**它为什么会出现，我没有查到根因**。
- 作息画像的实际价值(识别出的空窗是不是用户真的「没事做」)**未验证**，需要真人参与。
- `RhythmInference.infer()` 的耗时**未测**。
