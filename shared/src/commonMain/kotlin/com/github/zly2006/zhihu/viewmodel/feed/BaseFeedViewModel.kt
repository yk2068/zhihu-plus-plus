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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.flattenFeeds
import com.github.zly2006.zhihu.data.toDisplayItem
import com.github.zly2006.zhihu.platform.UserMessageSink
import com.github.zly2006.zhihu.viewmodel.FeedDisplayEnvironment
import com.github.zly2006.zhihu.viewmodel.HomeFeedFilterResult
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.PaginationViewModel
import com.github.zly2006.zhihu.viewmodel.QualityFilterMode
import com.github.zly2006.zhihu.viewmodel.filter.BlockedTopic
import com.github.zly2006.zhihu.viewmodel.filter.ContentDetailProvider
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import com.github.zly2006.zhihu.viewmodel.getOrFetchContentDetail
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlin.reflect.typeOf

/**
 * 滑动到底部时额外预取的「未过滤」条目数。
 *
 * 过滤规则会把一部分条目替换成「已屏蔽」占位，如果只按页大小预取，用户快速下滑时
 * 很容易滑到尚未加载的区域。多留 5 条缓冲可以让这一屏之后仍有内容可看。
 */
const val PREFETCH_UNFILTERED_MARGIN = 5

abstract class BaseFeedViewModel : PaginationViewModel<Feed>(typeOf<Feed>()) {
    var displayItems = mutableStateListOf<FeedDisplayItem>()
    internal var latestLoadedDisplayItems = mutableStateOf<List<FeedDisplayItem>>(emptyList())
    internal var completedPageCount by mutableIntStateOf(0)
    var isPullToRefresh by mutableStateOf(false)
        protected set

    /**
     * 已消费的未过滤条目数：滑动到底部时由 UI 上报，用来决定是否需要继续预取。
     *
     * 只在 [shouldPrefetchMore] 里作为基准使用，不影响分页游标本身。
     */
    var consumedUnfilteredCount by mutableIntStateOf(0)

    /**
     * 还需要的预取条数。
     *
     * 需求是「除了当前显示的，再多加载 5 条没被过滤的」：当已加载的可用条目相对
     * 已消费量不足 [PREFETCH_UNFILTERED_MARGIN] 条时，就继续翻页，直到补足或翻到底。
     */
    val shouldPrefetchMore: Boolean
        get() {
            if (isEnd || isLoading) return false
            val available = displayItems.count { !it.isFiltered }
            return available - consumedUnfilteredCount < PREFETCH_UNFILTERED_MARGIN
        }

    /**
     * 「隐藏」模式下，被质量规则命中的条目直接不进列表。
     *
     * 质量过滤在 [createDisplayItem] 里把命中条目替换成「已屏蔽」占位，再按 [QualityFilterMode] 决定去留：
     * - OFF：完全不判断
     * - RULES：保留占位，让用户看到「这里过滤掉了什么」
     * - HIDE：直接移除
     *
     * 这个判断放在基类，是因为 `displayItems` 是所有推荐模式（Web / 安卓 / 本地 / 混合）共用的最终列表。
     * 此前 HIDE 只写在 `HomeFeedViewModel` 里，混合推荐（默认模式）和安卓推荐都绕过了它。
     */
    private fun FeedDisplayEnvironment.dropsQualityFilteredItems(): Boolean =
        feedDisplaySettings().qualityFilterMode == QualityFilterMode.HIDE

    override fun processResponse(environment: PaginationEnvironment, data: List<Feed>, rawData: JsonArray) {
        super.processResponse(environment, data, rawData)
        val loadedItems = data.flattenFeeds().map { createDisplayItem(environment, it) }
        addDisplayItems(loadedItems, dropQualityFiltered = environment.dropsQualityFilteredItems())
        latestLoadedDisplayItems.value = loadedItems
    }

    override fun refresh(environment: PaginationEnvironment) {
        displayItems.clear()
        consumedUnfilteredCount = 0
        super.refresh(environment)
    }

    suspend fun pullToRefresh(environment: PaginationEnvironment) {
        isPullToRefresh = true
        displayItems.clear()
        consumedUnfilteredCount = 0
        if (isLoading) return
        errorMessage = null
        debugData.clear()
        allData.clear()
        lastPaging = null // 重置 lastPaging
        isLoading = true
        try {
            fetchFeeds(environment)
        } catch (e: Exception) {
            errorHandle(e)
        }
        isLoading = false
        isPullToRefresh = false
    }

    open fun createDisplayItem(environment: FeedDisplayEnvironment, feed: Feed): FeedDisplayItem {
        val settings = environment.feedDisplaySettings()
        return feed.toDisplayItem(
            enableQualityFilter = settings.qualityFilterMode != QualityFilterMode.OFF,
            reverseBlock = settings.reverseBlock,
            qualityFilterSettings = settings.qualityFilter,
        )
    }

