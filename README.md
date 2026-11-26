# For-The-Elder（常伴）

> 面向 55–65 岁刚退休人群的「懂节制的陪伴者」——低打扰的生活节奏陪伴 App。

本仓库是该 App 的完整交付物：一个可构建的 Android 工程（Kotlin + Jetpack Compose）、
一套从立项到测试的软件工程文档（`docs/00` ~ `docs/09`），以及逐轮迭代的过程记录（`records/`）。

## 这是什么

立项判断来自需求文档《项目总体思路：做“懂节制的陪伴者”》：

> 精准锁定 55-65 岁、刚退休、有一定智能机基础但面临“时间突然变多、感到失落”的过渡期老人。

所以本产品不做健康管理、不做子女远程看护，也不抢用户时长：**不推销、不打扰、不制造焦虑**，
只在用户自己说「愿意」时才往前递一步。立项理由、目标与降级路径见 `docs/00-项目章程与需求计划书.md`。

## 核心链路

App 从启动到跑完一圈走四步，每一步都有对应的领域引擎与界面：

| 步骤 | 做什么 | 代码入口 |
| --- | --- | --- |
| 作息感知 | 用本机使用行为（解锁频率、充电时段）推断空闲规律，找出「介入窗口」，不使用穿戴设备 | `app/src/main/java/com/fortheelder/domain/engine/rhythm/` |
| 意愿发现 | 不弹问卷、不要求填标签，在闲谈里接住话茬，**只有用户说「愿意」才提供帮助** | `app/src/main/java/com/fortheelder/domain/engine/intent/` |
| 兴趣陪伴 | 表达意愿后给低门槛尝试选项，并持续轻推，帮用户立起生活锚点 | `app/src/main/java/com/fortheelder/ui/talk/` |
| 脑力互动 | 注意力／执行功能的游戏化小游戏，以插件形式存在，剥离医疗宣称 | `app/src/main/java/com/fortheelder/domain/play/` |

界面只有五个去处：首页、闲谈、玩一玩、设置、引导回看，
路由表在 `app/src/main/java/com/fortheelder/ui/nav/MainNav.kt`。

## 怎么构建、怎么跑测试

前置：JDK 17 与 Android SDK。`gradle.properties` 已把 `org.gradle.java.home` 固定到 JDK 17
（理由写在同一文件的注释里），SDK 版本为 `compileSdk = 35`、`minSdk = 26`、`targetSdk = 35`。
`local.properties` 不入库，本地没有时先写上 `sdk.dir=<你的 Android SDK 路径>`。

```bash
# 单元测试（JVM，不需要模拟器）
./gradlew :app:testDebugUnitTest
# Windows 的 cmd / PowerShell 用：gradlew.bat :app:testDebugUnitTest

# 打 debug 包
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

# 设备层测试（需要已连接的模拟器或真机）
./gradlew :app:connectedDebugAndroidTest
```

装到已连接的设备并启动，用 `run.sh`（一条命令把「找 SDK → 构建 → 安装 → 启动 → 回显读数」串起来）：

```bash
./run.sh              # 构建 → 装到第一个已连接设备 → 启动
./run.sh --emulator   # 没有已连接设备时自动开模拟器（默认 AVD：Elder_API35）
./run.sh --no-build   # 跳过构建，直接用现有的 app-debug.apk
./run.sh --reinstall  # 启动前先卸载同名包（清掉上一次的数据）
./run.sh --shot       # 启动后截图存到 build/run-shot.png
./run.sh --logcat     # 启动后跟随 logcat（Ctrl-C 退出）
./run.sh --help       # 看用法
```

Windows 的 cmd / PowerShell 用 `run.bat`（`run.bat --no-build`）：它只负责找到 `bash.exe`
再把参数原样转给 `run.sh`，逻辑不重复实现一遍。

脚本自己算，不写死机器相关的值：SDK 依次试 `ANDROID_SDK` → `ANDROID_HOME` →
`.toolchain/android-sdk` → `local.properties` 的 `sdk.dir`；包名与启动组件从 APK 里读出
（debug 变体的 applicationId 带 `.debug` 后缀，见 `app/build.gradle.kts` 的
`applicationIdSuffix`），所以不写死 `.debug`。任一步读不出来就报错停下，不猜。

第 103 轮在 Android 15 模拟器（`emulator-5554`）上把四条路都真跑了一遍：
`./run.sh`、`./run.sh --help`、`./run.sh --no-build --shot`、`cmd /c run.bat --no-build`
全部退出码 0，安装回显 `Success`，启动回显 `Status: ok / LaunchState: COLD`。

它自动化的就是下面这几行手工命令：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.fortheelder.debug/com.fortheelder.MainActivity
```

## 文档地图

| 文件 | 内容 |
| --- | --- |
| `docs/00-项目章程与需求计划书.md` | 立项理由、项目目标、里程碑、假设与降级路径 |
| `docs/01-需求规格说明书.md` | 功能需求（FR）、非功能需求（NFR）与验收标准 |
| `docs/02-概要设计说明书.md` | 分层与模块划分、数据流 |
| `docs/03-详细设计说明书.md` | 逐模块的类与函数级设计，含语义挂载与版式读数 |
| `docs/04-数据库设计说明书.md` | Room 表结构、索引与迁移 |
| `docs/05-接口设计说明书.md` | 模块间接口与调用约定 |
| `docs/06-测试计划与用例.md` | 测试分层、用例清单与验收证据 |
| `docs/07-风险管理计划.md` | 风险台账、风险等级与监控信号 |
| `docs/08-部署与构建说明.md` | 构建、签名、安装与排障 |
| `docs/09-用户手册.md` | 面向使用者的说明书 |

## 过程记录

| 文件 | 内容 |
| --- | --- |
| `records/01-迭代记录.md` | 逐轮做了什么、读数是多大、哪一块没做成 |
| `records/02-需求变更记录.md` | 需求侧的每一次变更与其理由 |
| `records/03-架构决策记录.md` | ADR：每个架构决定与它的权衡 |
| `records/04-质量与验证记录.md` | 每轮的验证链与实测读数留档 |
| `records/CHANGELOG.md` | 按轮汇总的变更清单 |

## 当前状态

- 迭代轮次：**第 103 轮**已完成，每 10 轮向本仓库推一次，历史见 `records/CHANGELOG.md`。
- 代码规模：`app/src/main` 有 79 个 Kotlin 文件；`app/src/test` 55 个 JVM 测试类；
  `app/src/androidTest` 31 个设备层测试类。
- 最近一次实测读数（**第 103 轮**，逐条留档在 `records/04-质量与验证记录.md`）：
  `:app:testDebugUnitTest` 597 例全绿；`:app:assembleDebug` 产出 `app-debug.apk`
  （11,020,680 字节，md5 `8adbba63ca911252f98ed4f2b5f28bc6`）；
  `:app:connectedDebugAndroidTest` 在 Android 15 模拟器上 186 例全绿。

## 这个仓库不保证什么

- 文档里带轮次的**历史行号**（「第 N 轮时它在第几百行」）是当时的快照，**不回改成现值**，
  回改等于篡改历史，理由见 `records/03-架构决策记录.md`。
- 已知未关闭的风险、口径偏差与旧账都在 `docs/07-风险管理计划.md` 的台账里列着，不做隐藏。
- 面向 55–65 岁用户的**可用性**只能靠人工走查；机器只保证「可构建、可安装、可跑通、读数可复现」。
