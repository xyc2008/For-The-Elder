package com.fortheelder

import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadPoolExecutor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「设备层那段反射还摸得着 androidx 的私有字段吗」的守卫（第 69 轮新增）。
 *
 * ## 它守的是什么
 *
 * `app/src/androidTest` 里的 `TestTeardown.kt` 靠**五个私有字段名**穿过 androidx 的封装：
 * `ArchTaskExecutor.sIOThreadExecutor`、`ArchTaskExecutor.mDelegate`、
 * `ArchTaskExecutor.mDefaultTaskExecutor`、`DefaultTaskExecutor.mDiskIO`（以上四个为了证明
 * `database.queryExecutor` 派活的池子就是 `ArchTaskExecutor` 的共享 IO 池子），以及
 * `InvalidationTracker.observerMap`（数库里还挂着几个观察者）。
 *
 * 这五个名字**没有公开读法**，是绑在**具体版本**上的实现细节。从第 51 轮起，`records` 与
 * `docs/06` 一直挂着两条同样的旧账：「五个反射字段名无版本守卫」「错误升级时只会在设备层
 * 报红，而设备层不一定跑」——这一版把「升级会不会悄悄发生过」这件事变成一个**在 JVM 上
 * 就能跑**的用例。
 *
 * ## 它怎么守（四段）
 *
 * 1. **字段在不在、是不是原来的形状**：名字、`static` 与否、声明类型（`TaskExecutor` /
 *    `ExecutorService`），以及 `observerMap` 的声明类型上**有没有那条 `size()` 走法**；
 * 2. **真的走一遍取池路径**：在 JVM 上调 `ArchTaskExecutor.getInstance()`，断言
 *    `sIOThreadExecutor` 与 `getIOThreadExecutor()` 是**同一个对象**，再穿过
 *    `mDelegate` / `mDefaultTaskExecutor` 读 `mDiskIO`，断言**去重之后恰好一个**
 *    `ThreadPoolExecutor`。第 1 段只能证明「名字还在」，这一段证明「这条路还通」；
 * 3. **依赖版本仍钉在结论成立的那一版上**：读 `gradle/libs.versions.toml`，断言 `room` 还是
 *    `2.6.1`、`composeBom` 还是 `2024.10.01`；
 * 4. **被守卫的源码里那五个字面量还在**：守卫失效比守卫报错更糟，所以还要反过来钉住
 *    `TestTeardown.kt` 里真的写着这五个名字。
 *
 * ## 它不保证什么
 *
 * - **第 3 段是变更检测器（tripwire），不是证明**。它不证明 `observerMap` 在 2.6.1 上够用
 *   （那是设备层用例的事），也不证明 material3 1.3.1 的非可点 `Surface` 确实挂
 *   `pointerInput(Unit)`（那是第 51 轮 `javap` 字节码证据的事）。它只保证**换版本这件事
 *   不会静悄悄发生**：想升级，就得先看见这条红、先重验、再改钉住值。钉住值本身没有
 *   推导能力，是人工复核的入口。
 * - **`composeBom` 与 material3 版本之间的映射关系不在这里再算一遍**：
 *   `2024.10.01` 映射到 `material3 1.3.1` 这条事实记在 `docs/06` 第 51 轮那节与
 *   `records/03` 的 ADR 里，本用例只钉住 BOM 值本身，读它的 POM 需要 Gradle 缓存路径，
 *   那会让这条用例变成「换台机器就红」。
 * - **`android.os.Handler` 那一层一个字都没验**：`DefaultTaskExecutor.mMainHandler` 是
 *   Android 类型，本用例只确认「类能装进 JVM（`Class.forName(…, false, …)` 不初始化）」，
 *   不碰主线程派发。
 * - **`queryExecutor === ArchTaskExecutor 的共享 IO 执行器`这个同一性**只可能在设备层验
 *   （它要一个真的 `RoomDatabase`），本用例验的是它的**前置条件**：那条反射路径在 JVM 上
 *   真的通。两者不互相替代。
 *
 * 源码目录由 `app/build.gradle.kts` 的系统属性 `fortheelder.androidTestSrc` 给出；属性缺失
 * 时这条用例**直接失败**而不是跳过（纪律抄自 `DocScreenCopyGuardTest` 与 `ManifestPostureTest`）。
 */
class ReflectionContractGuardTest {

    private val androidTestSrc: File = File(
        System.getProperty("fortheelder.androidTestSrc")
            ?: error("缺少系统属性 fortheelder.androidTestSrc，无法定位 androidTest 源码目录"),
    )

    /** `app/src/androidTest/java` 往上四层就是仓库根。 */
    private val repoRoot: File = androidTestSrc.parentFile!!.parentFile!!.parentFile!!.parentFile!!

    /** 被守卫的那份源码。 */
    private val teardown: File = File(androidTestSrc, "com/fortheelder/ui/TestTeardown.kt")

    private val catalog: File = File(repoRoot, "gradle/libs.versions.toml")

    private val 载入器 = javaClass.classLoader