    /**
     * 追加待展示条目。
     *
     * [dropQualityFiltered] 为 true（即「隐藏」模式）时，被质量规则命中的条目直接丢弃，
     * 而不是以「已屏蔽」占位的形式留在列表里。
     */
    fun addDisplayItems(
        newItems: List<FeedDisplayItem>,
        dropQualityFiltered: Boolean = false,
    ) {
        newItems.forEach {
            if (dropQualityFiltered && it.isQualityFiltered) return@forEach
            if (displayItems.none { existing -> existing.stableKey == it.stableKey }) {
                displayItems.add(it)
            }
        }
    }

    fun handleBlockUser(
        environment: PaginationEnvironment,
        userMessages: UserMessageSink,
        feedItem: FeedDisplayItem,
        onShowDialog: (Pair<String, String>) -> Unit,
    ) {
        viewModelScope.launch {
            val authorInfo = resolveFeedBlockAuthorInfo(
                feedItem,
                ContentDetailProvider(environment::getOrFetchContentDetail),
            )
            if (authorInfo != null) {
                onShowDialog(authorInfo)
            } else {
                userMessages.showLongMessage("无法获取屏蔽用户所需的数据，请尝试进入内容详情页操作")
            }
        }
    }

    fun handleBlockQuestionAuthor(
        environment: PaginationEnvironment,
        userMessages: UserMessageSink,
        feedItem: FeedDisplayItem,
        onShowDialog: (Pair<String, String>) -> Unit,
    ) {
        viewModelScope.launch {
            val authorInfo = resolveFeedQuestionAuthorInfo(
                feedItem,
                ContentDetailProvider(environment::getOrFetchContentDetail),
            )
            if (authorInfo != null) {
                onShowDialog(authorInfo)
            } else {
                userMessages.showLongMessage("当前条目没有可用的提问者数据，无法屏蔽提问者")
            }
        }
    }

    fun handleBlockByKeywords(
        environment: PaginationEnvironment,
        userMessages: UserMessageSink,
        feedItem: FeedDisplayItem,
        onShowDialog: (Pair<FeedDisplayItem, Triple<String, String, String?>>) -> Unit,
    ) {
        viewModelScope.launch {
            val contentInfo = resolveFeedKeywordBlockingContent(
                feedItem,
                ContentDetailProvider(environment::getOrFetchContentDetail),
            )
            if (contentInfo != null) {
                onShowDialog(feedItem to contentInfo)
            } else {
                userMessages.showLongMessage("无法获取关键词屏蔽所需的数据，请尝试进入内容详情页操作")
            }
        }
    }

    fun handleBlockTopic(
        userMessages: UserMessageSink,
        topicId: String,
        topicName: String,
    ) {
        viewModelScope.launch {
            try {
                getContentFilterDatabase()
                    .blockedTopicDao()
                    .insertTopic(BlockedTopic(topicId = topicId, topicName = topicName))
                userMessages.showShortMessage("已屏蔽主题「$topicName」")
                removeFeedItemsByBlockedTopic(this@BaseFeedViewModel, topicId)
            } catch (e: Exception) {
                userMessages.showShortMessage("屏蔽失败: ${e.message}")
            }
        }
    }
}

/**
 * Merges the final home-feed filter result back into the list that was already shown optimistically.
 *
 * Only items from [HomeFeedFilterResult.foregroundItems] are touched, so older or unrelated cards in the
 * list keep their current state. A foreground item is removed when it is absent from
 * [HomeFeedFilterResult.filteredItems], and replaced when the final filter pipeline returns a matching item
 * with the same [FeedDisplayItem.stableKey]. This lets delayed quality/content filters swap an already
 * rendered card with an `已屏蔽` placeholder while preserving existing raw content if the replacement has not
 * loaded one. Reverse-block mode is intentionally ignored because it renders filtered items directly.
 *
 * [dropQualityFiltered] 对应「隐藏」模式：命中质量规则的条目已经在 [BaseFeedViewModel.addDisplayItems]
 * 里被丢弃，这里同样不能再写回列表，否则卡片会被重新加回来——表现为「选了隐藏却仍显示已屏蔽」。
 */
internal fun MutableList<FeedDisplayItem>.replaceHomeFeedItemsWithFilteredResult(
    filterResult: HomeFeedFilterResult,
    dropQualityFiltered: Boolean = false,
) {
    if (filterResult.reverseBlock) return

    val foregroundKeys = filterResult.foregroundItems.map { it.stableKey }.toSet()
    val filteredItemsByKey = filterResult.filteredItems.associateBy { it.stableKey }
    var index = 0
    while (index < size) {
        val item = this[index]
        if (item.stableKey !in foregroundKeys) {
            index++
            continue
        }

        val filteredVersion = filteredItemsByKey[item.stableKey]
        if (filteredVersion == null || (dropQualityFiltered && item.isQualityFiltered)) {
            removeAt(index)
        } else {
            this[index] = filteredVersion.copy(raw = filteredVersion.raw ?: item.raw)
            index++
        }
    }
}
