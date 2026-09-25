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
 * 质量屏蔽的分类规则：想法参与过滤、视频可被整体屏蔽、阈值为 0 时该条规则不生效。
 */
class QualityFilterRuleTest {
    private fun person(followers: Int = 0, following: Boolean = false) = Person(
        id = "author-id",
        url = "https://www.zhihu.com/people/author",
        userType = "people",
        urlToken = "author",
        name = "作者",
        headline = "",
        avatarUrl = "",
        followersCount = followers,
        isFollowing = following,
    )

    private fun pin(likes: Int, author: Person = person()) = Feed.PinTarget(
        id = 574,
        url = "https://www.zhihu.com/pin/574",
        author = author,
        likeCount = likes,
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

    private fun article(votes: Int, author: Person) = Feed.ArticleTarget(
        id = 557849764,
        url = "https://zhuanlan.zhihu.com/p/557849764",
        author = author,
        voteupCount = votes,
        commentCount = 0,
        title = "文章",
        excerpt = "",
    )

    @Test
    fun pinIsFilteredByLikeThreshold() {
        assertEquals(
            "规则：想法；点赞数 < 10，未关注作者",
            pin(likes = 3).filterReason(QualityFilterSettings(pinLikeCount = 10)),
        )
        assertNull(pin(likes = 10).filterReason(QualityFilterSettings(pinLikeCount = 10)))
        assertNull(pin(likes = 99).filterReason(QualityFilterSettings(pinLikeCount = 10)))
    }

    @Test
    fun pinLikeThresholdZeroDisablesTheRule() {
        assertNull(pin(likes = 0).filterReason(QualityFilterSettings(pinLikeCount = 0)))
    }

    @Test
    fun followedAuthorPinIsNeverFilteredByThreshold() {
        assertNull(
            pin(likes = 0, author = person(following = true))
                .filterReason(QualityFilterSettings(pinLikeCount = 10)),
        )
    }

    @Test
    fun blockPinIgnoresLikeCountAndFollowState() {
        val settings = QualityFilterSettings(blockPin = true)
        assertEquals("规则：想法；已开启直接屏蔽想法", pin(likes = 9999).filterReason(settings))
        assertEquals(
            "规则：想法；已开启直接屏蔽想法",
            pin(likes = 9999, author = person(following = true)).filterReason(settings),
        )
    }

    @Test
    fun blockVideoIgnoresVoteCountAndFollowState() {
        val settings = QualityFilterSettings(blockVideo = true)
        assertEquals("规则：视频；已开启直接屏蔽视频", video(votes = 9999).filterReason(settings))
        assertEquals(
            "规则：视频；已开启直接屏蔽视频",
            video(votes = 9999, author = person(following = true)).filterReason(settings),
        )
    }

    @Test
    fun videoIsFilteredOnlyByVoteThreshold() {
        // 作者粉丝数不再参与视频判定：粉丝极少但赞数达标的视频应保留。
        val lowFollowerAuthor = person(followers = 1)
        assertEquals(
            "规则：视频；赞数 < 20，未关注作者",
            video(votes = 5, author = lowFollowerAuthor).filterReason(QualityFilterSettings(videoVoteCount = 20)),
        )
        assertNull(video(votes = 20, author = lowFollowerAuthor).filterReason(QualityFilterSettings(videoVoteCount = 20)))
    }

    @Test
    fun articleFollowerThresholdDefaultsToDisabled() {
        // 默认 articleFollowersCount = 0，粉丝数不参与判定；显式设置后才生效。
        val lowFollowerAuthor = person(followers = 1)
        assertNull(article(votes = 100, author = lowFollowerAuthor).filterReason(QualityFilterSettings()))
        assertEquals(
            "规则：文章；作者粉丝数 < 50 或文章赞数 < 20，未关注作者",
            article(votes = 100, author = lowFollowerAuthor)
                .filterReason(QualityFilterSettings(articleFollowersCount = 50)),
        )
    }
}
