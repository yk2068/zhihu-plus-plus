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

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabase.Builder
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.github.zly2006.zhihu.data.applyPlatformDriver
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [CrawlingTask::class, CrawlingResult::class, LocalFeed::class, UserBehavior::class],
    version = 7, // v7: 移除 v6 的 cached_content_details 表（详情落盘缓存已删除）
    exportSchema = false,
)
@TypeConverters(LocalDatabaseConverters::class)
@ConstructedBy(LocalContentDatabaseConstructor::class)
abstract class LocalContentDatabase : RoomDatabase() {
    abstract fun contentDao(): LocalContentDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object LocalContentDatabaseConstructor : RoomDatabaseConstructor<LocalContentDatabase> {
    override fun initialize(): LocalContentDatabase
}

private val migration6To7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        // 详情落盘缓存已整体移除，v6 建的表一并丢弃。
        connection.execSQL("DROP TABLE IF EXISTS `cached_content_details`")
    }
}

fun buildLocalContentDatabase(
    builder: Builder<LocalContentDatabase>,
): LocalContentDatabase = builder
    .addMigrations(migration6To7)
    .fallbackToDestructiveMigration(true)
    .applyPlatformDriver()
    .setQueryCoroutineContext(Dispatchers.Default)
    .build()
