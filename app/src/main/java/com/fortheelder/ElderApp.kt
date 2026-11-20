package com.fortheelder

import android.app.Application
import com.fortheelder.core.AppContainer

/**
 * 应用进程的入口。
 *
 * 这里只做一件事：把容器建出来，再让它开始跑那些**没有界面可依附**的后台工作
 * （作息采集、过期清理）。放在这里而不是 Activity 里，是因为这些工作的寿命是
 * 「进程」，而 Activity 会随着用户进出反复创建销毁——挂在 Activity 上，
 * 进一次设置页就多注册一个广播接收者。
 *
 * 容器自己不做任何决定，「什么时候开始」由这里说了算：因此测试里可以只建容器、
 * 不调 [AppContainer.startProcessWork]，不会被后台协程干扰。
 */
class ElderApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.startProcessWork()
    }
}
