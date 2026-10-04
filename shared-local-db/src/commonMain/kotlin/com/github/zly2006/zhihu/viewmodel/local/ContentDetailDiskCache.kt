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

package com.github.zly2006.zhihu.viewmodel.local

import kotlin.time.Clock

/**
 * 内容详情磁盘缓存后端接口。
 * 由平台在 [ContentDetailCache] 初始化时注入，实现「下次打开优先读取磁盘缓存」的能力。
 */
interface ContentDetailDiskCache {
    /** 读取已缓存的原始详情 JSON；未命中返回 null。 */
    suspend fun read(contentType: String, contentId: String): String?

    /** 写入/更新一条详情的原始 JSON。 */
    suspend fun write(contentType: String, contentId: String, rawJson: String)

    /** 清理早于给定时间戳（毫秒）的缓存条目。 */
    suspend fun pruneBefore(thresholdMs: Long)
}

/**
 * 基于 Room [LocalContentDatabase] 的磁盘缓存实现。
 */
class RoomContentDetailDiskCache(
    private val dao: CachedContentDetailDao,
) : ContentDetailDiskCache {
    override suspend fun read(contentType: String, contentId: String): String? =
        runCatching { dao.get(contentType, contentId)?.json }.getOrNull()

    override suspend fun write(contentType: String, contentId: String, rawJson: String) {
        val now = Clock.System.now().toEpochMilliseconds()
        runCatching {
            dao.upsert(
                CachedContentDetail(
                    id = "$contentType:$contentId",
                    contentType = contentType,
                    contentId = contentId,
                    json = rawJson,
                    updatedAt = now,
                ),
            )
            // 软上限：超出后清理一周前的旧条目，避免表格无限膨胀
            if (dao.count() > MAX_CACHED_ENTRIES) {
                dao.deleteOlderThan(now - STALE_AGE_MS)
            }
        }
    }

    override suspend fun pruneBefore(thresholdMs: Long) {
        runCatching { dao.deleteOlderThan(thresholdMs) }
    }

    companion object {
        const val MAX_CACHED_ENTRIES = 2000
        const val STALE_AGE_MS = 7L * 24 * 60 * 60 * 1000 // 7 天
    }
}
