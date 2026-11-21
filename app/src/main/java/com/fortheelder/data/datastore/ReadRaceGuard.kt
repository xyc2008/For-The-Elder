package com.fortheelder.data.datastore

import androidx.datastore.preferences.core.Preferences
import java.io.FileNotFoundException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen

/**
 * 把「文件在这一刻刚刚出现」引发的那一次读失败重试掉。**只重试一次。**
 *
 * 起因是一个在真机上复现过的真缺陷：全新安装后第一次打开应用，
 * `dataStore.data` 会抛出
 * `java.io.FileNotFoundException: … talk-vm-…preferences_pb: open failed: ENOENT`。
 * 它不是「文件坏了」，而是读与写撞在了同一瞬间。把成因写清楚，因为「为什么重试是安全的」
 * 完全依赖成因：
 *
 * 1. `DataStore` 的读取只有非阻塞的 `tryLock`（`OkioReadScope.readScope`），
 *    所以读**不会**排队等写，交错是可能的；
 * 2. 文件还不存在时，`open` 必然失败（ENOENT），这是正常情况；
 *    正常路径是「open 失败 → 再看文件在不在 → 不在就返回默认值」，
 *    见 `OkioReadScope.readData()`：
 *    ```kotlin
 *    catch (ex: FileNotFoundException) {
 *        if (fileSystem.exists(path)) { throw ex }
 *        serializer.defaultValue
 *    }
 *    ```
 * 3. 于是 `FileNotFoundException` 逃到用户手上，**只可能**是
 *    「`open` 失败的时候文件不存在，但 `exists()` 查的时候文件已经在了」——
 *    也就是写入的 `atomicMove`（Linux 上就是一次原子 `rename`，不可能留下「目标消失」的窗口）
 *    恰好落在 `open` 与 `exists()` 之间。
 *
 * 换句话说：抛出这个异常时，文件**一定已经写好了**。数据没有损坏，读也没读错，
 * 只是读得早了一拍。这就是重试安全的理由，也是为什么这里不能顺手改成
 * 「吞掉异常、返回默认值」——那会在真的读不到时静默给出一份空数据。
 *
 * 重试确实会重新读盘，而不是把缓存的失败再抛一次：`InitDataStore` 继承的
 * `RunOnce.runIfNeeded()` 只有在 `doRun()` 正常返回后才会 `didRun.complete(Unit)`，
 * 抛异常时 `didRun` 仍是未完成态，下一次收集会重新走一遍 `doRun()`
 * （见 `DataStoreImpl.kt` 里 `RunOnce` 的注释「If it fails, it will be retried in the
 * next runIfNeeded call」）。
 *
 * `attempt == 0L` 是**必须**的：只重试一次，让「重试也失败」照原样抛出去。
 * 连续两次都撞上同一个窗口的概率可以忽略，而万一真抛了，那多半是别的问题
 * （磁盘满、权限错），不该被这层重试反复掩盖。文件真的损坏走的是
 * `CorruptionException`，不是 `FileNotFoundException`，不会被这里接住。
 */
internal fun Flow<Preferences>.guardFirstReadRace(): Flow<Preferences> =
    retryWhen { cause, attempt -> cause is FileNotFoundException && attempt == 0L }