    /** 五个反射字段名，逐个写清宿主类。 */
    private val 五个字段: List<Pair<String, String>> = listOf(
        "androidx.arch.core.executor.ArchTaskExecutor" to "sIOThreadExecutor",
        "androidx.arch.core.executor.ArchTaskExecutor" to "mDelegate",
        "androidx.arch.core.executor.ArchTaskExecutor" to "mDefaultTaskExecutor",
        "androidx.arch.core.executor.DefaultTaskExecutor" to "mDiskIO",
        "androidx.room.InvalidationTracker" to "observerMap",
    )

    private fun 声明字段(类名: String, 字段名: String): Field {
        val 类 = try {
            Class.forName(类名, false, 载入器)
        } catch (没有: ClassNotFoundException) {
            throw AssertionError(
                "测试类路径上定不到 $类名，而 TestTeardown.kt 要靠它读 $字段名：" +
                    "要么依赖被砍了、要么类换包了，这条守卫已经失去作用（见 ReflectionContractGuardTest.kt）",
                没有,
            )
        }
        return try {
            类.getDeclaredField(字段名)
        } catch (没有: NoSuchFieldException) {
            throw AssertionError(
                "$类名 上找不到字段 $字段名：androidx 升级改了实现细节，" +
                    "TestTeardown.kt 那段反射要重写（见 ReflectionContractGuardTest.kt）",
                没有,
            )
        }
    }

    /**
     * 判断一个声明类型是不是某个类型的子类型，**全部走运行时反射**。
     *
     * `androidx.arch.core` 不在单元测试的**编译**类路径上（它经 `room-runtime` 的
     * `implementation` 进来，只在运行时可见），所以这里不能用 `X::class.java`，
     * 只能在跑起来之后按名字装类。
     */
    private fun 是某类的子类(父类名: String, 类型: Class<*>): Boolean =
        Class.forName(父类名, false, 载入器).isAssignableFrom(类型)

    private fun 读私有字段(类名: String, 对象: Any?, 字段名: String): Any? {
        val 字段 = 声明字段(类名, 字段名)
        字段.isAccessible = true
        return try {
            字段.get(对象)
        } catch (拒绝: IllegalAccessException) {
            throw AssertionError(
                "$类名.$字段名 设了 isAccessible 之后仍然读不进去（模块化收紧？）：" +
                    "TestTeardown.kt 那段反射要重写（见 ReflectionContractGuardTest.kt）",
                拒绝,
            )
        }
    }

    /** 第 1 段：名字、`static` 与否、声明类型，五个点一个不落。 */
    @Test
    fun `五个反射字段名仍在原位且形状与读法没变`() {
        val 门面 = "androidx.arch.core.executor.ArchTaskExecutor"
        val 缺省 = "androidx.arch.core.executor.DefaultTaskExecutor"

        val 共享字段 = 声明字段(门面, "sIOThreadExecutor")
        assertTrue(
            "ArchTaskExecutor.sIOThreadExecutor 不再是 static：TestTeardown 读它的方式要改",
            Modifier.isStatic(共享字段.modifiers),
        )

        for (名 in listOf("mDelegate", "mDefaultTaskExecutor")) {
            val 字段 = 声明字段(门面, 名)
            assertTrue(
                "ArchTaskExecutor.$名 变成了 static：TestTeardown 把它当实例字段读，读法要改",
                !Modifier.isStatic(字段.modifiers),
            )
            assertTrue(
                "ArchTaskExecutor.$名 的声明类型是 ${字段.type.name}，不再是 TaskExecutor 的子类：" +
                    "穿过它去读 mDiskIO 这条路断了（见 ReflectionContractGuardTest.kt）",
                是某类的子类("androidx.arch.core.executor.TaskExecutor", 字段.type),
            )
        }

        val 池字段 = 声明字段(缺省, "mDiskIO")
        assertTrue(
            "$缺省.mDiskIO 的声明类型是 ${池字段.type.name}，不再是 ExecutorService：" +
                "TestTeardown 把它强转成 ThreadPoolExecutor 用，转不过去了",
            ExecutorService::class.java.isAssignableFrom(池字段.type),
        )
    }

    /** 第 1 段之续：`observerMap` 的声明类型上那条 `size()` 走法还在（`SafeIterableMap` 不是 `Map`）。 */
    @Test
    fun `Room 的 observerMap 上那条 size 走法还在`() {
        val 字段 = 声明字段("androidx.room.InvalidationTracker", "observerMap")
        val 读法 = try {
            字段.type.getMethod("size")
        } catch (没有: NoSuchMethodException) {
            throw AssertionError(
                "observerMap 的声明类型 ${字段.type.name} 上找不到 public 的 size()：" +
                    "TestTeardown 数观察者个数的方式要改（见 ReflectionContractGuardTest.kt）",
                没有,
            )
        }
        assertTrue(
            "size() 的返回类型是 ${读法.returnType.name}，而 TestTeardown 按 int 读它",
            读法.returnType == Int::class.javaPrimitiveType,
        )
    }

