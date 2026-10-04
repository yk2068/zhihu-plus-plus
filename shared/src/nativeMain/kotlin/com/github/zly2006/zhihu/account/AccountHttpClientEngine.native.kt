package com.github.zly2006.zhihu.account

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

internal actual val accountHttpClientEngineFactory: HttpClientEngine = Darwin.create { }
