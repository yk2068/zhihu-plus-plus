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

package com.github.zly2006.zhihu.reading

import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.platform.MapSettingsStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReadingPositionMemoryTest {
    private fun enabledStore(limit: Int = DEFAULT_READING_POSITION_MEMORY_LIMIT) = MapSettingsStore(
        mutableMapOf(
            PREF_READING_POSITION_MEMORY to true,
            PREF_READING_POSITION_MEMORY_LIMIT to limit,
        ),
    )

    @Test
    fun disabledByDefault() {
        val memory = ReadingPositionMemory(MapSettingsStore())
        memory.remember("answer:1", 1200)
        assertEquals(false, memory.isEnabled)
        assertNull(memory.savedPosition("answer:1"))
    }

    @Test
    fun remembersDefaultLimitOfOneContent() {
        val memory = ReadingPositionMemory(enabledStore())
        assertEquals(1, memory.limit)

        memory.remember("answer:1", 1200)
        assertEquals(1200, memory.savedPosition("answer:1"))

        // 默认只记 1 条：写入第二篇时淘汰第一篇。
        memory.remember("article:2", 800)
        assertNull(memory.savedPosition("answer:1"))
        assertEquals(800, memory.savedPosition("article:2"))
    }

    @Test
    fun keepsMostRecentlyReadWithinLimit() {
        val memory = ReadingPositionMemory(enabledStore(limit = 3))
        memory.remember("a:1", 100)
        memory.remember("a:2", 200)
        memory.remember("a:3", 300)
        memory.remember("a:4", 400)

        assertNull(memory.savedPosition("a:1"))
        assertEquals(200, memory.savedPosition("a:2"))
        assertEquals(300, memory.savedPosition("a:3"))
        assertEquals(400, memory.savedPosition("a:4"))

        // 重新读旧内容会把它提到最近位置，被淘汰的是最久未读的 a:2。
        memory.remember("a:3", 350)
        memory.remember("a:5", 500)
        assertEquals(350, memory.savedPosition("a:3"))
        assertNull(memory.savedPosition("a:2"))
        assertEquals(500, memory.savedPosition("a:5"))
    }

    @Test
    fun ignoresZeroOffsetAndCanForget() {
        val memory = ReadingPositionMemory(enabledStore())
        memory.remember("answer:1", 0)
        assertNull(memory.savedPosition("answer:1"))

        memory.remember("answer:1", 600)
        assertEquals(600, memory.savedPosition("answer:1"))

        memory.forget("answer:1")
        assertNull(memory.savedPosition("answer:1"))
    }

    @Test
    fun clampsLimitToSupportedRange() {
        assertEquals(MIN_READING_POSITION_MEMORY_LIMIT, normalizedReadingPositionLimit(0))
        assertEquals(MIN_READING_POSITION_MEMORY_LIMIT, normalizedReadingPositionLimit(-5))
        assertEquals(MAX_READING_POSITION_MEMORY_LIMIT, normalizedReadingPositionLimit(999))
    }

    @Test
    fun keySeparatesArticlesAndAnswers() {
        assertEquals("answer:12", readingPositionKey(ArticleType.Answer, 12))
        assertEquals("article:12", readingPositionKey(ArticleType.Article, 12))
    }
}
