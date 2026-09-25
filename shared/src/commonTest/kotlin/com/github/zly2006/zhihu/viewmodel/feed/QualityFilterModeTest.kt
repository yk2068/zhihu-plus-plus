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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.zly2006.zhihu.viewmodel.feed

import com.github.zly2006.zhihu.data.CommonFeed
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.Person
import com.github.zly2006.zhihu.data.QualityFilterSettings
import com.github.zly2006.zhihu.viewmodel.FeedDisplayEnvironment
import com.github.zly2006.zhihu.viewmodel.FeedDisplaySettings
import com.github.zly2006.zhihu.viewmodel.QualityFilterMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QualityFilterModeTest {
    private val lowQualityArticle = CommonFeed(
        target = Feed.ArticleTarget(
            id = 637,
            url = "https://zhuanlan.zhihu.com/p/637",
            author = Person(
                id = "author",
                url = "https://api.zhihu.com/people/author",
                userType = "people",
                name = "作者",
                headline = "",
                avatarUrl = "",
                followersCount = 0,
                isFollowing = false,
            ),
            voteupCount = 0,
            title = "低赞文章",
        ),
    )

    @Test
    fun offModeKeepsOriginalCard() {
        val item = HomeFeedViewModel().createDisplayItem(
            object : FeedDisplayEnvironment {
                override fun feedDisplaySettings() = FeedDisplaySettings(qualityFilterMode = QualityFilterMode.OFF)
            },
            lowQualityArticle,
        )

        assertEquals("低赞文章", item.title)
        assertFalse(item.isFiltered)
        assertFalse(item.isQualityFiltered)
    }

    @Test
    fun rulesAndHideModesMarkQualityFilteredCards() {
        listOf(QualityFilterMode.RULES, QualityFilterMode.HIDE).forEach { mode ->
            val item = HomeFeedViewModel().createDisplayItem(
                object : FeedDisplayEnvironment {
                    override fun feedDisplaySettings() = FeedDisplaySettings(qualityFilterMode = mode)
                },
                lowQualityArticle,
            )

            assertEquals("已屏蔽", item.title)
            assertTrue(item.isFiltered)
            assertTrue(item.isQualityFiltered)
        }
    }

    @Test
    fun customArticleVoteupThresholdIsApplied() {
        val item = HomeFeedViewModel().createDisplayItem(
            object : FeedDisplayEnvironment {
                override fun feedDisplaySettings() = FeedDisplaySettings(
                    qualityFilter = QualityFilterSettings(minLikeCount = 1000),
                )
            },
            lowQualityArticle.copy(target = (lowQualityArticle.target as Feed.ArticleTarget).copy(voteupCount = 500)),
        )

        assertTrue(item.isQualityFiltered)
        assertTrue(item.summary.orEmpty().contains("1000"))
    }

    /**
     * 「隐藏」模式下命中条目必须真的不进列表。
     *
     * 回归背景：HIDE 判断曾只写在 `HomeFeedViewModel` 里，而混合推荐（默认模式）和安卓推荐
     * 都直接继承 `BaseFeedViewModel`、绕过该判断，导致这两种模式下「隐藏」看起来完全不生效。
     * 现在丢弃逻辑收敛到基类 `addDisplayItems`，所有模式共用。
     */
    @Test
    fun hideModeDropsQualityFilteredItemsFromTheSharedList() {
        val model = HomeFeedViewModel()
        val filtered = FeedDisplayItem(
            title = "已屏蔽",
            summary = "低质量",
            details = "低质量",
            feed = null,
            isFiltered = true,
            isQualityFiltered = true,
        )
        val kept = FeedDisplayItem(title = "正常条目", summary = "摘要", details = "详情", feed = null)

        model.addDisplayItems(listOf(filtered, kept), dropQualityFiltered = true)

        assertEquals(1, model.displayItems.size)
        assertEquals("正常条目", model.displayItems.single().title)
    }

    @Test
    fun rulesModeKeepsQualityFilteredPlaceholder() {
        val model = HomeFeedViewModel()
        val filtered = FeedDisplayItem(
            title = "已屏蔽",
            summary = "低质量",
            details = "低质量",
            feed = null,
            isFiltered = true,
            isQualityFiltered = true,
        )

        model.addDisplayItems(listOf(filtered), dropQualityFiltered = false)

        assertEquals(1, model.displayItems.size)
        assertEquals("已屏蔽", model.displayItems.single().title)
    }
}
