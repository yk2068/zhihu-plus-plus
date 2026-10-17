/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 */

package com.github.zly2006.zhihu.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.platform.isPageTurnSupported
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.ui.subscreens.DEFAULT_PAGE_TURN_PERCENT
import com.github.zly2006.zhihu.ui.subscreens.DEFAULT_SHOW_PAGE_TURN_GUIDE
import com.github.zly2006.zhihu.ui.subscreens.PREF_PAGE_TURN_PERCENT
import com.github.zly2006.zhihu.ui.subscreens.PREF_SHOW_PAGE_TURN_GUIDE
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect

enum class PageTurnCommand {
    PageUp,
    PageDown,
    JumpToTop,
    JumpToBottom,
}

/** Routes each command to the most recently registered eligible scroll surface. */
class PageTurnDispatcher {
    private val targets = mutableListOf<PageTurnTargetRegistration>()

    var hasActiveTarget by mutableStateOf(false)
        private set

    internal fun registerTarget(): PageTurnTargetRegistration {
        val registration = PageTurnTargetRegistration(this)
        targets += registration
        hasActiveTarget = true
        return registration
    }

    fun dispatch(command: PageTurnCommand): Boolean =
        targets
            .lastOrNull()
            ?.commands
            ?.trySend(command)
            ?.isSuccess == true

    internal fun unregisterTarget(registration: PageTurnTargetRegistration) {
        targets.remove(registration)
        registration.commands.close()
        hasActiveTarget = targets.isNotEmpty()
    }
}

internal class PageTurnTargetRegistration(
    private val dispatcher: PageTurnDispatcher,
) {
    val commands = Channel<PageTurnCommand>(Channel.BUFFERED)
    private var closed = false

    fun close() {
        if (closed) return
        closed = true
        dispatcher.unregisterTarget(this)
    }
}

val LocalPageTurnDispatcher = staticCompositionLocalOf(::PageTurnDispatcher)

/** Shared runtime configuration and optional guide state for targets under one dispatcher. */
internal class PageTurnRuntimeState(
    val dispatcher: PageTurnDispatcher,
    pageTurnPercent: Int,
    showGuide: Boolean,
    val guideColor: Color,
) {
    var pageTurnPercent by mutableIntStateOf(pageTurnPercent)
    var showGuide by mutableStateOf(showGuide)
    var lastPageTurnDirection by mutableIntStateOf(0)
    var pageTurnScrollInProgress by mutableStateOf(false)
}

@Composable
internal fun rememberPageTurnRuntimeState(): PageTurnRuntimeState {
    val dispatcher = LocalPageTurnDispatcher.current
    val settings = rememberSettingsStore()
    val guideColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val state = remember(dispatcher, guideColor) {
        PageTurnRuntimeState(
            dispatcher = dispatcher,
            pageTurnPercent = settings
                .getInt(PREF_PAGE_TURN_PERCENT, DEFAULT_PAGE_TURN_PERCENT)
                .coerceIn(50, 100),
            showGuide = settings.getBoolean(PREF_SHOW_PAGE_TURN_GUIDE, DEFAULT_SHOW_PAGE_TURN_GUIDE),
            guideColor = guideColor,
        )
    }
    DisposableEffect(settings, state) {
        val subscription = settings.observeKeyChanges { key ->
            when (key) {
                PREF_PAGE_TURN_PERCENT ->
                    state.pageTurnPercent = settings
                        .getInt(PREF_PAGE_TURN_PERCENT, DEFAULT_PAGE_TURN_PERCENT)
                        .coerceIn(50, 100)
                PREF_SHOW_PAGE_TURN_GUIDE ->
                    state.showGuide =
                        settings.getBoolean(PREF_SHOW_PAGE_TURN_GUIDE, DEFAULT_SHOW_PAGE_TURN_GUIDE).also { showGuide ->
                            if (!showGuide) state.lastPageTurnDirection = 0
                        }
            }
        }
        onDispose(subscription::close)
    }
    return state
}

/** Registers the target only while its owning page is active and consumes commands in composition scope. */
@Composable
private fun PageTurnTargetRegistrationEffect(
    dispatcher: PageTurnDispatcher,
    active: Boolean,
    onCommand: suspend (PageTurnCommand) -> Unit,
) {
    val currentHandler by rememberUpdatedState(onCommand)
    var registration by remember(dispatcher) { mutableStateOf<PageTurnTargetRegistration?>(null) }

    DisposableEffect(dispatcher, active) {
        if (active) registration = dispatcher.registerTarget()
        onDispose {
            registration?.close()
            registration = null
        }
    }
    registration?.let { target ->
        LaunchedEffect(target) {
            for (command in target.commands) currentHandler(command)
        }
    }
}

/** Couples a page-turn command handler with the measured viewport used to calculate one-page distance. */
@Stable
class PageTurnTarget internal constructor(
    internal val state: PageTurnRuntimeState,
) {
    internal var viewportHeight = 0f
}

