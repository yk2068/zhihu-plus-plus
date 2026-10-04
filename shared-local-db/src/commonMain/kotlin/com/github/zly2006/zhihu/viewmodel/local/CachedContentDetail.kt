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

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 已加载内容详情的磁盘缓存。
 * 用于持久化「已经过过滤流水线、进入显示列表（但用户可能尚未滚动到屏幕）」的内容详情，
 * 使得下次冷启动打开 feed 时可以优先从本地数据库读取，跳过重复的网络请求。
 */
@Entity(tableName = "cached_content_details")
data class CachedContentDetail(
    /** 复合主键，格式为 "$contentType:$contentId" */
    @PrimaryKey val id: String,
    val contentType: String,
    val contentId: String,
    /** API 返回的原始详情 JSON 字符串，读取时按 contentType 解码回 DataHolder.Content */
    val json: String,
    val updatedAt: Long,
)
