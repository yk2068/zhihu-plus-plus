/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.zly2006.zhihu.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NetworkErrorMessagesTest {
    @Test
    fun ktorTimeoutIsTranslated() {
        // Ktor 的原话，也就是用户之前弹窗里看到的那段
        val error = IllegalStateException(
            "Connect timeout has expired [url=https://api.zhihu.com/topstory/recommend?limit=10, " +
                "connect_timeout=10000 ms]",
        )

        assertEquals("网络连接超时，请稍后重试", error.toUserFacingMessage())
    }

    @Test
    fun socketErrorsAreTranslated() {
        assertEquals(
            "连不上服务器，请检查网络",
            IllegalStateException("Unable to resolve host \"api.zhihu.com\": No address associated with hostname")
                .toUserFacingMessage(),
        )
        assertEquals(
            "网络连接中断，请稍后重试",
            IllegalStateException("Connection reset").toUserFacingMessage(),
        )
        assertEquals(
            "网络连接不安全，请更换网络后重试",
            IllegalStateException("java.security.cert.CertPathValidatorException: Trust anchor for certification path not found")
                .toUserFacingMessage(),
        )
    }

    @Test
    fun chineseMessagesAreKeptAsIs() {
        // 应用自己写的提示比任何兜底文案都准确，不能被覆盖
        val message = "您可能已被风控，请重新登录。"
        assertEquals(message, RuntimeException(message).toUserFacingMessage())
    }

    @Test
    fun unknownErrorsFallBackToGenericText() {
        val message = IllegalStateException("Something exploded").toUserFacingMessage()
        assertEquals("加载失败，请稍后重试", message)
        // 兜底文案里不应该再出现原始异常里的英文
        assertFalse("exploded" in message)
    }

    @Test
    fun nullMessageStillProducesSomethingReadable() {
        assertEquals("加载失败，请稍后重试", RuntimeException().toUserFacingMessage())
    }
}
