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

package com.github.zly2006.zhihu.ui

import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.RecommendationMode
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.util.Log
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

const val AUTO_REFRESH_HOME_ON_STARTUP_PREFERENCE_KEY = "autoRefreshHomeOnStartup"
const val LEGACY_HOME_FEED_STARTUP_CACHE_FILE_NAME = "home_feed_startup_cache.json"
private const val HOME_FEED_STARTUP_CACHE_FILE_PREFIX = "home_feed_startup_cache_"
private const val HOME_FEED_STARTUP_CACHE_FILE_SUFFIX = ".json"

/**
 * 启动快照最多保留的条目数。
 *
 * 取 30 是为了让「已显示之外的缓存」也有内容：启动时先用快照填充列表，
 * 用户下滑时仍有卡片可看，同时限制文件体积不至于过大。
 */
private const val HOME_FEED_STARTUP_SNAPSHOT_MAX_ITEMS = 30

fun homeFeedStartupCacheFileName(recommendationMode: RecommendationMode): String =
    HOME_FEED_STARTUP_CACHE_FILE_PREFIX + recommendationMode.key + HOME_FEED_STARTUP_CACHE_FILE_SUFFIX

fun homeFeedStartupCacheFileNames(): List<String> =
    listOf(LEGACY_HOME_FEED_STARTUP_CACHE_FILE_NAME) + RecommendationMode.entries.map(::homeFeedStartupCacheFileName)

fun encodeHomeFeedStartupSnapshot(items: List<FeedDisplayItem>): String? {
    // raw 的多态内容自带 type 字段，会与 kotlinx.serialization 的默认类型判别字段冲突；启动恢复也不需要这份可重新获取的详情缓存。
    val snapshotItems = items.take(HOME_FEED_STARTUP_SNAPSHOT_MAX_ITEMS).map { it.copy(raw = null) }
    if (snapshotItems.isEmpty()) return null

    return try {
        ZhihuJson.json.encodeToString(snapshotItems)
    } catch (error: Exception) {
        Log.e("HomeFeedStartupSnapshot", "Failed to encode home feed startup snapshot", error)
        null
    }
}

fun decodeHomeFeedStartupSnapshot(serialized: String?): List<FeedDisplayItem> {
    if (serialized.isNullOrBlank()) return emptyList()

    return runCatching {
        ZhihuJson
            .json
            .decodeFromString<List<FeedDisplayItem>>(serialized)
    }.getOrDefault(emptyList())
}
