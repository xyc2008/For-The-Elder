package com.fortheelder

import android.app.Application
import com.fortheelder.core.AppContainer

class ElderApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
