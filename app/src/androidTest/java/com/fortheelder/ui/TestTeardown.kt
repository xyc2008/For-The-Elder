package com.fortheelder.ui

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.fortheelder.core.AppContainer
import org.junit.Assert.assertTrue
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * 「关掉测试库」这一步的收尾规则。用法是**套在 compose 规则的外面**：
 *
 * ```
 * private val compose = createComposeRule()
 *
 * @get:Rule
 * val rules: RuleChain = RuleChain.outerRule(关库排在组合之后(TEST_DB) { container }).around(compose)
 * ```
 *
 * `RuleChain` 的收尾是内层先跑，于是宿主 Activity 先被 compose 规则销毁
 * （组合销毁 → `ViewModelStore.clear()` → `viewModelScope` 取消），之后才轮到这里关库。
 * 顺序反过来会留下一个窗口：页面上的收集器还挂在 `viewModelScope` 上，库却已经关了。
 *
 * 为什么屏幕级用例需要这条规则、而视图模型用例不需要：`TalkViewModelTest` /
 * `HomeViewModelTest` 里的 `ViewModelStore` 是手工建的，它们自己写 `viewModelStore.clear()`
 * 并且**写在 `database.close()` 之前**；屏幕级用例的视图模型活在 compose 规则自己的 store
 * 里，外面够不着，只能靠收尾顺序把这一步交回给规则。
 *
 * 关库之前先确认宿主 Activity 真的已经不在前台了。这条不变量被谁改没了，症状是「偶发」
 * 而不是「必红」——与其靠注释提醒，不如让它当场断言。
 */
fun 关库排在组合之后(databaseName: String, container: () -> AppContainer): TestRule =
    object : TestRule {
        override fun apply(base: Statement, description: Description): Statement =
            object : Statement() {
                override fun evaluate() {
                    try {
                        base.evaluate()
                    } finally {
                        要求宿主已经不在前台()
                        container().database.close()
                        InstrumentationRegistry.getInstrumentation().targetContext
                            .deleteDatabase(databaseName)
                    }
                }
            }
    }

/**
 * 前台上一个 Activity 都不剩。
 *
 * `ActivityLifecycleMonitorRegistry` 要求在主线程上问，所以这里过一遍 `runOnMainSync`。
 * 返回的名单里还有东西就说明 compose 规则还没把宿主收掉，那意味着收尾顺序被调回去了。
 */
private fun 要求宿主已经不在前台() {
    var 前台的宿主数 = -1
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        前台的宿主数 = ActivityLifecycleMonitorRegistry.getInstance()
            .getActivitiesInStage(Stage.RESUMED)
            .size
    }
    assertTrue(
        "关库时前台上还有 $前台的宿主数 个 Activity：收尾顺序反了（见 TestTeardown.kt）",
        前台的宿主数 == 0,
    )
}
