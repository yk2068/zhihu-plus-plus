package com.github.zly2006.zhihu.account

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.Dispatchers

internal actual val accountHttpClientEngineFactory: HttpClientEngine = CIO.create {
    // 提高 JVM 端单页详情并发拉取能力。
    // 默认 ioDispatcher 的并行度有限（约等于 CPU 核数），会让推荐/搜索一页约 20 条
    // 内容详情请求被排队串行处理，造成体感加载慢。这里显式放大 IO 调度器并行度。
    dispatcher = Dispatchers.IO.limitedParallelism(128)
    // 单主机最大连接数，避免被默认 100 限制住并发详情请求。
    endpoint.maxConnectionsPerRoute = 1000
}
