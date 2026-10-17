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
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
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

/**
 * 冷启动时是否要立刻联网刷新首页。
 *
 * 开关打开时总是刷新；关闭时除非完全读不到快照（首次安装、清过缓存、刚换推荐算法，
 * 这时不联网首页就是空白的），否则只用快照，不再请求网络。
 */
fun shouldRefreshHomeFeedOnStartup(
    autoRefreshOnStartup: Boolean,
    hasSnapshot: Boolean,
): Boolean = autoRefreshOnStartup || !hasSnapshot

/**
 * 读取启动快照。
 *
 * 任何异常都退化成「没有快照」，但会打日志：此前这里是静默 `runCatching`，
 * 快照写失败或读失败时用户只会觉得「关了自动刷新还是会刷新」，排查不到原因。
 */
fun readHomeFeedStartupSnapshot(file: Path): List<FeedDisplayItem> = try {
    if (SystemFileSystem.exists(file)) {
        SystemFileSystem.source(file).buffered().use { source ->
            decodeHomeFeedStartupSnapshot(source.readString())
        }
    } else {
        emptyList()
    }
} catch (error: Exception) {
    Log.e("HomeFeedStartupSnapshot", "Failed to read home feed startup snapshot: $file", error)
    emptyList()
}

/** 写入启动快照，失败时返回 false 并打日志（不影响首页正常使用）。 */
fun writeHomeFeedStartupSnapshot(
    file: Path,
    items: List<FeedDisplayItem>,
): Boolean {
    val serialized = encodeHomeFeedStartupSnapshot(items) ?: return false

    return try {
        SystemFileSystem.sink(file).buffered().use { it.writeString(serialized) }
        true
    } catch (error: Exception) {
        Log.e("HomeFeedStartupSnapshot", "Failed to write home feed startup snapshot: $file", error)
        false
    }
}
