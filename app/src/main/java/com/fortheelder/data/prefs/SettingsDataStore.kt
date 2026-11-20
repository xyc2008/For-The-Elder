package com.fortheelder.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * 设置的落盘位置：`filesDir/datastore/settings.preferences_pb`。
 *
 * 用 Context 扩展属性是为了让 `preferencesDataStore` 只创建一次实例；
 * 它内部会做同名去重，重复定义会抛异常。
 */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
