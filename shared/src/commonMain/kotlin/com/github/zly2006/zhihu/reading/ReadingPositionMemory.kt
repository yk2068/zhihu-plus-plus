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
import com.github.zly2006.zhihu.platform.SettingsStore

/** 「记忆阅读位置」总开关。 */
const val PREF_READING_POSITION_MEMORY = "readingPositionMemory"

/**
 * 默认关闭。
 *
 * 位置记忆会改变重新打开内容时的落点，属于会让用户「回不去顶部」的行为，因此需要用户主动开启。
 */
const val DEFAULT_READING_POSITION_MEMORY = false

/** 记忆条数：最多保留多少篇内容的阅读位置，按最近阅读淘汰。 */
const val PREF_READING_POSITION_MEMORY_LIMIT = "readingPositionMemoryLimit"
const val DEFAULT_READING_POSITION_MEMORY_LIMIT = 1
const val MIN_READING_POSITION_MEMORY_LIMIT = 1
const val MAX_READING_POSITION_MEMORY_LIMIT = 50

private const val POSITION_KEY_PREFIX = "readingPositionMemory.offset."
private const val INDEX_KEY = "readingPositionMemory.index"
private const val INDEX_SEPARATOR = "\n"

/** 内容级的阅读位置键；文章和回答分开存，回答按回答 id 区分。 */
fun readingPositionKey(type: ArticleType, id: Long): String = "${type.name.lowercase()}:$id"

fun normalizedReadingPositionLimit(limit: Int): Int =
    limit.coerceIn(MIN_READING_POSITION_MEMORY_LIMIT, MAX_READING_POSITION_MEMORY_LIMIT)

/**
 * 计算写入一条位置后的索引顺序：最近读过排最前，超出 [limit] 的末尾条目被淘汰。
 *
 * 抽成纯函数，让淘汰规则只有一处实现，也便于单测覆盖。
 */
internal fun updatedReadingPositionIndex(
    current: List<String>,
    key: String,
    limit: Int,
): List<String> = (listOf(key) + current.filterNot { it == key }).take(normalizedReadingPositionLimit(limit))

/**
 * 跨页面、跨启动的内容阅读位置记忆。
 *
 * 位置以滚动像素（`ScrollState.value`）保存：详情页正文是同一套 Compose Markdown 渲染链路，像素值在同类内容上
 * 可复用，且不需要等待总高度稳定。超出记忆条数的条目按最近使用顺序淘汰，避免偏好里无限堆积。
 */
class ReadingPositionMemory(
    private val settings: SettingsStore,
) {
    val isEnabled: Boolean
        get() = settings.getBoolean(PREF_READING_POSITION_MEMORY, DEFAULT_READING_POSITION_MEMORY)

    val limit: Int
        get() = normalizedReadingPositionLimit(
            settings.getInt(PREF_READING_POSITION_MEMORY_LIMIT, DEFAULT_READING_POSITION_MEMORY_LIMIT),
        )

    /** 返回该内容的阅读位置；未开启、未记录或已从索引淘汰时返回 null。 */
    fun savedPosition(key: String): Int? {
        if (!isEnabled) return null
        if (key !in index()) return null
        return settings.getInt(POSITION_KEY_PREFIX + key, 0).takeIf { it > 0 }
    }

    /**
     * 记录阅读位置。
     *
     * 位置为 0 视为「没有可恢复的位置」，直接忽略，避免把首页状态写进记忆并挤掉真正读过的内容。
     */
    fun remember(
        key: String,
        offsetPx: Int,
    ) {
        if (!isEnabled || offsetPx <= 0) return
        val previous = index()
        val next = updatedReadingPositionIndex(previous, key, limit)
        previous.filterNot(next::contains).forEach { settings.remove(POSITION_KEY_PREFIX + it) }
        settings.putInt(POSITION_KEY_PREFIX + key, offsetPx)
        settings.putString(INDEX_KEY, next.joinToString(INDEX_SEPARATOR))
    }

    /** 清除某篇内容的阅读位置，用于用户主动回到顶部后离开的场景。 */
    fun forget(key: String) {
        settings.remove(POSITION_KEY_PREFIX + key)
        val next = index().filterNot { it == key }
        if (next.isEmpty()) {
            settings.remove(INDEX_KEY)
        } else {
            settings.putString(INDEX_KEY, next.joinToString(INDEX_SEPARATOR))
        }
    }

    private fun index(): List<String> = settings
        .getString(INDEX_KEY, "")
        .split(INDEX_SEPARATOR)
        .filter(String::isNotBlank)
}
