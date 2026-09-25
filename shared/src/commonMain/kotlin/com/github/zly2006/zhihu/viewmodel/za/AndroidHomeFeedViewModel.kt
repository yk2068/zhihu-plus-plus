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

import androidx.lifecycle.viewModelScope
import com.github.zly2006.zhihu.data.CommonFeed
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.Person
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.target
import com.github.zly2006.zhihu.data.toFeedDisplayItemNavDestinationJson
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.resolveContent
import com.github.zly2006.zhihu.util.jsonObject
import com.github.zly2006.zhihu.viewmodel.ContentInteractionEnvironment
import com.github.zly2006.zhihu.viewmodel.HomeFeedFilterResult
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.QualityFilterMode
import com.github.zly2006.zhihu.viewmodel.feed.BaseFeedViewModel
import com.github.zly2006.zhihu.viewmodel.feed.HomeFeedInteractionViewModel
import com.github.zly2006.zhihu.viewmodel.feed.replaceHomeFeedItemsWithFilteredResult
import com.github.zly2006.zhihu.viewmodel.postSigned
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.decodeURLPart
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class AndroidHomeFeedViewModel :
    BaseFeedViewModel(),
    HomeFeedInteractionViewModel {
    override val initialUrl: String
        get() = "https://api.zhihu.com/topstory/recommend"

    public override suspend fun fetchFeeds(environment: PaginationEnvironment) {
        try {
            val response = environment.mobileHomeFeedHttpClient().get(lastPaging?.next ?: initialUrl)
            if (response.status.isSuccess()) {
                val jojo = response.jsonObject()
                val data = jojo["data"]?.jsonArray ?: throw IllegalStateException("No data found in response")

                // 收集所有待显示的项目
                val itemsToDisplay = mutableListOf<FeedDisplayItem>()
                val displaySettings = environment.feedDisplaySettings()

                data
                    .map { it.jsonObject }
                    .forEach { card ->
                        try {
                            val displayItem = parseMobileHomeFeedDisplayItem(card) ?: return@forEach
                            itemsToDisplay.add(displayItem)
                        } catch (e: Exception) {
                            environment.logDecodeFailure("AndroidHomeFeedViewModel", card, e)
                        }
                    }

                // 手机版推荐卡片由本方法直接构造，不经过 createDisplayItem，因此质量过滤在这里单独执行。
                // 缺失这一步会导致「手机版推荐」下赞数/屏蔽视频等规则完全不生效。
                val qualityFilteredItems = if (displaySettings.qualityFilterMode == QualityFilterMode.OFF) {
                    itemsToDisplay
                } else {
                    itemsToDisplay.mapNotNull { item ->
                        val feed = item.feed ?: return@mapNotNull item
                        val filtered = createDisplayItem(environment, feed)
                        // 保留手机版解析出的展示字段（标题/摘要/详情来自卡片本身），只接管过滤结果。
                        if (filtered.isQualityFiltered) filtered else item
                    }
                }

                val hideQualityFiltered =
                    displaySettings.qualityFilterMode == QualityFilterMode.HIDE

                // 前台先做本地已读过滤，再立即展示
                val reverseBlock = displaySettings.reverseBlock
                val foregroundItems = environment.applyForegroundHomeFeedFilter(qualityFilteredItems)
                if (!reverseBlock) {
                    withContext(Dispatchers.Main) {
                        addDisplayItems(
                            if (hideQualityFiltered) foregroundItems.filterNot { it.isQualityFiltered } else foregroundItems,
                        )
                    }
                }

                val filteredItems = environment.applyBackgroundHomeFeedFilter(foregroundItems)
                if (reverseBlock) {
                    addDisplayItems(filteredItems)
                }

                val filterResult = HomeFeedFilterResult(
                    foregroundItems = foregroundItems,
                    filteredItems = filteredItems,
                    reverseBlock = reverseBlock,
                )

                // 移除被过滤的条目，并更新已保留条目的 raw 内容
                withContext(Dispatchers.Main) {
                    displayItems.replaceHomeFeedItemsWithFilteredResult(filterResult)
                    latestLoadedDisplayItems.value = filterResult.filteredItems
                }

                lastPaging = if ("paging" in jojo) {
                    ZhihuJson.decodeJson(jojo["paging"]!!)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                environment.handleMobileHomeFeedFailure(e)
            }
            throw e
        } finally {
            isLoading = false
        }
    }

    override suspend fun recordContentInteraction(environment: ContentInteractionEnvironment, feed: Feed) {
        // Android 版本暂不记录交互
    }

    override fun onUiContentClick(environment: ContentInteractionEnvironment, feed: Feed, item: FeedDisplayItem) {
        viewModelScope.launch(Dispatchers.Default) {
            if (environment.authenticatedCookies()["d_c0"] != null) {
                val payloadItem = when (val target = feed.target) {
                    is Feed.AnswerTarget -> listOf("answer", target.id.toString(), "read")
                    is Feed.ArticleTarget -> listOf("article", target.id.toString(), "read")
                    is Feed.PinTarget -> listOf("pin", target.id.toString(), "read")
                    else -> null
                }
                if (payloadItem != null) {
                    environment.postSigned("https://www.zhihu.com/lastread/touch") {
                        header("x-requested-with", "fetch")
                        setBody(
                            MultiPartFormDataContent(
                                formData {
                                    append("items", ZhihuJson.json.encodeToString(listOf(payloadItem)))
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalEncodingApi::class)
fun parseMobileHomeFeedDisplayItem(card: JsonObject): FeedDisplayItem? {
    if (card["type"]?.jsonPrimitive?.content != "ComponentCard") {
        return null
    }
    val route =
        card["action"]!!
            .jsonObject["parameter"]!!
            .jsonPrimitive.content
            .substringAfter("route_url=")
    val routeUrl = route.decodeURLPart()
    val routeDest = resolveContent(routeUrl) ?: return null
    val children = card["children"]?.jsonArray?.map { it.jsonObject } ?: return null
    val extra = if (routeDest is Pin) {
        card["extra"]?.let { ZhihuJson.decodeJson<MobileHomeCardExtra>(it) }
    } else {
        null
    }
    val originalContent = extra
        ?.businessExtMap
        ?.oriContent
        ?.takeIf { it.isNotBlank() }
        ?.let { encoded ->
            runCatching {
                ZhihuJson.decodeJson<MobileHomeOriginalContent>(
                    ZhihuJson.json.parseToJsonElement(Base64.Default.decode(encoded).decodeToString()),
                )
            }.getOrNull()
        }
    val title = if (routeDest is Pin) {
        extra
            ?.passthroughInfo
            ?.content
            ?.title
            ?.takeIf { it.isNotBlank() }
            ?: children
                .firstOrNull { it["id"]?.jsonPrimitive?.content == "Text" }
                ?.get("text")
                ?.jsonPrimitive
                ?.content
                .orEmpty()
    } else {
        children.joStrMatch("id", "Text")["text"]!!.jsonPrimitive.content
    }
    val summary = children.joStrMatch("id", "text_pin_summary")["text"]!!.jsonPrimitive.content
    // 页脚是第二个 type == "Line" 的子节点；用安全取值，避免个别卡片缺少 type 字段时整个条目被丢弃。
    val footer = children
        .filter { it["type"]?.jsonPrimitive?.content == "Line" }
        .getOrNull(1) ?: return null
    val footerLine = footer["elements"]!!.jsonArray.map { it.jsonObject }
    val voteUp = footerLine.firstOrNull { it["reaction"]?.jsonPrimitive?.content == "Vote" }
    val comment = footerLine.firstOrNull { it["reaction"]?.jsonPrimitive?.content == "Comment" }
    val collect = footerLine.firstOrNull { it["reaction"]?.jsonPrimitive?.content == "Collect" }
    // 赞数在质量过滤里要用到；页脚没有 Vote 反应时视为未知（-1），交由过滤规则决定是否参与判断。
    val voteUpCount = voteUp?.get("count")?.jsonPrimitive?.int ?: -1
    val footerText = if (voteUp != null && comment != null && collect != null) {
        val commentCount = comment["count"]!!.jsonPrimitive.int
        val collectCount = collect["count"]!!.jsonPrimitive.int
        "$voteUpCount 赞同 · $commentCount 评论 · $collectCount 收藏"
    } else {
        footerLine.joStrMatch("type", "Text")["text"]!!.jsonPrimitive.content
    }
    val lineAuthor =
        children
            .firstOrNull {
                val style = it["style"]?.jsonPrimitive?.content ?: return@firstOrNull false
                style.startsWith("RecommendAuthorLine") || style.startsWith("LineAuthor_default")
            }?.get("elements")
            ?.jsonArray
            ?.map { it.jsonObject }
            ?: return null
    val avatar = lineAuthor
        .joStrMatch("style", "Avatar_default")["image"]!!
        .jsonObject["url"]!!
        .jsonPrimitive.content
    val authorName = lineAuthor.joStrMatch("type", "Text")["text"]!!.jsonPrimitive.content
    if (routeDest is Article) {
        routeDest.authorName = authorName
        routeDest.title = title
        routeDest.avatarSrc = avatar
    }
    val feed = when (routeDest) {
        is Pin -> {
            val author = extra?.passthroughInfo?.author
            CommonFeed(
                id = card["id"]?.jsonPrimitive?.content.orEmpty(),
                target = Feed.PinTarget(
                    id = routeDest.id,
                    url = routeUrl,
                    author = Person(
                        id = author?.id.orEmpty(),
                        url = author?.url.orEmpty(),
                        userType = "people",
                        urlToken = author?.urlToken,
                        name = authorName,
                        headline = "",
                        avatarUrl = avatar,
                        isFollowing = author?.isFollowing == true,
                        isFollowed = author?.isFollowed == true,
                    ),
                    content = buildList {
                        add(DataHolder.Pin.ContentText(title = title, content = summary))
                        originalContent
                            ?.mediaInfo
                            ?.images
                            .orEmpty()
                            .filter { it.url.isNotBlank() }
                            .forEachIndexed { index, image ->
                                add(
                                    DataHolder.Pin.ContentImage(
                                        url = image.url,
                                        thumbnail = extra
                                            ?.businessExtMap
                                            ?.images
                                            ?.getOrNull(index)
                                            ?.url
                                            .orEmpty(),
                                        width = image.width,
                                        height = image.height,
                                    ),
                                )
                            }
                    },
                    likeCount = voteUpCount,
                    excerptTitle = summary,
                ),
            )
        }

        // 手机版卡片同样要能参与质量过滤，因此为非想法类型补出带赞数的 target。
        // 回答在导航层也表示为 Article（type = Answer），这里按 type 分成两种 target。
        is Article -> {
            val cardAuthor = mobileCardAuthor(authorName, avatar)
            if (cardAuthor == null) {
                // 没有作者信息时无法判断「是否已关注」，不做过滤，保持原样展示。
                null
            } else if (routeDest.type == ArticleType.Answer) {
                CommonFeed(
                    id = card["id"]?.jsonPrimitive?.content.orEmpty(),
                    target = Feed.AnswerTarget(
                        id = routeDest.id,
                        url = routeUrl,
                        author = cardAuthor,
                        voteupCount = voteUpCount,
                        excerpt = summary,
                        question = Feed.QuestionTarget(
                            id = routeDest.id,
                            url = routeUrl,
                            type = "question",
                        ),
                    ),
                )
            } else {
                CommonFeed(
                    id = card["id"]?.jsonPrimitive?.content.orEmpty(),
                    target = Feed.ArticleTarget(
                        id = routeDest.id,
                        url = routeUrl,
                        author = cardAuthor,
                        voteupCount = voteUpCount,
                        title = title,
                        excerpt = summary,
                    ),
                )
            }
        }

        else -> null
    }

    return FeedDisplayItem(
        navDestinationJson = routeDest.toFeedDisplayItemNavDestinationJson(),
        avatarSrc = avatar,
        authorName = authorName,
        summary = summary,
        title = title,
        details = "$footerText · 手机版推荐",
        feed = feed,
    )
}

@Serializable
private data class MobileHomeCardExtra(
    val businessExtMap: MobileHomeBusinessExtMap = MobileHomeBusinessExtMap(),
    val passthroughInfo: MobileHomePassthroughInfo? = null,
)

@Serializable
private data class MobileHomeBusinessExtMap(
    val oriContent: String = "",
    val images: List<MobileHomeImage> = emptyList(),
)

@Serializable
private data class MobileHomePassthroughInfo(
    val author: MobileHomeAuthor? = null,
    val content: MobileHomeContent? = null,
)

@Serializable
private data class MobileHomeAuthor(
    val id: String = "",
    val url: String = "",
    val urlToken: String? = null,
    val isFollowing: Boolean = false,
    val isFollowed: Boolean = false,
)

@Serializable
private data class MobileHomeContent(
    val title: String? = null,
)

@Serializable
private data class MobileHomeOriginalContent(
    val mediaInfo: MobileHomeMediaInfo? = null,
)

@Serializable
private data class MobileHomeMediaInfo(
    val images: List<MobileHomeImage> = emptyList(),
)

@Serializable
private data class MobileHomeImage(
    val url: String = "",
    val width: Int = 0,
    val height: Int = 0,
)

/**
 * 手机版推荐卡片只提供作者名和头像，没有稳定的作者 id。
 *
 * 质量过滤只读取 `isFollowing`（已关注作者豁免）和展示用的名称/头像，
 * 因此这里用作者名兜底作为 id；没有作者信息时返回 null，表示「未知作者」。
 */
private fun mobileCardAuthor(authorName: String, avatar: String): Person? {
    if (authorName.isBlank()) return null
    return Person(
        id = authorName,
        url = "",
        userType = "people",
        name = authorName,
        headline = "",
        avatarUrl = avatar,
    )
}

/**
 * Find the first JsonObject in the list where the value associated with [key] matches [value].
 */
@Suppress("NOTHING_TO_INLINE")
private inline fun List<JsonObject>.joStrMatch(key: String, value: String): JsonObject =
    this.firstOrNull { it[key]?.jsonPrimitive?.content == value }
        ?: throw IllegalStateException("No matching JsonObject found for $key = $value: $this")
