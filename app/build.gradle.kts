import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.fortheelder"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fortheelder"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true

        // 设置页要显示版本号。AGP 8 起 BuildConfig 默认关闭，必须显式打开；
        // 否则会出现「明明声明了却找不到 BuildConfig.VERSION_NAME」的编译错误。
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    // 迁移测试要用 MigrationTestHelper 校验「升级之后的表结构」与 KSP 导出的 schema 一致，
    // 而 helper 是从**测试 APK 的 assets** 里读那些 json 的。不把 schemas 挂进来的话，
    // 它会在运行时报「Cannot find schema file」——也就是测试直接红，而不是悄悄跳过。
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// NFR-17 的守卫测试（`UiCopyPlacementTest`）要**逐行读 main 源码**，而 Gradle 单元测试的
// 工作目录并不保证是模块目录。与其在测试里猜路径，不如在构建脚本里明确告诉它：
// 属性缺失时测试会直接报错，不会悄悄跳过——守卫失去作用比守卫报错更糟。
// 第 38 轮的清单守卫（`ManifestPostureTest`）读的是 AndroidManifest.xml，同理。
tasks.withType<Test>().configureEach {
    systemProperty("fortheelder.mainSrc", layout.projectDirectory.dir("src/main/java").asFile.absolutePath)
    systemProperty(
        "fortheelder.manifest",
        layout.projectDirectory.file("src/main/AndroidManifest.xml").asFile.absolutePath,
    )
    // 第 39 轮的文案层守卫（`SettingsVoiceTest`）要读 androidTest 源码，核对屏幕层
    // 用来辨认结论句的前缀是否真的认得这些句子——两处文案一旦走散，屏幕上读到的
    // 结论就会失真而两边测试都还是绿。同样：属性缺失直接报错，不静默跳过。
    systemProperty(
        "fortheelder.androidTestSrc",
        layout.projectDirectory.dir("src/androidTest/java").asFile.absolutePath,
    )
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // 首页要按「前台/后台」判断是否重新决定说话的时机（见 HomeViewModel.onStart）。
    // 这个包本来经 navigation-compose 传递进了运行时类路径，但我们要直接 import
    // LocalLifecycleOwner，显式声明才不会在别人哪天砍掉 navigation 时突然编译不过。
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // 全量图标包（material-icons-extended）曾在这里，但它带来 11,404 个图标类
    // （只 debug 也占掉 classes.dex 的一多半）。界面实际只用到 ArrowBack 与
    // KeyboardArrowRight，两者都在 material3 传递依赖的 material-icons-core 里，
    // 所以这里不再直接依赖 extended。以后真要用别的图标，再加回来即可。
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