    /**
     * 第 2 段：真的走一遍取池路径，断言「恰好一个池子」——与设备层的同一段逻辑等价的 JVM 版。
     *
     * 这一段的断言之所以不是「知道值」而是「知道形状」：设备层要的就是
     * 「`sIOThreadExecutor` 与 `queryExecutor` 是同一个对象」+「穿过两个入口去重后只剩一个池子」，
     * 而池子线程数由设备层自己读 `maximumPoolSize`，所以这里只断言它 ≥ 1。
     */
    @Test
    fun `穿过 ArchTaskExecutor 真的摸得到恰好一个 IO 池子`() {
        val 门面名 = "androidx.arch.core.executor.ArchTaskExecutor"
        val 门面 = Class.forName(门面名, true, 载入器)
        val 单例 = 门面.getMethod("getInstance").invoke(null)
            ?: throw AssertionError(
                "ArchTaskExecutor.getInstance() 返回 null：TestTeardown 后面的路一步都走不了",
            )

        val 共享执行器 = 读私有字段(门面名, null, "sIOThreadExecutor")
            ?: throw AssertionError("ArchTaskExecutor.sIOThreadExecutor 读出来是 null")

        assertSame(
            "sIOThreadExecutor 与 getIOThreadExecutor() 不再是同一个对象：" +
                "TestTeardown 正是靠这个同一性认出「库往哪个池子排活」，这条判据作废了",
            门面.getMethod("getIOThreadExecutor").invoke(null),
            共享执行器,
        )

        val 池子 = listOf("mDelegate", "mDefaultTaskExecutor")
            .mapNotNull { 读私有字段(门面名, 单例, it) }
            .distinct()
            .mapNotNull { 委托 -> 读私有字段(委托.javaClass.name, 委托, "mDiskIO") as? ThreadPoolExecutor }
            .distinct()

        assertEquals(
            "穿过 mDelegate / mDefaultTaskExecutor 摸到的 IO 池子应当是 1 个：" +
                "多了少了都说明这段反射跟着 androidx 漂了（见 TestTeardown.kt）",
            1,
            池子.size,
        )
        assertTrue(
            "池子线程数是 ${池子.single().maximumPoolSize}：设备层拿它当两阶段屏障要排的任务数，必须 ≥ 1",
            池子.single().maximumPoolSize >= 1,
        )
    }

    /**
     * 第 3 段：依赖版本还是结论成立的那一版。
     *
     * 只解析 `[versions]` 段——`[libraries]` 里的行是 `{ group = …, version.ref = … }`，
     * 与这里要的键值对不是一种东西。
     */
    @Test
    fun `依赖版本仍钉在反射与 Material3 结论成立的那一版上`() {
        assertTrue("版本目录不存在：${catalog.path}", catalog.isFile)
        val 全文 = catalog.readText()
        val 开头 = 全文.indexOf("[versions]")
        assertTrue("版本目录里找不到 [versions] 段：解析口径过期了", 开头 >= 0)
        val 段尾 = 全文.indexOf("\n[", 开头 + 1)
        val 段 = if (段尾 >= 0) 全文.substring(开头, 段尾) else 全文.substring(开头)

        val 版本 = Regex("^([A-Za-z0-9_.-]+)\\s*=\\s*\"([^\"]*)\"", RegexOption.MULTILINE)
            .findAll(段)
            .associate { it.groupValues[1] to it.groupValues[2] }
        assertTrue("版本目录里只读出 ${版本.size} 个键，太少了：解析口径可能已经过期", 版本.size >= 10)

        assertEquals(
            "room 从 2.6.1 动了。TestTeardown.kt 里 InvalidationTracker.observerMap 这条反射" +
                "只在 2.6.1 上验过 —— 先在设备层重验它（字段名与 size() 走法），再更新本用例的钉住值。",
            "2.6.1",
            版本["room"],
        )
        assertEquals(
            "composeBom 从 2024.10.01 动了（它映射到 material3 1.3.1）。第 51 轮那条结论" +
                "（非可点 Surface 自带 pointerInput）只在这一个版本上验过，`ElderComponents.kt` 里" +
                "「那一层不用 Surface」的写法就是因为它 —— 先重验，再更新本用例的钉住值。",
            "2024.10.01",
            版本["composeBom"],
        )
    }

    /**
     * 第 4 段：被守卫的源码里那五个字面量还在。
     *
     * 这一段防的是「守卫变成空转」：反射段被删掉或改名，前三段照样绿，而这五个版本断言就
     * 变成了没有对象的口头保证。它**故意**会挡住「把反射换成公开读法」这类合法重构——
     * 换掉的当天请连这条用例一起改，别让它无声过期。
     */
    @Test
    fun `设备层源码里那五个反射字面量还在被引用`() {
        assertTrue("找不到被守卫的源码：${teardown.path}", teardown.isFile)
        val 全文 = teardown.readText()
        for ((类名, 字段名) in 五个字段) {
            assertTrue(
                "TestTeardown.kt 里已经找不到 \"$字段名\" 这个字面量了（原宿主 $类名）：" +
                    "反射段被删或改名之后，本用例的版本断言就没有对象了 —— " +
                    "要么把反射加回来，要么连这条用例一起改（见 ReflectionContractGuardTest.kt）",
                全文.contains("\"$字段名\""),
            )
        }
    }
}
