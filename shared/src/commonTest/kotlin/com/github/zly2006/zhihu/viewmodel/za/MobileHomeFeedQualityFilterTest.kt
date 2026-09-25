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

package com.github.zly2006.zhihu.viewmodel.za

import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.QualityFilterSettings
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.target
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * 手机版推荐卡片的解析结果必须带可参与质量过滤的 target。
 *
 * 回归背景：手机版推荐曾直接在 `parseMobileHomeFeedDisplayItem` 里构造 FeedDisplayItem，
 * 不走 `createDisplayItem`，且只为想法建了 `feed`，导致「手机版推荐」下的赞数过滤、
 * 屏蔽视频/想法等规则完全不生效。
 */
class MobileHomeFeedQualityFilterTest {
    private fun card(routeUrl: String, voteCount: Int?, authorStyle: String = "RecommendAuthorLine"): String {
        // 没有 Vote 反应时用纯文本页脚，模拟真实卡片里缺少赞数的情形。
        val voteElement =
            if (voteCount == null) {
                """{ "type": "Text", "text": "仅文本页脚" },"""
            } else {
                """{ "reaction": "Vote", "count": $voteCount },"""
            }
        return """
            {
              "type": "ComponentCard",
              "id": "card-1",
              "action": { "parameter": "route_url=${routeUrl.replace("&", "%26").replace("=", "%3D")}" },
              "children": [
                { "id": "Text", "type": "Text", "text": "卡片标题" },
                { "id": "text_pin_summary", "type": "Text", "text": "卡片摘要" },
                { "type": "Line", "elements": [] },
                {
                  "type": "Line",
                  "elements": [
                    $voteElement
                    { "reaction": "Comment", "count": 1 },
                    { "reaction": "Collect", "count": 1 }
                  ]
                },
                {
                  "style": "$authorStyle",
                  "elements": [
                    { "style": "Avatar_default", "image": { "url": "https://pic.example/avatar.jpg" } },
                    { "type": "Text", "text": "某作者" }
                  ]
                }
              ]
            }
            """.trimIndent()
    }

    private fun parse(routeUrl: String, voteCount: Int?) =
        parseMobileHomeFeedDisplayItem(
            ZhihuJson.json.parseToJsonElement(card(routeUrl, voteCount)).jsonObject,
        )

    @Test
    fun answerCardExposesVoteCountForQualityFilter() {
        val item = assertNotNull(parse("https://www.zhihu.com/answer/123", voteCount = 3))
        val answer = assertIs<Feed.AnswerTarget>(assertNotNull(item.feed).target)

        assertEquals(3, answer.voteupCount)
        // 赞数确实参与判定，而不是因为 target 缺失被跳过。
        assertEquals(
            "规则：回答；赞数 < 10，未关注作者",
            answer.filterReason(QualityFilterSettings(minLikeCount = 10)),
        )
    }

    @Test
    fun articleCardExposesVoteCountForQualityFilter() {
        val item = assertNotNull(parse("https://zhuanlan.zhihu.com/p/456", voteCount = 2))
        val article = assertIs<Feed.ArticleTarget>(assertNotNull(item.feed).target)

        assertEquals(2, article.voteupCount)
        assertEquals(
            "规则：文章；赞数 < 10，未关注作者",
            article.filterReason(QualityFilterSettings(minLikeCount = 10)),
        )
    }

    @Test
    fun cardWithoutVoteReactionCountsAsBelowThreshold() {
        // 没有 Vote 反应时赞数未知（-1）。按「缺失也算不达标」的口径处理，
        // 而不是跳过判断——否则卡片不返回该字段时规则会被整体绕过。
        val item = assertNotNull(parse("https://www.zhihu.com/answer/789", voteCount = null))
        val answer = assertIs<Feed.AnswerTarget>(assertNotNull(item.feed).target)

        assertEquals(-1, answer.voteupCount)
        assertEquals(
            "规则：回答；赞数 < 10，未关注作者",
            answer.filterReason(QualityFilterSettings(minLikeCount = 10)),
        )
    }
}
