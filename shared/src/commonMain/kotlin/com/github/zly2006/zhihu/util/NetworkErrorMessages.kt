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

/**
 * 把网络异常翻译成能直接弹给用户的短句。
 *
 * 之前这些地方直接弹 `e.message`，Ktor 超时异常的 message 形如
 * `Connect timeout has expired [url=https://api.zhihu.com/topstory/recommend?...]`，
 * 于是每次网络抖动都会糊用户一脸英文加一长串 URL。
 */
fun Throwable.toUserFacingMessage(): String {
    val message = message.orEmpty()
    val lower = message.lowercase()

    // 应用自己构造的中文提示（例如「您可能已被风控，请重新登录。」）原样保留，
    // 它比任何兜底文案都准确。
    if (message.any { it.code in CJK_RANGE }) return message

    return when {
        "timeout" in lower || "timed out" in lower -> "网络连接超时，请稍后重试"
        "unable to resolve host" in lower ||
            "unknownhost" in lower ||
            "nodename nor servname" in lower ||
            "name or service not known" in lower ||
            "no address associated" in lower -> "连不上服务器，请检查网络"
        "connection refused" in lower ||
            "connection reset" in lower ||
            "broken pipe" in lower ||
            "connection closed" in lower -> "网络连接中断，请稍后重试"
        "certificate" in lower ||
            "certification" in lower ||
            "certpath" in lower ||
            "ssl" in lower -> "网络连接不安全，请更换网络后重试"
        else -> "加载失败，请稍后重试"
    }
}

private val CJK_RANGE = 0x4E00..0x9FFF
