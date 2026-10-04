package com.github.zly2006.zhihu.account

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.android.Android
import kotlinx.coroutines.Dispatchers

internal actual val accountHttpClientEngineFactory: HttpClientEngine = Android.create {
    // 提高 Android 端单页详情并发拉取能力。
    // Android 引擎底层使用 HttpURLConnection，每条请求占用一个调度器线程，
    // 默认 ioDispatcher 并行度有限，会让推荐/搜索一页约 20 条详情请求被排队串行处理。
    // 显式放大 IO 调度器并行度，让一页内容可以同时发起更多详情请求。
    dispatcher = Dispatchers.IO.limitedParallelism(128)
}
