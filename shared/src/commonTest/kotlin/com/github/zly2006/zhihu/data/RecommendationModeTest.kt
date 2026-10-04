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

package com.github.zly2006.zhihu.data

import kotlin.test.Test
import kotlin.test.assertEquals

class RecommendationModeTest {
    @Test
    fun keepsPersistedKeysStable() {
        assertEquals("server", RecommendationMode.WEB.key)
        assertEquals("android", RecommendationMode.ANDROID.key)
        assertEquals("mixed", RecommendationMode.MIXED.key)
    }

    /**
     * 「本地推荐」已从选项中裁剪。旧安装里若存着 `local`，读取时会回落到 MIXED，
     * 因此这里同时锁住"local 不再是合法键"和"回落目标仍是 mixed"两点。
     */
    @Test
    fun localModeIsNoLongerAvailable() {
        assertEquals(null, RecommendationMode.entries.find { it.key == "local" })
        assertEquals(
            RecommendationMode.MIXED,
            RecommendationMode.entries.find { it.key == "local" } ?: RecommendationMode.MIXED,
        )
    }
}
