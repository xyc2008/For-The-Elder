package com.fortheelder

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 清单「姿态」的机械守卫（第 38 轮，从一条 lint warning 落下来的）。
 *
 * 起因：Android Lint 的 `DataExtractionRules` 在 `android:allowBackup="false"` 上报了一条
 * warning，建议 12+ 再声明 `android:dataExtractionRules`。第 38 轮的处理是**明确接受**
 * （理由见 `records/03-架构决策记录.md` ADR-036），但「接受一条 warning」不等于「这件事没人看着」。
 * 这台 App 的对外承诺是「数据不出这台手机」，而 `allowBackup` 正是这条承诺在清单里唯一的一处
 * 机械落点——所以给它配一条守卫：谁哪天顺手把它删了，这里会直接红。
 *
 * 顺带钉住清单里**一个权限都不能有**：清单开头那句注释写着「刻意不声明任何权限」，
 * 第 2 轮写下的这句话到现在仍然成立，但注释不会自己报警。
 *
 * 清单路径由 `app/build.gradle.kts` 的系统属性 `fortheelder.manifest` 提供；属性缺失或文件
 * 读不到时，这条用例**直接失败**而不是跳过——守卫悄悄失效，比守卫报错难发现得多。
 */
class ManifestPostureTest {

    private val manifest: File = File(
        System.getProperty("fortheelder.manifest")
            ?: error("缺少系统属性 fortheelder.manifest，无法定位 AndroidManifest.xml"),
    )

    private fun readManifest(): String {
        assertTrue("清单文件不存在：$manifest", manifest.isFile)
        val raw = manifest.readText(Charsets.UTF_8)
        assertTrue("清单只有 ${raw.length} 个字符，守卫可能没真的读到内容", raw.length > 200)
        return raw
    }

    @Test
    fun backupStaysOff() {
        val raw = readManifest()
        assertTrue(
            "`android:allowBackup=\"false\"` 不见了。这是「数据不出这台手机」在清单里唯一的机械落点，" +
                "删掉它等于把云备份那一路重新打开——要改这条，先改 docs/01 的承诺与 ADR-036。",
            raw.contains("android:allowBackup=\"false\""),
        )
    }

    @Test
    fun noPermissionIsDeclared() {
        val raw = readManifest()
        assertFalse(
            "清单里出现了 uses-permission。这台 App 刻意不声明任何权限（见清单开头那段注释）；" +
                "要引入权限，得先有对应的功能与需求条目，而不是先要权限。",
            raw.contains("<uses-permission"),
        )
    }
}
