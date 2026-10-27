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

package com.github.zly2006.zhihu.viewmodel.feed

private val AFTER_ID_REGEX = Regex("""([?&]after_id=)(\d+)""")
private val END_OFFSET_REGEX = Regex("""([?&]end_offset=)(\d+)""")

/** 一页通常是多少条，只在还没成功取过任何一页时用来估算游标步长。 */
internal const val RECOMMEND_PAGE_SIZE_FALLBACK = 10

/**
 * 推荐流的分页游标是「session_token + after_id/end_offset 偏移」。
 *
 * 同一个 session_token 下把偏移往前推，就能直接拿到后面第 N 页的地址，
 * 所以首屏可以一次并发拉好几页，不必等上一页返回才知道下一页地址。
 *
 * 偏移推得不准时最坏只是少量条目重复或漏掉：重复的会被
 * [BaseFeedViewModel.addDisplayItems] 按 stableKey 去掉，漏掉的无所谓（推荐流是无限的）。
 */
internal fun advanceRecommendCursor(url: String, byItems: Int): String {
    if (byItems <= 0) return url
    val after = AFTER_ID_REGEX.find(url)?.groupValues?.get(2)?.toIntOrNull() ?: return url
    val end = END_OFFSET_REGEX.find(url)?.groupValues?.get(2)?.toIntOrNull() ?: after
    return AFTER_ID_REGEX.replace(
        END_OFFSET_REGEX.replace(url) { match ->
            match.groupValues[1] + (end + byItems).toString()
        },
    ) { match ->
        match.groupValues[1] + (after + byItems).toString()
    }
}
