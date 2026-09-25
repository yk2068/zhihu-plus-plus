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

package com.github.zly2006.zhihu.ui.article

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest

@Stable
internal class ArticleTopBarState {
    val offset = Animatable(0f)
    var heightPx by mutableFloatStateOf(0f)
    internal var previousScrollValue by mutableIntStateOf(0)
}

/**
 * 顶栏位移跟随滚动位置。
 *
 * 手感约定：**不做二值吸附**。
 *
 * 此前的实现会在手指松开后按「偏移是否过半」把顶栏 `animateTo` 到全隐或全显，
 * 这一步在快速滑动时表现为突然跳一下；手指停在中间时也会被强行吸走。
 * 现在偏移只由滚动位置决定：滑到哪就是哪，回滚时按同样规律连续恢复。
 *
 * 唯一保留的边界处理是 [ScrollState.maxValue] 附近：接近底部时顶栏回到完全可见，
 * 避免内容到底后顶栏停在半隐状态。
 */
@Composable
internal fun rememberArticleTopBarState(
    scrollState: ScrollState,
    autoHide: Boolean,
): ArticleTopBarState {
    val state = remember { ArticleTopBarState() }

    LaunchedEffect(autoHide) {
        if (!autoHide) state.offset.snapTo(0f)
    }
    LaunchedEffect(scrollState, autoHide) {
        snapshotFlow { scrollState.value }.collectLatest { currentScroll ->
            val delta = currentScroll - state.previousScrollValue
            if (currentScroll == 0) {
                state.offset.snapTo(0f)
            } else if (autoHide && state.heightPx > 0f) {
                val deltaBasedOffset = (state.offset.value - delta).coerceIn(-state.heightPx, 0f)
                val distanceFromBottom = (scrollState.maxValue - currentScroll).coerceAtLeast(0)
                if (distanceFromBottom < state.heightPx.toInt()) {
                    val distanceBasedOffset = (-distanceFromBottom.toFloat()).coerceIn(-state.heightPx, 0f)
                    state.offset.snapTo(maxOf(distanceBasedOffset, deltaBasedOffset))
                } else {
                    state.offset.snapTo(deltaBasedOffset)
                }
            }
            state.previousScrollValue = currentScroll
        }
    }

    return state
}
