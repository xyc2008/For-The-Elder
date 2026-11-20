package com.fortheelder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.fortheelder.ui.AppRoot

/**
 * 唯一的 Activity。
 *
 * 界面全部由 Compose 承担，不需要在 Activity 里写任何布局或生命周期逻辑；
 * 依赖从 [ElderApp] 的容器取，Activity 自己不知道任何仓库或引擎的存在。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ElderApp).container
        setContent {
            AppRoot(container)
        }
    }
}