/**
 * Reports this scroll surface's visible height to [target] and, when enabled in settings, draws the
 * overlap guide left by the latest page turn. This modifier must wrap the actual scrolling viewport.
 */
fun Modifier.pageTurnViewportWithGuide(target: PageTurnTarget): Modifier =
    drawWithContent {
        drawContent()
        target.viewportHeight = size.height
        val state = target.state
        if (state.showGuide && state.lastPageTurnDirection != 0) {
            val overlapFraction = 1f - state.pageTurnPercent / 100f
            val y = if (state.lastPageTurnDirection > 0) {
                overlapFraction * size.height
            } else {
                state.pageTurnPercent / 100f * size.height
            }
            drawLine(
                color = state.guideColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
            )
        }
    }

/**
 * Creates a target for a continuous [ScrollState]. Boundary callbacks optionally turn an extra page command at the
 * start or end into navigation to adjacent content.
 */
@Composable
fun rememberPageTurnTarget(
    scrollState: ScrollState,
    enabled: Boolean,
    maxScrollValue: Int = scrollState.maxValue,
    onPageUpAtStart: (() -> Unit)? = null,
    onPageDownAtEnd: (() -> Unit)? = null,
): PageTurnTarget {
    val state = rememberPageTurnRuntimeState()
    val target = remember(state) { PageTurnTarget(state) }
    val currentOnPageUpAtStart by rememberUpdatedState(onPageUpAtStart)
    val currentOnPageDownAtEnd by rememberUpdatedState(onPageDownAtEnd)
    val currentMaxScrollValue by rememberUpdatedState(maxScrollValue)
    LaunchedEffect(state, scrollState) {
        snapshotFlow { scrollState.isScrollInProgress }.collect { scrolling ->
            if (scrolling && !state.pageTurnScrollInProgress) state.lastPageTurnDirection = 0
        }
    }
    PageTurnTargetRegistrationEffect(state.dispatcher, enabled && isPageTurnSupported) { command ->
        state.lastPageTurnDirection = command.scrollDirection.takeIf { state.showGuide } ?: 0
        state.pageTurnScrollInProgress = true
        try {
            when (command) {
                PageTurnCommand.JumpToTop -> scrollState.scrollTo(0)
                PageTurnCommand.JumpToBottom -> scrollState.scrollTo(currentMaxScrollValue)
                PageTurnCommand.PageUp,
                PageTurnCommand.PageDown,
                -> {
                    val reachedStart = command == PageTurnCommand.PageUp && scrollState.value <= 0
                    val reachedEnd = command == PageTurnCommand.PageDown &&
                        currentMaxScrollValue != Int.MAX_VALUE &&
                        scrollState.value >= currentMaxScrollValue
                    when {
                        reachedStart && currentOnPageUpAtStart != null -> currentOnPageUpAtStart?.invoke()
                        reachedEnd && currentOnPageDownAtEnd != null -> currentOnPageDownAtEnd?.invoke()
                        target.viewportHeight > 0f -> {
                            scrollState.scrollBy(
                                target.viewportHeight * state.pageTurnPercent / 100f * command.scrollDirection,
                            )
                        }
                    }
                }
            }
        } finally {
            state.pageTurnScrollInProgress = false
        }
    }
    return target
}

/** Creates a target for a lazy list while preserving ownership of [listState] in the calling page. */
@Composable
fun rememberPageTurnTarget(
    listState: LazyListState,
    enabled: Boolean,
): PageTurnTarget {
    val state = rememberPageTurnRuntimeState()
    val target = remember(state) { PageTurnTarget(state) }
    LaunchedEffect(state, listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling && !state.pageTurnScrollInProgress) state.lastPageTurnDirection = 0
        }
    }
    PageTurnTargetRegistrationEffect(state.dispatcher, enabled && isPageTurnSupported) { command ->
        state.lastPageTurnDirection = command.scrollDirection.takeIf { state.showGuide } ?: 0
        state.pageTurnScrollInProgress = true
        try {
            when (command) {
                PageTurnCommand.JumpToTop -> listState.scrollToItem(0)
                PageTurnCommand.JumpToBottom -> {
                    val lastIndex = listState.layoutInfo.totalItemsCount - 1
                    if (lastIndex >= 0) listState.scrollToItem(lastIndex)
                }
                PageTurnCommand.PageUp,
                PageTurnCommand.PageDown,
                -> {
                    if (target.viewportHeight > 0f) {
                        listState.scrollBy(
                            target.viewportHeight * state.pageTurnPercent / 100f * command.scrollDirection,
                        )
                    }
                }
            }
        } finally {
            state.pageTurnScrollInProgress = false
        }
    }
    return target
}

private val PageTurnCommand.scrollDirection: Int
    get() = when (this) {
        PageTurnCommand.PageUp -> -1
        PageTurnCommand.PageDown -> 1
        PageTurnCommand.JumpToTop,
        PageTurnCommand.JumpToBottom,
        -> 0
    }
