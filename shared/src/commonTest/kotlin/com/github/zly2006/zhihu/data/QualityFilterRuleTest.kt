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
import kotlin.test.assertNull

/**
 * 质量屏蔽规则：最低赞数对所有内容类型统一生效，视频与想法还支持整体屏蔽。
 */
class QualityFilterRuleTest {
    private fun person(following: Boolean = false) = Person(
        id = "author-id",
        url = "https://www.zhihu.com/people/author",
        userType = "people",
        urlToken = "author",
        name = "作者",
        headline = "",
        avatarUrl = "",
        isFollowing = following,
    )

    private fun answer(votes: Int, author: Person = person()) = Feed.AnswerTarget(
        id = 1,
        url = "https://www.zhihu.com/answer/1",
        author = author,
        voteupCount = votes,
        question = Feed.QuestionTarget(id = 2, url = "https://www.zhihu.com/question/2", type = "question"),
    )

    private fun article(votes: Int, author: Person = person()) = Feed.ArticleTarget(
        id = 557849764,
        url = "https://zhuanlan.zhihu.com/p/557849764",
        author = author,
        voteupCount = votes,
        title = "文章",
    )

    private fun video(votes: Int, author: Person = person()) = Feed.VideoTarget(
        id = 123,
        author = author,
        voteCount = votes,
        commentCount = 0,
        title = "视频",
        description = "",
        excerpt = "",
    )

    private fun pin(likes: Int, author: Person = person()) = Feed.PinTarget(
        id = 574,
        url = "https://www.zhihu.com/pin/574",
        author = author,
        likeCount = likes,
    )

    @Test
    fun minLikeCountAppliesToAnswerArticleAndVideo() {
        val settings = QualityFilterSettings(minLikeCount = 10)

        assertEquals("规则：回答；赞数 < 10，未关注作者", answer(votes = 3).filterReason(settings))
        assertEquals("规则：文章；赞数 < 10，未关注作者", article(votes = 3).filterReason(settings))
        assertEquals("规则：视频；赞数 < 10，未关注作者", video(votes = 3).filterReason(settings))

        assertNull(answer(votes = 10).filterReason(settings))
        assertNull(article(votes = 10).filterReason(settings))
        assertNull(video(votes = 10).filterReason(settings))
    }

    @Test
    fun minLikeCountZeroDisablesTheRule() {
        val settings = QualityFilterSettings(minLikeCount = 0)

        assertNull(answer(votes = 0).filterReason(settings))
        assertNull(article(votes = 0).filterReason(settings))
        assertNull(video(votes = 0).filterReason(settings))
    }

    @Test
    fun pinIsNotFilteredByDefaultBecauseLikeCountIsOftenAbsent() {
        // 很多想法卡片不返回点赞数，默认阈值必须放过它们，否则整个想法流会被误杀。
        assertNull(pin(likes = 0).filterReason(QualityFilterSettings()))
        assertNull(pin(likes = 0).filterReason(QualityFilterSettings(minLikeCount = 100)))
    }

    @Test
    fun pinUsesItsOwnOptInThreshold() {
        val settings = QualityFilterSettings(pinLikeCount = 10)

        assertEquals("规则：想法；点赞数 < 10，未关注作者", pin(likes = 3).filterReason(settings))
        assertNull(pin(likes = 10).filterReason(settings))
        // 已关注作者不受阈值影响。
        assertNull(pin(likes = 0, author = person(following = true)).filterReason(settings))
    }

    @Test
    fun followedAuthorIsNeverFilteredByThreshold() {
        val settings = QualityFilterSettings(minLikeCount = 10)
        val followed = person(following = true)

        assertNull(answer(votes = 0, author = followed).filterReason(settings))
        assertNull(article(votes = 0, author = followed).filterReason(settings))
        assertNull(video(votes = 0, author = followed).filterReason(settings))
    }

    @Test
    fun blockVideoIgnoresVoteCountAndFollowState() {
        val settings = QualityFilterSettings(blockVideo = true)

        assertEquals("规则：视频；已开启直接屏蔽视频", video(votes = 9999).filterReason(settings))
        assertEquals(
            "规则：视频；已开启直接屏蔽视频",
            video(votes = 9999, author = person(following = true)).filterReason(settings),
        )
        // 只影响视频：把赞数阈值关掉后，回答不会因 blockVideo 被牵连。
        assertNull(answer(votes = 0).filterReason(QualityFilterSettings(minLikeCount = 0, blockVideo = true)))
    }

    @Test
    fun blockPinIgnoresLikeCountAndFollowState() {
        val settings = QualityFilterSettings(blockPin = true)

        assertEquals("规则：想法；已开启直接屏蔽想法", pin(likes = 9999).filterReason(settings))
        assertEquals(
            "规则：想法；已开启直接屏蔽想法",
            pin(likes = 9999, author = person(following = true)).filterReason(settings),
        )
        // 只影响想法：把赞数阈值关掉后，文章不会因 blockPin 被牵连。
        assertNull(article(votes = 0).filterReason(QualityFilterSettings(minLikeCount = 0, blockPin = true)))
    }

    @Test
    fun questionUsesItsOwnThresholds() {
        val question = Feed.QuestionTarget(
            id = 1,
            url = "https://www.zhihu.com/question/1",
            type = "question",
            answerCount = 2,
            followerCount = 10,
        )

        assertEquals(
            "规则：问题；回答数 < 5，关注数 < 50",
            question.filterReason(QualityFilterSettings(questionAnswerCount = 5, questionFollowersCount = 50)),
        )
        assertNull(
            question.filterReason(QualityFilterSettings(questionAnswerCount = 0, questionFollowersCount = 0)),
        )
    }
}
